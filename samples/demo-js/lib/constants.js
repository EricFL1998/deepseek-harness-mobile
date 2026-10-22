/**
 * Demo 插件（JS 半边）的稳定身份与协议常量。
 *
 * 这些字符串同时被 Node 半边、浏览器半边和原生伴侣 `samples/demo-native`
 * 引用，是三方约定的唯一来源：
 *
 * - `RPC_PREFIX`：App 通过 `<prefix><endpoint>` 调用 Host RPC；
 * - 两个 `*_COMPONENT_ID`：原生界面向 JS 插件回传事件（`dispatch`）时使用的
 *   组件身份，等于 `<npm 包名>#<slot 内 id>`；
 * - `SECTION_ID`：原生 `PageEntry.id` 必须与它完全一致，App 才会用原生页接管
 *   对应的 `settings.section`。
 */

/** 本插件 RPC 方法的前缀；`demo.status` 的完整路由是 `/api/demo.status`。 */
export const RPC_PREFIX = 'demo.';

/** 本插件提供的全部 RPC 端点（不含前缀），顺序即文档/演示顺序。 */
export const RPC_ENDPOINTS = [
  'status',
  'catalog',
  'ping',
  'bump',
  'reset',
  'echo',
];

/** 设置页 `settings.section` 的注册 id；原生 `PageEntry.id` 必须与它相同。 */
export const SECTION_ID = 'demo';

/** 输入栏 dock 的注册 id。 */
export const DOCK_ID = 'demo-dock';

/** 原生设置页的组件身份：`<包名>#<SECTION_ID>`。 */
export const PAGE_COMPONENT_ID = 'dsh-plugin-demo#demo';

/** 原生 dock 贡献的组件身份：`<包名>#<DOCK_ID>`。 */
export const DOCK_COMPONENT_ID = 'dsh-plugin-demo#demo-dock';

/** 设置命名空间，等于 `cordis.patch.yml` 里本插件那一行的 id。 */
export const SETTINGS_NS = 'demo-plugin';

/** RPC 成功响应的 HTTP 状态；业务失败也用 200，让客户端读信封里的 error。 */
export const OK_STATUS = 200;

/** 请求/响应都在这个点上做类型判别，方便原生端忽略无关帧。 */
export const REQUEST_TYPE = 'client-request';
export const RESPONSE_TYPE = 'server-response';
