window.__ModuleLoader__.load({ id: "dsh-plugin-subscription-oauth", factory: (require) => {
var module = { exports: {} }; var exports = module.exports;
//#region rolldown:runtime
var __create = Object.create;
var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __getProtoOf = Object.getPrototypeOf;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __copyProps = (to, from, except, desc) => {
	if (from && typeof from === "object" || typeof from === "function") for (var keys = __getOwnPropNames(from), i = 0, n = keys.length, key; i < n; i++) {
		key = keys[i];
		if (!__hasOwnProp.call(to, key) && key !== except) __defProp(to, key, {
			get: ((k) => from[k]).bind(null, key),
			enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable
		});
	}
	return to;
};
var __toESM = (mod, isNodeMode, target) => (target = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(isNodeMode || !mod || !mod.__esModule ? __defProp(target, "default", {
	value: mod,
	enumerable: true
}) : target, mod));

//#endregion
let react = require("react");
react = __toESM(react);
let react_jsx_runtime = require("react/jsx-runtime");
react_jsx_runtime = __toESM(react_jsx_runtime);
let react_dom = require("react-dom");
react_dom = __toESM(react_dom);
let __deepseek_ai_dsh_client_ui_primitives = require("@deepseek-ai/dsh-client-ui-primitives");
__deepseek_ai_dsh_client_ui_primitives = __toESM(__deepseek_ai_dsh_client_ui_primitives);

//#region src/client/locales.ts
/**
* Copy dictionaries for the Subscriptions settings page.
*
* English is the key-set source of truth; the Chinese dictionary is typed
* against it, so a key added to one and forgotten in the other is a compile
* error rather than a silent fallback.
*/
/** English strings. */
const en = {
	nav: "Subscriptions",
	intro: "Sign in with the AI subscriptions you already pay for — no API key. Pick a provider below, connect once, and its models join the model picker with live plan usage. Tokens stay on this machine.",
	loading: "Checking subscriptions…",
	unavailable: "The subscriptions page needs the web connection service, which this composition does not provide.",
	statusConnected: "Connected · {count} account(s)",
	statusSignedOut: "Not signed in",
	statusBusy: "Waiting for browser approval",
	statusSignIn: "Sign in to add this provider's models to the model picker.",
	statusAuto: "Follows {count} enabled model(s) from every provider",
	connect: "Connect",
	edit: "Edit",
	collapse: "Collapse",
	addAccount: "Add account",
	manage: "Manage",
	cancel: "Cancel",
	close: "Close",
	logout: "Sign out",
	makeDefault: "Use by default",
	defaultBadge: "Default",
	badgeLive: "Live",
	accountNeedsLogin: "Sign in again",
	accountNeedsLoginHint: "The provider refused this account's grant, so its token cannot be renewed",
	accountExpired: "token expired",
	accountExpires: "expires {time}",
	deviceTitle: "Approve this device",
	devicePrompt: "Open the verification page and confirm the code below. This page updates by itself once the provider approves.",
	deviceOpen: "Open verification page",
	deviceCopy: "Copy code",
	deviceCopied: "Copied",
	deviceExpires: "The code expires at {time}.",
	openLogin: "Open sign-in page",
	openLoginHint: "Finish signing in in the browser tab that opened. This page updates by itself once the provider redirects back.",
	modelsTitle: "Models",
	modelsCount: "{count} available",
	modelsLive: "live",
	modelsBuiltIn: "built-in",
	modelsShow: "Show in picker",
	modelsHiddenCount: "· {count} hidden",
	modelsHidden: "Hidden from the model picker",
	modelsEmpty: "No models reported yet.",
	modelsAdd: "Add model",
	modelsAddHint: "Publish a model id this build does not know yet",
	modelsAddPlaceholder: "model id, e.g. k3-next",
	modelsAddConfirm: "Add",
	modelsRemove: "Remove this hand-added model",
	modelsContext: "{tokens} context",
	modelsAuto: "Auto",
	modelsAutoHint: "Pinned first: moves to the next model when one is unavailable",
	modelsAutoOff: "Off — the picker offers the models directly instead",
	modelsAutoChain: "Auto tries these, in this order — drag to re-order",
	modelsDrag: "Drag to re-order (or press ↑ / ↓ here)",
	modelsAutoChainOff: "Take out of Auto",
	modelsAutoChainEmpty: "No model is enabled, so Auto has nothing to try.",
	autoSwitchLabel: "Offer Auto in the model picker",
	checkinTitle: "Daily check-in",
	checkinPending: "not checked in today",
	checkinDone: "checked in · {days}-day streak",
	checkinClaim: "Check in",
	checkinClaimed: "Checked in, +{credit} credits (streak {days}d)",
	checkinAlready: "Already checked in today (streak {days}d)",
	tasksClaim: "Tasks",
	tasksClaimed: "Claimed {count} task rewards, +{credit} credits",
	tasksClaimable: "{count} tasks ready to claim but not yet earned",
	tasksNone: "No task rewards to claim right now",
	modelsPinned: "pinned",
	usageTitle: "Plan and usage",
	usagePlan: "Plan: {plan}",
	usageRenews: "renews {time}",
	usageReset: "resets {time}",
	usageResetsInMinutes: "resets in {minutes}m",
	usageResetsInHours: "resets in {hours}h",
	usageResetDue: "resets shortly",
	usageRefresh: "Refresh usage",
	usageFetched: "read {time}",
	usageNone: "This provider reports no plan quota.",
	usageError: "Could not read usage: {message}",
	usage_5h: "5-hour window",
	usage_weekly: "Weekly window",
	usage_credits: "Included credit",
	usageSpent: "used {value}",
	usage_gemini: "Gemini allowance (rolling)",
	usage_gemini_weekly: "Gemini allowance (weekly)",
	usage_claude: "Claude allowance (rolling)",
	usage_claude_weekly: "Claude allowance (weekly)",
	usage_other: "Allowance",
	refresh: "Refresh",
	refreshing: "Refreshing…",
	modelsRefreshed: "Model list refreshed.",
	refreshToken: "Refresh token",
	tokenRefreshed: "Token refreshed; valid until {time}.",
	hint: "Authorization follows the account the browser is signed in with. To add a different account, switch accounts there first, or use a private window.",
	manageTitle: "Manage {provider}",
	manageAccounts: "Accounts",
	reload: "Reload credentials",
	reloadHint: "Use this after signing in from another DeepSeek Harness window.",
	detail_oauthHost: "Authorization server",
	detail_apiBase: "API base",
	detail_credentialFile: "Credential file",
	detail_project: "Cloud project",
	lastError: "Last login attempt failed: {message}",
	loginFailed: "Could not start the sign-in: {message}",
	actionFailed: "Action failed: {message}",
	usageChipTitle: "{provider} subscription usage",
	usageChipFull: "{provider} allowance",
	fastModeOn: "Fast mode is on; press to go back to the standard model",
	fastModeOff: "Fast mode: switch to this model’s fast variant",
	fastModeRefused: "The switch was refused",
	fastModeUnavailable: "the model directory is unavailable on this host"
};
/** Chinese strings. */
const zh = {
	nav: "订阅",
	intro: "使用你已付费的 AI 订阅 —— 无需 API Key。在下方选择服务，连接一次，其模型即进入模型选择器并显示实时额度。令牌仅保存在本机。",
	loading: "正在检查订阅…",
	unavailable: "订阅页面需要 Web 连接服务，当前组合未提供该服务。",
	statusConnected: "已连接 {count} 个账号",
	statusSignedOut: "未登录",
	statusBusy: "等待浏览器确认",
	statusSignIn: "登录后即可在模型选择器中使用该服务的模型。",
	statusAuto: "依次使用来自各服务的 {count} 个已启用模型",
	connect: "连接",
	edit: "编辑",
	collapse: "收起",
	addAccount: "添加账号",
	manage: "管理",
	cancel: "取消",
	close: "关闭",
	logout: "退出登录",
	makeDefault: "设为默认",
	defaultBadge: "默认",
	badgeLive: "已连接",
	accountNeedsLogin: "需重新登录",
	accountNeedsLoginHint: "服务方已拒绝该账号的授权，令牌无法自动续期",
	accountExpired: "令牌已过期",
	accountExpires: "过期时间 {time}",
	deviceTitle: "确认此设备",
	devicePrompt: "打开验证页面并确认下方代码，服务确认后本页面会自动更新。",
	deviceOpen: "打开验证页面",
	deviceCopy: "复制代码",
	deviceCopied: "已复制",
	deviceExpires: "代码将于 {time} 过期。",
	openLogin: "打开登录页面",
	openLoginHint: "请在打开的浏览器标签页中完成登录；服务回调后本页面会自动更新。",
	modelsTitle: "模型",
	modelsCount: "共 {count} 个",
	modelsLive: "实时",
	modelsBuiltIn: "内置",
	modelsShow: "在选择器中显示",
	modelsHiddenCount: "· 已隐藏 {count} 个",
	modelsHidden: "已从模型选择器隐藏",
	modelsEmpty: "暂未获取到模型。",
	modelsAdd: "添加模型",
	modelsAddHint: "手动添加一个本版本还不认识的模型 ID",
	modelsAddPlaceholder: "模型 ID，例如 k3-next",
	modelsAddConfirm: "添加",
	modelsRemove: "移除这个手动添加的模型",
	modelsContext: "{tokens} 上下文",
	modelsAuto: "自动",
	modelsAutoHint: "固定在首位：某个模型不可用时自动改用下一个",
	modelsAutoOff: "已关闭 —— 选择器将直接列出各个模型",
	modelsAutoChain: "自动会按这个顺序依次尝试（拖动可调整）",
	modelsDrag: "拖动调整顺序（也可在此按 ↑ / ↓）",
	modelsAutoChainOff: "从自动中移除",
	modelsAutoChainEmpty: "没有启用任何模型，自动没有可尝试的模型。",
	autoSwitchLabel: "在模型选择器中提供 Auto",
	checkinTitle: "每日签到",
	checkinPending: "今日未签到",
	checkinDone: "已签到 · 连签 {days} 天",
	checkinClaim: "签到",
	checkinClaimed: "签到成功，+{credit} 积分（连签 {days} 天）",
	checkinAlready: "今日已签到（连签 {days} 天）",
	tasksClaim: "做任务",
	tasksClaimed: "已领取 {count} 个任务奖励，+{credit} 积分",
	tasksClaimable: "有 {count} 个任务可领但还未达成",
	tasksNone: "现在没有可领的任务奖励",
	modelsPinned: "固定",
	usageTitle: "套餐与额度",
	usagePlan: "套餐：{plan}",
	usageRenews: "续费时间 {time}",
	usageReset: "重置时间 {time}",
	usageResetsInMinutes: "{minutes} 分钟后重置",
	usageResetsInHours: "{hours} 小时后重置",
	usageResetDue: "即将重置",
	usageRefresh: "刷新额度",
	usageFetched: "读取于 {time}",
	usageNone: "该服务未提供套餐额度信息。",
	usageError: "额度读取失败：{message}",
	usage_5h: "5 小时窗口",
	usage_weekly: "每周窗口",
	usage_credits: "包含额度",
	usageSpent: "已用 {value}",
	usage_gemini: "Gemini 模型额度（滚动）",
	usage_gemini_weekly: "Gemini 模型额度（每周）",
	usage_claude: "Claude 模型额度（滚动）",
	usage_claude_weekly: "Claude 模型额度（每周）",
	usage_other: "额度",
	refresh: "刷新",
	refreshing: "正在刷新…",
	modelsRefreshed: "模型列表已刷新。",
	refreshToken: "刷新令牌",
	tokenRefreshed: "令牌已刷新，有效至 {time}。",
	hint: "授权以浏览器当前登录的账号为准；要添加不同账号，请先在浏览器里切换账号，或使用无痕窗口。",
	manageTitle: "管理 {provider}",
	manageAccounts: "账号",
	reload: "重新读取凭据",
	reloadHint: "在另一个 DeepSeek Harness 窗口中登录后，请使用此操作。",
	detail_oauthHost: "授权服务器",
	detail_apiBase: "API 地址",
	detail_credentialFile: "凭据文件",
	detail_project: "云项目",
	lastError: "上次登录失败：{message}",
	loginFailed: "无法开始登录：{message}",
	actionFailed: "操作失败：{message}",
	usageChipTitle: "{provider} 订阅用量",
	usageChipFull: "{provider} 额度",
	fastModeOn: "已开启快速模式；按下回到标准模型",
	fastModeOff: "快速模式：切换到此模型的快速变体",
	fastModeRefused: "切换被拒绝",
	fastModeUnavailable: "此宿主上的模型目录不可用"
};

//#endregion
//#region src/client/styles.ts
/**
* Scoped stylesheet for the Subscriptions page and its manage dialog.
*
* The host loads plugin markup without its own control classes, and a bare
* `<button>` therefore renders with the browser's default raised chrome — which
* reads as a stray highlight in a dark theme. Rather than invent a look, this
* follows the control vocabulary of the sibling `dsh-plugin-subscriptions`
* plugin (same author-facing design as the shell's settings forms): a 6 px
* radius small control, a transparent fill, a hairline border from the theme's
* alias tokens, and the theme's hover/active/focus tokens for state.
*
* The tokens all carry fallbacks, so the page still renders correctly on a host
* build that does not define them.
*
* @module dsh-plugin-subscription-oauth/client/styles
*/
/**
* Everything is scoped to this plugin's own roots, so no other surface in the
* shell can be affected by a rule here.
*/
const scope = ":is(.dsh-subscriptions-settings, .dsh-subscription-manager)";
/** The page's and dialog's stylesheet. */
const subscriptionsCss = `
.dsh-subscriptions-settings,
.dsh-subscription-manager {
  font-family: inherit;
  font-size: 13px;
  line-height: 1.5;
  /* Native controls (checkbox, dialog) follow the host theme, not the OS. Dark
     is signalled by the alias tokens themselves: body[data-ds-dark-theme]
     only remaps the --dsw-alias-* values at the root (design-platform.css), so
     a scope reading the tokens is already themed and needs no selector of its
     own. color-scheme names the palette for the *platform* control chrome —
     set per scope rather than inherited, the way the shell does for its own
     forced-light surfaces. */
  color-scheme: light;
}
.dsh-subscription-manager {
  box-sizing: border-box;
  padding: 0;
  border: 0;
  border-radius: var(--dsw-radius-panel, 16px);
  background: var(--dsw-alias-bg-layer-2, var(--dsw-alias-bg-layer-1));
  color: var(--dsw-alias-label-primary);
  box-shadow: var(--dsw-elevation-prominent, 0 20px 70px #0004);
}
.dsh-subscription-manager::backdrop {
  background: var(--dsw-alias-bg-mask-1, #0006);
  backdrop-filter: var(--dsw-mask-blur, none);
}
${scope} h2 {
  margin: 0;
  font-size: 16px;
  line-height: 24px;
  font-weight: 500;
}
${scope} strong,
${scope} .dsh-subscription-label {
  font-size: 13px;
  line-height: 1.5;
  font-weight: 500;
}
${scope} small,
${scope} .dsh-subscription-hint,
${scope} [role="status"] {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--dsw-alias-label-tertiary);
}
${scope} .dsh-subscription-error {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--dsw-alias-state-error-primary, #b42318);
}
${scope} .dsh-subscription-success {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--dsw-alias-state-success-primary, #067647);
}
${scope} label:has(> input[type="checkbox"]) {
  cursor: pointer;
}
${scope} input[type="checkbox"] {
  accent-color: var(--dsw-alias-brand-primary);
}
/*
 * A model's on/off control is a switch, not a checkbox: it takes effect
 * immediately and its state is what matters, not a tick. Drawn from the theme
 * tokens with appearance: none, so it follows the host palette in both themes
 * instead of the platform's checkbox chrome.
 */
${scope} input[type="checkbox"].dsh-subscription-switch {
  appearance: none;
  -webkit-appearance: none;
  position: relative;
  box-sizing: border-box;
  flex-shrink: 0;
  width: 34px;
  height: 20px;
  margin: 0;
  border: 0.5px solid var(--dsw-alias-border-l3, var(--dsw-alias-border-l2));
  border-radius: 999px;
  background: var(--dsw-alias-bg-layer-3, var(--dsw-alias-bg-layer-2));
  cursor: pointer;
  transition: background 120ms ease, border-color 120ms ease;
}
${scope} input[type="checkbox"].dsh-subscription-switch::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--dsw-alias-label-primary-foreground, #fff);
  box-shadow: 0 1px 2px #0003;
  transition: transform 120ms ease;
}
${scope} input[type="checkbox"].dsh-subscription-switch:checked {
  border-color: transparent;
  background: var(--dsw-alias-state-business-primary, var(--dsw-alias-brand-primary, #067647));
}
${scope} input[type="checkbox"].dsh-subscription-switch:checked::after {
  transform: translateX(14px);
}
${scope} input[type="checkbox"].dsh-subscription-switch:disabled {
  opacity: 0.4;
  cursor: default;
}
${scope} button {
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  min-height: 28px;
  padding: 0 10px;
  border: 0.5px solid var(--dsw-alias-border-l3, var(--dsw-alias-border-l2));
  border-radius: var(--dsw-radius-sm, 6px);
  background: transparent;
  color: var(--dsw-alias-label-primary);
  font: inherit;
  font-size: 12px;
  line-height: 18px;
  cursor: pointer;
}
${scope} button:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-hover, var(--dsw-alias-bg-layer-3));
}
${scope} button:active:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-active, var(--dsw-alias-bg-layer-3));
}
${scope} button:disabled {
  opacity: 0.4;
  cursor: default;
}
/* A control that is present but should not read as a bordered button. */
${scope} button.dsh-subscription-quiet {
  min-height: 22px;
  padding: 0 4px;
  border-color: transparent;
  color: var(--dsw-alias-label-secondary, var(--dsw-alias-label-primary));
}
/* The free-text field for a hand-added model id. */
${scope} input.dsh-subscription-input {
  box-sizing: border-box;
  flex: 1 1 auto;
  min-width: 0;
  min-height: 28px;
  padding: 0 10px;
  border: 0.5px solid var(--dsw-alias-border-l3, var(--dsw-alias-border-l2));
  border-radius: var(--dsw-radius-sm, 6px);
  background: var(--dsw-alias-bg-layer-1);
  color: var(--dsw-alias-label-primary);
  font: inherit;
  font-size: 12px;
  line-height: 18px;
}
${scope} input.dsh-subscription-input::placeholder {
  color: var(--dsw-alias-label-tertiary);
}
${scope} input.dsh-subscription-input:focus-visible {
  outline: var(--dsw-focus-ring-width, 2px) solid var(--dsw-focus-ring-color, var(--dsw-alias-state-business-primary));
  outline-offset: 2px;
}
/* A drag handle: no border or fill, so it reads as a grip rather than a button. */
${scope} button.dsh-subscription-handle {
  min-height: 18px;
  height: 18px;
  padding: 0 2px;
  border-color: transparent;
  border-radius: var(--dsw-radius-sm, 6px);
  background: transparent;
  color: var(--dsw-alias-label-tertiary, var(--dsw-alias-label-secondary));
  font-size: 12px;
  line-height: 18px;
  cursor: grab;
}
${scope} button.dsh-subscription-handle:hover:not(:disabled) {
  background: transparent;
  color: var(--dsw-alias-label-primary);
}
${scope} button.dsh-subscription-handle:active:not(:disabled) { cursor: grabbing; }
/* Controls inside the manage dialog are one step larger, like a form footer. */
${scope} .dsh-subscription-dialog-actions button {
  min-height: 34px;
  padding: 0 14px;
  border-radius: var(--dsw-radius-md, 8px);
  font-size: 13px;
  line-height: 1.5;
}
${scope} button.dsh-subscription-primary {
  border-color: transparent;
  background: var(--dsw-alias-button-primary-fill, var(--dsw-alias-label-primary));
  color: var(--dsw-alias-label-primary-foreground, var(--dsw-alias-bg-layer-1));
  font-weight: 500;
}
${scope} button.dsh-subscription-primary:hover:not(:disabled) {
  background: var(--dsw-alias-button-primary-hover, var(--dsw-alias-label-secondary));
}
${scope} button:focus-visible,
${scope} input[type="checkbox"]:focus-visible,
${scope} .dsh-subscription-disclosure:focus-visible {
  outline: var(--dsw-focus-ring-width, 2px) solid var(--dsw-focus-ring-color, var(--dsw-alias-state-business-primary));
  outline-offset: 2px;
}
/* The catalog heading is a summary-style disclosure, not a bordered button. */
${scope} .dsh-subscription-disclosure {
  border-color: transparent;
  border-radius: var(--dsw-radius-sm, 6px);
  background: transparent;
  padding: 0 4px;
  font-size: 12px;
  line-height: 18px;
  font-weight: 500;
  color: var(--dsw-alias-label-secondary, var(--dsw-alias-label-primary));
}
${scope} .dsh-subscription-disclosure:hover:not(:disabled) {
  background: transparent;
  color: var(--dsw-alias-label-primary);
}
/* A usage meter: the track, with the fill sized by the caller. */
${scope} .dsh-subscription-meter {
  height: 6px;
  border-radius: 999px;
  /* The track must stay visible when nothing is spent — a 0 % window otherwise reads as
     "no meter at all", which is the opposite of "nothing used". The layer token falls
     back to the card's own colour on a host that does not define it, so the neutral is
     explicit and translucent, and the inset ring keeps the empty track visible on a
     light theme as well as a dark one. */
  background: #8a8f9826;
  box-shadow: inset 0 0 0 1px #8a8f9833;
  overflow: hidden;
}
${scope} .dsh-subscription-meter > span {
  display: block;
  height: 100%;
  background: var(--dsw-alias-state-success-primary, #067647);
}
${scope} .dsh-subscription-meter > span[data-exhausted="true"] {
  background: var(--dsw-alias-state-error-primary, #b42318);
}
`;
/**
* The composer usage chip's stylesheet.
*
* Separate from the page's, because the chip renders in the composer's own bar rather
* than inside either page container — the page stylesheet is scoped to those
* containers on purpose, so nothing here may leak into the shell.
*
* The chip inherits the bar's type and only draws a meter, so it sits beside the shell's
* own stats pills without looking like a second design. The trigger is a bare control:
* the host gives plugin markup no control classes, and a default `<button>` would
* otherwise read as a raised chip. Every token carries a fallback for hosts that do not
* define it.
*/
const usageChipCss = `
.dsh-subscription-usage-chip {
  position: relative;
  display: inline-flex;
  align-items: center;
  /* The bar is a centred flex row whose children are this slot's entries followed by the
     shell's context meter, and a list slot renders its entries as bare children (no
     wrapper). So the chip is itself a flex item: a higher order places it after the
     context meter, and no auto margin is set, so it sits immediately beside it rather
     than being pushed to the edge of the bar. */
  order: 1;
  min-width: 0;
  font-family: inherit;
  font-size: 12px;
  line-height: 1;
}
.dsh-subscription-usage-chip-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  padding: 2px 6px;
  border: 0;
  border-radius: var(--dsw-radius-sm, 6px);
  background: transparent;
  color: var(--dsw-alias-label-secondary, #8a8f98);
  font: inherit;
  cursor: pointer;
}
.dsh-subscription-usage-chip-trigger:hover {
  background: var(--dsw-alias-interactive-bg-hover, #ffffff14);
}
.dsh-subscription-usage-chip-trigger:focus-visible {
  outline: var(--dsw-focus-ring-width, 2px) solid var(--dsw-focus-ring-color, var(--dsw-alias-state-business-primary));
  outline-offset: 1px;
}
.dsh-subscription-usage-chip-label {
  overflow: hidden;
  max-width: 6ch;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dsh-subscription-usage-chip-cell {
  display: inline-flex;
  align-items: baseline;
  gap: 2px;
}
.dsh-subscription-usage-chip-window {
  opacity: 0.75;
}
.dsh-subscription-usage-chip-value {
  color: var(--dsw-alias-label-primary, #e8e8e8);
  font-variant-numeric: tabular-nums;
}
.dsh-subscription-usage-chip-account {
  display: flex;
  flex-direction: column;
  gap: 6px;
  /* A hairline between accounts, so two sign-ins read as two groups not one run. */
  padding-top: 6px;
  border-top: 1px solid var(--dsw-alias-border-l2, #ffffff14);
}
.dsh-subscription-usage-chip-account:first-of-type {
  padding-top: 0;
  border-top: 0;
}
.dsh-subscription-usage-chip-panel {
  /* The chip portals this to the body and positions it from the trigger's
     viewport rect, so the coordinates useAnchoredPosition returns are
     viewport-relative: the panel must be fixed. It sits above the shell's
     modal layer, which is z-index 1000. */
  position: fixed;
  z-index: 1100;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 180px;
  max-width: min(360px, calc(100vw - 24px));
  padding: 12px 14px;
  border: 1px solid var(--dsw-alias-border-l2, #ffffff1f);
  border-radius: var(--dsw-radius-md, 10px);
  background: var(--dsw-alias-bg-layer-2, #26262a);
  box-shadow: var(--dsw-elevation-prominent, 0 12px 32px #0007);
  color: var(--dsw-alias-label-primary, #e8e8e8);
  white-space: nowrap;
}
.dsh-subscription-usage-chip-full {
  font-size: 11px;
  color: var(--dsw-alias-label-secondary, #8a8f98);
}
.dsh-subscription-usage-chip-meter {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.dsh-subscription-usage-chip-meterHead {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
}
.dsh-subscription-usage-chip-detail {
  color: var(--dsw-alias-label-secondary, #8a8f98);
  font-variant-numeric: tabular-nums;
}
.dsh-subscription-usage-chip-track {
  display: block;
  width: 100%;
  height: 5px;
  border-radius: 999px;
  /* As on the settings meter: an unspent window has to show its track, so the neutral and
     its ring are explicit rather than a layer token that may match the panel. */
  background: #8a8f9826;
  box-shadow: inset 0 0 0 1px #8a8f9833;
  overflow: hidden;
}
.dsh-subscription-usage-chip-fill {
  display: block;
  height: 100%;
  background: var(--dsw-alias-state-success-primary, #067647);
}
`;
/**
* The Models-page provider-card block's stylesheet.
*
* Scoped to its own root so nothing reaches the section's own cards. It borrows
* the section page's own dot and meter geometry so a subscription card reads as
* one row of the same family rather than a foreign design. Tokens carry
* fallbacks for hosts that define none.
*/
const providerCardCss = `
.dsh-subscription-models-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  font-family: inherit;
  font-size: 12px;
  line-height: 1.5;
  color: var(--dsw-alias-label-primary);
}
.dsh-subscription-models-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.dsh-subscription-models-card-dot {
  width: 8px;
  height: 8px;
  flex-shrink: 0;
  border: 1.5px solid var(--dsw-alias-label-dimmed);
  border-radius: 50%;
  background: transparent;
}
.dsh-subscription-models-card-dot-live {
  border-color: var(--dsw-alias-state-success-primary, #067647);
  background: var(--dsw-alias-state-success-primary, #067647);
}
.dsh-subscription-models-card-dot-busy {
  border-color: var(--dsw-alias-state-warn-primary, #f5a524);
  background: var(--dsw-alias-state-warn-primary, #f5a524);
}
.dsh-subscription-models-card-name {
  font-weight: 600;
  font-size: 13px;
  line-height: 20px;
  color: var(--dsw-alias-label-primary);
}
.dsh-subscription-models-card-state {
  font-size: 12px;
  line-height: 18px;
  color: var(--dsw-alias-label-tertiary);
}
.dsh-subscription-models-card-usage {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.dsh-subscription-models-card-meter {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.dsh-subscription-models-card-meterHead {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
}
.dsh-subscription-models-card-detail {
  color: var(--dsw-alias-label-secondary);
  font-variant-numeric: tabular-nums;
}
.dsh-subscription-models-card-track {
  display: block;
  width: 100%;
  height: 5px;
  border-radius: 999px;
  background: #8a8f9826;
  box-shadow: inset 0 0 0 1px #8a8f9833;
  overflow: hidden;
}
.dsh-subscription-models-card-fill {
  display: block;
  height: 100%;
  background: var(--dsw-alias-state-success-primary, #067647);
}
.dsh-subscription-models-card-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.dsh-subscription-models-card-actions button {
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 26px;
  padding: 0 10px;
  border: 0.5px solid var(--dsw-alias-border-l3, var(--dsw-alias-border-l2));
  border-radius: var(--dsw-radius-sm, 6px);
  background: transparent;
  color: var(--dsw-alias-label-primary);
  font: inherit;
  font-size: 12px;
  line-height: 18px;
  cursor: pointer;
}
.dsh-subscription-models-card-actions button:hover:not(:disabled) {
  background: var(--dsw-alias-interactive-bg-hover, var(--dsw-alias-bg-layer-3));
}
.dsh-subscription-models-card-actions button:disabled {
  opacity: 0.4;
  cursor: default;
}
`;
/**
* The composer's quick-mode button stylesheet.
*
* It renders in the composer's own control row — the same row as the model selector —
* so it borrows that row's geometry: a compact, borderless control that only colours on
* hover and when the mode is on. Tokens carry fallbacks for hosts that define none.
*/
const fastModeCss = `
.dsh-subscription-fast-mode {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin: 0;
  padding: 3px;
  border: 0;
  border-radius: var(--dsw-radius-sm, 6px);
  background: transparent;
  color: var(--dsw-alias-label-secondary, #8a8f98);
  font: inherit;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
}
.dsh-subscription-fast-mode:hover {
  background: var(--dsw-alias-interactive-bg-hover, #ffffff14);
}
/* A click has to look like one, even before the selection lands. */
.dsh-subscription-fast-mode:active {
  background: var(--dsw-alias-interactive-bg-active, #ffffff2e);
}
.dsh-subscription-fast-mode[data-pending="true"] {
  opacity: 0.7;
  cursor: progress;
}
/* A switch the host refused is marked rather than left looking merely off, so a failure
   is visible where it happened; the reason rides the title and the console. */
.dsh-subscription-fast-mode[data-failed="true"] {
  color: var(--dsw-alias-state-error-primary, #b42318);
}
.dsh-subscription-fast-mode:focus-visible {
  outline: var(--dsw-focus-ring-width, 2px) solid var(--dsw-focus-ring-color, var(--dsw-alias-state-business-primary));
  outline-offset: 1px;
}
/* Off: the bolt is dim, so an available mode reads as an invitation, not a state. */
.dsh-subscription-fast-mode[data-active="false"] .dsh-subscription-fast-mode-bolt {
  opacity: 0.55;
}
/* On: the bolt takes the accent and glows, which is the only thing distinguishing the
   fast variant from its base — both answer with the same model name. */
.dsh-subscription-fast-mode[data-active="true"] .dsh-subscription-fast-mode-bolt {
  color: var(--dsw-alias-state-warn-primary, #f5a524);
  filter: drop-shadow(0 0 6px var(--dsw-alias-state-warn-primary, #f5a52455));
}
.dsh-subscription-fast-mode-reason {
  max-width: 14ch;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
}
`;

//#endregion
//#region src/protocol.ts
/**
* Wire contract shared by the plugin's node half (the RPC routes) and its
* browser half (the Settings page).
*
* Only this module crosses the boundary: the client bundle imports the types
* and the channel name, and every call travels as one JSON envelope over a
* `POST /api/<prefix><endpoint>` route. One status document describes every
* provider route the plugin serves, so the page renders one card per entry.
*
* @module dsh-plugin-subscription-oauth/protocol
*/
/** Prefix of every RPC method this plugin serves. */
const RPC_PREFIX = "subscription-oauth.";
/**
* The settings namespace the Models page keys this plugin's provider cards by.
*
* The settings service projects a plugin's `Config` schema under the Cordis row
* id that mounted it (`settings/src/index.ts`: `ns = entry.options.id`), and the
* Models section dispatches `settings.models.provider-card` with that same
* namespace as `entryKey`. This constant therefore names the row id declared in
* `cordis.patch.yml`; the two must stay in step, or the Models page renders the
* routes with no subscription block (the standalone Subscriptions page is
* unaffected).
*/
const SETTINGS_NS = "llm-subscription-oauth";

//#endregion
//#region src/client/subscriptions-rpc.ts
/** The shared channel the node half's routes are mounted on. */
const CHANNEL = "/api";
/** A failed call: either the transport rejected or the endpoint answered `ok: false`. */
var SubscriptionsRpcError = class extends Error {
	constructor(message, code) {
		super(message);
		this.code = code;
		this.name = "SubscriptionsRpcError";
	}
};
/** Human-readable message for any thrown value. */
function messageOf$1(error) {
	return error instanceof Error ? error.message : String(error);
}
/**
* Bind a caller to one connection's RPC transport.
* @param rpc - the browser connection's RPC caller.
* @returns a typed caller for this plugin's endpoints.
*/
function createSubscriptionsCall(rpc) {
	return async function call(endpoint, payload) {
		let result;
		try {
			result = await rpc.call(CHANNEL, `${RPC_PREFIX}${endpoint}`, payload);
		} catch (error) {
			throw new SubscriptionsRpcError(messageOf$1(error), "RPC_TRANSPORT");
		}
		if (!result.ok) throw new SubscriptionsRpcError(result.error.message, result.error.code);
		return result.value;
	};
}
/**
* Read the DeepSeek account's balance through the plugin's own RPC.
*
* The browser cannot call the host's account service directly — it sits behind the
* host's Remote controller, not a `/api` fetch route — so the node half reads it and
* serves it on the plugin's channel, the same one the rest of the chip's data travels.
*
* @param rpc - the browser connection's RPC caller.
* @returns the balance, or undefined when there is nothing to show.
*/
function createAccountBalanceCall(rpc) {
	return async function call() {
		let result;
		try {
			result = await rpc.call(CHANNEL, `${RPC_PREFIX}deepseek-balance`, {
				version: "1",
				locale: Intl.DateTimeFormat().resolvedOptions().locale ?? "en-US",
				timezoneOffsetSeconds: -(/* @__PURE__ */ new Date()).getTimezoneOffset() * 60
			});
		} catch (error) {
			throw new SubscriptionsRpcError(messageOf$1(error), "RPC_TRANSPORT");
		}
		if (!result.ok) throw new SubscriptionsRpcError(result.error.message, result.error.code);
		return result.value;
	};
}
/**
* Read a manually added provider's balance through the plugin's cc-switch endpoint.
*
* The caller names the credential reference the provider's profile uses (its
* Read a non-subscription provider's balance through the plugin's cc-switch endpoint.
*
* The caller names the provider; the server maps it to the credential reference the
* provider's profile names (`apiKeyEnv`) and resolves that to the API key. The result
* is `undefined` when there is no key or the provider did not answer — never a wrong
* figure.
*
* @param rpc - the browser connection's RPC caller.
* @returns a narrow caller taking the provider id.
*/
function createCcSwitchBalanceCall(rpc) {
	return async function call(provider) {
		let result;
		try {
			result = await rpc.call(CHANNEL, `${RPC_PREFIX}ccswitch-balance`, { provider });
		} catch (error) {
			throw new SubscriptionsRpcError(messageOf$1(error), "RPC_TRANSPORT");
		}
		if (!result.ok) throw new SubscriptionsRpcError(result.error.message, result.error.code);
		return result.value;
	};
}

//#endregion
//#region src/client/SubscriptionsSection.tsx
/** Poll cadence while a login attempt is pending. */
const POLL_INTERVAL_MS = 2e3;
/**
* How often an open page re-reads every signed-in route's allowance.
*
* Slow on purpose: it is fast enough that a figure shown on screen is current, and
* slow enough that leaving the page open costs one small request a minute per
* signed-in provider.
*/
const USAGE_POLL_INTERVAL_MS = 6e4;
/**
* Disclosures start folded, every time the page is opened.
*
* Nothing is remembered between visits on purpose: the section is a list of
* providers first, and a card left open last time should not decide what this
* visit looks like. A fold is a gesture within one visit, not a stored setting.
*/
/** Horizontal room the page takes inside the settings panel. */
const SECTION_WIDTH = 560;
/** Minimal translator used when the locale seat is absent (standalone render). */
function fallbackTranslate(key, params) {
	const template = en[key];
	if (params === void 0) return template;
	return template.replace(/\{(\w+)\}/g, (match, name) => name in params ? String(params[name]) : match);
}
/** Human-readable message for any thrown value. */
function messageOf(error) {
	if (error instanceof SubscriptionsRpcError) return error.message;
	return error instanceof Error ? error.message : String(error);
}
/** Local time for one epoch-millisecond instant. */
function formatTime(at) {
	try {
		return new Date(at).toLocaleString();
	} catch {
		return String(at);
	}
}
/** How far ahead a reset is still stated as hours rather than a date. */
const RESET_RELATIVE_MS = 1440 * 60 * 1e3;
/**
* One allowance reset, in the form a reader acts on.
*
* How far off it is decides how it is said: minutes inside the hour, hours inside
* the day, and the date beyond that — "137 小时后" would be arithmetic rather than
* information, and a window that comes back in forty minutes is better described in
* minutes than as "1 小时后". Every route's windows go through here, so all of them
* read the same way.
*
* @param at - epoch milliseconds the window resets.
* @param t - translator, so the wording follows the page language.
* @returns the reset text.
*/
function formatReset$1(at, t) {
	const remaining = at - Date.now();
	if (remaining <= 0) return t("usageResetDue");
	if (remaining >= RESET_RELATIVE_MS) return t("usageReset", { time: formatTime(at) });
	if (remaining < 3600 * 1e3) return t("usageResetsInMinutes", { minutes: Math.max(1, Math.round(remaining / 6e4)) });
	return t("usageResetsInHours", { hours: Math.max(1, Math.round(remaining / 36e5)) });
}
/** Compact token count for a context window. */
function formatTokens(tokens) {
	if (tokens >= 1e6) return `${String(Math.round(tokens / 1e5) / 10)}M`;
	if (tokens >= 1e3) return `${String(Math.round(tokens / 1e3))}K`;
	return String(tokens);
}
/**
* Routes worth asking for allowances.
*
* A route with no account of its own — `auto` — reports itself as signed in so its
* card reads as usable, but it holds no allowance to read: asking it every minute
* spends a request to be told nothing. The routes it stands in for are each read
* on their own.
*
* @param providers - the routes in the current status.
* @returns the route keys to read usage for.
*/
function usageTargets(providers) {
	return providers.filter((provider) => provider.loggedIn && provider.connectable !== false).map((provider) => provider.provider);
}
const styles = {
	section: {
		display: "flex",
		flexDirection: "column",
		gap: 12,
		maxWidth: SECTION_WIDTH,
		color: "var(--dsw-alias-label-primary)"
	},
	sectionHeader: {
		display: "flex",
		flexDirection: "column",
		gap: 4,
		marginBottom: 2
	},
	heading: {
		margin: 0,
		fontSize: 18,
		lineHeight: "26px",
		fontWeight: 600,
		color: "var(--dsw-alias-label-primary)"
	},
	intro: {
		margin: 0,
		color: "var(--dsw-alias-label-tertiary)",
		fontSize: 13,
		lineHeight: "20px",
		maxWidth: 560
	},
	card: {
		border: "0.5px solid var(--dsw-alias-settings-card-stroke, var(--dsw-alias-border-l4))",
		borderRadius: "var(--dsw-radius-xl, 16px)",
		background: "var(--dsw-alias-settings-card-fill, var(--dsw-alias-bg-layer-2))",
		padding: "14px 16px",
		display: "flex",
		flexDirection: "column",
		gap: 8,
		transition: "border-color 120ms ease, background 120ms ease"
	},
	cardOpen: {
		borderColor: "var(--dsw-alias-border-l2, var(--dsw-alias-border-l3))",
		background: "var(--dsw-alias-settings-card-fill-hover, var(--dsw-alias-settings-card-fill, var(--dsw-alias-bg-layer-2)))"
	},
	cardHeader: {
		display: "flex",
		alignItems: "center",
		gap: 10,
		cursor: "pointer",
		userSelect: "none"
	},
	cardBody: {
		display: "flex",
		flexDirection: "column",
		gap: 10,
		marginTop: 2
	},
	cardSummary: {
		fontSize: 12,
		lineHeight: "18px",
		color: "var(--dsw-alias-label-tertiary)"
	},
	dot: {
		width: 8,
		height: 8,
		borderRadius: "50%",
		flexShrink: 0,
		border: "1.5px solid var(--dsw-alias-label-dimmed)",
		background: "transparent"
	},
	dotLive: {
		borderColor: "var(--dsw-alias-state-success-primary, #067647)",
		background: "var(--dsw-alias-state-success-primary, #067647)"
	},
	dotBusy: {
		borderColor: "var(--dsw-alias-state-warn-primary, #f5a524)",
		background: "var(--dsw-alias-state-warn-primary, #f5a524)"
	},
	name: {
		fontWeight: 600,
		fontSize: 14,
		lineHeight: "22px",
		color: "var(--dsw-alias-label-primary)"
	},
	statusLine: {
		margin: 0,
		fontSize: 12,
		lineHeight: "18px",
		color: "var(--dsw-alias-label-tertiary)"
	},
	errorLine: {
		margin: 0,
		fontSize: 12,
		lineHeight: "18px",
		color: "var(--dsw-alias-state-error-primary)"
	},
	successLine: {
		margin: 0,
		fontSize: 12,
		lineHeight: "18px",
		color: "var(--dsw-alias-state-success-primary)"
	},
	actions: {
		display: "flex",
		gap: 8,
		marginTop: 4,
		alignItems: "center",
		flexWrap: "wrap"
	},
	accountRow: {
		display: "flex",
		flexDirection: "column",
		gap: 6,
		border: "0.5px solid var(--dsw-alias-border-l2)",
		borderRadius: "var(--dsw-radius-lg, 12px)",
		padding: "10px 12px",
		background: "var(--dsw-alias-bg-layer-1)"
	},
	accountHeader: {
		display: "flex",
		alignItems: "center",
		gap: 8,
		flexWrap: "wrap"
	},
	starButton: {
		border: "none",
		padding: 0,
		width: 20,
		background: "transparent",
		font: "inherit",
		fontSize: 14,
		lineHeight: "20px",
		cursor: "pointer",
		color: "var(--dsw-alias-state-warn-label)"
	},
	accountName: {
		fontSize: 13,
		lineHeight: "20px",
		color: "var(--dsw-alias-label-primary)",
		userSelect: "all"
	},
	badge: {
		fontSize: 10,
		lineHeight: "14px",
		padding: "1px 7px",
		borderRadius: 999,
		fontWeight: 500,
		background: "var(--dsw-alias-bg-layer-3, var(--dsw-alias-bg-layer-1))",
		border: "0.5px solid var(--dsw-alias-border-l2)",
		color: "var(--dsw-alias-label-secondary)"
	},
	badgeDefault: {
		fontSize: 10,
		lineHeight: "14px",
		padding: "1px 7px",
		borderRadius: 999,
		fontWeight: 500,
		background: "var(--dsw-alias-state-success-primary, #067647)",
		color: "var(--dsw-alias-label-primary-foreground, #fff)"
	},
	badgeWarn: {
		fontSize: 10,
		lineHeight: "14px",
		padding: "1px 7px",
		borderRadius: 999,
		fontWeight: 500,
		background: "var(--dsw-alias-state-warn-primary, #f5a524)",
		color: "var(--dsw-alias-label-primary-foreground, #fff)"
	},
	pushRight: {
		marginLeft: "auto",
		flexShrink: 0
	},
	catalog: {
		display: "flex",
		flexDirection: "column",
		gap: 6,
		marginTop: 4,
		borderTop: "0.5px solid var(--dsw-alias-border-l2)",
		paddingTop: 8
	},
	catalogHeader: {
		display: "flex",
		alignItems: "center",
		gap: 8,
		flexWrap: "wrap"
	},
	catalogTitle: {
		fontSize: 12,
		lineHeight: "18px",
		fontWeight: 500,
		color: "var(--dsw-alias-label-secondary)"
	},
	catalogMeta: {
		fontSize: 12,
		lineHeight: "18px",
		color: "var(--dsw-alias-label-tertiary)"
	},
	pill: {},
	pillSmall: {},
	disclosure: {
		display: "inline-flex",
		alignItems: "center",
		gap: 4,
		margin: 0
	},
	chevron: {
		fontSize: 10,
		lineHeight: "18px",
		color: "var(--dsw-alias-label-tertiary)"
	},
	modelList: {
		display: "flex",
		flexDirection: "column",
		gap: 6,
		marginTop: 2
	},
	modelRowGrow: {
		display: "flex",
		alignItems: "flex-start",
		gap: 8,
		flex: "1 1 auto",
		minWidth: 0
	},
	autoRow: {
		display: "flex",
		alignItems: "flex-start",
		gap: 8,
		cursor: "pointer",
		fontSize: 13,
		lineHeight: "20px",
		color: "var(--dsw-alias-label-primary)"
	},
	chain: {
		display: "flex",
		flexDirection: "column",
		gap: 3,
		margin: "2px 0 4px 26px",
		paddingLeft: 8,
		borderLeft: "0.5px solid var(--dsw-alias-border-l2)"
	},
	chainTitle: {
		fontSize: 11,
		lineHeight: "16px",
		color: "var(--dsw-alias-label-tertiary)"
	},
	addRow: {
		display: "flex",
		alignItems: "center",
		gap: 6,
		marginTop: 2
	},
	addInput: {},
	removeButton: {
		flexShrink: 0,
		alignSelf: "center",
		padding: "0 6px"
	},
	chainRowWrap: {
		display: "flex",
		alignItems: "center",
		gap: 4
	},
	chainRow: {
		display: "flex",
		alignItems: "center",
		gap: 6,
		cursor: "pointer",
		flex: "1 1 auto",
		fontSize: 12,
		lineHeight: "18px",
		color: "var(--dsw-alias-label-secondary)"
	},
	usage: {
		display: "flex",
		flexDirection: "column",
		gap: 8,
		marginTop: 2,
		borderTop: "0.5px solid var(--dsw-alias-border-l2)",
		paddingTop: 10
	},
	usageHeader: {
		display: "flex",
		alignItems: "center",
		gap: 8,
		flexWrap: "wrap"
	},
	usageRow: {
		display: "flex",
		flexDirection: "column",
		gap: 4
	},
	usageLine: {
		display: "flex",
		alignItems: "baseline",
		gap: 8,
		justifyContent: "space-between"
	},
	usageMeter: {
		height: 6,
		borderRadius: 999,
		background: "var(--dsw-alias-bg-layer-3, var(--dsw-alias-bg-layer-2))",
		overflow: "hidden",
		boxShadow: "inset 0 0 0 1px #8a8f9833"
	},
	usageAccount: {
		display: "flex",
		flexDirection: "column",
		gap: 6,
		padding: "8px 10px",
		borderRadius: "var(--dsw-radius-md, 10px)",
		background: "var(--dsw-alias-bg-layer-1)",
		border: "0.5px solid var(--dsw-alias-border-l2)"
	},
	usageInline: {
		display: "flex",
		flexDirection: "column",
		gap: 6,
		marginTop: 4
	},
	autoSwitchRow: {
		display: "flex",
		alignItems: "center",
		gap: 8,
		justifyContent: "space-between"
	},
	modelRow: {
		display: "flex",
		alignItems: "flex-start",
		gap: 8,
		cursor: "pointer",
		fontSize: 13,
		lineHeight: "20px",
		color: "var(--dsw-alias-label-primary)"
	},
	modelRowHidden: {
		display: "flex",
		alignItems: "flex-start",
		gap: 8,
		cursor: "pointer",
		fontSize: 13,
		lineHeight: "20px",
		color: "var(--dsw-alias-label-dimmed)"
	},
	modelBody: {
		display: "flex",
		flexDirection: "column",
		gap: 1,
		minWidth: 0
	},
	modelName: {
		fontSize: 13,
		lineHeight: "20px"
	},
	modelMeta: {
		fontSize: 11,
		lineHeight: "16px",
		color: "var(--dsw-alias-label-tertiary)",
		userSelect: "all"
	},
	device: {
		marginTop: 4,
		display: "flex",
		flexDirection: "column",
		gap: 6,
		border: "0.5px solid var(--dsw-alias-border-l2)",
		borderRadius: "var(--dsw-radius-lg, 16px)",
		padding: "10px 12px",
		background: "var(--dsw-alias-bg-layer-1)"
	},
	deviceTitle: {
		fontWeight: 500,
		fontSize: 13,
		lineHeight: "20px",
		color: "var(--dsw-alias-label-primary)"
	},
	deviceCode: {
		fontFamily: "monospace",
		fontSize: 18,
		lineHeight: "24px",
		letterSpacing: 2,
		color: "var(--dsw-alias-label-primary)",
		userSelect: "all"
	},
	details: {
		display: "flex",
		flexDirection: "column",
		gap: 4,
		fontSize: 12,
		lineHeight: "18px",
		color: "var(--dsw-alias-label-tertiary)"
	},
	detailRow: {
		display: "flex",
		gap: 8,
		justifyContent: "space-between",
		alignItems: "baseline"
	},
	detailValue: {
		userSelect: "all",
		overflowWrap: "anywhere",
		textAlign: "right"
	},
	modal: {
		width: 440,
		maxWidth: "100%",
		maxHeight: "85vh",
		overflowY: "auto",
		boxSizing: "border-box",
		display: "flex",
		flexDirection: "column",
		gap: 10,
		padding: "16px 18px",
		margin: "auto"
	},
	modalHeader: {
		display: "flex",
		alignItems: "center",
		gap: 8
	},
	modalTitle: {
		fontWeight: 500,
		fontSize: 16,
		lineHeight: "24px",
		color: "var(--dsw-alias-label-primary)"
	},
	modalActions: {
		display: "flex",
		gap: 8,
		justifyContent: "flex-end",
		marginTop: 2
	},
	divider: { borderTop: "0.5px solid var(--dsw-alias-border-l2)" }
};
/** Status dot style for one provider's connection state. */
function dotStyle(status) {
	if (status.busy) return styles.dotBusy;
	if (status.loggedIn) return styles.dotLive;
	return {};
}
/** Localized label for one provider-owned detail row. */
function detailLabel(t, row) {
	const key = `detail_${row.key}`;
	return key in en ? t(key) : row.key;
}
/** Localized label for one metered window the provider states. */
function windowLabel(t, window$1) {
	const key = `usage_${window$1.label}`;
	return key in en ? t(key) : window$1.label;
}
/** Credit-like window labels: money, not a share of an allowance. */
const CREDIT_WINDOW_LABELS = /credit|wallet|balance|token|point|积分|额度/i;
/**
* How much of one window is spent, as a display string.
*
* A credit balance (WorkBuddy's Credits) reads as the raw used/limit count; a time
* quota reads as the spent percent. Told apart by the label, since both state the
* same fields.
*/
function windowAmount$1(window$1) {
	if (CREDIT_WINDOW_LABELS.test(window$1.label) && window$1.used !== void 0 && window$1.limit !== void 0) return `${String(window$1.used)}/${String(window$1.limit)}`;
	if (window$1.usedPercent !== void 0) return `${String(Math.round(window$1.usedPercent))}%`;
	if (window$1.used !== void 0 && window$1.limit !== void 0) return `${String(window$1.used)}/${String(window$1.limit)}`;
	return "—";
}
/**
* A window's figure, marked as the share spent.
*
* The meter fills as the allowance is spent, so the number beside it is a share spent, not a
* share left — saying which is what keeps "0%" from reading as "no allowance left". A window
* that states no figure at all keeps its dash rather than claiming nothing is spent.
*/
function windowSpentLabel(t, window$1) {
	const amount = windowAmount$1(window$1);
	return amount === "—" ? amount : t("usageSpent", { value: amount });
}
/** One account's metered windows and check-in, rendered under its account row. */
function AccountUsage(props) {
	const { entry, t, busyAction, onCheckin, onTasks } = props;
	if (entry === void 0) return null;
	const checkin = entry.checkin;
	return /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
		style: styles.usageInline,
		children: [
			/* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
				style: styles.usageLine,
				children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
					style: styles.modelName,
					children: checkin?.active === true ? checkin.todayCheckedIn ? t("checkinDone", { days: checkin.streakDays }) : t("checkinPending") : ""
				}), /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("span", {
					style: styles.pushRight,
					children: [onTasks !== void 0 && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
						type: "button",
						className: "dsh-subscription-quiet",
						disabled: busyAction,
						onClick: onTasks,
						children: t("tasksClaim")
					}), checkin?.active === true && !checkin.todayCheckedIn && onCheckin !== void 0 && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
						type: "button",
						className: "dsh-subscription-quiet",
						disabled: busyAction,
						onClick: onCheckin,
						children: t("checkinClaim")
					})]
				})]
			}),
			entry.windows.map((window$1) => /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
				style: styles.usageRow,
				children: [/* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
					style: styles.usageLine,
					children: [/* @__PURE__ */ (0, react_jsx_runtime.jsxs)("span", {
						style: styles.modelName,
						children: [
							windowLabel(t, window$1),
							" · ",
							windowSpentLabel(t, window$1)
						]
					}), /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
						style: styles.modelMeta,
						children: window$1.resetsAt === void 0 ? "" : formatReset$1(window$1.resetsAt, t)
					})]
				}), window$1.usedPercent !== void 0 && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("div", {
					className: "dsh-subscription-meter",
					children: /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
						style: { width: `${String(Math.min(100, Math.max(0, window$1.usedPercent)))}%` },
						"data-exhausted": window$1.usedPercent >= 90 ? "true" : "false"
					})
				})]
			}, `${entry.account}-${window$1.label}`)),
			entry.windows.length === 0 && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("p", {
				style: styles.statusLine,
				children: t("usageNone")
			})
		]
	});
}
/** The pending-login block: a device code, or a link to the sign-in page. */
function PendingLogin(props) {
	const { prompt, t, copied, onCopy } = props;
	return /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
		style: styles.device,
		children: [
			/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
				style: styles.deviceTitle,
				children: prompt.mode === "device-code" ? t("deviceTitle") : t("openLogin")
			}),
			/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
				style: styles.statusLine,
				children: prompt.mode === "device-code" ? t("devicePrompt") : t("openLoginHint")
			}),
			prompt.userCode !== void 0 && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
				style: styles.deviceCode,
				children: prompt.userCode
			}),
			/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
				style: styles.statusLine,
				children: t("deviceExpires", { time: formatTime(prompt.expiresAt) })
			}),
			/* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
				style: styles.actions,
				children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
					type: "button",
					onClick: () => {
						window.open(prompt.url, "_blank", "noopener");
					},
					children: prompt.mode === "device-code" ? t("deviceOpen") : t("openLogin")
				}), prompt.userCode !== void 0 && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
					type: "button",
					onClick: () => {
						onCopy(prompt.userCode);
					},
					children: copied ? t("deviceCopied") : t("deviceCopy")
				})]
			})
		]
	});
}
/** One provider's card. */
function ProviderCard(props) {
	const { status, t, busyAction, message } = props;
	const [copied, setCopied] = (0, react.useState)(false);
	const [modelsOpen, setModelsOpen] = (0, react.useState)(false);
	/** Whether this provider's whole card is unfolded. */
	const [cardOpen, setCardOpen] = (0, react.useState)(false);
	const [manageOpen, setManageOpen] = (0, react.useState)(false);
	const mounted = (0, react.useRef)(true);
	const dialog = (0, react.useRef)(null);
	(0, react.useEffect)(() => {
		mounted.current = true;
		return () => {
			mounted.current = false;
		};
	}, []);
	(0, react.useEffect)(() => {
		const element = dialog.current;
		if (element === null || typeof element.showModal !== "function") return;
		if (manageOpen && !element.open) element.showModal();
		if (!manageOpen && element.open) element.close();
	}, [manageOpen]);
	const copyCode = (0, react.useCallback)(async (code) => {
		try {
			await navigator.clipboard.writeText(code);
			if (mounted.current) {
				setCopied(true);
				setTimeout(() => {
					if (mounted.current) setCopied(false);
				}, 2e3);
			}
		} catch {}
	}, []);
	const toggleModelsOpen = (0, react.useCallback)(() => {
		setModelsOpen((open) => !open);
	}, []);
	/** Fold or unfold this provider's card. */
	const toggleCardOpen = (0, react.useCallback)(() => {
		setCardOpen((open) => !open);
	}, []);
	const accounts = status.accounts;
	const models = status.models;
	/** Whether this route signs in at all; the auto provider does not. */
	const connectable = status.connectable !== false;
	const hiddenCount = models.filter((model) => !model.visible).length;
	/** Whether the add-a-model-id field is open, and what has been typed. */
	const [adding, setAdding] = (0, react.useState)(false);
	const [draft, setDraft] = (0, react.useState)("");
	/** The row being dragged, so it can be dimmed while it moves. */
	const [dragFrom, setDragFrom] = (0, react.useState)(void 0);
	/**
	* The dragged row, mirrored in a ref.
	*
	* The drop is handled by an ancestor whose handler was attached in an earlier
	* render, so it must not depend on state that was set during the drag.
	*/
	const dragFromRef = (0, react.useRef)(void 0);
	/** The row the pointer is currently over, so a drop lands where the user aims. */
	const dropTargetRef = (0, react.useRef)(void 0);
	/** The same row in state, purely so it can be highlighted while dragging. */
	const [dropTarget, setDropTarget] = (0, react.useState)(void 0);
	/**
	* Commit one dropped arrangement.
	*
	* The node half takes the full chain in its new order and appends whatever the
	* page left out, so sending just these ids is enough — and a model hidden in
	* another window is never dropped from the stored rank by a stale page.
	*/
	const commitOrder = (0, react.useCallback)((ids) => {
		props.onSetModelOrder(ids);
	}, [props]);
	/** Drag-and-drop plus its keyboard equivalent on the drag handle. */
	const drag = {
		start: (event, index) => {
			setDragFrom(index);
			dragFromRef.current = index;
			event.dataTransfer.effectAllowed = "move";
			event.dataTransfer.setData("text/plain", String(index));
		},
		over: (event, _index) => {
			event.preventDefault();
			event.dataTransfer.dropEffect = "move";
			const container = event.currentTarget;
			const to = indexAt(container, event.clientY);
			dropTargetRef.current = to;
			if (to !== void 0 && to !== dropTarget) setDropTarget(to);
		},
		drop: (event, index) => {
			event.preventDefault();
			const to = dropTargetRef.current ?? indexAt(event.currentTarget, event.clientY) ?? index;
			move(dragFromRef.current, to);
			dragFromRef.current = void 0;
			dropTargetRef.current = void 0;
			setDropTarget(void 0);
			setDragFrom(void 0);
		},
		dropAt: (event) => {
			event.preventDefault();
			const container = event.currentTarget;
			const to = dropTargetRef.current ?? indexAt(container, event.clientY);
			if (to !== void 0) move(dragFromRef.current, to);
			dragFromRef.current = void 0;
			dropTargetRef.current = void 0;
			setDropTarget(void 0);
			setDragFrom(void 0);
		},
		end: () => {
			dragFromRef.current = void 0;
			dropTargetRef.current = void 0;
			setDropTarget(void 0);
			setDragFrom(void 0);
		},
		key: (event, index) => {
			if (event.key !== "ArrowUp" && event.key !== "ArrowDown") return;
			event.preventDefault();
			move(index, index + (event.key === "ArrowUp" ? -1 : 1));
		}
	};
	/** Move one model row to another position and persist the result. */
	function move(from, to) {
		if (from === void 0 || from === to) return;
		if (from < 0 || to < 0 || from >= models.length || to >= models.length) return;
		const ids = models.map((model) => model.id);
		const [moved] = ids.splice(from, 1);
		if (moved === void 0) return;
		ids.splice(to, 0, moved);
		commitOrder(ids);
	}
	/**
	* The row index a drop at this pointer position lands on.
	*
	* The drop is handled on the whole list rather than per row: rows are tall and
	* separated by gaps, so a drop between two of them hits no target at all and
	* silently does nothing. Comparing the pointer against each row's midpoint also
	* makes the gesture match what the user sees.
	*/
	function indexAt(container, clientY) {
		const rows = Array.from(container.querySelectorAll("[data-model-index]"));
		if (rows.length === 0) return void 0;
		let closest = 0;
		let best = Number.POSITIVE_INFINITY;
		for (const row of rows) {
			const box = row.getBoundingClientRect();
			const distance = Math.abs(clientY - (box.top + box.height / 2));
			if (distance >= best) continue;
			best = distance;
			closest = Number(row.dataset.modelIndex);
		}
		return closest;
	}
	const busy = status.busy;
	const liveCatalog = (status.discovered?.length ?? 0) > 0;
	/**
	* What a folded card says about itself.
	*
	* The connection is the reason to open a card, so the folded row carries it;
	* `auto` has no account and counts the models it will actually try instead.
	*/
	const summaryLine = !connectable ? t("statusAuto", { count: models.filter((model) => model.visible).length }) : busy ? t("statusBusy") : status.loggedIn ? t("statusConnected", { count: accounts.length }) : t("statusSignedOut");
	return /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
		style: cardOpen ? {
			...styles.card,
			...styles.cardOpen
		} : styles.card,
		children: [
			/* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
				style: styles.cardHeader,
				role: "button",
				tabIndex: 0,
				"aria-expanded": cardOpen,
				onClick: toggleCardOpen,
				onKeyDown: (event) => {
					if (event.key === "Enter" || event.key === " ") {
						event.preventDefault();
						toggleCardOpen();
					}
				},
				children: [
					/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
						style: {
							...styles.dot,
							...dotStyle(status)
						},
						"aria-hidden": "true"
					}),
					/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
						style: styles.name,
						children: status.displayName
					}),
					status.loggedIn && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
						style: styles.badgeDefault,
						children: t("badgeLive")
					}),
					/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
						style: styles.cardSummary,
						children: summaryLine
					}),
					/* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
						type: "button",
						className: "dsh-subscription-quiet",
						style: styles.pushRight,
						"aria-expanded": cardOpen,
						"aria-controls": `dsh-subscription-card-${status.provider}`,
						onClick: (event) => {
							event.stopPropagation();
							toggleCardOpen();
						},
						children: cardOpen ? t("collapse") : t("edit")
					})
				]
			}),
			/* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
				id: `dsh-subscription-card-${status.provider}`,
				style: cardOpen ? styles.cardBody : {
					...styles.cardBody,
					display: "none"
				},
				children: [
					connectable && !status.loggedIn && !busy && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("p", {
						style: styles.statusLine,
						children: t("statusSignIn")
					}),
					status.lastError !== void 0 && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("p", {
						className: "dsh-subscription-error",
						children: t("lastError", { message: status.lastError })
					}),
					message?.tone === "error" && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("p", {
						className: "dsh-subscription-error",
						children: message.text
					}),
					message?.tone === "success" && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("p", {
						className: "dsh-subscription-success",
						children: message.text
					}),
					status.provider === "auto" && props.onSetAutoOn !== void 0 && /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("label", {
						style: styles.autoSwitchRow,
						children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
							style: styles.modelName,
							children: t("autoSwitchLabel")
						}), /* @__PURE__ */ (0, react_jsx_runtime.jsx)("input", {
							type: "checkbox",
							className: "dsh-subscription-switch",
							checked: props.autoOn ?? true,
							disabled: busyAction,
							onChange: (event) => {
								props.onSetAutoOn?.(event.target.checked);
							}
						})]
					}),
					accounts.map((account) => {
						const usageEntry = (status.usage ?? []).find((entry) => entry.account === account.account);
						return /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
							style: styles.accountRow,
							children: [/* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
								style: styles.accountHeader,
								children: [
									/* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
										type: "button",
										style: styles.starButton,
										title: account.isDefault ? t("defaultBadge") : t("makeDefault"),
										"aria-label": account.isDefault ? t("defaultBadge") : t("makeDefault"),
										disabled: busyAction || account.isDefault,
										onClick: () => {
											props.onSetDefault(account.account);
										},
										children: account.isDefault ? "★" : "☆"
									}),
									/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
										style: styles.accountName,
										children: account.label
									}),
									account.isDefault && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
										style: styles.badgeDefault,
										children: t("defaultBadge")
									}),
									account.needsLogin === true && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
										style: styles.badgeWarn,
										title: t("accountNeedsLoginHint"),
										children: t("accountNeedsLogin")
									}),
									usageEntry?.plan !== void 0 && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
										style: styles.badge,
										children: t("usagePlan", { plan: usageEntry.plan })
									}),
									/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
										style: styles.statusLine,
										children: account.expired ? t("accountExpired") : t("accountExpires", { time: formatTime(account.expiresAt) })
									}),
									/* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
										type: "button",
										className: "dsh-subscription-quiet",
										style: styles.pushRight,
										disabled: busyAction,
										onClick: () => {
											props.onLogout(account.account);
										},
										children: t("logout")
									})
								]
							}), /* @__PURE__ */ (0, react_jsx_runtime.jsx)(AccountUsage, {
								entry: usageEntry,
								t,
								busyAction,
								...props.onCheckin === void 0 ? {} : { onCheckin: () => {
									props.onCheckin?.(account.account);
								} },
								...props.onTasks === void 0 ? {} : { onTasks: () => {
									props.onTasks?.(account.account);
								} }
							})]
						}, account.account);
					}),
					status.loggedIn && /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
						style: styles.catalog,
						children: [
							/* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
								style: styles.catalogHeader,
								children: [
									/* @__PURE__ */ (0, react_jsx_runtime.jsxs)("button", {
										type: "button",
										className: "dsh-subscription-disclosure",
										style: styles.disclosure,
										"aria-expanded": modelsOpen,
										"aria-controls": `dsh-subscription-models-${status.provider}`,
										onClick: toggleModelsOpen,
										children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
											style: styles.chevron,
											"aria-hidden": "true",
											children: modelsOpen ? "▾" : "▸"
										}), /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
											style: styles.catalogTitle,
											children: t("modelsTitle")
										})]
									}),
									/* @__PURE__ */ (0, react_jsx_runtime.jsxs)("span", {
										style: styles.catalogMeta,
										children: [
											t("modelsCount", { count: models.length }),
											" · ",
											liveCatalog ? t("modelsLive") : t("modelsBuiltIn"),
											hiddenCount === 0 ? "" : ` ${t("modelsHiddenCount", { count: hiddenCount })}`
										]
									}),
									/* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
										type: "button",
										className: "dsh-subscription-quiet",
										style: styles.pushRight,
										disabled: busyAction,
										onClick: props.onRefreshModels,
										children: busyAction ? t("refreshing") : t("refresh")
									}),
									/* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
										type: "button",
										className: "dsh-subscription-quiet",
										disabled: busyAction || !connectable,
										title: t("modelsAddHint"),
										onClick: () => {
											setAdding((open) => !open);
										},
										children: t("modelsAdd")
									})
								]
							}),
							adding && /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("form", {
								style: styles.addRow,
								onSubmit: (event) => {
									event.preventDefault();
									const id = draft.trim();
									if (id.length === 0) return;
									props.onAddModel(id);
									setDraft("");
									setAdding(false);
								},
								children: [
									/* @__PURE__ */ (0, react_jsx_runtime.jsx)("input", {
										type: "text",
										className: "dsh-subscription-input",
										style: styles.addInput,
										value: draft,
										autoFocus: true,
										spellCheck: false,
										placeholder: t("modelsAddPlaceholder"),
										"aria-label": t("modelsAdd"),
										disabled: busyAction,
										onChange: (event) => {
											setDraft(event.target.value);
										}
									}),
									/* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
										type: "submit",
										disabled: busyAction || draft.trim().length === 0,
										children: t("modelsAddConfirm")
									}),
									/* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
										type: "button",
										className: "dsh-subscription-quiet",
										onClick: () => {
											setAdding(false);
											setDraft("");
										},
										children: t("cancel")
									})
								]
							}),
							modelsOpen && /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
								id: `dsh-subscription-models-${status.provider}`,
								style: styles.modelList,
								onDragOver: (event) => {
									drag.over(event, 0);
								},
								onDrop: drag.dropAt,
								children: [
									status.provider === "auto" && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
										style: styles.chainTitle,
										children: t("modelsAutoChain")
									}),
									models.map((model, index) => /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
										"data-model-index": index,
										draggable: !busyAction,
										style: {
											...styles.modelRowWrap,
											...dragFrom === index ? { opacity: .5 } : {},
											...dropTarget === index && dragFrom !== void 0 && dragFrom !== index ? { boxShadow: "inset 0 2px 0 0 var(--dsw-alias-brand-primary)" } : {}
										},
										onDragStart: (event) => {
											drag.start(event, index);
										},
										onDragEnd: drag.end,
										children: [
											/* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
												type: "button",
												className: "dsh-subscription-handle",
												disabled: busyAction,
												title: t("modelsDrag"),
												"aria-label": `${t("modelsDrag")} · ${model.name}`,
												onKeyDown: (event) => {
													drag.key(event, index);
												},
												children: "⠿"
											}),
											/* @__PURE__ */ (0, react_jsx_runtime.jsxs)("label", {
												style: model.visible ? {
													...styles.modelRowGrow,
													cursor: "pointer"
												} : {
													...styles.modelRowGrow,
													cursor: "pointer",
													color: "var(--dsw-alias-label-dimmed)"
												},
												title: model.visible ? t("modelsHidden") : t("modelsShow"),
												children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("input", {
													type: "checkbox",
													className: "dsh-subscription-switch",
													role: "switch",
													checked: model.visible,
													disabled: busyAction,
													"aria-label": `${t("modelsShow")} · ${model.name}`,
													onChange: (event) => {
														props.onSetModelVisible(model.id, event.target.checked);
													}
												}), /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("span", {
													style: styles.modelBody,
													children: [/* @__PURE__ */ (0, react_jsx_runtime.jsxs)("span", {
														style: styles.modelName,
														children: [model.name, model.source === void 0 ? null : /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
															style: styles.badge,
															children: model.source
														})]
													}), /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("span", {
														style: styles.modelMeta,
														children: [
															model.id,
															" · ",
															t("modelsContext", { tokens: formatTokens(model.contextWindow) }),
															model.defaultEffort === void 0 ? "" : ` · ${model.defaultEffort}`,
															model.inputModalities === void 0 || model.inputModalities.length === 0 ? "" : ` · ${model.inputModalities.join("/")}`
														]
													})]
												})]
											}),
											model.userAdded === true && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
												type: "button",
												className: "dsh-subscription-quiet",
												style: styles.removeButton,
												disabled: busyAction,
												title: t("modelsRemove"),
												"aria-label": `${t("modelsRemove")} · ${model.name}`,
												onClick: () => {
													props.onRemoveModel(model.id);
												},
												children: "×"
											})
										]
									}, model.id)),
									models.length === 0 && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("p", {
										style: styles.statusLine,
										children: status.provider === "auto" ? t("modelsAutoChainEmpty") : t("modelsEmpty")
									})
								]
							})
						]
					}),
					connectable && /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
						style: styles.actions,
						children: [
							!busy && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
								type: "button",
								disabled: busyAction,
								onClick: props.onLogin,
								children: accounts.length === 0 ? t("connect") : t("addAccount")
							}),
							!busy && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
								type: "button",
								disabled: busyAction,
								onClick: () => {
									setManageOpen(true);
								},
								children: t("manage")
							}),
							busy && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
								type: "button",
								disabled: busyAction,
								onClick: props.onCancel,
								children: t("cancel")
							})
						]
					}),
					connectable && accounts.length > 0 && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("p", {
						style: styles.statusLine,
						children: t("hint")
					}),
					busy && status.pending !== void 0 && /* @__PURE__ */ (0, react_jsx_runtime.jsx)(PendingLogin, {
						prompt: status.pending,
						t,
						copied,
						onCopy: (code) => {
							copyCode(code);
						}
					})
				]
			}),
			/* @__PURE__ */ (0, react_jsx_runtime.jsxs)("dialog", {
				ref: dialog,
				className: "dsh-subscription-manager",
				"aria-labelledby": `dsh-subscription-manage-${status.provider}`,
				style: manageOpen ? styles.modal : {
					...styles.modal,
					display: "none"
				},
				onClose: () => {
					setManageOpen(false);
				},
				onCancel: () => {
					setManageOpen(false);
				},
				children: [
					/* @__PURE__ */ (0, react_jsx_runtime.jsx)("div", {
						style: styles.modalHeader,
						children: /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
							style: styles.modalTitle,
							id: `dsh-subscription-manage-${status.provider}`,
							children: t("manageTitle", { provider: status.displayName })
						})
					}),
					/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
						style: styles.statusLine,
						children: t("manageAccounts")
					}),
					accounts.map((account) => /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
						style: styles.accountRow,
						children: [/* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
							style: styles.accountHeader,
							children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
								style: styles.accountName,
								children: account.label
							}), account.isDefault && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
								style: styles.badge,
								children: t("defaultBadge")
							})]
						}), /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
							style: styles.actions,
							children: [
								/* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
									type: "button",
									disabled: busyAction,
									onClick: () => {
										props.onRefreshToken(account.account);
									},
									children: t("refreshToken")
								}),
								!account.isDefault && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
									type: "button",
									disabled: busyAction,
									onClick: () => {
										props.onSetDefault(account.account);
									},
									children: t("makeDefault")
								}),
								/* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
									type: "button",
									disabled: busyAction,
									onClick: () => {
										props.onLogout(account.account);
									},
									children: t("logout")
								})
							]
						})]
					}, account.account)),
					accounts.length === 0 && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("p", {
						style: styles.statusLine,
						children: t("statusSignedOut")
					}),
					/* @__PURE__ */ (0, react_jsx_runtime.jsx)("div", { style: styles.divider }),
					/* @__PURE__ */ (0, react_jsx_runtime.jsx)("div", {
						style: styles.actions,
						children: /* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
							type: "button",
							disabled: busyAction,
							onClick: props.onReload,
							children: t("reload")
						})
					}),
					/* @__PURE__ */ (0, react_jsx_runtime.jsx)("p", {
						style: styles.statusLine,
						children: t("reloadHint")
					}),
					/* @__PURE__ */ (0, react_jsx_runtime.jsx)("div", { style: styles.divider }),
					/* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
						style: styles.details,
						children: [status.details.map((row) => /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
							style: styles.detailRow,
							children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", { children: detailLabel(t, row) }), /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
								style: styles.detailValue,
								children: row.value
							})]
						}, row.key)), status.discovered !== void 0 && status.discovered.length > 0 && /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
							style: styles.detailRow,
							children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", { children: t("modelsLive") }), /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
								style: styles.detailValue,
								children: status.discovered.join(", ")
							})]
						})]
					}),
					/* @__PURE__ */ (0, react_jsx_runtime.jsx)("div", {
						className: "dsh-subscription-dialog-actions",
						style: styles.modalActions,
						children: /* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
							type: "button",
							onClick: () => {
								setManageOpen(false);
							},
							children: t("close")
						})
					})
				]
			})
		]
	});
}
/**
* The Subscriptions settings section.
* @param props - section props (inject face plus the shell's `close`).
* @returns the section content.
*/
function SubscriptionsSection(props) {
	const { call } = props;
	const t = props.t ?? fallbackTranslate;
	const [status, setStatus] = (0, react.useState)(void 0);
	const [error, setError] = (0, react.useState)(void 0);
	const [messages, setMessages] = (0, react.useState)({});
	const [busyAction, setBusyAction] = (0, react.useState)(false);
	const [autoOn, setAutoOn] = (0, react.useState)(true);
	const mounted = (0, react.useRef)(true);
	const timer = (0, react.useRef)(void 0);
	/** Read whether the `auto` provider is offered, so its card can render the switch. */
	(0, react.useEffect)(() => {
		if (call === void 0) return;
		let live = true;
		call("auto-state", void 0).then((result) => {
			if (live) setAutoOn(result.enabled);
		}).catch(() => {});
		return () => {
			live = false;
		};
	}, [call]);
	/** Fetch the current state; a failure is shown rather than swallowed. */
	const refresh = (0, react.useCallback)(async () => {
		if (call === void 0) return;
		try {
			const next = await call("status", void 0);
			if (!mounted.current) return;
			setStatus(next);
			setError(void 0);
		} catch (caught) {
			if (mounted.current) setError(t("actionFailed", { message: messageOf(caught) }));
		}
	}, [call, t]);
	/** Flip the `auto` provider on or off. */
	const setAuto = (0, react.useCallback)(async (enabled) => {
		if (call === void 0) return;
		setBusyAction(true);
		try {
			const result = await call("setAutoOn", { enabled });
			if (!mounted.current) return;
			setAutoOn(result.enabled);
			await refresh();
		} catch (caught) {
			if (mounted.current) setError(t("actionFailed", { message: messageOf(caught) }));
		} finally {
			if (mounted.current) setBusyAction(false);
		}
	}, [
		call,
		refresh,
		t
	]);
	/** Claim one account's daily check-in reward, then refresh the figures. */
	const checkin = (0, react.useCallback)(async (provider, account) => {
		if (call === void 0) return;
		setBusyAction(true);
		try {
			const result = await call("checkin", {
				provider,
				account
			});
			if (!mounted.current) return;
			setMessages((previous) => ({
				...previous,
				[provider]: {
					tone: "success",
					text: result.already ? t("checkinAlready", { days: result.streakDays }) : t("checkinClaimed", {
						credit: result.credit,
						days: result.streakDays
					})
				}
			}));
			await refresh();
		} catch (caught) {
			if (mounted.current) setError(t("actionFailed", { message: messageOf(caught) }));
		} finally {
			if (mounted.current) setBusyAction(false);
		}
	}, [
		call,
		refresh,
		t
	]);
	/** Auto-claim one account's growth-task rewards, then refresh the figures. */
	const runTasks = (0, react.useCallback)(async (provider, account) => {
		if (call === void 0) return;
		setBusyAction(true);
		try {
			const result = await call("tasks", {
				provider,
				account,
				claim: true
			});
			if (!mounted.current) return;
			setMessages((previous) => ({
				...previous,
				[provider]: {
					tone: "success",
					text: result.claimed > 0 ? t("tasksClaimed", {
						count: result.claimed,
						credit: result.creditEarned
					}) : result.claimable > 0 ? t("tasksClaimable", { count: result.claimable }) : t("tasksNone")
				}
			}));
			await refresh();
		} catch (caught) {
			if (mounted.current) setError(t("actionFailed", { message: messageOf(caught) }));
		} finally {
			if (mounted.current) setBusyAction(false);
		}
	}, [
		call,
		refresh,
		t
	]);
	const stopPolling = (0, react.useCallback)(() => {
		if (timer.current !== void 0) {
			clearInterval(timer.current);
			timer.current = void 0;
		}
	}, []);
	const startPolling = (0, react.useCallback)(() => {
		if (timer.current !== void 0) return;
		timer.current = setInterval(() => {
			refresh();
		}, POLL_INTERVAL_MS);
	}, [refresh]);
	(0, react.useEffect)(() => {
		mounted.current = true;
		refresh();
		return () => {
			mounted.current = false;
			stopPolling();
		};
	}, [refresh, stopPolling]);
	/** Every route worth reading, as of the latest render, for the usage poll. */
	const signedInRef = (0, react.useRef)([]);
	(0, react.useEffect)(() => {
		signedInRef.current = usageTargets(status?.providers ?? []);
	});
	/**
	* Read each named route's plan and allowance, applying every answer.
	*
	* A read is not forced: the node half reuses a figure only for a few seconds and
	* never reuses a substitute, so a steady interval refreshes the figures while
	* collapsing bursts into one request per route.
	*/
	const readUsage = (0, react.useCallback)(async (providers) => {
		if (call === void 0) return;
		for (const provider of providers) try {
			const result = await call("usage", { provider });
			if (!mounted.current) return;
			setStatus(result.status);
		} catch {}
	}, [call]);
	const usageRequested = (0, react.useRef)(false);
	(0, react.useEffect)(() => {
		if (call === void 0 || status === void 0 || usageRequested.current) return;
		const signedIn = usageTargets(status.providers);
		if (signedIn.length === 0) return;
		usageRequested.current = true;
		readUsage(signedIn);
	}, [
		call,
		status,
		readUsage
	]);
	(0, react.useEffect)(() => {
		if (call === void 0) return;
		const tick = () => {
			if (globalThis.document?.visibilityState === "hidden") return;
			readUsage(signedInRef.current);
		};
		const interval = setInterval(tick, USAGE_POLL_INTERVAL_MS);
		return () => {
			clearInterval(interval);
		};
	}, [call, readUsage]);
	const anyBusy = status?.providers.some((provider) => provider.busy) === true;
	(0, react.useEffect)(() => {
		if (anyBusy) startPolling();
		else stopPolling();
	}, [
		anyBusy,
		startPolling,
		stopPolling
	]);
	/**
	* Signed-in providers read first: a route with a live account is the one a
	* reader most likely wants, and `auto` (which owns no account) leads the rest.
	* The sort is stable, so equal ranks keep the host's configured order.
	*/
	const orderedProviders = (0, react.useMemo)(() => {
		const providers = status?.providers ?? [];
		const rank = (provider) => {
			if (provider.provider === "auto") return 0;
			if (provider.loggedIn) return 1;
			return 2;
		};
		return [...providers].sort((left, right) => rank(left) - rank(right));
	}, [status?.providers]);
	/** Run one action for one provider, reporting its own failure. */
	const act = (0, react.useCallback)(async (provider, run) => {
		setBusyAction(true);
		setMessages((current) => {
			const next = { ...current };
			delete next[provider];
			return next;
		});
		try {
			await run();
		} catch (caught) {
			if (mounted.current) setMessages((current) => ({
				...current,
				[provider]: {
					tone: "error",
					text: t("actionFailed", { message: messageOf(caught) })
				}
			}));
		} finally {
			if (mounted.current) setBusyAction(false);
		}
	}, [t]);
	const login = (0, react.useCallback)((provider) => {
		if (call === void 0) return;
		act(provider, async () => {
			const started = await call("login", { provider });
			if (!mounted.current) return;
			setStatus(started.status);
			if (started.prompt.mode === "open-url") window.open(started.prompt.url, "_blank", "noopener");
		});
	}, [act, call]);
	const cancel = (0, react.useCallback)((provider) => {
		if (call === void 0) return;
		act(provider, async () => {
			const next = await call("cancel", { provider });
			if (mounted.current) setStatus(next);
		});
	}, [act, call]);
	const logout = (0, react.useCallback)((provider, account) => {
		if (call === void 0) return;
		act(provider, async () => {
			const next = await call("logout", {
				provider,
				account
			});
			if (mounted.current) setStatus(next);
		});
	}, [act, call]);
	const setDefault = (0, react.useCallback)((provider, account) => {
		if (call === void 0) return;
		act(provider, async () => {
			const next = await call("setDefault", {
				provider,
				account
			});
			if (mounted.current) setStatus(next);
		});
	}, [act, call]);
	const refreshToken = (0, react.useCallback)((provider, account) => {
		if (call === void 0) return;
		act(provider, async () => {
			const result = await call("refresh", {
				provider,
				account
			});
			if (!mounted.current) return;
			setStatus(result.status);
			setMessages((current) => ({
				...current,
				[provider]: {
					tone: "success",
					text: t("tokenRefreshed", { time: formatTime(result.expiresAt) })
				}
			}));
		});
	}, [
		act,
		call,
		t
	]);
	const refreshModels = (0, react.useCallback)((provider) => {
		if (call === void 0) return;
		act(provider, async () => {
			const next = await call("discover", {
				provider,
				force: true
			});
			if (!mounted.current) return;
			setStatus(next);
			setMessages((current) => ({
				...current,
				[provider]: {
					tone: "success",
					text: t("modelsRefreshed")
				}
			}));
		});
	}, [
		act,
		call,
		t
	]);
	const setModelVisible = (0, react.useCallback)((provider, id, visible) => {
		if (call === void 0) return;
		act(provider, async () => {
			const next = await call("setModelVisible", {
				provider,
				id,
				visible
			});
			if (mounted.current) setStatus(next);
		});
	}, [act, call]);
	const refreshUsage = (0, react.useCallback)((provider) => {
		if (call === void 0) return;
		act(provider, async () => {
			const result = await call("usage", {
				provider,
				force: true
			});
			if (mounted.current) setStatus(result.status);
		});
	}, [act, call]);
	const setModelOrder = (0, react.useCallback)((provider, ids) => {
		if (call === void 0) return;
		act(provider, async () => {
			const next = await call("setModelOrder", {
				provider,
				ids: [...ids]
			});
			if (mounted.current) setStatus(next);
		});
	}, [act, call]);
	const addModel = (0, react.useCallback)((provider, id) => {
		if (call === void 0) return;
		act(provider, async () => {
			const next = await call("addModel", {
				provider,
				id
			});
			if (mounted.current) setStatus(next);
		});
	}, [act, call]);
	const removeModel = (0, react.useCallback)((provider, id) => {
		if (call === void 0) return;
		act(provider, async () => {
			const next = await call("removeModel", {
				provider,
				id
			});
			if (mounted.current) setStatus(next);
		});
	}, [act, call]);
	const reload = (0, react.useCallback)((provider) => {
		if (call === void 0) return;
		act(provider, async () => {
			const next = await call("reload", void 0);
			if (mounted.current) setStatus(next);
		});
	}, [act, call]);
	if (call === void 0) return /* @__PURE__ */ (0, react_jsx_runtime.jsx)("p", {
		style: styles.intro,
		children: t("unavailable")
	});
	return /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
		className: "dsh-subscriptions-settings",
		style: styles.section,
		children: [
			/* @__PURE__ */ (0, react_jsx_runtime.jsx)("style", { children: subscriptionsCss }),
			/* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
				style: styles.sectionHeader,
				children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("h2", {
					style: styles.heading,
					children: t("nav")
				}), /* @__PURE__ */ (0, react_jsx_runtime.jsx)("p", {
					style: styles.intro,
					children: t("intro")
				})]
			}),
			error !== void 0 && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("p", {
				className: "dsh-subscription-error",
				children: error
			}),
			status === void 0 && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("p", {
				style: styles.statusLine,
				children: t("loading")
			}),
			orderedProviders.map((provider) => /* @__PURE__ */ (0, react_jsx_runtime.jsx)(ProviderCard, {
				status: provider,
				t,
				busyAction,
				...messages[provider.provider] === void 0 ? {} : { message: messages[provider.provider] },
				onLogin: () => {
					login(provider.provider);
				},
				onCancel: () => {
					cancel(provider.provider);
				},
				onLogout: (account) => {
					logout(provider.provider, account);
				},
				onSetDefault: (account) => {
					setDefault(provider.provider, account);
				},
				onRefreshToken: (account) => {
					refreshToken(provider.provider, account);
				},
				onRefreshModels: () => {
					refreshModels(provider.provider);
				},
				onSetModelVisible: (id, visible) => {
					setModelVisible(provider.provider, id, visible);
				},
				onSetModelOrder: (ids) => {
					setModelOrder(provider.provider, ids);
				},
				onAddModel: (id) => {
					addModel(provider.provider, id);
				},
				onRemoveModel: (id) => {
					removeModel(provider.provider, id);
				},
				onRefreshUsage: () => {
					refreshUsage(provider.provider);
				},
				onReload: () => {
					reload(provider.provider);
				},
				...provider.provider !== "auto" ? {} : {
					autoOn,
					onSetAutoOn: (enabled) => {
						setAuto(enabled);
					}
				},
				...provider.provider !== "workbuddy" ? {} : { onCheckin: (account) => {
					checkin(provider.provider, account);
				} },
				...provider.provider !== "workbuddy" ? {} : { onTasks: (account) => {
					runTasks(provider.provider, account);
				} }
			}, provider.provider))
		]
	});
}

//#endregion
//#region src/client/chip-helpers.ts
/**
* Abbreviations for the routes this plugin serves.
*
* The bar is a status line under the composer, where a full product name crowds out the
* shell's own pills; the full name is one click away. The fallback shortens an unknown
* route's display name rather than dropping it.
*/
const SHORT_LABELS = {
	kimi: "Kimi",
	grok: "Grok",
	antigravity: "Anti",
	glm: "GLM",
	ark: "Ark"
};
/**
* Abbreviations for the window names the providers use.
*
* The bar has room for a token or two, so `weekly` becomes `7d` — the same figure in a
* form that reads at a glance. An unrecognised window keeps its own name, shortened
* only if it is long.
*/
const SHORT_WINDOWS = {
	"weekly": "7d",
	"7d": "7d",
	"5h": "5h",
	"monthly": "mo",
	"daily": "1d",
	"credits": "cr",
	"free": "free",
	"session": "s",
	"gemini": "G5h",
	"gemini_weekly": "G7d",
	"claude": "C5h",
	"claude_weekly": "C7d"
};
/** The session's provider, from its model-selection projection. */
function providerOf(projection) {
	return (projection?.next ?? projection?.lastUsed)?.provider;
}
/**
* Reduce the node half's balance payload to the wallets the chip shows.
*
* Bonus wallets are shown after the topped-up ones, so a reader sees their own credit
* first and any promotional credit as a secondary figure. A bonus wallet that holds
* nothing is dropped rather than shown as `+¥0`: the platform reports one for every
* currency whether or not there is a bonus, and an empty one is noise, not information.
*
* @param value - the node half's balance, or undefined when there is none.
* @returns the wallets, topped-up first then bonus, or undefined when nothing is usable.
*/
function balanceOf(value) {
	if (value === null || value === void 0) return void 0;
	const wallets = [...(value.wallets ?? []).map((wallet) => ({
		currency: wallet.currency,
		balance: wallet.balance,
		bonus: false
	})), ...(value.bonusWallets ?? []).filter((wallet) => Number.parseFloat(wallet.balance) > 0).map((wallet) => ({
		currency: wallet.currency,
		balance: wallet.balance,
		bonus: true
	}))];
	return wallets.length === 0 ? void 0 : wallets;
}
/**
* Format one wallet for the chip's compact line.
*
* A balance is money, not a percentage, so it renders as `¥6.89` rather than a meter;
* the currency prefix keeps two currencies from being mistaken for one sum. The amount
* is rounded to two decimal places (a balance reads to the cent, and a figure like
* `1.1` for `1.10` would mislead) and always carries both places.
*
* @param wallet - the wallet to show.
* @returns the short form, e.g. `¥6.89` or `+$2.00` for a bonus wallet.
*/
function formatBalance(wallet) {
	const amount = Number.parseFloat(wallet.balance);
	const trimmed = Number.isFinite(amount) ? amount.toFixed(2) : wallet.balance;
	const symbol = wallet.currency === "CNY" ? "¥" : wallet.currency === "USD" ? "$" : `${wallet.currency} `;
	return `${wallet.bonus ? "+" : ""}${symbol}${trimmed}`;
}
/**
* The short form of a route's name.
*
* @param provider - the route id.
* @param label - the route's own display name.
* @returns an abbreviation that still identifies the route.
*/
function shortLabel(provider, label) {
	const known = SHORT_LABELS[provider];
	if (known !== void 0) return known;
	const [first] = label.trim().split(/\s+/);
	return first === void 0 || first === "" ? provider : first;
}
/**
* The short form of a window's name.
*
* @param label - the window's own name, e.g. `weekly`.
* @returns an abbreviation, or the label itself when it is already short.
*/
function shortWindow(label) {
	const known = SHORT_WINDOWS[label.toLowerCase()];
	if (known !== void 0) return known;
	return label.length <= 4 ? label : label.slice(0, 4);
}
/**
* The name a window reads as in the panel.
*
* A provider's window label is a wire id — `gemini_weekly` is not something to put in front
* of a reader — so a label this build has copy for is named in the reader's language, and
* anything else keeps the name its provider gave it.
*
* @param t - copy for the active locale.
* @param label - the window's own label.
* @returns the display name.
*/
function windowName(t, label) {
	const key = `usage_${label}`;
	return key in en ? t(key) : label;
}
/**
* How one window reads as a figure.
*
* A credit balance (WorkBuddy's Credits, a wallet) is money: it reads as the raw
* `used`/`limit` count. A quota is a share of an allowance: it reads as the spent
* percent. The two are told apart by what the window is called — a credit or wallet
* names money, a time window names an allowance — because both state the same fields.
*
* @param window - the metered window.
* @returns the display figure.
*/
function windowAmount(window$1) {
	if (isCreditWindow(window$1.label) && window$1.used !== void 0 && window$1.limit !== void 0) return `${String(window$1.used)}/${String(window$1.limit)}`;
	return `${String(Math.round(window$1.usedPercent ?? 0))}%`;
}
/** Credit-like window labels: money, not a share of an allowance. */
const CREDIT_LABELS = /credit|wallet|balance|token|point|积分|额度/i;
/** Whether a window's label names a credit balance rather than a time quota. */
function isCreditWindow(label) {
	return CREDIT_LABELS.test(label);
}
/**
* Reduce a route's per-account usage to one reading per account.
*
* A provider can hold several accounts (two Antigravity sign-ins, say), and the chip
* shows each one's allowance on its own line, the way the settings cards do — a merged
* figure would hide that one account is exhausted while the other is free.
*
* @param usage - every account the route answered for.
* @returns one reading per account that states a figure, in the route's order.
*/
/**
* Reduce a route's per-account usage to one reading per account.
*
* A provider can hold several accounts (two Antigravity sign-ins, say). Every account's
* windows are kept as the route stated them — a percentage-metered quota and a credit
* balance (`credits`) alike — so the per-account panel shows the real figures, and the
* compact line's merge decides which to integrate.
*
* @param usage - every account the route answered for.
* @returns one reading per account that states any window, in the route's order.
*/
function accountReadingsOf(usage) {
	const out = [];
	for (const entry of usage) {
		if (entry.windows.length === 0) continue;
		out.push({
			account: entry.account,
			windows: entry.windows,
			...entry.plan === void 0 ? {} : { plan: entry.plan }
		});
	}
	return out;
}
/**
* Merge every account's percentage-metered windows into one figure per label.
*
* The chip's compact line integrates the whole pool: two accounts at 50 percent read as
* one window at 50 percent (the same allowance spent twice as wide), not two lines. A
* credit balance (`credits`) is money, not a share of a shared allowance, so it is not
* merged — it stays on the per-account panel, where it reads correctly. Only windows
* that state a `usedPercent` (a share of an allowance) merge.
*
* @param readings - one per account, from {@link accountReadingsOf}.
* @returns one merged window per percentage-metered label, most-spent first.
*/
function mergeAccountWindows(readings) {
	const byLabel = /* @__PURE__ */ new Map();
	for (const reading of readings) for (const window$1 of reading.windows) {
		if (window$1.usedPercent === void 0) continue;
		const list = byLabel.get(window$1.label) ?? [];
		list.push(window$1);
		byLabel.set(window$1.label, list);
	}
	const merged = [];
	for (const [label, list] of byLabel) {
		const withPercent = list.filter((window$1) => window$1.usedPercent !== void 0);
		if (withPercent.length === 0) continue;
		const usedPercent = withPercent.reduce((sum, window$1) => sum + (window$1.usedPercent ?? 0), 0) / withPercent.length;
		const used = list.reduce((sum, window$1) => sum + (window$1.used ?? 0), 0);
		const limit = list.reduce((sum, window$1) => sum + (window$1.limit ?? 0), 0);
		const resets = list.map((window$1) => window$1.resetsAt).filter((at) => typeof at === "number");
		merged.push({
			label,
			usedPercent: Math.max(0, Math.min(100, usedPercent)),
			...limit > 0 ? {
				used,
				limit
			} : {},
			...resets.length === 0 ? {} : { resetsAt: Math.min(...resets) }
		});
	}
	const order = (label) => {
		const short = shortWindow(label).toLowerCase();
		if (short.endsWith("5h") || short === "s") return 0;
		if (short === "1d" || short === "mo") return 1;
		if (short.endsWith("7d")) return 2;
		return 3;
	};
	return merged.sort((left, right) => order(left.label) - order(right.label));
}
/**
* Format a window's reset the way the settings cards do.
*
* The rule is shared with the cards on purpose: a figure reading `6d20h` here and
* something else there would be two answers to one question.
*
* @param at - epoch milliseconds, or undefined when the provider states none.
* @param now - the instant to measure from.
* @returns a short phrase, or undefined when there is no reset to show.
*/
function formatReset(at, now) {
	if (at === void 0) return void 0;
	const remaining = at - now;
	if (remaining <= 0) return "now";
	const hours = remaining / 36e5;
	if (hours < 1) return `${String(Math.max(1, Math.round(remaining / 6e4)))}m`;
	if (hours < 24) return `${String(Math.round(hours))}h`;
	const days = Math.floor(hours / 24);
	return `${String(days)}d${String(Math.round(hours - days * 24))}h`;
}

//#endregion
//#region src/client/UsageChip.tsx
/** How often a visible chip refreshes its figures. */
const REFRESH_INTERVAL_MS = 6e4;
/**
* The panel's placement before its first measurement.
*
* `useAnchoredPosition` reports `null` until it has run, and the panel must be
* mounted for that run to read its real size. It renders at the origin and
* hidden for that one layout pass instead of not rendering at all, so the
* measurement and the first paint agree.
*/
const HIDDEN_POSITION = {
	left: 0,
	top: 0,
	visibility: "hidden"
};
/**
* The composer's usage chip.
*
* @param props - injected RPC caller, copy, and the session's provider reader.
* @returns the chip, or null when there is nothing true to say.
*/
function UsageChip({ call, t, isOwnProvider, accountBalance, ccSwitchBalance, useProjection }) {
	const [reading, setReading] = (0, react.useState)(void 0);
	const [accountReadings, setAccountReadings] = (0, react.useState)(void 0);
	const [balance, setBalance] = (0, react.useState)(void 0);
	const [ccBalance, setCcBalance] = (0, react.useState)(void 0);
	const [now, setNow] = (0, react.useState)(() => Date.now());
	const [open, setOpen] = (0, react.useState)(false);
	const lastOwn = (0, react.useRef)(void 0);
	const selected = providerOf(useProjection("modelSelection", (value) => value));
	const isDeepSeek = selected === "deepseek-account";
	const isBalanceProvider = selected !== void 0 && !isOwnProvider(selected) && !isDeepSeek;
	(0, react.useEffect)(() => {
		if (!isBalanceProvider || selected === void 0) {
			setCcBalance(void 0);
			return;
		}
		let live = true;
		const target = selected;
		const refresh = () => {
			ccSwitchBalance(target).then((value) => {
				if (live) setCcBalance(value === void 0 ? void 0 : {
					currency: value.currency,
					total: value.total
				});
			}).catch(() => {});
		};
		refresh();
		const timer = setInterval(refresh, REFRESH_INTERVAL_MS);
		return () => {
			live = false;
			clearInterval(timer);
		};
	}, [
		isBalanceProvider,
		selected,
		ccSwitchBalance
	]);
	const load = (0, react.useCallback)(async (target) => {
		try {
			return accountReadingsOf((await call("usage", { provider: target })).usage);
		} catch {
			return;
		}
	}, [call]);
	(0, react.useEffect)(() => {
		if (!isDeepSeek) {
			setBalance(void 0);
			return;
		}
		let live = true;
		const refresh = () => {
			accountBalance().then((value) => {
				if (live) setBalance(balanceOf(value));
			}).catch(() => {});
		};
		refresh();
		const timer = setInterval(refresh, REFRESH_INTERVAL_MS);
		return () => {
			live = false;
			clearInterval(timer);
		};
	}, [isDeepSeek, accountBalance]);
	(0, react.useEffect)(() => {
		const target = selected !== void 0 && isOwnProvider(selected) ? selected : lastOwn.current;
		if (target === void 0) {
			setAccountReadings(void 0);
			return;
		}
		let live = true;
		const refresh = () => {
			setNow(Date.now());
			load(target).then((next) => {
				if (!live || next === void 0) return;
				lastOwn.current = target;
				setAccountReadings(next);
			});
		};
		refresh();
		const timer = setInterval(refresh, REFRESH_INTERVAL_MS);
		return () => {
			live = false;
			clearInterval(timer);
		};
	}, [
		selected,
		isOwnProvider,
		load
	]);
	const rootRef = (0, react.useRef)(null);
	const panelRef = (0, react.useRef)(null);
	const setRootRef = (0, react.useCallback)((el) => {
		rootRef.current = el;
	}, []);
	const setPanelRef = (0, react.useCallback)((el) => {
		panelRef.current = el;
	}, []);
	const title = t("usageChipTitle", { provider: selected ?? "" });
	const panelLabel = t("usageChipFull", { provider: selected ?? "" });
	if (isBalanceProvider) {
		if (ccBalance === void 0) return null;
		const short$1 = shortLabel(selected ?? "", selected ?? "");
		return /* @__PURE__ */ (0, react_jsx_runtime.jsx)(ChipFrame, {
			open,
			setOpen,
			rootRef,
			panelRef,
			setRootRef,
			setPanelRef,
			title,
			panelLabel,
			cells: /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
				className: "dsh-subscription-usage-chip-cell",
				children: /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
					className: "dsh-subscription-usage-chip-value",
					children: `${ccBalance.currency === "CNY" ? "¥" : ccBalance.currency === "USD" ? "$" : `${ccBalance.currency} `}${ccBalance.total}`
				})
			}),
			panel: /* @__PURE__ */ (0, react_jsx_runtime.jsxs)(react_jsx_runtime.Fragment, { children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
				className: "dsh-subscription-usage-chip-full",
				children: panelLabel
			}), /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
				className: "dsh-subscription-usage-chip-meter",
				children: /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("span", {
					className: "dsh-subscription-usage-chip-meterHead",
					children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", { children: ccBalance.currency }), /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
						className: "dsh-subscription-usage-chip-detail",
						children: ccBalance.total
					})]
				})
			})] }),
			children: /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
				className: "dsh-subscription-usage-chip-label",
				children: short$1
			})
		});
	}
	if (isDeepSeek) {
		if (balance === void 0) return null;
		const short$1 = shortLabel("deepseek-account", "DeepSeek Account");
		return /* @__PURE__ */ (0, react_jsx_runtime.jsx)(ChipFrame, {
			open,
			setOpen,
			rootRef,
			panelRef,
			setRootRef,
			setPanelRef,
			title: t("usageChipTitle", { provider: "DeepSeek Account" }),
			panelLabel: t("usageChipFull", { provider: "DeepSeek Account" }),
			cells: balance.map((wallet) => /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
				className: "dsh-subscription-usage-chip-cell",
				children: /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
					className: "dsh-subscription-usage-chip-value",
					children: formatBalance(wallet)
				})
			}, `${wallet.currency}${wallet.bonus ? ":bonus" : ""}`)),
			panel: /* @__PURE__ */ (0, react_jsx_runtime.jsxs)(react_jsx_runtime.Fragment, { children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
				className: "dsh-subscription-usage-chip-full",
				children: t("usageChipFull", { provider: "DeepSeek Account" })
			}), balance.map((wallet) => /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
				className: "dsh-subscription-usage-chip-meter",
				children: /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("span", {
					className: "dsh-subscription-usage-chip-meterHead",
					children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", { children: `${wallet.bonus ? "Bonus · " : ""}${wallet.currency}` }), /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
						className: "dsh-subscription-usage-chip-detail",
						children: formatBalance(wallet)
					})]
				})
			}, `${wallet.currency}${wallet.bonus ? ":bonus" : ""}`))] }),
			children: /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
				className: "dsh-subscription-usage-chip-label",
				children: short$1
			})
		});
	}
	if (accountReadings === void 0 || accountReadings.length === 0) return null;
	const short = shortLabel(selected ?? "", selected ?? "");
	return /* @__PURE__ */ (0, react_jsx_runtime.jsx)(ChipFrame, {
		open,
		setOpen,
		rootRef,
		panelRef,
		setRootRef,
		setPanelRef,
		title,
		panelLabel,
		cells: mergeAccountWindows(accountReadings).map((window$1) => /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("span", {
			className: "dsh-subscription-usage-chip-cell",
			children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
				className: "dsh-subscription-usage-chip-window",
				children: shortWindow(window$1.label)
			}), /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
				className: "dsh-subscription-usage-chip-value",
				children: windowAmount(window$1)
			})]
		}, window$1.label)),
		panel: /* @__PURE__ */ (0, react_jsx_runtime.jsxs)(react_jsx_runtime.Fragment, { children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
			className: "dsh-subscription-usage-chip-full",
			children: panelLabel
		}), accountReadings.map((account) => /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("span", {
			className: "dsh-subscription-usage-chip-account",
			children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
				className: "dsh-subscription-usage-chip-meterHead",
				children: /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", { children: account.plan ?? account.account })
			}), account.windows.map((window$1) => {
				const windowReset = formatReset(window$1.resetsAt, now);
				return /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("span", {
					className: "dsh-subscription-usage-chip-meter",
					children: [/* @__PURE__ */ (0, react_jsx_runtime.jsxs)("span", {
						className: "dsh-subscription-usage-chip-meterHead",
						children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", { children: windowName(t, window$1.label) }), /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
							className: "dsh-subscription-usage-chip-detail",
							children: `${t("usageSpent", { value: windowAmount(window$1) })}${windowReset === void 0 ? "" : ` · ${windowReset}`}`
						})]
					}), window$1.usedPercent !== void 0 && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
						className: "dsh-subscription-usage-chip-track",
						"aria-hidden": "true",
						children: /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
							className: "dsh-subscription-usage-chip-fill",
							style: { width: `${String(Math.max(0, Math.min(100, window$1.usedPercent ?? 0)))}%` }
						})
					})]
				}, window$1.label);
			})]
		}, account.account))] }),
		children: /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
			className: "dsh-subscription-usage-chip-label",
			children: short
		})
	});
}
/**
* The chip's shared frame: trigger button plus its detail popover.
*
* The popover is a dialog, not a status line: it opens on a user gesture and
* carries per-account rows a reader can act on, so it renders through a portal
* anchored to the trigger (viewport-clamped, the same `useAnchoredPosition`
* the shell's own stat pills use) rather than an absolutely-positioned child
* of a centered flex row. Outside-pointer dismissal runs in the capture phase
* and Escape closes, with focus returning to the trigger on close.
*/
function ChipFrame({ open, setOpen, rootRef, panelRef, title, panelLabel, cells, panel, children, setRootRef, setPanelRef }) {
	const position = (0, __deepseek_ai_dsh_client_ui_primitives.useAnchoredPosition)({
		open,
		anchorRef: rootRef,
		panelRef,
		side: "top",
		align: "end",
		gap: 6,
		margin: 12
	});
	(0, __deepseek_ai_dsh_client_ui_primitives.useDismissOnOutsidePointer)(rootRef, open, (next) => setOpen(() => next), panelRef);
	(0, react.useEffect)(() => {
		if (!open) return;
		const onKey = (event) => {
			if (event.key === "Escape") {
				event.stopPropagation();
				setOpen(() => false);
				rootRef.current?.querySelector("button")?.focus();
			}
		};
		document.addEventListener("keydown", onKey, true);
		return () => {
			document.removeEventListener("keydown", onKey, true);
		};
	}, [
		open,
		setOpen,
		rootRef
	]);
	return /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("span", {
		className: "dsh-subscription-usage-chip",
		ref: setRootRef,
		children: [
			/* @__PURE__ */ (0, react_jsx_runtime.jsx)("style", { children: usageChipCss }),
			/* @__PURE__ */ (0, react_jsx_runtime.jsxs)("button", {
				type: "button",
				className: "dsh-subscription-usage-chip-trigger",
				"aria-haspopup": "dialog",
				"aria-expanded": open,
				title,
				onClick: () => {
					setOpen((was) => !was);
				},
				children: [children, cells]
			}),
			open && (0, react_dom.createPortal)(/* @__PURE__ */ (0, react_jsx_runtime.jsx)("div", {
				ref: setPanelRef,
				className: "dsh-subscription-usage-chip-panel",
				role: "dialog",
				"aria-label": panelLabel,
				style: position ?? HIDDEN_POSITION,
				children: panel
			}), document.body)
		]
	});
}

