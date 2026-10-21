/**
 * Server-sent-events reader for one provider response body.
 *
 * The reader is deliberately protocol-only: it reassembles transport chunks
 * into whole events and leaves interpretation to the wire layer above it.
 *
 * @module dsh-plugin-subscription-oauth/translate/sse
 */
/** One decoded SSE event. */
export interface SseEvent {
    /** Joined `data:` lines of the event. */
    readonly data: string;
    /** `event:` field, when the server set one. */
    readonly event?: string;
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
export declare function parseSse(stream: ReadableStream<Uint8Array>, onActivity?: () => void): AsyncGenerator<SseEvent>;
