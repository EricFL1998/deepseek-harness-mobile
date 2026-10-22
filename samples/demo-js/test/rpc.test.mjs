/**
 * Demo 插件 RPC / 原生动作的离线测试。
 *
 * 不需要 DSH Host、Android 或网络：用最小的 Cordis 上下文桩收集插件注册的
 * 路由与动作，再按 App 实际发送的信封调用它们。这样文档里的「RPC 接入案例」
 * 是可执行、可回归的，而不只是贴在 Markdown 里的示例。
 *
 * 运行：node --test samples/demo-js/test/
 */

import test from 'node:test';
import assert from 'node:assert/strict';

import { apply, createDemoState } from '../lib/index.js';

/**
 * 搭一个最小 Cordis 上下文。
 *
 * @returns {{
 *   routes: Map<string, any>,
 *   actions: Map<string, Function>,
 *   ctx: any,
 * }}
 */
function createHarness() {
  const routes = new Map();
  const actions = new Map();
  const connection = {
    fetch: {
      register(route) {
        routes.set(route.path, route);
        return () => routes.delete(route.path);
      },
    },
  };
  const mobileUi = {
    registerAction(componentId, actionId, handler) {
      const key = `${componentId}/${actionId}`;
      actions.set(key, handler);
      return () => actions.delete(key);
    },
  };
  const ctx = {
    get(service) {
      if (service === 'connection') return connection;
      if (service === 'mobileUi') return mobileUi;
      return undefined;
    },
    inject(_services, callback) {
      callback(ctx);
      return () => {};
    },
    effect(callback) {
      const disposer = callback();
      return typeof disposer === 'function' ? disposer : () => {};
    },
    logger: { warn() {}, info() {}, debug() {} },
  };
  return { routes, actions, ctx };
}

/**
 * 发一条与 App 相同的信封请求。
 *
 * @param {Map<string, any>} routes 已注册路由。
 * @param {string} method RPC 方法名。
 * @param {object} [payload] 原样 payload。
 * @returns {Promise<{status: number, body: any}>}
 */
async function call(routes, method, payload = {}) {
  const route = routes.get(`/api/${method}`);
  assert.ok(route, `route /api/${method} 应已注册`);
  const request = new Request(`http://localhost/api/${method}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      type: 'client-request',
      rpcId: 'test-rpc',
      method,
      payload,
    }),
  });
  const response = await route.fetch(request);
  return { status: response.status, body: await response.json() };
}

test('Demo 插件注册全部 RPC 路由与原生动作', () => {
  const { routes, actions, ctx } = createHarness();
  apply(ctx);
  for (const endpoint of ['status', 'catalog', 'ping', 'bump', 'reset', 'echo']) {
    assert.ok(routes.has(`/api/demo.${endpoint}`), `应注册 /api/demo.${endpoint}`);
  }
  assert.ok(actions.has('dsh-plugin-demo#demo/bump'), '设置页 bump 动作');
  assert.ok(actions.has('dsh-plugin-demo#demo/reset'), '设置页 reset 动作');
  assert.ok(actions.has('dsh-plugin-demo#demo-dock/bump'), 'dock bump 动作');
});

test('demo.status 返回初始计数 42，bump/reset 改变 Host 状态', async () => {
  const { routes, ctx } = createHarness();
  apply(ctx);

  const first = await call(routes, 'demo.status');
  assert.equal(first.status, 200);
  assert.equal(first.body.type, 'server-response');
  assert.equal(first.body.rpcId, 'test-rpc');
  assert.equal(first.body.result.ok, true);
  assert.equal(first.body.result.value.count, 42);
  assert.equal(first.body.result.value.greeting, 'Hello from dsh-plugin-demo');

  const bumped = await call(routes, 'demo.bump', { by: 8 });
  assert.equal(bumped.body.result.value.count, 50);
  assert.equal(bumped.body.result.value.lastAction, 'bump(8)');

  const reset = await call(routes, 'demo.reset');
  assert.equal(reset.body.result.value.count, 42);
  assert.equal(reset.body.result.value.lastAction, 'reset');
});

test('demo.catalog 返回注册清单与 RPC 端点，供原生页渲染', async () => {
  const { routes, ctx } = createHarness();
  apply(ctx);

  const { body } = await call(routes, 'demo.catalog');
  assert.equal(body.result.ok, true);
  const value = body.result.value;
  assert.equal(value.plugin, 'dsh-plugin-demo');
  assert.equal(value.companion.pluginId, 'demo.native-ui');
  assert.deepEqual(
    value.registrations.map((entry) => entry.componentId),
    ['dsh-plugin-demo#demo', 'dsh-plugin-demo#demo-dock'],
  );
  assert.deepEqual(value.endpoints, [
    'demo.status',
    'demo.catalog',
    'demo.ping',
    'demo.bump',
    'demo.reset',
    'demo.echo',
  ]);
});

test('payload 原样透传给 echo，不被包成 args', async () => {
  const { routes, ctx } = createHarness();
  apply(ctx);

  const { body } = await call(routes, 'demo.echo', { hello: '原生端', nested: { n: 1 } });
  assert.deepEqual(body.result.value, { received: { hello: '原生端', nested: { n: 1 } } });
});

test('信封错误：方法不匹配返回 400，非 JSON 返回 415', async () => {
  const { routes, ctx } = createHarness();
  apply(ctx);
  const route = routes.get('/api/demo.status');

  const wrongMethod = await route.fetch(new Request('http://localhost/api/demo.status', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ type: 'client-request', rpcId: 'x', method: 'demo.bump', payload: {} }),
  }));
  assert.equal(wrongMethod.status, 400);
  assert.equal((await wrongMethod.json()).result.ok, false);

  const wrongContentType = await route.fetch(new Request('http://localhost/api/demo.status', {
    method: 'POST',
    headers: { 'content-type': 'text/plain' },
    body: '{}',
  }));
  assert.equal(wrongContentType.status, 415);
  assert.equal((await wrongContentType.json()).result.error.code, 'demo/unsupported-media-type');
});

test('原生动作受 componentId 区分，且两个组件各自生效', async () => {
  const { routes, actions, ctx } = createHarness();
  apply(ctx);

  actions.get('dsh-plugin-demo#demo/bump')({ by: 2 });
  let status = await call(routes, 'demo.status');
  assert.equal(status.body.result.value.count, 44);

  actions.get('dsh-plugin-demo#demo-dock/bump')({});
  status = await call(routes, 'demo.status');
  assert.equal(status.body.result.value.count, 45);

  actions.get('dsh-plugin-demo#demo/reset')({});
  status = await call(routes, 'demo.status');
  assert.equal(status.body.result.value.count, 42);

  // 独立状态实例证明 state.js 本身不共享全局变量。
  const isolated = createDemoState();
  isolated.bump(2);
  assert.equal(isolated.snapshot().count, 44);
});

test('两次 apply 的状态互不影响', async () => {
  const first = createHarness();
  const second = createHarness();
  apply(first.ctx);
  apply(second.ctx);

  await call(first.routes, 'demo.bump', { by: 10 });
  const firstStatus = await call(first.routes, 'demo.status');
  const secondStatus = await call(second.routes, 'demo.status');
  assert.equal(firstStatus.body.result.value.count, 52);
  assert.equal(secondStatus.body.result.value.count, 42);
});
