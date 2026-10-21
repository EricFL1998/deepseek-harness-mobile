package com.deepseek.dsh.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * 插件市场的数据模型与解析。
 *
 * 官方「发现」页读的是 Host 的 `pluginMarket` Remote（`listIndexes` / `search` /
 * `setIndexes`），索引格式由 `@deepseek-ai/dsh-plugin-market-index` 定义。这里只
 * 保留原生列表/详情真正要用的字段，其余字段原样忽略。
 */

/** 一个索引源的摘要。 */
data class MarketIndexSummary(
    val url: String,
    /** 最近一次成功读取的市场标题；失败或未读时为 null。 */
    val title: String?,
    /** 最近一次读取失败的原因。 */
    val error: String?,
)

/** 一个可安装版本。 */
data class MarketVersion(
    val version: String,
    /** 交给安装器的 spec（npm 为 `name@version`，git 为 `git+...`）。 */
    val spec: String,
    val registry: String?,
    /** 发布时间（Unix 秒）。 */
    val publishTimestamp: Long,
    val compatible: Boolean,
    /** 不兼容时要求的 DSH 版本范围。 */
    val requirement: String?,
)

/** 市场里的一条插件。 */
data class MarketEntry(
    val name: String,
    val title: String,
    val description: String?,
    val author: String?,
    val homepage: String?,
    val labels: List<String>,
    val versions: List<MarketVersion>,
    val defaultVersion: String?,
    /** `not-installed` / `installed` / `update-available` / `different-version` / `provided`。 */
    val installationStatus: String,
) {
    /** 列表里展示的版本：默认版本，否则第一个。 */
    val presentedVersion: MarketVersion?
        get() = versions.firstOrNull { it.version == defaultVersion } ?: versions.firstOrNull()
}

/** 一页搜索结果。 */
data class MarketListing(
    val url: String,
    val title: String?,
    val entries: List<MarketEntry>,
    val total: Int,
    val nextOffset: Int?,
    /** 回答这一页的文档时间；翻页时用它钉住同一份文档。 */
    val fetchedAt: Long,
)

/** 解析 `plugin-market/listIndexes` 的 `value`。 */
internal fun parseMarketIndexes(value: JsonElement?): List<MarketIndexSummary> {
    val array = value as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val url = obj.string("url") ?: return@mapNotNull null
        MarketIndexSummary(
            url = url,
            title = localized(obj["title"]),
            error = obj.string("error"),
        )
    }
}

/** 解析 `plugin-market/search` 的 `value`（可能为空：索引尚未读取成功时没有数据）。 */
internal fun parseMarketSearch(value: JsonElement?): MarketListing? {
    val obj = value as? JsonObject ?: return null
    val url = obj.string("url") ?: return null
    val entries = (obj["entries"] as? JsonArray).orEmpty().mapNotNull { parseEntry(it) }
    return MarketListing(
        url = url,
        title = localized(obj["title"]),
        entries = entries,
        total = obj.int("total") ?: entries.size,
        nextOffset = (obj["nextOffset"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
        fetchedAt = obj["fetchedAt"]?.let { (it as? JsonPrimitive)?.longOrNull } ?: 0L,
    )
}

private fun parseEntry(element: JsonElement): MarketEntry? {
    val obj = element as? JsonObject ?: return null
    val name = obj.string("name") ?: return null
    val versions = (obj["versions"] as? JsonArray).orEmpty().mapNotNull { parseVersion(it) }
    val installation = obj["installation"] as? JsonObject
    return MarketEntry(
        name = name,
        title = localized(obj["title"]) ?: name,
        description = localized(obj["description"]),
        author = obj.string("author"),
        homepage = obj.string("homepage"),
        labels = (obj["labels"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .map { label -> label },
        versions = versions,
        defaultVersion = obj.string("defaultVersion"),
        installationStatus = installation?.string("status") ?: "not-installed",
    )
}

private fun parseVersion(element: JsonElement): MarketVersion? {
    val obj = element as? JsonObject ?: return null
    val version = obj.string("version") ?: return null
    val install = obj["install"] as? JsonObject
    val spec = install?.string("spec") ?: return null
    val compatibility = obj["compatibility"] as? JsonObject
    val compatible = (compatibility?.get("compatible") as? JsonPrimitive)?.booleanOrNull ?: true
    val requirements = compatibility?.get("requirements") as? JsonObject
    return MarketVersion(
        version = version,
        spec = spec,
        registry = install.string("registry"),
        publishTimestamp = obj["publishTimestamp"]?.let { (it as? JsonPrimitive)?.longOrNull } ?: 0L,
        compatible = compatible,
        requirement = requirements?.string("dsh"),
    )
}

/** 一个 `LocalizedText`（字符串或 `{en, zh, ...}`）取中文优先的展示文本。 */
internal fun localized(element: JsonElement?): String? {
    when (element) {
        null -> return null
        is JsonPrimitive -> return element.contentOrNull?.takeIf { it.isNotBlank() }
        is JsonObject -> {
            for (key in listOf("zh", "zh-CN", "zh-Hans", "en")) {
                val text = element.string(key)?.takeIf { it.isNotBlank() }
                if (text != null) return text
            }
            return element.values.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .firstOrNull { it.isNotBlank() }
        }
        else -> return null
    }
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
