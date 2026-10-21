/**
 * Durable credentials, keyed by provider.
 *
 * The file mirrors the subscriptions plugin's layout so both plugins' token
 * files stay recognisable to a user browsing `~/.dsh/plugins`: a JSON object
 * keyed by provider, each entry holding a `default` account key and an
 * `accounts` record. Writes are atomic (temp file plus rename) with owner-only
 * permissions, so a crash mid-write cannot leave a truncated credential file.
 *
 * The store is generic over the session shape: each provider declares its own
 * session fields plus the parser that validates them, while account keying,
 * defaults, and the file lifecycle stay shared.
 *
 * @module dsh-plugin-subscription-oauth/auth/store
 */
/** Fields every provider's stored session carries. */
export interface SessionBase {
    accessToken: string;
    /** Refresh token; absent for a token set the service issued without one. */
    refreshToken?: string;
    /** Epoch milliseconds after which `accessToken` must not be sent. */
    expiresAt: number;
    /** Service-disclosed account identity, when the tokens carry one. */
    accountId?: string;
    email?: string;
}
/** True for a plain object usable as a JSON record. */
export declare function isRecord(value: unknown): value is Record<string, unknown>;
/** Read a non-empty trimmed string, or undefined. */
export declare function nonEmpty(value: unknown): string | undefined;
/** Read a finite epoch-millisecond value, or undefined. */
export declare function finiteInstant(value: unknown): number | undefined;
/**
 * Derive the account key for one session: the identity the service disclosed,
 * falling back to a digest of the refresh token so distinct logins stay
 * distinct even when both tokens are opaque.
 * @param session - the session to key.
 * @returns a stable account key.
 */
export declare function accountKeyOf(session: SessionBase): string;
/** Label shown for one account in the Settings page. */
export declare function accountLabelOf(session: SessionBase): string;
/**
 * Owns the on-disk credential file for one provider.
 *
 * In-memory state is authoritative between writes: a failed read leaves the
 * store empty rather than throwing, so a corrupt file degrades to "logged out"
 * instead of breaking plugin startup.
 */
export declare class TokenStore<S extends SessionBase> {
    private readonly file;
    private readonly provider;
    private readonly parse;
    private entries;
    private loaded;
    /**
     * The in-flight load, shared by every caller.
     *
     * Without this, a second caller saw `loaded` already true, skipped the read and
     * wrote its own state — while the first read was still on its way back and then
     * replaced that state with the file it had read a moment earlier. A sign-in that
     * settled quickly enough to overlap the first load was therefore wiped: the
     * account was saved in memory, the pending read overwrote it, and the next write
     * persisted the empty document. A flow whose session arrives in milliseconds,
     * like GLM's polled one, hits that window every time.
     */
    private loading;
    /**
     * @param file - absolute path of the credential file.
     * @param provider - section key this store owns.
     * @param parse - validates and coerces one stored account into a session.
     */
    constructor(file: string, provider: string, parse: (value: unknown) => S | undefined);
    /** Load the file once; later calls share the same in-flight read. */
    private ensureLoaded;
    /** Read the document from disk into memory. */
    private read;
    /** Re-read the file from disk, discarding in-memory changes. */
    reload(): Promise<void>;
    private providerEntry;
    /** Every stored session, keyed by account. */
    list(): Promise<Record<string, S>>;
    /** The default account key, falling back to the only stored account. */
    defaultAccount(): Promise<string | undefined>;
    /** Read one account's session. */
    get(account?: string): Promise<S | undefined>;
    /** Whether any account is stored. */
    hasAny(): Promise<boolean>;
    /** Insert or replace one account, making it the default when none is set. */
    save(key: string, session: S, options?: {
        makeDefault?: boolean;
    }): Promise<void>;
    /** Promote one account to default. */
    setDefault(key: string): Promise<void>;
    /** Remove one account; the default moves to a remaining account when any. */
    remove(key: string): Promise<void>;
    /** Write the file atomically with owner-only permissions. */
    private persist;
}
