/**
 * Provider registration contracts and the generic dispatch registry.
 *
 * A provider is one feature behind the single `/query` route: adding a
 * compatibility feature adds a provider and leaves the wire format alone.
 */
/** A provider failure carrying a stable code and an HTTP status. */
export class BridgeFailure extends Error {
    /** Stable failure code reported on the wire. */
    code;
    /** HTTP status the carrier answers with. */
    status;
    /** Optional structured detail; shape depends on the code. */
    details;
    /**
     * @param code - stable failure code reported on the wire.
     * @param message - diagnostic text; clients do not parse it.
     * @param status - HTTP status the carrier answers with.
     * @param details - optional structured detail.
     */
    constructor(code, message, status = 200, details) {
        super(message);
        this.name = 'BridgeFailure';
        this.code = code;
        this.status = status;
        this.details = details;
    }
}
/**
 * Narrow a wire `args` object to one string field.
 * @param args - the request arguments.
 * @param field - required field name.
 * @returns the string value.
 * @throws {BridgeFailure} `args/invalid` when the field is missing or not a string.
 */
export function requiredString(args, field) {
    const value = args[field];
    if (typeof value !== 'string' || value === '') {
        throw new BridgeFailure('args/invalid', `args.${field} must be a non-empty string`);
    }
    return value;
}
/**
 * Read an optional string field.
 * @param args - the request arguments.
 * @param field - optional field name.
 * @returns the string value, or undefined when absent.
 * @throws {BridgeFailure} `args/invalid` when present and not a string.
 */
export function optionalString(args, field) {
    const value = args[field];
    if (value === undefined)
        return undefined;
    if (typeof value !== 'string') {
        throw new BridgeFailure('args/invalid', `args.${field} must be a string when present`);
    }
    return value;
}
/**
 * Read an optional BCP-47 locale tag.
 *
 * A locale rides beside the data it localizes rather than replacing the Host's
 * configured `localeTags`, so one client asking for `zh-Hans-CN` and another for
 * `en` receive their own language from the same Host. Underscores are accepted as
 * separators because native clients sometimes send POSIX-style tags.
 *
 * @param args - the request arguments.
 * @param field - optional field name.
 * @returns the normalized tag, or undefined when absent.
 * @throws {BridgeFailure} `args/invalid` when present and not a language tag.
 */
export function optionalLocale(args, field = 'locale') {
    const value = optionalString(args, field);
    if (value === undefined)
        return undefined;
    const tag = value.trim().replaceAll('_', '-');
    // BCP-47 subtags are alphanumeric; the length bound keeps a cache key derived
    // from the tag from growing without limit.
    if (tag === '' || tag.length > 64 || !/^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$/.test(tag)) {
        throw new BridgeFailure('args/invalid', `args.${field} must be a BCP-47 language tag`);
    }
    return tag;
}
/**
 * Language tags one request resolves text with, most preferred first.
 *
 * The order is the one a client's own language negotiation applies: the tag it
 * asked for, that tag's base language (`zh-Hans-CN` → `zh`), then the tags the
 * Host is configured with. Duplicates are dropped case-insensitively, so a
 * configured `zh` does not appear twice for a `zh-CN` request.
 *
 * @param requested - the request's own `locale`, when it sent one.
 * @param configured - the Host's configured tags, in preference order.
 * @returns the tags to try, most preferred first.
 */
export function preferredLocaleTags(requested, configured) {
    const tags = [];
    const seen = new Set();
    const add = (raw) => {
        const tag = raw.trim().replaceAll('_', '-');
        if (tag === '')
            return;
        const key = tag.toLowerCase();
        if (seen.has(key))
            return;
        seen.add(key);
        tags.push(tag);
    };
    if (requested !== undefined) {
        add(requested);
        const base = requested.split('-')[0];
        if (base !== undefined)
            add(base);
    }
    for (const tag of configured)
        add(tag);
    return tags;
}
/**
 * Read an optional positive integer field, clamped to a ceiling.
 * @param args - the request arguments.
 * @param field - optional field name.
 * @param fallback - value used when the field is absent.
 * @param maximum - inclusive ceiling applied to an accepted value.
 * @returns the resolved integer.
 * @throws {BridgeFailure} `args/invalid` when present and not a positive integer.
 */
export function optionalPositiveInt(args, field, fallback, maximum) {
    const value = args[field];
    if (value === undefined)
        return fallback;
    if (typeof value !== 'number' || !Number.isInteger(value) || value < 1) {
        throw new BridgeFailure('args/invalid', `args.${field} must be a positive integer when present`);
    }
    return Math.min(value, maximum);
}
/**
 * Read an optional plain-object field.
 * @param args - the request arguments.
 * @param field - optional field name.
 * @returns the object, or undefined when absent.
 * @throws {BridgeFailure} `args/invalid` when present and not a plain object.
 */
export function optionalObject(args, field) {
    const value = args[field];
    if (value === undefined)
        return undefined;
    if (typeof value !== 'object' || value === null || Array.isArray(value)) {
        throw new BridgeFailure('args/invalid', `args.${field} must be an object when present`);
    }
    return value;
}
/**
 * The provider table. Registration order is the manifest order, so clients see
 * a stable catalog for a stable composition.
 */
export class ProviderRegistry {
    providers = new Map();
    /**
     * Add one provider.
     * @param provider - the provider to add.
     * @throws {Error} when the id is already registered; two features must not
     * share an id, because clients switch on it.
     */
    add(provider) {
        if (this.providers.has(provider.id)) {
            throw new Error(`dsh-mobile-bridge: provider ${provider.id} is already registered`);
        }
        this.providers.set(provider.id, provider);
    }
    /** Registered providers in registration order. */
    list() {
        return [...this.providers.values()];
    }
    /**
     * Look one provider up by id.
     * @param id - provider id from the wire.
     * @returns the provider, or undefined when the composition does not include it.
     */
    get(id) {
        return this.providers.get(id);
    }
    /**
     * Run one provider operation.
     * @param providerId - provider id from the wire.
     * @param op - operation name from the wire.
     * @param args - validated arguments.
     * @returns the answer to serialize.
     * @throws {BridgeFailure} `provider/unknown`, `op/unknown`, or whatever the handler raises.
     */
    async query(providerId, op, args) {
        const provider = this.providers.get(providerId);
        if (provider === undefined) {
            throw new BridgeFailure('provider/unknown', `unknown provider: ${providerId}`);
        }
        const handler = provider.ops[op];
        if (handler === undefined) {
            throw new BridgeFailure('op/unknown', `provider ${providerId} has no operation ${op}`, 200, {
                capabilities: Object.keys(provider.ops),
            });
        }
        const warnings = [];
        const data = await handler(args, { warn: (message) => { warnings.push(message); } });
        return { provider: provider.id, version: provider.version, data, warnings };
    }
}
