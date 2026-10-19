/**
 * `events`: transient messages queued for the phone.
 *
 * The client polls `pending`; each call drains, so a message is delivered once.
 */
/**
 * Build the `events` provider.
 * @param options - queue accessor.
 * @returns the provider to register.
 */
export function createEventsProvider(options) {
    return {
        id: 'events',
        version: 1,
        ops: {
            /** Messages queued since the previous call. */
            pending: () => ({ events: options.drain() }),
        },
    };
}
