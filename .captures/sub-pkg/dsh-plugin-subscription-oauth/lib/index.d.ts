/**
 * Kimi Code, Grok (X Premium) and Google Antigravity subscription access for
 * DeepSeek Harness.
 *
 * Each provider route authorizes once through its own OAuth flow (Settings →
 * Subscriptions) and then streams over the wire that provider's subscription
 * exposes. Tokens live below `~/.dsh/plugins/kimi-oauth/`, one provider section
 * per route.
 *
 * @module dsh-plugin-subscription-oauth
 */
import type { Context } from '@deepseek-ai/cordis';
import z from '@deepseek-ai/schemastery';
/** Plugin id; also the name every effect label and log line is prefixed with. */
export declare const name = "dsh-plugin-subscription-oauth";
/** The provider routes need the LLM runtime; everything else is optional. */
export declare const inject: string[];
/** Every route key this plugin accepts in configuration. */
export declare const PROVIDER_IDS: readonly ["kimi", "grok", "antigravity", "glm", "ark", "mimo", "workbuddy"];
/** The routes this build can actually register. */
export declare const IMPLEMENTED_PROVIDER_IDS: readonly ["kimi", "grok", "antigravity", "glm", "ark", "mimo", "workbuddy"];
/** One route key. */
export type ProviderId = typeof PROVIDER_IDS[number];
/** Plugin configuration, as accepted from `cordis.patch.yml`. */
export interface Config {
    /** Routes to register; defaults to every implemented provider. */
    providers?: ProviderId[];
    /** Register the routes at all. Defaults to true. */
    enabled?: boolean;
    /** OAuth host serving the Kimi Code device flow. */
    oauthHost?: string;
    /** Kimi Code request base; `<base>/responses` is the endpoint. */
    apiBase?: string;
    /** Consult each endpoint's live model list. Defaults to true. */
    discovery?: boolean;
    /** Abandon a stream after this much silence. Defaults to 300000. */
    streamIdleTimeoutMs?: number;
    /** Model the picker preselects on the Kimi Code route. */
    defaultModel?: string;
    /** Kimi Code model ids to publish without shipped metadata. */
    extraModels?: string[];
    /**
     * Register the `auto` provider, which stands in for every enabled model across
     * the other routes. Defaults to true.
     */
    auto?: boolean;
    /**
     * The balance source a non-subscription provider uses, by provider id.
     *
     * A provider that is not a subscription this plugin serves has no usage windows to
     * meter, so the chip shows its balance instead — read by cc-switch's query, which needs
     * the provider's API key and its own base URL (the balance endpoint lives beside the
     * provider's OpenAI-compatible wire, not on DeepSeek's). Each entry names the
     * credential reference the provider's profile uses (`apiKeyEnv`) and the base URL that
     * profile connects to; the host's credential service resolves the reference. A provider
     * with no entry, or whose key does not resolve, gets no balance and the chip stays
     * silent.
     */
    balanceSources?: Record<string, {
        credentialRef: string;
        baseUrl: string;
    }>;
    /** @deprecated Use {@link balanceSources}, which carries the base URL too. */
    balanceCredentialRefs?: Record<string, string>;
}
/** Configuration schema, with the config file's own defaults applied. */
export declare const Config: z<Config>;
/**
 * Register every configured provider route, its credential store, and — when
 * the host has a connection service — the Settings page's RPC routes.
 *
 * @param ctx - the plugin's context.
 * @param config - validated plugin configuration.
 */
export declare function apply(ctx: Context, config?: Config): void;
