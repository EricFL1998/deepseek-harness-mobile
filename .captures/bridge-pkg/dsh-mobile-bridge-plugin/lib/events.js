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
/** How many undelivered messages are kept. */
const MAX_PENDING = 16;
/**
 * Build an event queue.
 * @returns the queue.
 */
export function createEventQueue() {
    let pending = [];
    let seq = 0;
    return {
        push(kind, text) {
            seq += 1;
            pending.push({ kind, text, seq });
            if (pending.length > MAX_PENDING)
                pending = pending.slice(-MAX_PENDING);
        },
        drain() {
            const taken = pending;
            pending = [];
            return taken;
        },
    };
}
