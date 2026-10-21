/**
 * `events`: transient messages queued for the phone.
 *
 * The client polls `pending`; each call drains, so a message is delivered once.
 */
import type { Provider } from '../protocol/provider.ts';
import type { TransientEvent } from '../events.ts';
/** `events` configuration. */
export interface EventsOptions {
    /** Take everything queued since the last drain. */
    readonly drain: () => readonly TransientEvent[];
}
/**
 * Build the `events` provider.
 * @param options - queue accessor.
 * @returns the provider to register.
 */
export declare function createEventsProvider(options: EventsOptions): Provider;
