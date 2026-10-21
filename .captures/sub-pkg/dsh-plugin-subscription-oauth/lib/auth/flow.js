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
export {};
