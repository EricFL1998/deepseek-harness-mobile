import { BAD_REQUEST, ProviderError } from '../errors.js';
import { queryCcSwitchBalance, queryUsageBalance } from './ccswitch.js';
import { readDeepSeekBalance } from './deepseek-balance.js';
import { RPC_ENDPOINTS, RPC_PREFIX } from '../protocol.js';
/** Wrap a value as a successful envelope result. */
function success(value) {
    return { ok: true, value };
}
/** Classify a thrown failure into the envelope's error shape. */
function failure(error) {
    if (error instanceof ProviderError) {
        return { ok: false, error: { code: error.code, message: error.message, details: {} } };
    }
    const message = error instanceof Error ? error.message : String(error);
    return { ok: false, error: { code: 'RPC_FAILED', message, details: {} } };
}
/** Build the JSON body of one response envelope. */
function envelope(rpcId, result, status = 200) {
    return Response.json({ type: 'server-response', rpcId, result }, { status });
}
/** Reject with an envelope the client can read, using the given HTTP status. */
function rejection(rpcId, code, message, status) {
    const error = { code, message, details: {} };
    return envelope(rpcId, { ok: false, error }, status);
}
/** Read a non-empty trimmed string field from a payload. */
function stringField(payload, field) {
    if (typeof payload !== 'object' || payload === null)
        return undefined;
    const value = payload[field];
    if (typeof value !== 'string')
        return undefined;
    const trimmed = value.trim();
    return trimmed.length === 0 ? undefined : trimmed;
}
/**
 * Read the client metadata a balance endpoint forwards to the host service.
 *
 * The host's account service expects a version / locale / timezone object; a missing or
 * partial one is tolerated by building a default rather than failing the read.
 *
 * @param payload - the request payload.
 * @returns the client metadata object.
 */
function payloadClient(payload) {
    const record = typeof payload === 'object' && payload !== null ? payload : {};
    return {
        version: typeof record.version === 'string' ? record.version : '1',
        locale: typeof record.locale === 'string' ? record.locale : 'en-US',
        timezoneOffsetSeconds: typeof record.timezoneOffsetSeconds === 'number'
            ? record.timezoneOffsetSeconds
            : 0,
    };
}
/** Read a boolean field from a payload, defaulting to false when absent. */
function booleanField(payload, field) {
    if (typeof payload !== 'object' || payload === null)
        return false;
    return payload[field] === true;
}
/**
 * Read a boolean field that the endpoint cannot default.
 *
 * A missing value must not silently mean `false` — for a visibility toggle that
 * would hide the model the caller meant to show.
 *
 * @param payload - the request payload.
 * @param field - required field name.
 * @returns the supplied boolean.
 * @throws {ProviderError} when the field is absent or not a boolean.
 */
function requiredBooleanField(payload, field) {
    const value = typeof payload === 'object' && payload !== null
        ? payload[field]
        : undefined;
    if (typeof value !== 'boolean') {
        throw new ProviderError(`payload.${field} must be a boolean`, BAD_REQUEST);
    }
    return value;
}
/** Read the provider route a payload names. */
function requiredProvider(payload, endpoint) {
    const provider = stringField(payload, 'provider');
    if (provider === undefined) {
        throw new ProviderError(`${endpoint} requires a provider`, BAD_REQUEST);
    }
    return provider;
}
/**
 * Run one endpoint against the registry, converting any failure into the
 * envelope's error branch — a route handler therefore never throws.
 *
 * @param registry - every registered provider route.
 * @param endpoint - endpoint name, without the prefix.
 * @param payload - the request's decoded payload.
 * @returns the envelope result.
 */
export async function dispatchRpc(registry, endpoint, payload, balance) {
    try {
        return success(await runEndpoint(registry, endpoint, payload, balance));
    }
    catch (error) {
        return failure(error);
    }
}
/**
 * The endpoint table.
 * @throws {ProviderError} for a request the registry refuses.
 */
