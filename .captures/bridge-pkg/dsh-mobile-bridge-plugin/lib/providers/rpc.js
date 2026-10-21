/**
 * `rpc`: one generic proxy for whitelisted Host remotes.
 *
 * Authorization is the caller's existing Host session cookie: the carrier has
 * already admitted the request through the Host's own connection fence before a
 * provider runs, so this provider never mints or forwards credentials. What it
 * adds is an allowlist and size caps, so a native client can reach exactly the
 * methods its profile names and nothing else.
 */
import { BridgeFailure, optionalObject, requiredString } from "../protocol/provider.js";
import { projectJson } from "../protocol/json.js";
/**
 * Build the `rpc` provider.
 * @param options - gateway accessor, allowlist, and caps.
 * @returns the provider to register.
 */
export function createRpcProvider(options) {
    const allowed = new Set(options.allowlist.map(entry => `${entry.namespace}.${entry.method}`));
    return {
        id: 'rpc',
        version: 1,
        authorization: 'host-session',
        ops: {
            /** Invoke one allowlisted remote method. */
            invoke: async (args) => {
                const namespace = requiredString(args, 'namespace');
                const method = requiredString(args, 'method');
                if (allowed.size === 0) {
                    throw new BridgeFailure('provider/unavailable', 'this profile allowlists no remote method');
                }
                if (!allowed.has(`${namespace}.${method}`)) {
                    throw new BridgeFailure('args/invalid', `${namespace}.${method} is not allowlisted`, 200, {
                        allowlist: [...allowed].sort(),
                    });
                }
                const gateway = options.gateway();
                if (gateway === undefined) {
                    throw new BridgeFailure('provider/unavailable', 'this Host composition has no typert gateway');
                }
                const callArgs = optionalObject(args, 'args') ?? {};
                const controller = new AbortController();
                const timer = setTimeout(() => { controller.abort(); }, options.timeoutMs);
                let result;
                try {
                    result = await gateway.invoke({
                        namespace,
                        method,
                        args: callArgs,
                        peer: options.peer,
                        signal: controller.signal,
                    });
                }
                catch (error) {
                    if (controller.signal.aborted) {
                        throw new BridgeFailure('internal/error', `${namespace}.${method} exceeded ${options.timeoutMs}ms`);
                    }
                    throw new BridgeFailure('internal/error', `${namespace}.${method} failed: ${error.message}`);
                }
                finally {
                    clearTimeout(timer);
                }
                const projected = projectJson(result, 'result');
                const bytes = Buffer.byteLength(JSON.stringify(projected.value ?? null), 'utf8');
                if (bytes > options.maxResultBytes) {
                    throw new BridgeFailure('budget/exceeded', `${namespace}.${method} returned ${bytes} bytes, above the ${options.maxResultBytes}-byte ceiling`, 200, { dropped: projected.dropped });
                }
                return { namespace, method, result: projected.value ?? null };
            },
        },
    };
}
