/**
 * Demo 插件的 RPC 路由。
 *
 * 与真实插件（例如订阅插件）走完全相同的通道，没有为 Demo 发明新协议：
 *
 * ```
 * 原生 PluginRpcHost.callRpc("demo.status", {})
 *   → App 侧 pluginRpc(method, payload)
 *   → POST /api/demo.status   { type: "client-request", rpcId, method, payload }
 *   → 本文件的 route.fetch
 *   → { type: "server-response", rpcId, result: { ok, value | error } }
 * ```
 *
 * 关键点：
 * - 路由注册在 Host 的 `connection` 服务上（`connection.fetch.register`）；
 * - 请求体是**信封**，payload 原样传递、不包 `args`；
 * - 业务失败也返回 HTTP 200，错误码放在 `result.error`，这样原生端能统一按
 *   信封解析，不需要区分「网络错误」和「业务错误」两种失败形状。
 */

import {
  OK_STATUS,
  REQUEST_TYPE,
  RESPONSE_TYPE,
  RPC_ENDPOINTS,
  RPC_PREFIX,
} from './constants.js';
import { demoCatalog } from './catalog.js';

/** 包里可见的错误码；原生端可以按码分支，而不是匹配文案。 */
const ERROR_BAD_REQUEST = 'demo/bad-request';
const ERROR_UNSUPPORTED_MEDIA_TYPE = 'demo/unsupported-media-type';
const ERROR_UNKNOWN_ENDPOINT = 'demo/unknown-endpoint';

/**
 * 把一段值包成成功信封。
 * @param {unknown} value 可 JSON 序列化的结果。
 * @returns {{ok: true, value: unknown}}
 */
function success(value) {
  return { ok: true, value };
}

/**
 * 把一次失败包成错误信封。
 * @param {string} code 稳定错误码。
 * @param {string} message 给人看的说明。
 * @returns {{ok: false, error: {code: string, message: string, details: object}}}
 */
function failure(code, message) {
  return { ok: false, error: { code, message, details: {} } };
}

/**
 * 构造一个完整的响应信封。
 * @param {string} rpcId 请求里带回来的 id。
 * @param {object} result 成功/失败信封。
 * @param {number} [status] HTTP 状态，默认 200。
 * @returns {Response}
 */
function envelope(rpcId, result, status = OK_STATUS) {
  return Response.json({ type: RESPONSE_TYPE, rpcId, result }, { status });
}

/**
 * 读一个可选的数字字段。
 * @param {unknown} payload 请求 payload。
 * @param {string} field 字段名。
 * @returns {number|undefined}
 */
function numberField(payload, field) {
  if (typeof payload !== 'object' || payload === null) return undefined;
  const value = /** @type {Record<string, unknown>} */ (payload)[field];
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}

/**
 * 执行一个端点。
 *
 * 所有业务错误都在这里转成错误信封，路由本身不抛异常。
 * @param {string} endpoint 端点名（不含前缀）。
 * @param {unknown} payload 请求 payload。
 * @param {{ snapshot: Function, bump: Function, reset: Function, touch: Function }} state Demo 状态。
 * @returns {object} 成功或失败信封。
 */
function dispatch(endpoint, payload, state) {
  switch (endpoint) {
    case 'status':
      return success(state.snapshot());
    case 'catalog':
      return success(demoCatalog());
    case 'ping':
      return success({ pong: true, serverTime: Date.now() });
    case 'bump':
      return success(state.bump(numberField(payload, 'by') ?? 1));
    case 'reset':
      return success(state.reset());
    case 'echo':
      return success({ received: payload ?? {} });
    default:
      return failure(ERROR_UNKNOWN_ENDPOINT, `unknown endpoint: ${endpoint}`);
  }
}

/**
 * 为一个端点构造 Fetch 路由。
 * @param {string} method 完整方法名（`demo.status`）。
 * @param {string} endpoint 端点名（`status`）。
 * @param {object} state Demo 状态。
 * @returns {{path: string, methods: string[], requestBody: string, fetch: (request: Request) => Promise<Response>}}
 */
function rpcRoute(method, endpoint, state) {
  return {
    path: `/api/${method}`,
    methods: ['POST'],
    requestBody: 'buffered',
    fetch: async (request) => {
      const contentType = request.headers.get('content-type') ?? '';
      if (!contentType.toLowerCase().includes('application/json')) {
        return envelope(
          '',
          failure(ERROR_UNSUPPORTED_MEDIA_TYPE, `${method} 需要 application/json 请求体`),
          415,
        );
      }
      const body = await request.text();
      /** @type {any} */
      let parsed;
      try {
        parsed = body.length === 0 ? {} : JSON.parse(body);
      } catch {
        return envelope('', failure(ERROR_BAD_REQUEST, `${method} 收到无法解析的 JSON`), 400);
      }
      const rpcId = typeof parsed?.rpcId === 'string' ? parsed.rpcId : '';
      if (parsed?.type !== REQUEST_TYPE || parsed?.method !== method) {
        return envelope(
          rpcId,
          failure(ERROR_BAD_REQUEST, `${method} 收到了属于其它方法的信封`),
          400,
        );
      }
      return envelope(rpcId, dispatch(endpoint, parsed.payload, state));
    },
  };
}

/**
 * 把全部端点注册到 Host 的 `connection` 服务。
 *
 * `ctx.inject` 是「服务出现时再执行」的等待语义：Host 组合里没有 web server
 * 时插件照常加载，只是不暴露 RPC（原生端会收到可读的失败）。
 *
 * @param {any} ctx 插件 Cordis 上下文。
 * @param {object} state Demo 状态。
 */
export function registerDemoRpc(ctx, state) {
  ctx.inject(['connection'], (/** @type {any} */ connectionCtx) => {
    const connection = connectionCtx.get('connection');
    const register = connection?.fetch?.register;
    if (typeof register !== 'function') {
      ctx.logger?.warn?.('dsh-plugin-demo: connection.fetch.register 不可用，demo RPC 未注册');
      return;
    }
    for (const endpoint of RPC_ENDPOINTS) {
      const method = `${RPC_PREFIX}${endpoint}`;
      const route = rpcRoute(method, endpoint, state);
      connectionCtx.effect(
        () => register.call(connection.fetch, route),
        `dsh-plugin-demo: /api/${method}`,
      );
    }
  });
}

/** 导出给文档生成器/测试复用：全部路由方法名。 */
export const RPC_METHODS = RPC_ENDPOINTS.map((endpoint) => `${RPC_PREFIX}${endpoint}`);
