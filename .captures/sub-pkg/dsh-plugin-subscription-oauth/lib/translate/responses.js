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
import { CONTEXT_WINDOW_EXCEEDED_CODE, EMPTY_RESPONSE_CODE, isContextWindowExceededError, isQuotaExceededError, LlmError, QUOTA_EXCEEDED_CODE, ToolCallId, } from '@deepseek-ai/dsh-llm';
import { parseSse } from './sse.js';
/** Read the text of one content block, when it has any. */
function blockText(block) {
    switch (block.type) {
        case 'text':
            return block.text;
        default:
            return '';
    }
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
export async function toResponsesInput(messages, system, resolveImage) {
    const input = [];
    const systemTexts = [];
    if (system !== undefined && system.length > 0)
        systemTexts.push(system);
    for (const message of messages) {
        switch (message.role) {
            case 'system': {
                const text = message.content.map(blockText).join('\n').trim();
                if (text.length > 0)
                    systemTexts.push(text);
                break;
            }
            case 'developer':
                // Tool additions/removals have no Responses equivalent; declarations
                // travel in the request's `tools`.
                break;
            case 'tool': {
                const text = message.content.map(blockText).join('');
                input.push({
                    type: 'function_call_output',
                    call_id: String(message.toolCallId),
                    output: message.isError === true && text.length === 0 ? 'tool call rejected' : text,
                });
                break;
            }
            case 'user': {
                const content = [];
                for (const block of message.content) {
                    if (block.type === 'text') {
                        if (block.text.length > 0)
                            content.push({ type: 'input_text', text: block.text });
                    }
                    else if (block.type === 'image') {
                        const resolved = resolveImage === undefined ? undefined : await resolveImage(block.attachment);
                        content.push(resolved === undefined
                            ? { type: 'input_text', text: '[image unavailable]' }
                            : { type: 'input_image', image_url: `data:${resolved.mediaType};base64,${resolved.base64}` });
                    }
                    else if (block.type === 'file') {
                        content.push({ type: 'input_text', text: block.attachment.name ?? '[file]' });
                    }
                }
                if (content.length > 0)
                    input.push({ type: 'message', role: 'user', content });
                break;
            }
            case 'assistant': {
                const content = [];
                const flush = () => {
                    if (content.length === 0)
                        return;
                    input.push({ type: 'message', role: 'assistant', content: [...content] });
                    content.length = 0;
                };
                for (const block of message.content) {
                    if (block.type === 'text') {
                        if (block.text.length > 0)
                            content.push({ type: 'output_text', text: block.text });
                    }
                    else if (block.type === 'tool-call') {
                        flush();
                        input.push({
                            type: 'function_call',
                            call_id: String(block.id),
                            name: block.name,
                            arguments: block.arguments,
                        });
                    }
                    // reasoning: never replayed; see the module doc.
                }
                flush();
                break;
            }
            default:
                break;
        }
    }
    const instructions = systemTexts.length > 0 ? systemTexts.join('\n\n') : undefined;
    return { ...instructions === undefined ? {} : { instructions }, input };
}
/**
 * Map harness tool schemas onto Responses function tools.
 *
 * `strict` is set false to preserve the harness's optional parameters rather
 * than letting the endpoint normalize every schema into strict mode.
 *
 * @param tools - the request's tool schemas.
 * @returns `tools` array entries.
 */
export function toResponsesTools(tools) {
    return tools.map((tool) => ({
        type: 'function',
        name: tool.name,
        description: tool.description,
        parameters: tool.parameters,
        strict: false,
    }));
}
/**
 * Map Responses usage onto the harness's disjoint counters.
 * @param usage - wire usage from `response.completed`.
 * @returns harness token usage.
 */
export function mapResponsesUsage(usage) {
    const cached = usage.input_tokens_details?.cached_tokens;
    const reasoning = usage.output_tokens_details?.reasoning_tokens;
    const prompt = usage.input_tokens ?? 0;
    return {
        inputTokens: Math.max(0, prompt - (cached ?? 0)),
        outputTokens: usage.output_tokens ?? 0,
        ...usage.total_tokens === undefined ? {} : { totalTokens: usage.total_tokens },
        ...cached === undefined ? {} : { cacheReadTokens: cached },
        ...reasoning === undefined ? {} : { reasoningTokens: reasoning },
    };
}
/**
 * Classify a Responses failure payload into a thrown error.
 * @param code - provider error code, when present.
 * @param message - provider error message, when present.
 * @returns the mapped error (context overflow, quota, otherwise SERVER).
 */
export function responsesFailure(code, message) {
    const text = message ?? code ?? 'the provider reported a failed response';
    const detail = `${code ?? ''} ${message ?? ''}`;
    if (code === 'context_window_exceeded' || isContextWindowExceededError(detail)) {
        return new LlmError(text, CONTEXT_WINDOW_EXCEEDED_CODE);
    }
    if ((code !== undefined && /insufficient|quota/i.test(code)) || isQuotaExceededError(detail)) {
        return new LlmError(text, QUOTA_EXCEEDED_CODE);
    }
    return new LlmError(text, 'SERVER');
}
/** Assemble the final content block for one closed block. */
function closeBlock(block) {
    switch (block.kind) {
        case 'text':
            return { type: 'text', text: block.text };
        case 'reasoning':
            return { type: 'reasoning', text: block.text };
        case 'tool-call':
            return {
                type: 'tool-call',
                id: ToolCallId(block.callId),
                name: block.name ?? '',
                arguments: block.text,
            };
    }
}
/**
 * Push-model Responses translator: feed each parsed event to {@link push} and
 * yield the chunks it returns.
 *
 * Block indexes are allocated in first-seen order; `usage` precedes the
 * terminal `finish` and nothing is emitted after it; a terminal provider
 * failure throws.
 */
export class ResponsesStreamTranslator {
    blocks = new Map();
    order = [];
    nextIndex = 0;
    sawToolCall = false;
    /** Set once `response.completed` produced the terminal finish chunk. */
    terminated = false;
    open(key, kind, chunks, callId = '', name) {
        const block = {
            index: this.nextIndex++,
            kind,
            text: '',
            callId,
            ...name === undefined ? {} : { name },
        };
        this.blocks.set(key, block);
        this.order.push(block);
        chunks.push({ type: 'block-start', index: block.index, blockType: kind });
        return block;
    }
    close(key, chunks) {
        const block = this.blocks.get(key);
        if (block === undefined)
            return;
        this.blocks.delete(key);
        chunks.push({ type: 'block-end', index: block.index, block: closeBlock(block) });
    }
    /** Close every open block whose key belongs to one output item. */
    closeItem(itemId, chunks) {
        for (const key of [...this.blocks.keys()]) {
            if (key.startsWith(`${itemId}:`))
                this.close(key, chunks);
        }
    }
    closeAll(chunks) {
        for (const key of [...this.blocks.keys()])
            this.close(key, chunks);
    }
    /**
     * Process one parsed Responses event.
     * @param event - the parsed event object.
     * @returns the chunks this event produced, possibly none.
     * @throws {LlmError} on a terminal provider failure event.
     */
    push(event) {
        if (this.terminated)
            return [];
        const chunks = [];
        switch (event.type) {
            case 'response.output_item.added': {
                const item = event.item;
                if (item?.type === 'function_call' && item.id !== undefined) {
                    this.sawToolCall = true;
                    const callId = item.call_id ?? '';
                    const block = this.open(`${item.id}:call`, 'tool-call', chunks, callId, item.name);
                    chunks.push({
                        type: 'tool-call-delta',
                        index: block.index,
                        id: ToolCallId(callId),
                        ...item.name === undefined ? {} : { name: item.name },
                        argumentsDelta: '',
                    });
                }
                return chunks;
            }
            case 'response.output_text.delta': {
                const key = `${event.item_id ?? ''}:text:${String(event.content_index ?? 0)}`;
                const block = this.blocks.get(key) ?? this.open(key, 'text', chunks);
                const text = event.delta ?? '';
                block.text += text;
                chunks.push({ type: 'text-delta', index: block.index, text });
                return chunks;
            }
            case 'response.reasoning_summary_text.delta':
            case 'response.reasoning_text.delta': {
                const part = event.summary_index ?? event.content_index ?? 0;
                const key = `${event.item_id ?? ''}:reason:${String(part)}`;
                const block = this.blocks.get(key) ?? this.open(key, 'reasoning', chunks);
                const text = event.delta ?? '';
                block.text += text;
                chunks.push({ type: 'reasoning-delta', index: block.index, text });
                return chunks;
            }
            case 'response.function_call_arguments.delta': {
                const key = `${event.item_id ?? ''}:call`;
                let block = this.blocks.get(key);
                if (block === undefined) {
                    // The item.added event was missed; open the block from the delta alone.
                    this.sawToolCall = true;
                    block = this.open(key, 'tool-call', chunks);
                }
                const text = event.delta ?? '';
                block.text += text;
                chunks.push({
                    type: 'tool-call-delta',
                    index: block.index,
                    id: ToolCallId(block.callId),
                    ...block.name === undefined ? {} : { name: block.name },
                    argumentsDelta: text,
                });
                return chunks;
            }
            case 'response.output_item.done': {
                const item = event.item;
                if (item === undefined || item.id === undefined) {
                    this.closeAll(chunks);
                    return chunks;
                }
                if (item.type === 'function_call') {
                    const key = `${item.id}:call`;
                    let block = this.blocks.get(key);
                    if (block === undefined) {
                        this.sawToolCall = true;
                        block = this.open(key, 'tool-call', chunks, item.call_id ?? '', item.name);
                    }
                    // A complete item may deliver its arguments only on done.
                    if (block.text.length === 0 && item.arguments !== undefined)
                        block.text = item.arguments;
                    this.close(key, chunks);
                }
                else if (item.type === 'message') {
                    const hasDeltas = [...this.blocks.keys()].some((key) => key.startsWith(`${item.id}:text:`));
                    if (!hasDeltas) {
                        for (const [partIndex, part] of (item.content ?? []).entries()) {
                            if (part?.type !== 'output_text' || typeof part.text !== 'string' || part.text.length === 0)
                                continue;
                            const key = `${item.id}:text:${String(partIndex)}`;
                            const block = this.open(key, 'text', chunks);
                            block.text = part.text;
                            chunks.push({ type: 'text-delta', index: block.index, text: part.text });
                            this.close(key, chunks);
                        }
                    }
                    this.closeItem(item.id, chunks);
                }
                else {
                    this.closeItem(item.id, chunks);
                }
                return chunks;
            }
            case 'response.completed': {
                this.terminated = true;
                this.closeAll(chunks);
                const usage = event.response?.usage;
                if (usage !== undefined)
                    chunks.push({ type: 'usage', usage: mapResponsesUsage(usage) });
                if (this.order.length === 0) {
                    chunks.push({
                        type: 'finish',
                        reason: {
                            kind: 'error',
                            failure: {
                                message: 'model returned a completed response with no content',
                                code: EMPTY_RESPONSE_CODE,
                            },
                        },
                    });
                }
                else {
                    chunks.push({ type: 'finish', reason: { kind: this.sawToolCall ? 'tool-calls' : 'stop' } });
                }
                return chunks;
            }
            case 'response.failed':
                throw responsesFailure(event.response?.error?.code, event.response?.error?.message);
            case 'response.incomplete':
                throw responsesFailure(event.response?.incomplete_details?.reason, event.response?.error?.message
                    ?? `the provider reported an incomplete response (${event.response?.incomplete_details?.reason ?? 'unknown reason'})`);
            case 'error':
                throw responsesFailure(event.code, event.message);
            default:
                // response.created, response.in_progress, content_part events, etc.
                return chunks;
        }
    }
}
/**
 * Consume a Responses SSE body and yield harness chunks.
 * @param stream - the raw response body.
 * @param onActivity - transport-activity callback for the idle watchdog.
 * @yields harness stream chunks in contract order.
 * @throws {LlmError} `MALFORMED_RESPONSE` on an undecodable event, `STREAM_CLOSED` when the
 *   body ends before `response.completed`.
 */
export async function* streamResponses(stream, onActivity) {
    const translator = new ResponsesStreamTranslator();
    for await (const event of parseSse(stream, onActivity)) {
        let decoded;
        try {
            decoded = JSON.parse(event.data);
        }
        catch {
            throw new LlmError(`malformed SSE payload: ${event.data.slice(0, 120)}`, 'MALFORMED_RESPONSE');
        }
        yield* translator.push(decoded);
        if (translator.terminated)
            return;
    }
    throw new LlmError('Kimi Responses stream ended before response.completed', 'STREAM_CLOSED');
}
