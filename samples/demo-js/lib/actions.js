/**
 * 原生控件动作注册：把「原生界面上点了哪个控件」接回插件自己的 Host 逻辑。
 *
 * 原生 APK 里没有 React 的 `onClick` 闭包，所以 App 不执行插件 JS；它只把
 * 「哪个组件、哪个动作、什么 payload」经 bridge `client-slots/dispatch` 送回
 * Host。桥插件会先查这里注册的动作，命中就执行插件代码，然后给手机排一条
 * `client/refresh`，让原生页面重新走 RPC 拉状态。
 *
 * 完整链路（Demo 设置页的「服务端 +1」按钮）：
 *
 * ```
 * NativeButton.onClick
 *   → host.dispatchEvent("dsh-plugin-demo#demo", "submit", { actionId: "bump" })
 *   → App bridge client-slots/dispatch
 *   → bridge runAction → 本文件注册的 bump handler
 *   → state.bump() 变化
 *   → bridge queue client/refresh
 *   → 原生页 collect refreshEvents → 重新 callRpc("demo.status")
 * ```
 *
 * `mobileUi` 由桥插件提供；Host 没装桥插件时 `ctx.inject` 不会执行，Demo 的
 * 纯 RPC 路径仍然可用。
 */

import { DOCK_COMPONENT_ID, PAGE_COMPONENT_ID } from './constants.js';

/**
 * 注册两个组件的 `bump` / `reset` 动作。
 *
 * @param {any} ctx 插件 Cordis 上下文。
 * @param {object} state Demo 状态。
 */
export function registerDemoActions(ctx, state) {
  ctx.inject(['mobileUi'], (/** @type {any} */ uiCtx) => {
    const ui = uiCtx.get('mobileUi');
    if (ui === undefined || typeof ui.registerAction !== 'function') {
      return;
    }

    /**
     * 处理一次原生动作。
     * @param {'bump'|'reset'} actionId 动作名。
     * @param {unknown} payload bridge 透传的 payload。
     */
    const runAction = (actionId, payload) => {
      if (actionId === 'bump') {
        const by = typeof payload === 'object' && payload !== null
          && typeof (/** @type {any} */ (payload).by) === 'number'
          ? /** @type {any} */ (payload).by
          : 1;
        state.bump(by);
        return;
      }
      if (actionId === 'reset') {
        state.reset();
      }
    };

    // 同一份代码为原生设置页与 dock 各注册一次：componentId 必须是 JS 侧注册的
    // `<包名>#<id>`，否则 bridge 找不到动作，只能退回通用 bus 事件。
    for (const componentId of [PAGE_COMPONENT_ID, DOCK_COMPONENT_ID]) {
      for (const actionId of ['bump', 'reset']) {
        uiCtx.effect(
          () => ui.registerAction(componentId, actionId, (payload) => runAction(actionId, payload)),
          `dsh-plugin-demo: ${componentId}/${actionId}`,
        );
      }
    }
  });
}
