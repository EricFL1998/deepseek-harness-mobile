/**
 * The `X-Msh-*` client-identity headers Kimi Code admits a request under.
 *
 * Kimi's coding service admits a caller through this header family rather than
 * a bearer-token grant alone: the CLI declares its own version, its platform
 * family, and a per-machine device id that must stay stable across restarts
 * (the service correlates usage to it). The device model string is a single
 * `<platform> <release> <arch>` line, not three headers.
 *
 * @module dsh-plugin-subscription-oauth/auth/headers
 */
import os from 'node:os';
import { attributionHeaders, userAgent } from '@deepseek-ai/dsh-llm';
import { KIMI_CLI_VERSION } from '../constants.js';
/**
 * The platform word Kimi's client reports. `os.platform()` yields `win32` and
 * `darwin`, which the service does not recognise; the CLI reports the
 * marketing name instead.
 * @param platform - `process.platform` value.
 * @returns the platform word used in `X-Msh-Device-Model`.
 */
export function platformWord(platform = process.platform) {
    switch (platform) {
        case 'win32': return 'Windows';
        case 'darwin': return 'macOS';
        case 'linux': return 'Linux';
        default: return platform;
    }
}
/**
 * Build the device-model header value: `<platform> <release> <arch>`.
 * @param platform - `process.platform` value.
 * @returns one space-separated descriptor line.
 */
export function deviceModel(platform = process.platform) {
    return `${platformWord(platform)} ${os.release()} ${os.arch()}`;
}
/**
 * Resolve the OS-version header. `os.version()` is absent on some runtimes, so
 * an unavailable value degrades to the kernel release rather than the literal
 * string `"undefined"`.
 * @returns a non-empty version descriptor.
 */
export function osVersion() {
    const version = os.version;
    if (typeof version === 'function') {
        try {
            const value = version();
            if (typeof value === 'string' && value.length > 0)
                return value;
        }
        catch {
            // fall through to the release string
        }
    }
    return os.release();
}
/**
 * Assemble the identity headers every Kimi Code request carries.
 * @param deviceId - this machine's persisted 32-hex device id.
 * @returns the `X-Msh-*` / `User-Agent` header record.
 */
export function kimiIdentityHeaders(deviceId) {
    return {
        'User-Agent': `KimiCLI/${KIMI_CLI_VERSION}`,
        'X-Msh-Platform': 'kimi_code_cli',
        'X-Msh-Version': KIMI_CLI_VERSION,
        'X-Msh-Device-Name': os.hostname(),
        'X-Msh-Device-Model': deviceModel(),
        'X-Msh-Os-Version': osVersion(),
        'X-Msh-Device-Id': deviceId,
    };
}
/**
 * Merge the Kimi client identity with the harness attribution header for one
 * provider request.
 *
 * Two requirements meet here: Kimi's service admits a request by the
 * `KimiCLI/<version>` product token, and the harness contract requires
 * `attributionHeaders()` on every provider request. Sending both products as
 * separate `User-Agent` fields would let a transport join them with `", "`, so
 * they are combined into one RFC 9110 product value instead. Every other
 * identity header is passed through untouched.
 *
 * @param identity - {@link kimiIdentityHeaders} output for this machine.
 * @param extra - request-specific headers, which win on collision.
 * @returns the header record to send.
 */
export function kimiRequestHeaders(identity, extra = {}) {
    const cliAgent = identity['User-Agent'] ?? identity['user-agent'];
    const headers = { ...identity, ...attributionHeaders(), ...extra };
    if (cliAgent !== undefined) {
        delete headers['user-agent'];
        headers['User-Agent'] = `${cliAgent} ${userAgent()}`;
    }
    return headers;
}
/**
 * Attribution headers for a provider that needs no client identity of its own.
 *
 * @param extra - request-specific headers, which win on collision.
 * @returns the header record to send.
 */
export function attributedHeaders(extra = {}) {
    return { ...attributionHeaders(), ...extra };
}
