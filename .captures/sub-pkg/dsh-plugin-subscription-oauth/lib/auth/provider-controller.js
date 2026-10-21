/**
 * The per-provider login/lifecycle controller: the single place one route's
 * Settings state is derived from.
 *
 * A login is completed by the *browser*, so `login()` returns as soon as a
 * prompt exists and the attempt settles in the background; every later
 * `status()` call reports whether it is still pending, and a failure is recorded
 * so the card can show why instead of silently returning to "logged out".
 *
 * Everything provider-specific arrives through {@link ProviderSpec}, the token
 * manager, and the login flow, so the same controller serves a device-flow
 * provider and a loopback-redirect provider.
 *
 * @module dsh-plugin-subscription-oauth/auth/provider-controller
 */
import { ACCOUNT_NOT_FOUND, BAD_REQUEST, MODEL_NOT_FOUND, REAUTH_REQUIRED, ProviderError } from '../errors.js';
import { entryMatches, resolveCatalog } from '../providers/catalog.js';
import { accountLabelOf, accountKeyOf } from './store.js';
import { allowanceScheduler } from './allowance-scheduler.js';
/** Human-readable message for any thrown value. */
function messageOf(error) {
    return error instanceof Error ? error.message : String(error);
}
/**
 * Owns one provider route's login lifecycle and its Settings state.
 */
/**
 * Whether a failure means the stored grant can never be renewed.
 *
 * OAuth answers `invalid_grant` once a refresh token is revoked, expired or
 * already used — the one case where retrying cannot help and the user must sign in
 * again, so it is worth distinguishing from a transient failure.
 *
 * @param message - the failure text.
 * @returns whether the login is dead.
 */
function isDeadGrant(message) {
    return /invalid_grant|invalid grant|authorization grant is invalid|refresh token (?:is )?(?:invalid|expired|revoked)/i.test(message);
}
/** Context advertised for a hand-added id on a route that states none. */
const DEFAULT_FALLBACK_CONTEXT_WINDOW = 262_144;
/**
 * How long a usage reading may be reused without asking again.
 *
 * Short on purpose: it exists to collapse a burst of reads (a card opening, a
 * poll and a refresh arriving together) into one request, not to keep a figure.
 * Allowances move minute to minute, so anything longer makes the page report the
 * state of an older read as if it were current.
 */
