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
/**
 * The platform word Kimi's client reports. `os.platform()` yields `win32` and
 * `darwin`, which the service does not recognise; the CLI reports the
 * marketing name instead.
 * @param platform - `process.platform` value.
 * @returns the platform word used in `X-Msh-Device-Model`.
 */
export declare function platformWord(platform?: string): string;
/**
 * Build the device-model header value: `<platform> <release> <arch>`.
 * @param platform - `process.platform` value.
 * @returns one space-separated descriptor line.
 */
export declare function deviceModel(platform?: string): string;
/**
 * Resolve the OS-version header. `os.version()` is absent on some runtimes, so
 * an unavailable value degrades to the kernel release rather than the literal
 * string `"undefined"`.
 * @returns a non-empty version descriptor.
 */
export declare function osVersion(): string;
/**
 * Assemble the identity headers every Kimi Code request carries.
 * @param deviceId - this machine's persisted 32-hex device id.
 * @returns the `X-Msh-*` / `User-Agent` header record.
 */
export declare function kimiIdentityHeaders(deviceId: string): Record<string, string>;
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
export declare function kimiRequestHeaders(identity: Record<string, string>, extra?: Record<string, string>): Record<string, string>;
/**
 * Attribution headers for a provider that needs no client identity of its own.
 *
 * @param extra - request-specific headers, which win on collision.
 * @returns the header record to send.
 */
export declare function attributedHeaders(extra?: Record<string, string>): Record<string, string>;
