/**
 * Loopback OAuth: PKCE helpers plus a temporary local redirect listener.
 *
 * Providers that complete authorization in the browser (xAI, Google) redirect to
 * a `127.0.0.1` URL this process owns for the duration of one attempt. The
 * authorization code never leaves the machine, and `state` is validated before
 * the code is accepted so a stray or replayed redirect cannot settle the login.
 *
 * @module dsh-plugin-subscription-oauth/auth/pkce-flow
 */
import { createHash, randomBytes } from 'node:crypto';
import { createServer } from 'node:http';
/** Mint a fresh PKCE pair (32-byte verifier, S256 challenge). */
export function createPkce() {
    const verifier = randomBytes(32).toString('base64url');
    const challenge = createHash('sha256').update(verifier).digest('base64url');
    return { verifier, challenge };
}
/** Mint a random OAuth `state` value. */
export function randomState() {
    return randomBytes(32).toString('base64url');
}
/** Attempt lifetime when a spec names none. */
export const DEFAULT_FLOW_TIMEOUT_MS = 180_000;
/** Message every cancelled attempt rejects with. */
export const LOGIN_CANCELLED_MESSAGE = 'login cancelled';
const SUCCESS_PAGE = '<!doctype html><html><head><meta charset="utf-8"><title>Login successful</title></head>'
    + '<body style="font-family:sans-serif"><h1>Login successful</h1>'
    + '<p>You can close this tab and return to DeepSeek Harness.</p></body></html>';
/**
 * Addresses one listen host covers.
 *
 * `localhost` resolves to `::1` or `127.0.0.1` depending on the client, and a
 * listener binds exactly one family per call — a browser picking the other gets
 * connection-refused and the login times out, so both families must serve.
 */
function listenHosts(host) {
    return host === 'localhost' ? ['127.0.0.1', '::1'] : [host];
}
/** True when an address family simply does not exist here (safe to skip). */
function familyUnavailable(error) {
    const code = error.code;
    return code === 'EADDRNOTAVAIL' || code === 'EPROTONOSUPPORT';
}
/** Listen on the first port free on every loopback family. */
async function listen(handler, host, ports) {
    const hosts = listenHosts(host);
    let lastError;
    for (const candidate of ports) {
        const servers = [];
        let port = candidate;
        let unusable = false;
        for (const address of hosts) {
            const server = createServer(handler);
            try {
                await new Promise((resolve, reject) => {
                    const onError = (error) => reject(error);
                    server.once('error', onError);
                    server.listen(port, address, () => {
                        server.removeListener('error', onError);
                        resolve();
                    });
                });
                const bound = server.address();
                if (bound === null)
                    throw new Error(`callback listener on ${address}:${String(port)} has no address`);
                if (port === 0)
                    port = bound.port;
                servers.push(server);
            }
            catch (error) {
                server.close();
                if (familyUnavailable(error))
                    continue;
                lastError = error;
                unusable = true;
                break;
            }
        }
        if (unusable || servers.length === 0) {
            for (const server of servers)
                server.close();
            continue;
        }
        return { servers, port };
    }
    throw lastError instanceof Error
        ? lastError
        : new Error(`callback listener could not bind ${host} (ports ${ports.join(', ')})`);
}
/**
 * Owns at most one in-flight loopback login per provider.
 *
 * A listener is created per attempt and closed as soon as the attempt settles,
 * so nothing stays open on this machine between logins.
 */
export class LoopbackFlow {
    key;
    spec;
    host;
    attempts = new Map();
    /**
     * @param key - provider route key, so one attempt runs at a time per route.
     * @param spec - static flow facts for this provider.
     */
    constructor(key, spec, host = '127.0.0.1') {
        this.key = key;
        this.spec = spec;
        this.host = host;
    }
    pending() {
        return this.attempts.get(this.key);
    }
    async start() {
        if (this.attempts.has(this.key)) {
            throw new Error(`a ${this.key} login attempt is already in progress`);
        }
        const pkce = createPkce();
        const state = randomState();
        const timeoutMs = this.spec.timeoutMs ?? DEFAULT_FLOW_TIMEOUT_MS;
        const controller = new AbortController();
        // Discovery-backed specs resolve their endpoints before anything is bound.
        await this.spec.prepare?.();
        let settle;
        let fail;
        const session = new Promise((resolve, reject) => {
            settle = resolve;
            fail = reject;
        });
        session.catch(() => undefined);
        let settled = false;
        let timer;
        let servers = [];
        /** The bound port, known once the listener is up; the handler runs later. */
        let port = 0;
        const finish = (error, value) => {
            if (settled)
                return;
            settled = true;
            if (timer !== undefined)
                clearTimeout(timer);
            for (const server of servers) {
                server.close();
                server.closeAllConnections();
            }
            this.attempts.delete(this.key);
            controller.abort();
            if (error !== undefined)
                fail(error);
            else if (value !== undefined)
                settle(value);
        };
        const handler = (request, response) => {
            const url = new URL(request.url ?? '/', 'http://localhost');
            if (url.pathname !== this.spec.callbackPath) {
                response.writeHead(404, { 'content-type': 'text/plain' });
                response.end('not found');
                return;
            }
            const reported = url.searchParams.get('error_description') ?? url.searchParams.get('error');
            if (reported !== null) {
                response.writeHead(200, { 'content-type': 'text/html' });
                response.end('<!doctype html><html><body><h1>Login failed</h1></body></html>');
                finish(new Error(`authorization failed: ${reported}`));
                return;
            }
            if (url.searchParams.get('state') !== state) {
                // A stray or replayed redirect must not kill the real attempt.
                response.writeHead(400, { 'content-type': 'text/plain' });
                response.end('state mismatch');
                return;
            }
            const code = url.searchParams.get('code');
            if (code === null || code.length === 0) {
                response.writeHead(400, { 'content-type': 'text/plain' });
                response.end('missing authorization code');
                return;
            }
            response.writeHead(200, { 'content-type': 'text/html' });
            response.end(SUCCESS_PAGE);
            const redirectUri = `http://${this.host}:${String(port)}${this.spec.callbackPath}`;
            void this.spec.exchange({ code, redirectUri, pkce, state, signal: controller.signal })
                .then((session) => { finish(undefined, session); })
                .catch((error) => {
                finish(error instanceof Error ? error : new Error(String(error)));
            });
        };
        const bound = await listen(handler, this.host, this.spec.ports);
        servers = bound.servers;
        port = bound.port;
        const redirectUri = `http://${this.host}:${String(port)}${this.spec.callbackPath}`;
        timer = setTimeout(() => {
            finish(new Error(`login timed out after ${String(Math.round(timeoutMs / 1000))}s`));
        }, timeoutMs);
        timer.unref?.();
        const prompt = { url: this.spec.buildAuthorizeUrl({ redirectUri, state, pkce }), expiresAt: Date.now() + timeoutMs, mode: 'open-url' };
        const attempt = {
            prompt,
            session,
            cancel: () => { finish(new Error(LOGIN_CANCELLED_MESSAGE)); },
        };
        this.attempts.set(this.key, attempt);
        return attempt;
    }
}
