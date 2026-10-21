/**
 * The OpenAI Responses wire, as Kimi Code's `/coding/v1/responses` endpoint
 * speaks it.
 *
 * Kimi's Responses route keeps a subscription account's thinking encrypted on
 * the server: the stream still reports reasoning deltas, but a continuation
 * replays the provider's own reasoning items rather than reconstructed text.
 * That is why a stored `reasoning` block is never sent back as `reasoning_text`
 * — the item shape the endpoint requires is provider-owned, so this plugin
 * simply omits reasoning from `input` and lets each turn stand on its own
 * visible content.
 *
 * `tool` results are represented as `function_call_output` items, and the
 * request keeps them adjacent to their `function_call`, which this endpoint
 * requires.
 *
 * @module dsh-plugin-subscription-oauth/translate/responses
 */
import { LlmError } from '@deepseek-ai/dsh-llm';
import type { ContentBlock, RequestMessage, StreamChunk, TokenUsage, ToolSchema } from '@deepseek-ai/dsh-llm';
/** One image occurrence inlined as a data URL. */
export interface ResponsesImagePart {
    /** MIME type verified by the attachment service. */
    mediaType: string;
    /** Base64-encoded bytes. */
    base64: string;
}
/** Resolve one image block's bytes, or undefined when they are unreachable. */
export type ImageResolver = (ref: Extract<ContentBlock, {
    type: 'image';
}>['attachment']) => Promise<ResponsesImagePart | undefined>;
/** Assembled `instructions` + `input` pair for one Responses request. */
export interface ResponsesRequestInput {
    /** System text for the top-level `instructions` field; absent when there is none. */
    instructions?: string;
    /** Responses `input` items in conversation order. */
    input: Record<string, unknown>[];
}
/**
 * Convert harness messages into Responses `instructions` + `input` items.
 *
 * @param messages - the assembled request messages.
 * @param system - explicit system prompt, which takes precedence over in-band ones.
 * @param resolveImage - resolves an image block's bytes; an unresolved occurrence
 *   becomes a stable placeholder so the request still dispatches.
 * @returns the request fields to merge into the body.
 */
export declare function toResponsesInput(messages: readonly RequestMessage[], system?: string, resolveImage?: ImageResolver): Promise<ResponsesRequestInput>;
/**
 * Map harness tool schemas onto Responses function tools.
 *
 * `strict` is set false to preserve the harness's optional parameters rather
 * than letting the endpoint normalize every schema into strict mode.
 *
 * @param tools - the request's tool schemas.
 * @returns `tools` array entries.
 */
export declare function toResponsesTools(tools: readonly ToolSchema[]): Record<string, unknown>[];
/** The `usage` object of a completed response. */
export interface ResponsesUsage {
    input_tokens?: number;
    output_tokens?: number;
    total_tokens?: number;
    input_tokens_details?: {
        cached_tokens?: number;
    };
    output_tokens_details?: {
        reasoning_tokens?: number;
    };
}
/**
 * Map Responses usage onto the harness's disjoint counters.
 * @param usage - wire usage from `response.completed`.
 * @returns harness token usage.
 */
export declare function mapResponsesUsage(usage: ResponsesUsage): TokenUsage;
/**
 * Classify a Responses failure payload into a thrown error.
 * @param code - provider error code, when present.
 * @param message - provider error message, when present.
 * @returns the mapped error (context overflow, quota, otherwise SERVER).
 */
export declare function responsesFailure(code: string | undefined, message: string | undefined): LlmError;
/** The subset of Responses SSE event shapes this translator reads. */
export interface ResponsesStreamEvent {
    type: string;
    item_id?: string;
    output_index?: number;
    content_index?: number;
    summary_index?: number;
    delta?: string;
    item?: {
        type?: string;
        id?: string;
        call_id?: string;
        name?: string;
        arguments?: string;
        encrypted_content?: string;
        summary?: unknown[];
        status?: string;
        content?: {
            type?: string;
            text?: string;
        }[];
    };
    response?: {
        status?: string;
        usage?: ResponsesUsage;
        error?: {
            code?: string;
            message?: string;
        };
        incomplete_details?: {
            reason?: string;
        };
    };
    code?: string;
    message?: string;
}
/**
 * Push-model Responses translator: feed each parsed event to {@link push} and
 * yield the chunks it returns.
 *
 * Block indexes are allocated in first-seen order; `usage` precedes the
 * terminal `finish` and nothing is emitted after it; a terminal provider
 * failure throws.
 */
export declare class ResponsesStreamTranslator {
    private readonly blocks;
    private readonly order;
    private nextIndex;
    private sawToolCall;
    /** Set once `response.completed` produced the terminal finish chunk. */
    terminated: boolean;
    private open;
    private close;
    /** Close every open block whose key belongs to one output item. */
    private closeItem;
    private closeAll;
    /**
     * Process one parsed Responses event.
     * @param event - the parsed event object.
     * @returns the chunks this event produced, possibly none.
     * @throws {LlmError} on a terminal provider failure event.
     */
    push(event: ResponsesStreamEvent): StreamChunk[];
}
/**
 * Consume a Responses SSE body and yield harness chunks.
 * @param stream - the raw response body.
 * @param onActivity - transport-activity callback for the idle watchdog.
 * @yields harness stream chunks in contract order.
 * @throws {LlmError} `MALFORMED_RESPONSE` on an undecodable event, `STREAM_CLOSED` when the
 *   body ends before `response.completed`.
 */
export declare function streamResponses(stream: ReadableStream<Uint8Array>, onActivity?: () => void): AsyncGenerator<StreamChunk>;