//#endregion
//#region src/client/FastModeButton.tsx
/**
* How many times a *loading* route is re-asked, and how long between attempts.
*
* The route answers `pending` while it has not read its listing, which is the only case
* worth asking again: a route that has read its listing and reports no variant means the
* model truly has none, and the button stays absent without further reads.
*/
const VARIANT_READ_ATTEMPTS = 6;
const VARIANT_READ_INTERVAL_MS = 1e3;
/**
* The quick-mode control, for the composer's row of standard controls.
*
* @param props - injected RPC caller, directory, and the session standard kit.
* @returns the button, or null when this model has no fast variant.
*/
function FastModeButton({ call, isOwnProvider, modelDirectory, t, useProjection }) {
	const [pair, setPair] = (0, react.useState)(void 0);
	const [failure, setFailure] = (0, react.useState)(void 0);
	const [pending, setPending] = (0, react.useState)(false);
	const selection = useProjection("modelSelection", (value) => value);
	const provider = providerOf(selection);
	const model = selection?.next?.model ?? selection?.lastUsed?.model;
	(0, react.useEffect)(() => {
		if (provider === void 0 || model === void 0 || !isOwnProvider(provider)) {
			setPair(void 0);
			return;
		}
		let live = true;
		let timer;
		let attempts = 0;
		const ask = () => {
			attempts += 1;
			call("model-variants", {
				provider,
				id: model
			}).then((result) => {
				if (!live) return;
				if (result?.fast !== void 0) {
					setPair({ rung: result.fastRung ?? "fast" });
					return;
				}
				setPair(void 0);
				if (result?.pending === true && attempts < VARIANT_READ_ATTEMPTS) timer = setTimeout(ask, VARIANT_READ_INTERVAL_MS);
			}).catch(() => {
				if (!live) return;
				setPair(void 0);
				if (attempts < VARIANT_READ_ATTEMPTS) timer = setTimeout(ask, VARIANT_READ_INTERVAL_MS);
			});
		};
		ask();
		return () => {
			live = false;
			if (timer !== void 0) clearTimeout(timer);
		};
	}, [
		provider,
		model,
		isOwnProvider,
		call
	]);
	(0, react.useEffect)(() => {
		setFailure(void 0);
	}, [model]);
	if (pair === void 0 || provider === void 0 || model === void 0) return null;
	const active = (selection?.next ?? selection?.lastUsed)?.reasoningEffort === pair.rung;
	const target = model;
	const base = active ? t("fastModeOn") : t("fastModeOff");
	const title = failure === void 0 ? base : `${base}\n${t("fastModeRefused")} — ${`${failure.code ?? ""} ${failure.message}`.trim()}`;
	return /* @__PURE__ */ (0, react_jsx_runtime.jsxs)(react_jsx_runtime.Fragment, { children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("style", { children: fastModeCss }), /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("button", {
		type: "button",
		className: "dsh-subscription-fast-mode",
		"data-active": active,
		"data-pending": pending,
		"data-failed": failure !== void 0,
		"aria-pressed": active,
		"aria-busy": pending,
		title,
		"aria-label": title,
		onClick: (event) => {
			event.stopPropagation();
			setPending(true);
			setFailure(void 0);
			const next = active ? {
				provider,
				model: target
			} : {
				provider,
				model: target,
				reasoningEffort: pair.rung
			};
			if (modelDirectory?.select === void 0) {
				setFailure({ message: t("fastModeUnavailable") });
				setPending(false);
				return;
			}
			Promise.resolve(modelDirectory.select(next)).then((result) => {
				const outcome = result;
				if (outcome?.ok !== false) return;
				const code = outcome.error?.code;
				const message = outcome.error?.message ?? code ?? "the host refused the selection";
				setFailure({
					...code === void 0 ? {} : { code },
					message
				});
				console.warn(`[subscription-oauth] fast mode ${provider}/${target} refused:`, outcome.error);
			}).catch((error) => {
				setFailure({ message: error instanceof Error ? error.message : String(error) });
				console.warn(`[subscription-oauth] fast mode ${provider}/${target} failed:`, error);
			}).finally(() => {
				setPending(false);
			});
		},
		children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("svg", {
			className: "dsh-subscription-fast-mode-bolt",
			viewBox: "0 0 24 24",
			width: "16",
			height: "16",
			"aria-hidden": "true",
			fill: "none",
			children: /* @__PURE__ */ (0, react_jsx_runtime.jsx)("path", {
				d: "M13 2 3.6 13.2a1.2 1.2 0 0 0 .92 1.98H11l-.86 6.42a1.2 1.2 0 0 0 2.2.7L21.4 10.6a1.2 1.2 0 0 0-.92-1.98H13l.86-6.42A1.2 1.2 0 0 0 13 2z",
				fill: "currentColor",
				stroke: "currentColor",
				strokeWidth: "1.2",
				strokeLinejoin: "round"
			})
		}), failure !== void 0 && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
			className: "dsh-subscription-fast-mode-reason",
			children: failure.code ?? failure.message
		})]
	})] });
}

