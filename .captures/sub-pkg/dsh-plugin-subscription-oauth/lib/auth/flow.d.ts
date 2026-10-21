/**
 * The interactive-login contract every provider route implements.
 *
 * A login engine owns at most one in-flight attempt per provider. Two shapes
 * exist behind it: a device authorization (the browser shows a code and the
 * engine polls) and a loopback authorization-code flow (the browser opens a URL
 * and the engine receives the redirect). The controller above only ever sees
 * this interface, so both behave identically from the Settings page's view.
 *
 * @module dsh-plugin-subscription-oauth/auth/flow
 */
import type { LoginPrompt } from '../protocol.js';
import type { SessionBase } from './store.js';
/** One in-flight login. */
export interface AuthAttempt<S extends SessionBase> {
    /** What the Settings page shows to finish this attempt. */
    readonly prompt: LoginPrompt;
    /** Resolves with the session once the user approves; rejects on failure/cancel. */
    readonly session: Promise<S>;
    /** Abandon the attempt; `session` rejects with a cancellation. */
    cancel(): void;
}
/** Starts and tracks interactive logins for one provider. */
export interface AuthFlow<S extends SessionBase> {
    /**
     * Begin an attempt.
     * @returns the live attempt, whose prompt the caller shows.
     * @throws when an attempt is already running.
     */
    start(): Promise<AuthAttempt<S>>;
    /** The in-flight attempt, when one exists. */
    pending(): AuthAttempt<S> | undefined;
}
