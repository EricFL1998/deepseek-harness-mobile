/**
 * Demo 插件（JS 半边）的入口。
 *
 * 这是 SDK 文档里的「接入案例」：一个真实的 npm 插件包 + 一个原生伴生 APK。
 *
 * - 本文件（Host 半边）：提供 `demo.*` RPC、原生动作处理；
 * - `lib/client.js`：注册 `settings.section` 与 `conversation.composer.dock`
 *   两个 Web 落点（同时也是 App 感知这两个组件身份的来源）；
 * - `samples/demo-native`：Kotlin/Compose 伴生 APK，用 RPC 读状态、回传事件。
 *
 * 数据只放在 Host 进程内（见 `state.js`）；原生端永远通过 RPC 取值，不自己
 * 维护业务状态，也不持有任何凭证——与真实插件遵守同一套纪律。
 */

import { registerDemoActions } from './actions.js';
import { createDemoState } from './state.js';
import { registerDemoRpc } from './rpc.js';

/** 插件名；必须与 `package.json` 的 `name` 一致。 */
export const name = 'dsh-plugin-demo';

/**
 * 插件加载时调用。
 *
 * 这里不声明顶层 `inject`：Host 组合里可能没有 web server 或桥插件，Demo
 * 仍应能加载，只是对应能力缺失。两个能力都用 `ctx.inject` 在服务出现时注册。
 *
 * @param {any} ctx 插件 Cordis 上下文。
 */
export function apply(ctx) {
  /** 每个插件实例一份状态；Host 重启后回到初始值。 */
  const state = createDemoState();
  registerDemoRpc(ctx, state);
  registerDemoActions(ctx, state);
}

/** 给测试与文档用：构造一份独立状态，不影响已挂载的插件。 */
export { createDemoState };
