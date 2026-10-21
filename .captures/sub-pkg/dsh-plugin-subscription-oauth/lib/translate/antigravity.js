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
import { randomUUID } from 'node:crypto';
import { EMPTY_RESPONSE_CODE, LlmError, ReasoningEffortId, ToolCallId } from '@deepseek-ai/dsh-llm';
import { antigravityToolParameters } from './antigravity-schema.js';
import { parseSse } from './sse.js';
/**
 * Reasoning effort ladders the Antigravity runtime families accept.
 *
 * An unknown family advertises none, which leaves the provider's own default in
 * charge instead of inventing a level it may reject.
 *
 * @param model - model id as the live catalog reports it.
 * @returns the reasoning block, or undefined when the family takes no level.
 */
export function antigravityReasoning(model) {
    const ids = model.startsWith('claude-') ? ['high']
        : model.startsWith('gpt-oss-') ? ['medium']
            : model.startsWith('gemini-3.1-pro') || model === 'gemini-pro' || model === 'gemini-pro-agent' ? ['low', 'high']
                : /^gemini-(?:2\.5|3)/.test(model) ? ['low', 'medium', 'high'] : [];
    const fixed = /^gemini-.*-(low|medium|high)$/.exec(model)?.[1];
    const efforts = fixed !== undefined && ids.includes(fixed) ? [fixed] : ids;
    return efforts.length === 0 ? undefined : {
        efforts: efforts.map((id) => ({ id: ReasoningEffortId(id), name: id[0]?.toUpperCase() + id.slice(1) })),
    };
}
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
export function antigravityThinking(model, effort, maxTokens) {
    if (effort === undefined)
        return undefined;
    const supported = antigravityReasoning(model);
    const known = supported?.efforts.some((entry) => String(entry.id) === effort) === true;
    if (!known) {
        throw new LlmError(`Antigravity model ${model} does not support reasoning effort ${effort}`, 'UNSUPPORTED_REASONING_EFFORT');
    }
    let budget;
    if (model.startsWith('claude-'))
        budget = 1024;
    else if (model.startsWith('gpt-oss-'))
        budget = 8192;
    else if (model.startsWith('gemini-3.1-pro') || model === 'gemini-pro' || model === 'gemini-pro-agent') {
        budget = effort === 'high' ? 10001 : 1001;
    }
    else if (model.startsWith('gemini-3.5-flash') || model === 'gemini-3-flash' || model === 'gemini-3-flash-agent') {
        budget = effort === 'high' ? 10000 : effort === 'medium' ? 4000 : 1000;
    }
    else {
        budget = effort === 'high' ? -1 : effort === 'medium' ? 4000 : 1000;
    }
    if (maxTokens !== undefined && budget > 0 && maxTokens <= budget) {
        throw new LlmError(`Antigravity maxTokens must exceed the thinking budget (${String(budget)}) for ${model}/${effort}`, 'INVALID_REQUEST');
    }
    return { includeThoughts: true, thinkingBudget: budget };
}
/**
 * Normalize one tool result to the JSON object `FunctionResponse.response` needs.
 *
 * That field maps to a singular protobuf `Struct`, so a bare array, string,
 * number or boolean is rejected upstream. Only a non-array JSON object survives
 * verbatim; every other value is wrapped in an `output` envelope, which is the
 * shape other Cloud Code Assist clients send.
 */
function toolResultValue(text, isError) {
    let parsed;
    try {
        parsed = JSON.parse(text);
    }
    catch {
        return { output: text, ...isError ? { isError: true } : {} };
    }
    if (parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return { ...parsed, ...isError ? { isError: true } : {} };
    }
    return { output: parsed, ...isError ? { isError: true } : {} };
}
/** Map harness tool schemas to Gemini function declarations. */
export function toAntigravityTools(tools, model = 'gemini') {
    if (tools.length === 0)
        return [];
    const legacy = /^(claude-|gpt-oss-)/.test(model);
    return [{
            functionDeclarations: tools.map((tool) => ({
                name: tool.name,
                description: tool.description,
                [legacy ? 'parameters' : 'parametersJsonSchema']: antigravityToolParameters(tool.parameters, legacy),
            })),
        }];
}
/**
 * Placeholder a Gemini 3 model needs in place of a signature this plugin did not
 * record.
 *
 * Gemini 3 validates the thought signature on the first function call of every
 * model tool-call step. This plugin does not persist signatures across turns, so
 * it sends the documented validator-skip marker instead of failing the turn.
 */
