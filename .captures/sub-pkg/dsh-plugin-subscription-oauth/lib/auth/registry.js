/**
 * The provider registry: one erased façade over every route's controller.
 *
 * The RPC layer talks to this object only, so it never has to know how many
 * providers exist, what their routes are called, or which login shape each one
 * uses. Each route's controller is generic over its own session type; this
 * façade is the single place that erasure happens.
 *
 * @module dsh-plugin-subscription-oauth/auth/registry
 */
import { BAD_REQUEST, ProviderError } from '../errors.js';
/**
 * Where a route sits in the page.
 *
 * `auto` is a way to reach every other route, so it leads the list; the routes it
 * stands in for keep the order they were configured in. Registration order is
 * therefore not the reading order, and this is the one place that says so.
 *
 * @param id - the route key.
 * @returns the sort rank; lower reads first.
 */
function displayRank(id) {
    return id === 'auto' ? 0 : 1;
}
/**
 * Routes every Settings call to the provider it names.
 *
 * A provider key the plugin does not serve is a request error, not an internal
 * failure: the message names the routes that do exist.
 */
export class SubscriptionRegistry {
    preferences;
    byId;
    constructor(routes, preferences) {
        this.preferences = preferences;
        this.byId = new Map(routes.map((route) => [route.id, route]));
    }
    /** Route keys this registry serves, in display order. */
    ids() {
        return [...this.byId.keys()];
    }
    /** Every route, in registration order. */
    all() {
        return [...this.byId.values()];
    }
    /** One route by key, without the request-error path. */
    get(id) {
        return this.byId.get(id);
    }
    /**
     * Add one route.
     *
     * `auto` is built last on purpose: it stands in for the others, so it is
     * registered once their catalogs exist, without a circular construction order.
     * That is a construction detail, not a display one — the page shows `auto`
     * first, so the reading order is stated here rather than inherited from the
     * order the routes happened to be built in.
     *
     * @param route - the route to serve from now on.
     * @throws {ProviderError} when the key is already served.
     */
    register(route) {
        if (this.byId.has(route.id)) {
            throw new ProviderError(`provider ${route.id} is already registered`, BAD_REQUEST);
        }
        this.byId.set(route.id, route);
        // Re-insert in display order: a leading route (Auto) is built last but read first.
        const ordered = [...this.byId.entries()].sort(([left], [right]) => displayRank(left) - displayRank(right));
        this.byId.clear();
        for (const [id, entry] of ordered)
            this.byId.set(id, entry);
    }
    /** One route, or a request error naming the known ones. */
    route(provider) {
        const found = this.byId.get(provider);
        if (found === undefined) {
            throw new ProviderError(`unknown provider ${JSON.stringify(provider)}; this plugin serves ${[...this.byId.keys()].join(', ')}`, BAD_REQUEST);
        }
        return found;
    }
    /** Every provider's state, in registration order. */
    async status() {
        return { providers: await Promise.all([...this.byId.values()].map((route) => route.status())) };
    }
    /** Start a login on one route. */
    async login(provider, account) {
        const started = await this.route(provider).login(account);
        return { prompt: started.prompt, status: await this.status() };
    }
    /** Abandon one route's pending login. */
    async cancel(provider) {
        await this.route(provider).cancel();
        return this.status();
    }
    /** Forget one account of one route. */
    async logout(provider, account) {
        await this.route(provider).logout(account);
        return this.status();
    }
    /** Promote one account to default. */
    async setDefault(provider, account) {
        await this.route(provider).setDefault(account);
        return this.status();
    }
    /** Refresh one account's token now. */
    async refresh(provider, account) {
        const result = await this.route(provider).refresh(account);
        return { account: result.account, expiresAt: result.expiresAt, status: await this.status() };
    }
    /** Re-read one route's live model list. */
    async discover(provider, force = false) {
        await this.route(provider).discover(force);
        return this.status();
    }
    /** Set one model's picker visibility. */
    async setModelVisible(provider, id, visible) {
        await this.route(provider).setModelVisible(id, visible);
        return this.status();
    }
    /** Replace one route's model order with one dropped arrangement. */
    async setModelOrder(provider, ids) {
        await this.route(provider).setModelOrder(ids);
        return this.status();
    }
    /** Add one model id by hand to one route. */
    async addModel(provider, id) {
        await this.route(provider).addModel(id);
        return this.status();
    }
    /** Forget one hand-added model id. */
    async removeModel(provider, id) {
        await this.route(provider).removeModel(id);
        return this.status();
    }
    /** Read one route's plan and metered allowances. */
    async usage(provider, force = false) {
        const route = this.route(provider);
        const result = await route.usage(force);
        return {
            provider: route.id,
            usage: result.usage,
            ...result.error === undefined ? {} : { error: result.error },
            status: await this.status(),
        };
    }
    /** Re-read every credential file and the shared preferences. */
    async reload() {
        await Promise.all([...this.byId.values()].map((route) => route.reload()));
        return this.status();
    }
    /** The fast and image variants for one model, as the route's discovery reported them. */
    async modelVariants(provider, id) {
        const route = this.route(provider);
        return route.modelVariants?.(id);
    }
    /** Whether the `auto` provider is offered; flips it. */
    async autoState() {
        return { enabled: (await this.preferences.autoPreferences()).enabled };
    }
    async setAutoOn(enabled) {
        await this.preferences.setAutoOn(enabled);
        return { enabled };
    }
    /** Claim one account's daily check-in reward, when the route supports it. */
    async checkin(provider, account) {
        const route = this.route(provider);
        if (route.checkin === undefined)
            throw new ProviderError(`${provider} has no check-in`, BAD_REQUEST);
        return route.checkin(account);
    }
    /** Read one account's growth tasks and optionally auto-claim the claimable rewards. */
    async tasks(provider, account, claim) {
        const route = this.route(provider);
        if (route.tasks === undefined)
            throw new ProviderError(`${provider} has no tasks`, BAD_REQUEST);
        return route.tasks(account, claim);
    }
}