//#endregion
//#region src/client/ProviderCardExtras.tsx
/** One metered window, as the settings cards and the composer chip read it. */
/** Compact reset text for one window (minutes, hours, then a date). */
function shortReset(at) {
	if (at === void 0) return void 0;
	const remaining = at - Date.now();
	if (remaining <= 0) return "now";
	const hours = remaining / 36e5;
	if (hours < 1) return `${String(Math.max(1, Math.round(remaining / 6e4)))}m`;
	if (hours < 24) return `${String(Math.round(hours))}h`;
	const days = Math.floor(hours / 24);
	return `${String(days)}d${String(Math.round(hours - days * 24))}h`;
}
/** Whether one status belongs to the route this card renders. */
function statusFor(status, provider) {
	return status.providers.find((entry) => entry.provider === provider);
}
/**
* One Models-page provider card's subscription block.
*
* Renders nothing while the route's status has not been read yet, so a cold
* page does not flash a wrong card.
*
* @param props - the keyed slot's owner share plus the injected RPC and copy.
* @returns the block, or null when there is no live state to show.
*/
function ProviderCardExtras({ call, t, provider }) {
	const [status, setStatus] = (0, react.useState)(void 0);
	const [usage, setUsage] = (0, react.useState)(void 0);
	const [busy, setBusy] = (0, react.useState)(false);
	const routeId = provider.provider;
	const refresh = (0, react.useCallback)(async () => {
		try {
			setStatus(await call("status", void 0));
		} catch {}
	}, [call]);
	const refreshUsage = (0, react.useCallback)(async () => {
		try {
			setUsage((await call("usage", { provider: routeId })).usage);
		} catch {}
	}, [call, routeId]);
	(0, react.useEffect)(() => {
		refresh();
		const timer = setInterval(() => {
			refresh();
		}, 6e4);
		return () => {
			clearInterval(timer);
		};
	}, [refresh]);
	(0, react.useEffect)(() => {
		refreshUsage();
		const timer = setInterval(() => {
			refreshUsage();
		}, 6e4);
		return () => {
			clearInterval(timer);
		};
	}, [refreshUsage]);
	const login = (0, react.useCallback)(() => {
		setBusy(true);
		call("login", { provider: routeId }).then(async (started) => {
			setStatus(started.status);
			if (started.prompt.mode === "open-url") window.open(started.prompt.url, "_blank", "noopener");
			await refresh();
		}).catch(() => {}).finally(() => {
			setBusy(false);
		});
	}, [
		call,
		routeId,
		refresh
	]);
	const cancel = (0, react.useCallback)(() => {
		setBusy(true);
		call("cancel", { provider: routeId }).then((next) => {
			setStatus(next);
		}).catch(() => {}).finally(() => {
			setBusy(false);
		});
	}, [call, routeId]);
	const route = status === void 0 ? void 0 : statusFor(status, routeId);
	if (route === void 0) return null;
	const loggedIn = route.loggedIn;
	const pending = route.busy && route.pending !== void 0;
	return /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
		className: "dsh-subscription-models-card",
		children: [
			/* @__PURE__ */ (0, react_jsx_runtime.jsx)("style", { children: providerCardCss }),
			/* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
				className: "dsh-subscription-models-card-head",
				children: [
					/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
						className: `dsh-subscription-models-card-dot${loggedIn ? " dsh-subscription-models-card-dot-live" : ""}${pending ? " dsh-subscription-models-card-dot-busy" : ""}`,
						"aria-hidden": "true"
					}),
					/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
						className: "dsh-subscription-models-card-name",
						children: route.displayName
					}),
					/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
						className: "dsh-subscription-models-card-state",
						children: loggedIn ? t("statusConnected", { count: route.accounts.length }) : pending ? t("statusBusy") : t("statusSignedOut")
					})
				]
			}),
			loggedIn && usage !== void 0 && usage.length > 0 && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("div", {
				className: "dsh-subscription-models-card-usage",
				children: usage.flatMap((account) => account.windows.map((window$1) => {
					const spent = window$1.usedPercent === void 0 ? void 0 : Math.round(window$1.usedPercent);
					const reset = shortReset(window$1.resetsAt);
					return /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
						className: "dsh-subscription-models-card-meter",
						children: [/* @__PURE__ */ (0, react_jsx_runtime.jsxs)("span", {
							className: "dsh-subscription-models-card-meterHead",
							children: [/* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", { children: account.plan ?? account.account }), /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
								className: "dsh-subscription-models-card-detail",
								children: `${t("usageSpent", { value: spent === void 0 ? "—" : `${String(spent)}%` })}${reset === void 0 ? "" : ` · ${reset}`}`
							})]
						}), window$1.usedPercent !== void 0 && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
							className: "dsh-subscription-models-card-track",
							"aria-hidden": "true",
							children: /* @__PURE__ */ (0, react_jsx_runtime.jsx)("span", {
								className: "dsh-subscription-models-card-fill",
								style: { width: `${String(Math.max(0, Math.min(100, window$1.usedPercent)))}%` }
							})
						})]
					}, `${account.account}:${window$1.label}`);
				}))
			}),
			route.connectable !== false && /* @__PURE__ */ (0, react_jsx_runtime.jsxs)("div", {
				className: "dsh-subscription-models-card-actions",
				children: [!route.busy && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
					type: "button",
					disabled: busy,
					onClick: login,
					children: route.accounts.length === 0 ? t("connect") : t("addAccount")
				}), route.busy && /* @__PURE__ */ (0, react_jsx_runtime.jsx)("button", {
					type: "button",
					disabled: busy,
					onClick: cancel,
					children: t("cancel")
				})]
			})
		]
	});
}