const SKIP_THOUGHT_SIGNATURE_VALIDATOR = 'skip_thought_signature_validator';
/** Whether a model validates thought signatures on the first function call. */
function requiresThoughtSignatures(model) {
    return model !== undefined && /^gemini-3(?:[.-]|$)/i.test(model);
}
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
export async function toAntigravityContents(messages, model, resolveImage) {
    const out = [];
    const callNames = new Map();
    for (const message of messages) {
        if (message.role === 'system' || message.role === 'developer')
            continue;
        if (message.role === 'tool') {
            const id = String(message.toolCallId);
            if (id.length === 0)
                throw new LlmError('tool result has no call id', 'INVALID_REQUEST');
            const text = message.content.map((block) => block.type === 'text' ? block.text : '').join('');
            const part = {
                functionResponse: {
                    id,
                    name: callNames.get(id) ?? '',
                    response: toolResultValue(text, message.isError === true),
                },
            };
            const previous = out.at(-1);
            if (previous?.role === 'user')
                previous.parts.push(part);
            else
                out.push({ role: 'user', parts: [part] });
            continue;
        }
        const role = message.role === 'assistant' ? 'model' : 'user';
        const parts = [];
        let sawFunctionCall = false;
        for (const block of message.content) {
            switch (block.type) {
                case 'text':
                    // Antigravity's Claude-backed models reject an empty text part.
                    parts.push({ text: block.text.trim().length > 0 ? block.text : '.' });
                    break;
                case 'image': {
                    const resolved = resolveImage === undefined ? undefined : await resolveImage(block.attachment);
                    if (resolved !== undefined) {
                        parts.push({ inlineData: { mimeType: resolved.mediaType, data: resolved.base64 } });
                    }
                    break;
                }
                case 'tool-call': {
                    callNames.set(String(block.id), block.name);
                    let args;
                    try {
                        args = JSON.parse(block.arguments);
                    }
                    catch {
                        args = {};
                    }
                    const thoughtSignature = !sawFunctionCall && requiresThoughtSignatures(model)
                        ? SKIP_THOUGHT_SIGNATURE_VALIDATOR
                        : undefined;
                    parts.push({
                        functionCall: { id: String(block.id), name: block.name, args },
                        ...thoughtSignature === undefined ? {} : { thoughtSignature },
                    });
                    sawFunctionCall = true;
                    break;
                }
                default:
                    // Reasoning is not replayed without its provider signature, and file
                    // blocks never reach a provider natively.
                    break;
            }
        }
        if (parts.length === 0)
            continue;
        const previous = out.at(-1);
        if (previous?.role === role)
            previous.parts.push(...parts);
        else
            out.push({ role, parts });
    }
    return out;
}
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
export async function toAntigravityRequest(options, projectId, system, resolveImage, forStreaming = false) {
    const tools = toAntigravityTools(options.tools ?? [], options.model);
    const thinkingConfig = antigravityThinking(options.model, options.reasoningEffort, options.maxTokens);
    let maxOutputTokens = options.maxTokens;
    // Claude models on the streaming path reject an output cap of 65535 or more.
    if (forStreaming && maxOutputTokens !== undefined && /^claude-/.test(options.model) && maxOutputTokens >= 65535) {
        maxOutputTokens = 64000;
    }
    const generationConfig = {
        ...maxOutputTokens === undefined ? {} : { maxOutputTokens },
        ...options.temperature === undefined ? {} : { temperature: options.temperature },
        ...options.stop === undefined || options.stop.length === 0 ? {} : { stopSequences: options.stop },
        ...thinkingConfig === undefined ? {} : { thinkingConfig },
    };
    const sessionId = options.sessionId === undefined ? randomUUID() : String(options.sessionId);
    return {
        project: projectId,
        requestId: `agent/${String(Date.now())}/${randomUUID()}/4`,
        model: options.model,
        userAgent: 'antigravity',
        requestType: 'agent',
        request: {
            contents: await toAntigravityContents(options.messages, options.model, resolveImage),
            sessionId,
            ...system === undefined || system.length === 0 ? {} : { systemInstruction: { parts: [{ text: system }] } },
            ...tools.length === 0 ? {} : {
                tools,
                toolConfig: { functionCallingConfig: { mode: 'VALIDATED' } },
            },
            ...Object.keys(generationConfig).length === 0 ? {} : { generationConfig },
        },
    };
}
/** Map Gemini usage metadata onto the harness's disjoint counters. */
export function mapAntigravityUsage(metadata) {
    const cached = metadata.cachedContentTokenCount ?? 0;
    return {
        // Gemini's prompt count already includes the cached prefix; the harness
        // reports cache reads separately, so it must not be double counted.
        inputTokens: Math.max(0, (metadata.promptTokenCount ?? 0) - cached),
        outputTokens: metadata.candidatesTokenCount ?? 0,
        ...cached > 0 ? { cacheReadTokens: cached } : {},
        ...metadata.thoughtsTokenCount === undefined ? {} : { reasoningTokens: metadata.thoughtsTokenCount },
    };
}
/**
 * Push translator for Antigravity frames.
 *
 * Gemini streams any number of parts per frame and never announces a block, so
 * the block boundaries are inferred: consecutive text joins one text block,
 * consecutive thought text joins one reasoning block, and each function call is a
 * complete block of its own.
 */
