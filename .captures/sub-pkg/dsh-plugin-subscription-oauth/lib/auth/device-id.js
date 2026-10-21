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
import { randomUUID } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { KIMI_DEVICE_ID_FILENAME } from '../constants.js';
/** Matches the CLI's id format: 32 lowercase hex characters. */
const DEVICE_ID_PATTERN = /^[0-9a-f]{32}$/;
/**
 * Mint a new device id in the CLI's format.
 * @returns 32 lowercase hex characters.
 */
export function mintDeviceId() {
    return randomUUID().replace(/-/g, '');
}
/**
 * Read the persisted device id, minting and storing one on first use.
 *
 * A malformed or unreadable file is replaced rather than rejected: the header
 * is an identity hint, and refusing to start would strand the plugin.
 *
 * @param directory - absolute plugin data directory.
 * @returns the stable 32-hex device id.
 */
export async function loadOrCreateDeviceId(directory) {
    const file = path.join(directory, KIMI_DEVICE_ID_FILENAME);
    const existing = await readDeviceId(file);
    if (existing !== undefined)
        return existing;
    const created = mintDeviceId();
    await mkdir(directory, { recursive: true });
    await writeFile(file, `${created}\n`, { encoding: 'utf8', mode: 0o600 });
    return created;
}
/**
 * Read and validate one stored device id.
 * @param file - absolute path to the device-id file.
 * @returns the stored id, or undefined when absent or malformed.
 */
export async function readDeviceId(file) {
    let raw;
    try {
        raw = await readFile(file, 'utf8');
    }
    catch {
        return undefined;
    }
    const value = raw.trim();
    return DEVICE_ID_PATTERN.test(value) ? value : undefined;
}
