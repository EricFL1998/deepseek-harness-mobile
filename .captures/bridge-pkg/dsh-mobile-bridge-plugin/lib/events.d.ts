/**
 * Transient messages a plugin asks the phone to show.
 *
 * A toast has no screen: it must appear *when the plugin says so*, which the
 * registration list cannot express. This queue is that channel — a plugin pushes a
 * message, the client drains it and shows it with its own toast.
 *
 * The queue is deliberately small and bounded: a transient message that nobody
 * collected is stale, not backlogged, so the oldest entries fall off instead of
 * replaying a burst the user can no longer relate to anything.
 */
/** One transient message. */
export interface TransientEvent {
    /**
     * Message kind. `toast` is shown as-is; `client/refresh` names a component
     * (`text` is its componentId) whose tree the client should re-pull, which is
     * how a plugin moves fresh state onto the phone after a `dispatch`.
     */
    readonly kind: 'toast' | 'client/refresh';
    /** One line of text (toast) or the componentId to refresh (client/refresh). */
    readonly text: string;
    /** Monotonic sequence number, so a client can drop repeats. */
    readonly seq: number;
}
/** The queue the route writes to and the provider drains. */
export interface EventQueue {
    /** Append one message; oldest entries are dropped past the cap. */
    push(kind: 'toast' | 'client/refresh', text: string): void;
    /** Take everything queued since the last drain. */
    drain(): readonly TransientEvent[];
}
/**
 * Build an event queue.
 * @returns the queue.
 */
export declare function createEventQueue(): EventQueue;
