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