const USAGE_FRESH_MS = 30_000;
export class ProviderController {
    options;
    /** Live catalog ids, once discovery succeeded this session. */
    discovered;
    /** Last background failure a user should see. */
    lastError;
    /** Most recent eager-refresh failure, cleared once a refresh succeeds. */
    lastRefreshError;
    /**
     * Usage figures recently read, keyed by account.
     *
     * The entry is a short-lived coalescing cache, not a stored reading: an
     * allowance moves as the account is used, so a figure kept for the whole
     * session would keep answering with the state of the first read and the card
     * would look like it never noticed an update.
     */
    usageByAccount = new Map();
    /** Why the last usage read failed, when it did. */
    usageError;
    /** Accounts whose grant the provider refused, so only a new login helps. */
    refused = new Set();
    constructor(options) {
        this.options = options;
    }
    /** Provider route key. */
    get id() {
        return this.options.spec.id;
    }
    /** Human name for selectors and diagnostics. */
    get displayName() {
        return this.options.spec.displayName;
    }
    /** Absolute path of the credential file this route reads. */
    get credentialFile() {
        return this.options.spec.credentialFile;
    }
    /** Current state of the route. */
    async status() {
        // A status read must never trigger a network request: the settings page polls it,
        // and a refresh here would mean every poll is a token call. The token is refreshed
        // lazily when a request actually needs it (or by discover/usage below).
        const accounts = await this.options.store.list();
        const defaultAccount = await this.options.store.defaultAccount();
        const pending = this.options.flow.pending();
        const keys = Object.keys(accounts);
        const spec = this.options.spec;
        // A stored token within its life is reported as fresh; a lapsed one reads as expired
        // until a real request refreshes it. This is honest (the stored expiry is what we
        // know) and avoids a network call on every status poll.
        return {
            provider: spec.id,
            displayName: spec.displayName,
            ...spec.codeUrl === undefined ? {} : { codeUrl: spec.codeUrl },
            loggedIn: keys.length > 0,
            busy: pending !== undefined,
            ...pending === undefined ? {} : { pending: pending.prompt },
            accounts: keys.map((account) => {
                const session = accounts[account];
                return {
                    account,
                    label: accountLabelOf(session),
                    expiresAt: session.expiresAt,
                    expired: session.expiresAt <= Date.now(),
                    isDefault: account === defaultAccount,
                    ...this.refused.has(account) ? { needsLogin: true } : {},
                };
            }),
            ...defaultAccount === undefined ? {} : { activeAccount: defaultAccount },
            credentialFile: spec.credentialFile,
            discovery: spec.discovery,
            ...this.discovered === undefined ? {} : { discovered: this.discovered },
            models: await this.modelSummaries(),
            ...spec.defaultModel === undefined ? {} : { defaultModel: spec.defaultModel },
            ...this.usageByAccount.size === 0 ? {} : { usage: [...this.usageByAccount.values()] },
            ...this.usageError === undefined ? {} : { usageError: this.usageError },
            details: spec.details(),
            ...this.lastError === undefined ? {} : { lastError: this.lastError },
        };
    }
    /**
     * Refresh the default account's token when it is at or near its margin.
     *
     * Best-effort by design: a transient failure leaves the stored session alone
     * and the next model call retries. A permanent failure is recorded so the card
     * can tell the user to sign in again instead of silently showing a dead token.
     */
    async refreshIfLapsing() {
        const account = await this.options.store.defaultAccount();
        if (account === undefined)
            return;
        const session = await this.options.store.get(account);
        if (session === undefined || session.refreshToken === undefined)
            return;
        try {
            await this.options.tokens.resolve(account);
            if (isReauthRequired(this.lastRefreshError))
                this.lastRefreshError = undefined;
        }
        catch (error) {
            this.lastRefreshError = error;
            if (isReauthRequired(error) || isDeadGrant(messageOf(error))) {
                this.lastError = `${this.displayName} sign-in has lapsed; connect again`;
            }
        }
    }
    /** The catalog as the page lists it, in the user's order. */
    async modelSummaries() {
        const [hidden, order, added] = await Promise.all([
            this.options.preferences.hiddenIds(this.options.spec.id),
            this.options.preferences.orderOf(this.options.spec.id),
            this.options.preferences.extraIds(this.options.spec.id),
        ]);
        const own = new Set((this.options.spec.catalogOwn?.() ?? this.options.spec.catalog()).map((entry) => entry.id));
        // A model the route serves is a synced row even while the manual entry that
        // introduced it is still stored, so the marker follows the route, not the file.
        const userAdded = new Set(added.filter((id) => !own.has(id)));
        const listed = this.resolveOrdered(hidden, order).ranked;
        const known = new Set(listed.map((entry) => entry.id));
        // A hand-added id the adapter has not merged yet is appended here, with the
        // conservative metadata this build can honestly state. The adapter carries the
        // same ids for the picker; this list must not depend on its async refresh.
        const rows = listed.map((entry) => {
            const fast = this.options.spec.fastVariant?.(entry.id);
            return {
                id: entry.id,
                name: entry.name,
                ...entry.description === undefined ? {} : { description: entry.description },
                contextWindow: entry.contextWindow,
                ...entry.inputModalities === undefined ? {} : { inputModalities: entry.inputModalities },
                ...entry.defaultEffort === undefined ? {} : { defaultEffort: entry.defaultEffort },
                visible: !hidden.has(entry.id),
                ...userAdded.has(entry.id) ? { userAdded: true } : {},
                ...fast === undefined ? {} : { fastVariant: fast },
            };
        });
        for (const id of added) {
            if (known.has(id) || own.has(id))
                continue;
            known.add(id);
            rows.push({
                id,
                name: id,
                description: 'Added by hand in Settings',
                contextWindow: this.options.spec.fallbackContextWindow ?? DEFAULT_FALLBACK_CONTEXT_WINDOW,
                visible: !hidden.has(id),
                userAdded: true,
            });
        }
        return rows;
    }
    /** This route's catalog after the user's order and visibility are applied. */
    resolveOrdered(hidden, order) {
        return resolveCatalog(this.options.spec.catalog(), order, hidden);
    }
    /**
     * Begin an interactive login, returning the prompt to display.
     *
     * The returned status is already `busy`, because the attempt is registered
     * before this resolves; the login itself settles in the background.
     *
     * @param account - existing account key to replace, for a re-login.
     * @returns the pending prompt plus a fresh status.
     */
    async login(account) {
        const attempt = await this.options.flow.start();
        this.lastError = undefined;
        // A new sign-in replaces the credential, so a previous refusal no longer
        // describes what is stored.
        this.refused.clear();
        void this.completeLogin(attempt.session, account);
        return { prompt: attempt.prompt, status: await this.status() };
    }
    /** Persist the account once the browser approves, or record why it did not. */
    async completeLogin(session, account) {
        try {
            const settled = await session;
            await this.options.store.save(account ?? accountKeyOf(settled), settled);
            this.lastError = undefined;
            // A fresh login can reveal models this build has not seen yet.
            // Discovery uses the stored token directly (no resolve), so it never opens
            // the browser from here.
            await this.discover(true);
        }
        catch (error) {
            if (isCancellation(error))
                return;
            this.lastError = messageOf(error);
            this.options.onWarn?.(`login failed: ${this.lastError}`);
        }
    }
    /** Abandon the pending authorization, when one exists. */
    async cancel() {
        this.options.flow.pending()?.cancel();
        return this.status();
    }
    /** Forget one account (the default one when none is named). */
    async logout(account) {
        this.refused.delete(account ?? '');
        const key = account ?? await this.options.store.defaultAccount();
        if (key !== undefined) {
            await this.options.store.remove(key);
            // Drop every trace of the signed-out account: the remembered allowance state (so
            // the scheduler stops treating it as live) and the cached usage figures (so the
            // card and chip stop showing its quota).
            allowanceScheduler.clear(this.options.spec.id, key);
            this.usageByAccount.delete(key);
        }
        return this.status();
    }
    /** Promote one account to default. */
    async setDefault(account) {
        const accounts = await this.options.store.list();
        if (accounts[account] === undefined) {
            throw new ProviderError(`${this.displayName} account ${account} is not signed in`, ACCOUNT_NOT_FOUND);
        }
        await this.options.store.setDefault(account);
        return this.status();
    }
    /**
     * Refresh one account's access token now.
     *
     * @param account - account to refresh; the default one when omitted.
     * @returns the refreshed expiry and a fresh status.
     * @throws {ProviderError} with `REAUTH_REQUIRED` when the account must sign in again.
     */
    async refresh(account) {
        const token = await this.options.tokens.resolve(account, true);
        this.lastError = undefined;
        return { account: token.account, expiresAt: token.expiresAt, status: await this.status() };
    }
    /**
     * Re-read the endpoint's live model list.
     *
     * @param force - fetch again even when a list was already read this session.
     * @returns a fresh status.
     */
    /**
     * Re-read the endpoint's live model list.
     *
     * @param force - fetch again even when a list was already read this session.
     * @returns a fresh status.
     */
    async discover(force = false) {
        const spec = this.options.spec;
        if (!spec.discovery)
            return this.status();
        if (!force && this.discovered !== undefined)
            return this.status();
        // A logged-out install has nothing to discover, and warning about it on
        // every startup would be noise.
        if (!await this.options.store.hasAny())
            return this.status();
        // Discovery reads the live catalog on startup so the picker shows what the account
        // actually serves without a manual refresh. A resolve must never open the browser:
        // a refresh failure is recorded as the route's last error, never as a login prompt.
        const discovered = [];
        for (const account of Object.keys(await this.options.store.list())) {
            // An unexpired token is used as-is; a lapsed one is refreshed in memory so the
            // catalog still loads on startup. The refreshed token is not written back to the
            // store here — a durable renewal still happens on the next real request.
            let session = await this.options.store.get(account);
            if (session === undefined)
                continue;
            if (session.expiresAt <= Date.now()) {
                try {
                    const refreshed = await this.options.tokens.resolve(account, true);
                    session = { ...session, accessToken: refreshed.accessToken, expiresAt: refreshed.expiresAt };
                }
                catch {
                    this.options.onWarn?.(`${spec.id} model discovery for ${account} skipped: token refresh failed`);
                    continue;
                }
            }
            const ids = await spec.discover(session.accessToken);
            if (ids !== undefined) {
                for (const id of ids)
                    if (!discovered.includes(id))
                        discovered.push(id);
            }
        }
        if (discovered.length > 0) {
            this.discovered = discovered;
            spec.applyDiscovery?.(discovered);
            // A model this route now serves is no longer hand-added: keeping the extra
            // entry would list it as manual and invite a pointless "remove" later.
            await this.forgetDiscovered(discovered);
            this.options.onCatalogChanged?.();
        }
        return this.status();
    }
    /**
     * Drop hand-added ids the route's own catalog now covers.
     *
     * Discovery is the authority on what the endpoint serves, so once it reports an
     * id the manual entry has done its job.
     *
     * @param ids - the ids discovery reported.
     */
    async forgetDiscovered(ids) {
        // Everything the route itself serves — this run's discovery plus whatever an
        // earlier one already put in the shipped catalog.
        const served = new Set(ids);
        for (const entry of this.options.spec.catalogOwn?.() ?? [])
            served.add(entry.id);
        for (const id of await this.options.preferences.extraIds(this.options.spec.id)) {
            if (served.has(id))
                await this.options.preferences.removeExtra(this.options.spec.id, id);
        }
    }
    /**
     * Set whether one model appears in the session model picker.
     *
     * The route is re-announced so the picker re-queries the catalog; a model
     * hidden here stays resolvable, so a session that already selected it keeps
     * working.
     *
     * @param id - model id as the catalog reports it.
     * @param visible - whether the picker should offer it.
     * @returns a fresh status.
     * @throws {ProviderError} with `MODEL_NOT_FOUND` when the id is not in the catalog.
     */
    async setModelVisible(id, visible) {
        const known = this.options.spec.catalog().some((entry) => entryMatches(entry, id))
            // A hand-added id is part of the list too, whether or not the adapter has
            // merged it into its own catalog yet.
            || (await this.options.preferences.extraIds(this.options.spec.id)).includes(id);
        if (!known) {
            throw new ProviderError(`${this.displayName} model ${id} is not in the catalog`, MODEL_NOT_FOUND);
        }
        if (await this.options.preferences.setVisible(this.options.spec.id, id, visible)) {
            this.options.onCatalogChanged?.();
        }
        return this.status();
    }
    /**
     * Replace this route's model order with the arrangement the user dropped into
     * place.
     *
     * The page sends the complete list it displays, so a drop is one atomic
     * statement of intent rather than a sequence of moves. Ids are validated
     * against the catalog first: a stale page could otherwise persist a list
     * naming a model this build no longer has.
     *
     * @param ids - every catalog model id, in the order the user arranged them.
     * @returns a fresh status.
     * @throws {ProviderError} when an id is not a rankable catalog model.
     */
    async setModelOrder(ids) {
        const catalog = this.options.spec.catalog();
        const rankable = new Set(catalog.map((entry) => entry.id));
        // Hand-added ids are rankable and droppable like any other row.
        for (const id of await this.options.preferences.extraIds(this.options.spec.id))
            rankable.add(id);
        const next = [];
        for (const id of ids) {
            if (!rankable.has(id)) {
                throw new ProviderError(`${this.displayName} model ${id} is not in the catalog`, MODEL_NOT_FOUND);
            }
            if (!next.includes(id))
                next.push(id);
        }
        // Ids the page did not send keep their catalog position after the ranked
        // ones, so a model added since the page loaded is never dropped.
        for (const entry of catalog) {
            if (!next.includes(entry.id))
                next.push(entry.id);
        }
        if (await this.options.preferences.setOrder(this.options.spec.id, next)) {
            this.options.onCatalogChanged?.();
        }
        return this.status();
    }
    /**
     * Add one model id by hand, for a model this build's catalog does not know yet.
     *
     * The endpoint is the only authority on which ids it serves, so the id is
     * checked for shape rather than against a list: a model released after this
     * build must be usable without waiting for a plugin update.
     *
     * @param id - the model id to publish on this route.
     * @returns a fresh status.
     * @throws {ProviderError} when the id is unusable or already known.
     */
    async addModel(id) {
        const trimmed = id.trim();
        if (!/^[A-Za-z0-9][A-Za-z0-9._:/[\]-]{0,127}$/.test(trimmed)) {
            throw new ProviderError(`"${id}" is not a usable model id`, BAD_REQUEST);
        }
        if (this.options.spec.catalog().some((entry) => entryMatches(entry, trimmed))) {
            throw new ProviderError(`${this.displayName} already offers ${trimmed}`, BAD_REQUEST);
        }
        // Already added by hand: the click is idempotent, not an error.
        const added = await this.options.preferences.extraIds(this.options.spec.id);
        if (added.includes(trimmed))
            return this.status();
        if (await this.options.preferences.addExtra(this.options.spec.id, trimmed)) {
            this.options.onCatalogChanged?.();
        }
        return this.status();
    }
    /**
     * Forget one hand-added model id.
     * @param id - the id to drop from this route.
     * @returns a fresh status.
     */
    async removeModel(id) {
        if (await this.options.preferences.removeExtra(this.options.spec.id, id)) {
            this.options.onCatalogChanged?.();
        }
        return this.status();
    }
    /**
     * Read every account's plan and metered allowances.
     *
     * A provider that states no usage answers with an empty list rather than an
     * error, and one account's failure never hides the others' figures.
     *
     * @param force - read again even when figures were already read this session.
     * @returns the figures plus a fresh status.
     */
    async usage(force = false) {
        const spec = this.options.spec;
        if (spec.usage === undefined) {
            return { usage: [], status: await this.status() };
        }
        const accounts = Object.keys(await this.options.store.list());
        const collected = [];
        let error;
        for (const account of accounts) {
            const cached = this.usageByAccount.get(account);
            // A reading is reused only briefly, and never when it was a substitute for
            // the route's own accounting: that one has to be replaced by a real read
            // before it can be shown again.
            if (!force && cached !== undefined && Date.now() - cached.fetchedAt < USAGE_FRESH_MS && cached.substitute !== true) {
                collected.push(cached);
                continue;
            }
            try {
                const token = await this.options.tokens.resolve(account);
                const read = await spec.usage(token.accessToken, account);
                if (read === undefined)
                    continue;
                const entry = {
                    account,
                    windows: read.windows,
                    fetchedAt: Date.now(),
                    ...read.plan === undefined ? {} : { plan: read.plan },
                    ...read.renewsAt === undefined ? {} : { renewsAt: read.renewsAt },
                    ...read.checkin === undefined ? {} : { checkin: read.checkin },
                    ...read.usageError === undefined ? {} : { substitute: true },
                };
                this.usageByAccount.set(account, entry);
                collected.push(entry);
                // A route may publish a substitute for its primary accounting and say so;
                // that note travels with the figures rather than being dropped here.
                if (read.usageError !== undefined && error === undefined)
                    error = read.usageError;
            }
            catch (caught) {
                const message = messageOf(caught);
                // A refresh answered with an invalid grant is terminal for this credential:
                // no retry can renew it, and saying "usage failed" would understate it.
                if (isDeadGrant(message)) {
                    this.refused.add(account);
                    error = `${spec.displayName} sign-in has expired; connect the account again`;
                }
                else {
                    error = message;
                }
            }
        }
        this.usageError = error;
        const status = await this.status();
        return { usage: collected, ...error === undefined ? {} : { error }, status };
    }
    /** Re-read the credential file and the preferences, after an external change. */
    async reload() {
        await this.options.store.reload();
        await this.options.preferences.reload();
        return this.status();
    }
}
/** Whether a login settled because the user cancelled it. */
function isCancellation(error) {
    return error instanceof Error && error.message === 'login cancelled';
}
/** Whether a failure means the stored credential can never be used again. */
function isReauthRequired(error) {
    return error instanceof ProviderError && error.code === REAUTH_REQUIRED;
}