export class AntigravityStreamTranslator {
    blocks = new Map();
    nextIndex = 0;
    sawContent = false;
    sawToolCall = false;
    /** Whether a terminating chunk has been produced. */
    terminated = false;
    /** Open a block and announce it. */
    open(key, kind, chunks, values = {}) {
        const block = { index: this.nextIndex++, kind, text: '', ...values };
        this.blocks.set(key, block);
        chunks.push({ type: 'block-start', index: block.index, blockType: kind });
        return block;
    }
    /** Close one block with the content it accumulated. */
    close(key, chunks) {
        const block = this.blocks.get(key);
        if (block === undefined)
            return;
        this.blocks.delete(key);
        let content;
        if (block.kind === 'text')
            content = { type: 'text', text: block.text };
        else if (block.kind === 'reasoning')
            content = { type: 'reasoning', text: block.text };
        else
            content = {
                type: 'tool-call',
                id: ToolCallId(block.id ?? `call_${randomUUID().replaceAll('-', '')}`),
                name: block.name ?? '',
                arguments: block.text,
            };
        chunks.push({ type: 'block-end', index: block.index, block: content });
    }
    /** Close every block still open, in the order they were opened. */
    closeAll(chunks) {
        for (const key of [...this.blocks.keys()])
            this.close(key, chunks);
    }
    /** The terminating chunk for one finish reason. */
    finish(reason) {
        if (!this.sawContent) {
            return {
                type: 'finish',
                reason: { kind: 'error', failure: { message: 'Antigravity returned no content', code: EMPTY_RESPONSE_CODE } },
            };
        }
        if (reason === 'MAX_TOKENS')
            return { type: 'finish', reason: { kind: 'max-tokens' } };
        if (reason === 'SAFETY' || reason === 'RECITATION' || reason === 'BLOCKLIST') {
            return {
                type: 'finish',
                reason: {
                    kind: 'error',
                    failure: { message: `Antigravity blocked the response (${reason})`, code: 'CONTENT_FILTER' },
                },
            };
        }
        return { type: 'finish', reason: { kind: this.sawToolCall ? 'tool-calls' : 'stop' } };
    }
    /**
     * Process one decoded frame.
     *
     * @param event - one decoded `v1internal` frame.
     * @returns the chunks it produced, in contract order.
     */
    push(event) {
        if (this.terminated)
            return [];
        const chunks = [];
        const candidate = event.response?.candidates?.[0];
        for (const [partIndex, part] of (candidate?.content?.parts ?? []).entries()) {
            if (part.thought === true && typeof part.text === 'string' && part.text.length > 0) {
                const block = this.blocks.get('reasoning') ?? this.open('reasoning', 'reasoning', chunks);
                block.text += part.text;
                this.sawContent = true;
                chunks.push({ type: 'reasoning-delta', index: block.index, text: part.text });
            }
            else if (typeof part.text === 'string' && part.text.length > 0) {
                const block = this.blocks.get('text') ?? this.open('text', 'text', chunks);
                block.text += part.text;
                this.sawContent = true;
                chunks.push({ type: 'text-delta', index: block.index, text: part.text });
            }
            else if (part.functionCall !== undefined) {
                const call = part.functionCall;
                const id = typeof call.id === 'string' && call.id.length > 0
                    ? call.id
                    : `call_${randomUUID().replaceAll('-', '')}`;
                const key = `call:${id}:${String(partIndex)}`;
                const args = JSON.stringify(call.args ?? {});
                const block = this.open(key, 'tool-call', chunks, { id, name: call.name ?? '' });
                block.text = args;
                this.sawContent = true;
                this.sawToolCall = true;
                chunks.push({
                    type: 'tool-call-delta',
                    index: block.index,
                    id: ToolCallId(id),
                    name: call.name ?? '',
                    argumentsDelta: args,
                });
                this.close(key, chunks);
            }
        }
        if (candidate?.finishReason !== undefined) {
            this.closeAll(chunks);
            const usage = event.response?.usageMetadata;
            if (usage !== undefined)
                chunks.push({ type: 'usage', usage: mapAntigravityUsage(usage) });
            chunks.push(this.finish(candidate.finishReason));
            this.terminated = true;
        }
        return chunks;
    }
}
/**
 * Consume Antigravity's SSE response into the harness streaming contract.
 *
 * @param stream - the response body.
 * @param onActivity - called per frame, so the caller's idle watchdog keeps up.
 * @yields harness stream chunks in contract order.
 * @throws {LlmError} for a malformed frame or a stream that ends without a finish.
 */
export async function* streamAntigravity(stream, onActivity) {
    const translator = new AntigravityStreamTranslator();
    for await (const event of parseSse(stream, onActivity)) {
        if (event.data === '[DONE]')
            break;
        let parsed;
        try {
            parsed = JSON.parse(event.data);
        }
        catch {
            throw new LlmError(`malformed Antigravity SSE payload: ${event.data.slice(0, 120)}`, 'MALFORMED_RESPONSE');
        }
        yield* translator.push(parsed);
        if (translator.terminated)
            return;
    }
    if (!translator.terminated) {
        throw new LlmError('Antigravity SSE stream ended before a finish chunk', 'STREAM_CLOSED');
    }
}
