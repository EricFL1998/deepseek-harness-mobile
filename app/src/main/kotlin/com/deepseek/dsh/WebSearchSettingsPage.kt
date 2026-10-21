package com.deepseek.dsh

/**
 * 「网页搜索」页的字段表。
 *
 * 逐条对照官方 `@deepseek-ai/dsh-client-ui-settings-web-search` 的 `WebSearchCard`：
 *
 * - 开关「启用新版 Web 搜索 API」写 `web` 命名空间的 `searchProvider` /
 *   `fetchProvider`（`deepseek-search-api`+`deepseek-fetch-api` 或
 *   `deepseek-official`+`http`）；
 * - 开关关闭时编辑 `web-search-deepseek` 的 `baseURL`（Messages API 基础地址）与
 *   `maxUses`（单次请求最多搜索次数）；
 * - 开关打开时编辑 `web-deepseek-api` 的 `baseURL`（Search / Fetch API 基础地址）与
 *   `count`（每次搜索结果数）。
 *
 * 文案取自该包 `lib/client.js` 里的 `zh` 字典。官方页面还有一个 API Key 控件，
 * 它通过 Host 的凭据域写入、不落设置文件；bridge 没有凭据接口，所以原生页不画
 * 一个改不动的输入框，密钥仍在网页版/Host 侧维护。
 */
internal val WEB_SEARCH_SETTINGS_PAGE: OfficialPageSpec = OfficialPageSpec(
    title = "网页搜索",
    description = "设置 DeepSeek 的搜索提供方。",
    searchApiToggle = true,
    fields = listOf(
        OfficialField(
            ns = "web-search-deepseek",
            key = "baseURL",
            label = "Messages API 基础地址",
            hint = "填写含 API 前缀的地址，例如 https://api.deepseek.com/anthropic/v1；程序追加 /messages，留空恢复默认。",
            kind = OfficialFieldKind.TEXT,
        ),
        OfficialField(
            ns = "web-search-deepseek",
            key = "maxUses",
            label = "单次请求最多搜索次数",
            hint = "辅助模型在一次 Messages 请求内最多可执行的搜索次数，仅适用于 Messages API。",
            kind = OfficialFieldKind.NUMBER,
        ),
        OfficialField(
            ns = "web-deepseek-api",
            key = "baseURL",
            label = "Search / Fetch API 基础地址",
            hint = "填写含 API 前缀的地址，例如 https://api.deepseek.com/v1/tools；程序追加 /search 或 /fetch，留空恢复默认。",
            kind = OfficialFieldKind.TEXT,
        ),
        OfficialField(
            ns = "web-deepseek-api",
            key = "count",
            label = "每次搜索结果数",
            hint = "每个查询返回的结果条数，须为 1–50 的整数，默认 10；不控制搜索调用次数。",
            kind = OfficialFieldKind.NUMBER,
        ),
    ),
    namespaces = listOf("web", "web-search-deepseek", "web-deepseek-api"),
)
