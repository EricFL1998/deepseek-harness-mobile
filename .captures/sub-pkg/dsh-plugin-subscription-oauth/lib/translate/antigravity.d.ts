/**
 * Harness messages, tools and reasoning efforts to Antigravity's Gemini-shaped
 * `v1internal` envelope — and the response/SSE translation back.
 *
 * The wire is Cloud Code Assist's own dialect, not the public Gemini API: the
 * model, project and request all ride inside one `{project, requestId, model,
 * userAgent, requestType, request}` object, and reasoning effort becomes a
 * `thinkingConfig` budget rather than a named level.
 *
 * @module dsh-plugin-subscription-oauth/translate/antigravity
 */
import { ReasoningEffortId } from '@deepseek-ai/dsh-llm';
import type { GenerateOptions, RequestMessage, StreamChunk, TokenUsage, ToolSchema } from '@deepseek-ai/dsh-llm';
import type { ImageResolver } from './responses.js';
/** One Gemini part on the v1internal wire. */
export interface AntigravityPart {
    text?: string;
    thought?: boolean;
    thoughtSignature?: string;
    inlineData?: {
        mimeType: string;
        data: string;
    };
    functionCall?: {
        id?: string;
        name?: string;
        args?: unknown;
    };
    functionResponse?: {
        id: string;
        name: string;
        response: Record<string, unknown>;
    };
}
/** One conversation turn on the v1internal wire. */
export interface AntigravityContent {
    role: 'user' | 'model';
    parts: AntigravityPart[];
}
/** The full request envelope. */
export interface AntigravityRequest {
    project: string;
    requestId: string;
    model: string;
    userAgent: 'antigravity';
    requestType: 'agent';
    request: {
        contents: AntigravityContent[];
        sessionId: string;
        systemInstruction?: {
            parts: {
                text: string;
            }[];
        };
        tools?: {
            functionDeclarations: Record<string, unknown>[];
        }[];
        toolConfig?: {
            functionCallingConfig: {
                mode: 'VALIDATED';
            };
        };
        generationConfig?: Record<string, unknown>;
    };
}
/**
 * Reasoning effort ladders the Antigravity runtime families accept.
 *
 * An unknown family advertises none, which leaves the provider's own default in
 * charge instead of inventing a level it may reject.
 *
 * @param model - model id as the live catalog reports it.
 * @returns the reasoning block, or undefined when the family takes no level.
 */
export declare function antigravityReasoning(model: string): {
    efforts: {
        id: ReasoningEffortId;
        name: string;
    }[];
} | undefined;
/**
 * Build the `thinkingConfig` for one request.
 *
 * Budgets follow the runtime family, and a budget larger than the caller's own
 * output cap is refused locally rather than sent as a request the endpoint would
 * reject.
 *
 * @param model - model id.
 * @param effort - harness effort id, when the caller picked one.
 * @param maxTokens - the caller's output cap, when it set one.
 * @returns the config, or undefined when no effort was requested.
 * @throws {LlmError} when the effort is unsupported or exceeds the output cap.
 */
export declare function antigravityThinking(model: string, effort: string | undefined, maxTokens?: number): Record<string, unknown> | undefined;
/** Map harness tool schemas to Gemini function declarations. */
export declare function toAntigravityTools(tools: readonly ToolSchema[], model?: string): {
    functionDeclarations: Record<string, unknown>[];
}[];
/**
 * Convert request messages into Gemini contents.
 *
 * Function-response names are recovered from the preceding tool calls, because
 * the harness correlates results by id while this wire requires both id and name.
 * Stored reasoning is not replayed: without the provider's signature the part
 * would be rejected, so only fresh reasoning from the model travels back.
 *
 * @param messages - the assembled request messages.
 * @param model - model id, which decides the signature rule.
 * @param resolveImage - resolves an image block's bytes.
 * @returns the contents array.
 * @throws {LlmError} when a tool result carries no call id.
 */
export declare function toAntigravityContents(messages: readonly RequestMessage[], model?: string, resolveImage?: ImageResolver): Promise<AntigravityContent[]>;
/**
 * Build one `v1internal` generate/stream request.
 *
 * @param options - the call's assembled options.
 * @param projectId - the account's Cloud AI Companion project.
 * @param system - resolved system prompt, which wins over in-band system text.
 * @param resolveImage - resolves an image block's bytes.
 * @param forStreaming - whether this is the streaming endpoint.
 * @returns the envelope to POST.
 */
export declare function toAntigravityRequest(options: GenerateOptions, projectId: string, system: string | undefined, resolveImage: ImageResolver | undefined, forStreaming?: boolean): Promise<AntigravityRequest>;
/** The response subset this plugin reads, streamed or not. */
export interface AntigravityResponseEvent {
    response?: {
        candidates?: {
            content?: {
                parts?: AntigravityPart[];
            };
            finishReason?: string;
        }[];
        usageMetadata?: {
            promptTokenCount?: number;
            candidatesTokenCount?: number;
            thoughtsTokenCount?: number;
            totalTokenCount?: number;
            cachedContentTokenCount?: number;
        };
    };
}
/** Map Gemini usage metadata onto the harness's disjoint counters. */
export declare function mapAntigravityUsage(metadata: NonNullable<NonNullable<AntigravityResponseEvent['response']>['usageMetadata']>): TokenUsage;
/**
 * Push translator for Antigravity frames.
 *
 * Gemini streams any number of parts per frame and never announces a block, so
 * the block boundaries are inferred: consecutive text joins one text block,
 * consecutive thought text joins one reasoning block, and each function call is a
 * complete block of its own.
 */
export declare class AntigravityStreamTranslator {
    private readonly blocks;
    private nextIndex;
    private sawContent;
    private sawToolCall;
    /** Whether a terminating chunk has been produced. */
    terminated: boolean;
    /** Open a block and announce it. */
    private open;
    /** Close one block with the content it accumulated. */
    private close;
    /** Close every block still open, in the order they were opened. */
    private closeAll;
    /** The terminating chunk for one finish reason. */
    private finish;
    /**
     * Process one decoded frame.
     *
     * @param event - one decoded `v1internal` frame.
     * @returns the chunks it produced, in contract order.
     */
    push(event: AntigravityResponseEvent): StreamChunk[];
}
/**
 * Consume Antigravity's SSE response into the harness streaming contract.
 *
 * @param stream - the response body.
 * @param onActivity - called per frame, so the caller's idle watchdog keeps up.
 * @yields harness stream chunks in contract order.
 * @throws {LlmError} for a malformed frame or a stream that ends without a finish.
 */
export declare function streamAntigravity(stream: ReadableStream<Uint8Array>, onActivity?: () => void): AsyncGenerator<StreamChunk>;
