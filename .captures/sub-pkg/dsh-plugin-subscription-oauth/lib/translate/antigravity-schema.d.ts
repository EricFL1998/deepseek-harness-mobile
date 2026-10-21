/**
 * Tool-schema preparation for Antigravity's v1internal bridge.
 *
 * Two worlds meet here. Gemini-backed models take a JSON-Schema-ish
 * `parametersJsonSchema`, but only after local `$ref`s are resolved and
 * `$defs` are gone. Claude- and GPT-OSS-backed models take a much smaller
 * `parameters` dialect and reject the rest, so their schema is reduced to
 * `type`/`description`/`properties`/`required`/`items`/`enum` with every
 * alternative folded into one shape that is never narrower than the original.
 *
 * @module dsh-plugin-subscription-oauth/translate/antigravity-schema
 */
/** A JSON-schema-shaped object. */
type Schema = Record<string, unknown>;
/**
 * Detach and normalize one tool's root object without changing the registry schema.
 *
 * @param parameters - the tool's declared argument schema.
 * @param legacy - whether the target model takes the reduced `parameters` dialect.
 * @returns the schema to send.
 * @throws {LlmError} for an unsupported reference or a non-object root.
 */
export declare function antigravityToolParameters(parameters: Record<string, unknown>, legacy: boolean): Schema;
export {};
