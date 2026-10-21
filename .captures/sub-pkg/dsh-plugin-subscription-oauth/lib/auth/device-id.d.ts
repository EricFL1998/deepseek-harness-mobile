/**
 * The per-machine device id Kimi Code's `X-Msh-Device-Id` header carries.
 *
 * The value is a 32-character lowercase hex UUID (the CLI's format: a v4 UUID
 * with its dashes removed). It must survive restarts — a changing id looks
 * like a new machine to the service — so it is persisted under the plugin's
 * data directory with owner-only permissions.
 *
 * @module dsh-plugin-subscription-oauth/auth/device-id
 */
/**
 * Mint a new device id in the CLI's format.
 * @returns 32 lowercase hex characters.
 */
export declare function mintDeviceId(): string;
/**
 * Read the persisted device id, minting and storing one on first use.
 *
 * A malformed or unreadable file is replaced rather than rejected: the header
 * is an identity hint, and refusing to start would strand the plugin.
 *
 * @param directory - absolute plugin data directory.
 * @returns the stable 32-hex device id.
 */
export declare function loadOrCreateDeviceId(directory: string): Promise<string>;
/**
 * Read and validate one stored device id.
 * @param file - absolute path to the device-id file.
 * @returns the stored id, or undefined when absent or malformed.
 */
export declare function readDeviceId(file: string): Promise<string | undefined>;