async function runEndpoint(registry, endpoint, payload, balance) {
    switch (endpoint) {
        case 'status':
            return registry.status();
        case 'login':
            return registry.login(requiredProvider(payload, endpoint), stringField(payload, 'account'));
        case 'cancel':
            return registry.cancel(requiredProvider(payload, endpoint));
        case 'logout':
            return registry.logout(requiredProvider(payload, endpoint), stringField(payload, 'account'));
        case 'setDefault': {
            const account = stringField(payload, 'account');
            if (account === undefined)
                throw new ProviderError('setDefault requires an account', BAD_REQUEST);
            return registry.setDefault(requiredProvider(payload, endpoint), account);
        }
        case 'refresh':
            return registry.refresh(requiredProvider(payload, endpoint), stringField(payload, 'account'));
        case 'discover':
            return registry.discover(requiredProvider(payload, endpoint), booleanField(payload, 'force'));
        case 'setModelVisible': {
            const id = stringField(payload, 'id');
            if (id === undefined)
                throw new ProviderError('setModelVisible requires a model id', BAD_REQUEST);
            return registry.setModelVisible(requiredProvider(payload, endpoint), id, requiredBooleanField(payload, 'visible'));
        }
        case 'addModel':
        case 'removeModel': {
            const id = stringField(payload, 'id');
            if (id === undefined)
                throw new ProviderError(`${endpoint} requires a model id`, BAD_REQUEST);
            const provider = requiredProvider(payload, endpoint);
            return endpoint === 'addModel'
                ? registry.addModel(provider, id)
                : registry.removeModel(provider, id);
        }
        case 'setModelOrder': {
            const payloadRecord = typeof payload === 'object' && payload !== null
                ? payload
                : {};
            const ids = payloadRecord.ids;
            if (!Array.isArray(ids) || ids.some((id) => typeof id !== 'string')) {
                throw new ProviderError('setModelOrder requires an array of model ids', BAD_REQUEST);
            }
            return registry.setModelOrder(requiredProvider(payload, endpoint), ids);
        }
        case 'usage':
            return registry.usage(requiredProvider(payload, endpoint), booleanField(payload, 'force'));
        case 'auto-state':
            return registry.autoState();
        case 'setAutoOn': {
            const enabled = booleanField(payload, 'enabled');
            if (enabled === undefined)
                throw new ProviderError('setAutoOn requires an enabled flag', BAD_REQUEST);
            return registry.setAutoOn(enabled);
        }
        case 'checkin': {
            const account = stringField(payload, 'account');
            if (account === undefined)
                throw new ProviderError('checkin requires an account', BAD_REQUEST);
            return registry.checkin(requiredProvider(payload, endpoint), account);
        }
        case 'tasks': {
            const account = stringField(payload, 'account');
            if (account === undefined)
                throw new ProviderError('tasks requires an account', BAD_REQUEST);
            return registry.tasks(requiredProvider(payload, endpoint), account, booleanField(payload, 'claim') === true);
        }
        case 'deepseek-balance': {
            // The DeepSeek account's balance, read on the node half through the host's own
            // account service. The browser cannot call that service directly (it sits behind
            // the host's Remote controller, not a /api fetch route), so the node half reads it
            // and serves it here, on the same channel as the rest of the chip's data. The
            // service is resolved lazily, per request, because it may register after apply.
            const client = payloadClient(payload);
            return await readDeepSeekBalance(balance?.resolveDeepSeekAccount?.(), client);
        }
        case 'ccswitch-balance': {
            // A non-subscription provider's balance, by cc-switch's query against the provider's
            // own base URL. The caller names the provider; the configured map turns that into
            // the credential reference the provider's profile uses and the base URL that profile
            // connects to, and the host's credential service resolves the reference. No entry,
            // no key, or a key the provider does not answer for means no balance — never a
            // fabricated zero.
            const provider = requiredProvider(payload, endpoint);
            const credentialRef = balance?.credentialRefs[provider];
            if (credentialRef === undefined || balance?.resolveCredential === undefined)
                return undefined;
            const apiKey = await balance.resolveCredential(credentialRef);
            if (apiKey === undefined)
                return undefined;
            const baseUrl = balance?.baseUrls[provider] ?? 'https://api.deepseek.com';
            // A provider that answers the OpenAI-compatible `/v1/usage` (the shape cc-switch's
            // custom template reads, `{ remaining, unit }`) is asked that first; one that
            // answers the DeepSeek `balance_infos` shape is asked that as a fallback.
            return await queryUsageBalance({ apiKey, baseUrl })
                ?? await queryCcSwitchBalance({ apiKey, baseUrl });
        }
        case 'model-variants': {
            // The fast variant for one model, as the route's discovery reported it. The picker
            // uses this to show a quick-mode button without listing the variant as its own row.
            const provider = requiredProvider(payload, endpoint);
            const id = typeof payload.id === 'string' ? payload.id : undefined;
            if (id === undefined)
                return undefined;
            return registry.modelVariants(provider, id);
        }
        case 'reload':
            return registry.reload();
        default:
            throw new ProviderError(`unknown endpoint: ${endpoint}`, BAD_REQUEST);
    }
}
/** Build the Fetch route for one endpoint. */
function rpcRoute(endpoint, registry, balance) {
    const method = `${RPC_PREFIX}${endpoint}`;
    return {
        path: `/api/${method}`,
        methods: ['POST'],
        requestBody: 'buffered',
        fetch: async (request) => {
            const contentType = request.headers.get('content-type') ?? '';
            if (!contentType.toLowerCase().includes('application/json')) {
                return rejection('', 'gateway/unsupported-media-type', `${method} requires an application/json body`, 415);
            }
            const body = await request.text();
            let parsed;
            try {
                parsed = body.length === 0 ? {} : JSON.parse(body);
            }
            catch {
                return rejection('', 'gateway/bad-request', `${method} received an unparseable JSON body`, 400);
            }
            const envelopeRequest = parsed;
            const rpcId = typeof envelopeRequest.rpcId === 'string' ? envelopeRequest.rpcId : '';
            if (envelopeRequest.type !== 'client-request' || envelopeRequest.method !== method) {
                return rejection(rpcId, 'gateway/bad-request', `${method} received an envelope for another method`, 400);
            }
            return envelope(rpcId, await dispatchRpc(registry, endpoint, envelopeRequest.payload, balance));
        },
    };
}
/**
 * Register every endpoint route on the `connection` service, when one exists.
 *
 * @param ctx - the plugin's context.
 * @param registry - the provider routes every route dispatches through.
 */
export function registerSubscriptionRpc(ctx, registry, balance) {
    ctx.inject(['connection'], (connectionCtx) => {
        const connection = connectionCtx.get('connection');
        if (connection === undefined)
            return;
        const register = (route) => connection.fetch.register(route);
        for (const endpoint of RPC_ENDPOINTS) {
            const method = `${RPC_PREFIX}${endpoint}`;
            connectionCtx.effect(() => register(rpcRoute(endpoint, registry, balance)), `dsh-plugin-subscription-oauth: /api/${method} route`);
        }
    });
}
