/**
 * Loopback OAuth: PKCE helpers plus a temporary local redirect listener.
 *
 * Providers that complete authorization in the browser (xAI, Google) redirect to
 * a `127.0.0.1` URL this process owns for the duration of one attempt. The
 * authorization code never leaves the machine, and `state` is validated before
 * the code is accepted so a stray or replayed redirect cannot settle the login.
 *
 * @module dsh-plugin-subscription-oauth/auth/pkce-flow
 */
import type { AuthAttempt, AuthFlow } from './flow.js';
import type { SessionBase } from './store.js';
/** One PKCE verifier/challenge pair; the verifier stays local until exchange. */
export interface PkcePair {
    verifier: string;
    challenge: string;
}
/** Mint a fresh PKCE pair (32-byte verifier, S256 challenge). */
export declare function createPkce(): PkcePair;
/** Mint a random OAuth `state` value. */
export declare function randomState(): string;
/** Inputs an authorize-URL builder needs for one attempt. */
export interface LoopbackAuthorizeInput {
    /** Loopback redirect URI pointing at the temporary listener. */
    redirectUri: string;
    state: string;
    pkce: PkcePair;
}
/** Inputs a token exchange needs once the code arrives. */
export interface LoopbackExchangeInput<S extends SessionBase> {
    code: string;
    redirectUri: string;
    pkce: PkcePair;
    state: string;
    /** Aborts when the attempt is cancelled or times out. */
    signal: AbortSignal;
}
/** Static facts of one provider's loopback flow. */
export interface LoopbackFlowSpec<S extends SessionBase> {
    /** Ports tried in order; the first free one wins. */
    readonly ports: readonly number[];
    /** Path the provider redirects to. */
    readonly callbackPath: string;
    /** Attempt lifetime; defaults to three minutes. */
    readonly timeoutMs?: number;
    /**
     * Resolve anything `buildAuthorizeUrl` needs but only an async step can fetch,
     * such as OIDC discovery endpoints. Called before the listener is opened.
     */
    prepare?(): Promise<void>;
    /** Build the URL the user's browser opens. */
    buildAuthorizeUrl(input: LoopbackAuthorizeInput): string;
    /** Exchange the authorization code for a session. */
    exchange(input: LoopbackExchangeInput<S>): Promise<S>;
}
/** Attempt lifetime when a spec names none. */
export declare const DEFAULT_FLOW_TIMEOUT_MS = 180000;
/** Message every cancelled attempt rejects with. */
export declare const LOGIN_CANCELLED_MESSAGE = "login cancelled";
/**
 * Owns at most one in-flight loopback login per provider.
 *
 * A listener is created per attempt and closed as soon as the attempt settles,
 * so nothing stays open on this machine between logins.
 */
export declare class LoopbackFlow<S extends SessionBase> implements AuthFlow<S> {
    private readonly key;
    private readonly spec;
    private readonly host;
    private readonly attempts;
    /**
     * @param key - provider route key, so one attempt runs at a time per route.
     * @param spec - static flow facts for this provider.
     */
    constructor(key: string, spec: LoopbackFlowSpec<S>, host?: string);
    pending(): AuthAttempt<S> | undefined;
    start(): Promise<AuthAttempt<S>>;
}