//#endregion
//#region src/client/index.ts
/**
* Routes this plugin serves.
*
* The chip only reports for these; a provider somebody else registered gets no chip,
* because this plugin has no way to read its allowance.
*/
const OWN_PROVIDER_IDS = new Set([
	"kimi",
	"grok",
	"antigravity",
	"glm",
	"ark",
	"mimo",
	"workbuddy"
]);
/** Dictionary namespace owned by this plugin. */
const NS = "settings.subscriptions";
/** Settings nav position: after the shipped sections, before plugin tooling. */
const SECTION_ORDER = 92;
/**
* Composer-dock position: after the shell's own stats pills, which sit at 0.
*
* The shell owns that bar and registers `tok/s` and the cache-hit pill into it, so the
* chip follows them rather than displacing them.
*/
const CHIP_ORDER = 10;
/**
* Composer-control position: ahead of the shell's own entries in that row, if it ever
* adds any, so the quick-mode button stays adjacent to the model selector it belongs to.
*/
const FAST_MODE_ORDER = 10;
/**
* Required services: `slots` carries the registration seat, `connection` the
* RPC caller, and `locale` the copy dictionaries.
*
* `modelDirectories` is deliberately NOT in this list: the fiber's `inject` is
* wait semantics, and the quick-mode button is the only consumer, which reads
* the resolver lazily with `ctx.get` (below) and renders nothing when the host
* exposes none. Declaring it would park the whole browser half on a service
* whose presence is not guaranteed, taking the settings page with it.
*/
const inject = [
	"slots",
	"connection",
	"locale"
];
/**
* Register the Subscriptions settings section.
*
* Registration goes through `slots.inject`, so the shell's own apply order
* relative to this one is unconstrained.
*
* @param ctx - client root context.
*/
function apply(ctx) {
	ctx.effect(() => ctx.locale.register(NS, {
		zh,
		en
	}), "dsh-plugin-subscription-oauth: copy dictionaries");
	const connection = ctx.get("connection");
	const t = ctx.locale.bind(NS);
	const call = createSubscriptionsCall(connection.rpc);
	const modelDirectories = ctx.get("modelDirectories");
	ctx.slots.inject("settings.section", () => ctx.slots.register({
		name: "settings.section",
		id: "subscriptions",
		order: SECTION_ORDER,
		label: () => t("nav"),
		inject: () => ({
			call,
			t
		})
	}, SubscriptionsSection));
	ctx.slots.inject("conversation.composer.dock", () => ctx.slots.register({
		name: "conversation.composer.dock",
		id: "subscription-usage",
		order: CHIP_ORDER,
		inject: () => ({
			call,
			t,
			isOwnProvider: (provider) => OWN_PROVIDER_IDS.has(provider),
			accountBalance: createAccountBalanceCall(connection.rpc),
			ccSwitchBalance: createCcSwitchBalanceCall(connection.rpc)
		})
	}, UsageChip));
	ctx.slots.inject("conversation.input.right", () => ctx.slots.register({
		name: "conversation.input.right",
		id: "subscription-fast-mode",
		order: FAST_MODE_ORDER,
		inject: (sessionId) => ({
			call,
			isOwnProvider: (provider) => OWN_PROVIDER_IDS.has(provider),
			t,
			modelDirectory: modelDirectoryFor(modelDirectories, sessionId)
		})
	}, FastModeButton));
	ctx.slots.inject("settings.models.provider-card", () => ctx.slots.register({
		name: "settings.models.provider-card",
		key: SETTINGS_NS,
		inject: () => ({
			call,
			t
		})
	}, ProviderCardExtras));
}
/**
* The session's model directory, in the shape the quick-mode button writes through.
*
* The lookup is lazy: the returned `select` resolves the host's directory on first use,
* not at inject time. The host's `directoryFor` throws while its own `remote.session`
* dependency is still starting (observed on desktop), so resolving eagerly at mount hid
* the button for the whole session even though the directory became ready a moment later.
*
* @param modelDirectories - the host's directory resolver, when it has one.
* @param sessionId - the session whose directory is wanted.
* @returns the writer, or undefined when there is no resolver or session at all.
*/
function modelDirectoryFor(modelDirectories, sessionId) {
	if (sessionId === void 0 || modelDirectories?.directoryFor === void 0) return void 0;
	return { select: async (selection) => {
		const resolve = () => modelDirectories.directoryFor?.(sessionId);
		let directory = (() => {
			try {
				return resolve();
			} catch {
				return;
			}
		})();
		if (directory?.select === void 0) {
			await new Promise((resolveWait) => setTimeout(resolveWait, 500));
			try {
				directory = resolve();
			} catch {
				directory = void 0;
			}
		}
		if (directory?.select === void 0) throw new Error("the model directory is not available yet; try again");
		return directory.select(selection);
	} };
}

//#endregion
exports.SubscriptionsRpcError = SubscriptionsRpcError;
exports.apply = apply;
exports.createSubscriptionsCall = createSubscriptionsCall;
exports.inject = inject;
return module.exports; } });
//# sourceMappingURL=client.js.map