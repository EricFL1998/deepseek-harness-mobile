/**
 * Demo 插件向原生界面上报的「接入清单」。
 *
 * 原生设置页通过 `demo.catalog` 拉取它并渲染成列表——同样是一段真实的 RPC
 * 数据，而不是原生端写死的常量。`componentId` 是原生端回传事件时要用的身份，
 * `nativeSlot` 是 App 侧承载它的落点。
 */

import {
  DOCK_COMPONENT_ID,
  DOCK_ID,
  PAGE_COMPONENT_ID,
  RPC_ENDPOINTS,
  RPC_PREFIX,
  SECTION_ID,
} from './constants.js';

/** 本插件注册的两个 JS UI 落点，以及它们对应的原生落点。 */
export const DEMO_REGISTRATIONS = [
  {
    slot: 'settings.section',
    id: SECTION_ID,
    componentId: PAGE_COMPONENT_ID,
    nativeSlot: 'SETTINGS_SHEET',
    purpose: '整页原生设置页（PageContent 接管）',
  },
  {
    slot: 'conversation.composer.dock',
    id: DOCK_ID,
    componentId: DOCK_COMPONENT_ID,
    nativeSlot: 'COMPOSER_DOCK',
    purpose: '输入栏 dock 胶囊（SlotContribution + PluginPopover）',
  },
];

/** 供 `demo.catalog` 返回的完整清单。 */
export function demoCatalog() {
  return {
    plugin: 'dsh-plugin-demo',
    companion: {
      pluginId: 'demo.native-ui',
      apkPackage: 'com.dsh.mobile.dsh_plugin_demo',
      sample: 'samples/demo-native',
    },
    registrations: DEMO_REGISTRATIONS,
    endpoints: RPC_ENDPOINTS.map((endpoint) => `${RPC_PREFIX}${endpoint}`),
  };
}
