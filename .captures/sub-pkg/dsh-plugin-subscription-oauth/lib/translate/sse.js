/**
 * Server-sent-events reader for one provider response body.
 *
 * The reader is deliberately protocol-only: it reassembles transport chunks
 * into whole events and leaves interpretation to the wire layer above it.
 *
 * @module dsh-plugin-subscription-oauth/translate/sse
 */
/** Apply one already-unfolded SSE line to the pending event. */
function applyLine(pending, line) {
    const colon = line.indexOf(':');
    const field = colon < 0 ? line : line.slice(0, colon);
    let value = colon < 0 ? '' : line.slice(colon + 1);
    if (value.startsWith(' '))
        value = value.slice(1);
    if (field === 'data')
        pending.data.push(value);
    else if (field === 'event')
        pending.event = value;
}
/** Convert a pending event into a yielded value, or undefined when empty. */
function asEvent(pending) {
    if (pending.data.length === 0 && pending.event === undefined)
        return undefined;
    const data = pending.data.join('\n');
    return pending.event === undefined ? { data } : { data, event: pending.event };
}
/**
 * Decode an SSE byte stream into events.
 *
 * Multiple `data:` lines of one event are joined with `\n`, per the SSE
 * specification, and `:` comment lines (often keep-alive pings) are skipped
 * but still reported as transport activity so an idle watchdog can see them.
 *
 * @param stream - the raw response body.
 * @param onActivity - called for every decoded line, including comments.
 * @yields each complete event in arrival order.
 */
export async function* parseSse(stream, onActivity) {
    const reader = stream.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let pending = { data: [] };
    try {
        for (;;) {
            const { done, value } = await reader.read();
            if (done)
                break;
            buffer += decoder.decode(value, { stream: true });
            let newline = buffer.indexOf('\n');
            while (newline >= 0) {
                const line = buffer.slice(0, newline).replace(/\r$/, '');
                buffer = buffer.slice(newline + 1);
                if (line.length === 0) {
                    const event = asEvent(pending);
                    if (event !== undefined)
                        yield event;
                    pending = { data: [] };
                }
                else {
                    if (line.startsWith(':'))
                        onActivity?.();
                    else
                        applyLine(pending, line);
                    onActivity?.();
                }
                newline = buffer.indexOf('\n');
            }
        }
        // A stream that ends without a terminating blank line still carries a
        // usable final event; flush it rather than dropping the payload.
        const tail = buffer.replace(/\r$/, '');
        if (tail.length > 0)
            applyLine(pending, tail);
        const last = asEvent(pending);
        if (last !== undefined)
            yield last;
    }
    finally {
        await reader.cancel().catch(() => undefined);
    }
}
