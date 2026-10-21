/**
 * How one named credential resolves to a value.
 *
 * The host's credential service owns this; the RPC layer only calls it, so the balance
 * query does not re-derive how a key is stored.
 */
export type CredentialResolver = (ref: string) => Promise<string | undefined>;
/**
 * The balance a non-subscription provider reports, and how to read it.
 *
 * A provider that is not a subscription this plugin serves has no usage windows, so the
 * chip shows its balance instead. The balance is read by cc-switch's query, which needs
 * the provider's API key; the provider's profile names that key by reference
 * (`apiKeyEnv`), and this map is the one place a provider id is tied to that reference.
 */
export interface BalanceContext {
    /** The credential reference each provider id's balance query uses. */
    readonly credentialRefs: Readonly<Record<string, string>>;
    /** The base URL each provider id's balance query reads, when it is not DeepSeek's own. */
    readonly baseUrls: Readonly<Record<string, string>>;
    /** Resolve one credential reference to its key. */
    readonly resolveCredential?: CredentialResolver;
    /**
     * Resolve the host's DeepSeek account service, read lazily per request.
     *
     * The service may register after this plugin applies, so it is resolved when a
     * balance is actually read, not once at startup. Returns undefined when the
     * composition has no such service.
     */
    readonly resolveDeepSeekAccount?: () => DeepSeekAccountService | undefined;
}
/**
 * The host-side transport behind the Settings page.
 *
 * Each endpoint is served as its own `POST /api/subscription-oauth.<endpoint>`
 * Fetch route on the `connection` service. `connection` is deliberately not in
 * the plugin's `inject` list: a headless composition has no connection service,
 * and the provider routes must still work there. Nothing else is registered
 * when it is absent, so the browser half simply finds no routes.
 *
 * @module dsh-plugin-subscription-oauth/auth/rpc
 */
import type { Context } from '@deepseek-ai/cordis';
import type { DeepSeekAccountService } from './deepseek-balance.js';
import type { RpcResult } from '../protocol.js';
import type { SubscriptionRegistry } from './registry.js';
/**
 * Run one endpoint against the registry, converting any failure into the
 * envelope's error branch — a route handler therefore never throws.
 *
 * @param registry - every registered provider route.
 * @param endpoint - endpoint name, without the prefix.
 * @param payload - the request's decoded payload.
 * @returns the envelope result.
 */
export declare function dispatchRpc(registry: SubscriptionRegistry, endpoint: string, payload: unknown, balance?: BalanceContext): Promise<RpcResult>;
/**
 * Register every endpoint route on the `connection` service, when one exists.
 *
 * @param ctx - the plugin's context.
 * @param registry - the provider routes every route dispatches through.
 */
export declare function registerSubscriptionRpc(ctx: Context, registry: SubscriptionRegistry, balance?: BalanceContext): void;
