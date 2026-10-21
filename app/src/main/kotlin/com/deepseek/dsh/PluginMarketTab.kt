package com.deepseek.dsh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.data.MarketEntry
import com.deepseek.dsh.core.data.MarketIndexSummary
import com.deepseek.dsh.core.data.MarketListing
import com.deepseek.dsh.core.data.MarketVersion
import com.deepseek.dsh.core.designsystem.DshTextInput
import com.deepseek.dsh.core.designsystem.LocalDshPanelColors
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 插件市场的原生 UI 能用到的东西。
 *
 * @param loadIndexes 读取已配置的索引源。
 * @param search 在某个索引里搜索（`queryJson` 是官方 `MarketQuery`）。
 * @param saveIndexes 覆盖式保存索引源列表。
 * @param install 安装一个 spec（沿用插件页现有的安装器）。
 */
internal data class MarketHooks(
    val loadIndexes: suspend () -> Result<List<MarketIndexSummary>>,
    val search: suspend (url: String, queryJson: String) -> Result<MarketListing?>,
    val saveIndexes: suspend (List<String>) -> Result<Boolean>,
    val install: suspend (String) -> Result<String>,
)

/** 一页请求的条数：官方 `PAGE_SIZE = 50`。 */
private const val MARKET_PAGE_SIZE = 50

/**
 * 插件市场的数据来源（Host Remote）。安装动作由插件页现有的安装器承担，
 * 所以不在这里。
 */
public data class MarketBackend(
    val loadIndexes: suspend () -> Result<List<MarketIndexSummary>>,
    val search: suspend (url: String, queryJson: String) -> Result<MarketListing?>,
    val saveIndexes: suspend (List<String>) -> Result<Boolean>,
)

/**
 * 「发现」tab：浏览/安装用户自己配置的插件市场索引。
 *
 * 与网页版同一条数据链（Host `pluginMarket` Remote），文案取自官方
 * `client/ui-plugin-market/src/client/locales.ts` 的 `zh` 字典。排序/分类/标签
 * 这些次级筛选暂未做，默认按「有搜索词用相关度、否则索引顺序」请求。
 */
@Composable
internal fun PluginMarketTab(
    hooks: MarketHooks,
    onInstalled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDshPanelColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var indexes by remember { mutableStateOf<List<MarketIndexSummary>?>(null) }
    var indexError by remember { mutableStateOf<String?>(null) }
    var selectedUrl by remember { mutableStateOf<String?>(null) }
    var listing by remember { mutableStateOf<MarketListing?>(null) }
    var loading by remember { mutableStateOf(false) }
    var listingError by remember { mutableStateOf<String?>(null) }
    var queryText by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<MarketEntry?>(null) }
    var sourcesOpen by remember { mutableStateOf(false) }
    var draftUrl by remember { mutableStateOf("") }
    var sourceError by remember { mutableStateOf<String?>(null) }
    var installMessage by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    fun queryJson(text: String, offset: Int, fetchedAt: Long, refresh: Boolean): String =
        buildJsonObject {
            if (text.isNotBlank()) {
                put("text", text)
                put("sort", buildJsonObject { put("kind", "relevance") })
            } else {
                put("sort", buildJsonObject { put("kind", "index") })
            }
            put("offset", offset)
            put("limit", MARKET_PAGE_SIZE)
            if (fetchedAt > 0) put("fetchedAt", fetchedAt)
            if (refresh) put("refresh", true)
        }.toString()

    // 读取索引源：进入 tab 时一次，保存/重试时重来。
    LaunchedEffect(reloadKey) {
        loading = true
        indexError = null
        val result = hooks.loadIndexes()
        result.fold(
            onSuccess = { list ->
                indexes = list
                if (selectedUrl == null) selectedUrl = list.firstOrNull()?.url
            },
            onFailure = { indexes = emptyList(); indexError = context.getString(R.string.market_reading_index) },
        )
        loading = false
    }

    // 选中索引或搜索词变化时重查第一页。
    LaunchedEffect(selectedUrl, queryText, reloadKey) {
        val url = selectedUrl ?: return@LaunchedEffect
        if (queryText.isNotBlank()) delay(250)
        loading = true
        listingError = null
        hooks.search(url, queryJson(queryText, 0, 0, false)).fold(
            onSuccess = { listing = it; if (it == null) listingError = context.getString(R.string.market_no_results) },
            onFailure = { listing = null; listingError = context.getString(R.string.market_no_results) },
        )
        loading = false
    }

    if (detail != null) {
        MarketEntryDetail(
            entry = detail!!,
            onBack = { detail = null },
            installing = installing,
            onInstall = { version ->
                installing = version.version
                scope.launch {
                    val result = hooks.install(version.spec)
                    installing = null
                    installMessage = result.fold(
                        onSuccess = { context.getString(R.string.market_install_success, it) },
                        onFailure = { context.getString(R.string.market_install_failure, it.message ?: context.getString(R.string.market_no_results)) },
                    )
                    if (result.isSuccess) {
                        onInstalled()
                        reloadKey += 1
                    }
                }
            },
            modifier = modifier,
        )
        return
    }

    val current = indexes.orEmpty()
    Column(modifier = modifier.fillMaxSize()) {
        if (current.isEmpty() && !loading) {
            EmptyMarket(
                error = indexError,
                onAdd = { sourcesOpen = true; sourceError = null },
                onRetry = { reloadKey += 1 },
                modifier = Modifier.weight(1f),
            )
        } else {
            // 工具栏：市场选择 + 搜索。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PluginsDimens.PageHorizontal),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (current.size > 1) {
                    MarketChip(
                        text = current.firstOrNull { it.url == selectedUrl }?.let { marketLabel(it) }
                            ?: stringResource(R.string.market_sources),
                        onClick = { sourcesOpen = true },
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.bgModulePlatform)
                        .border(0.5.dp, colors.borderL2, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    if (queryText.isEmpty()) {
                        Text(stringResource(R.string.market_search_plugins), color = colors.labelTertiary, fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        singleLine = true,
                        textStyle = TextStyle(color = colors.labelPrimary, fontSize = 13.sp),
                        cursorBrush = SolidColor(colors.labelPrimary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MarketChip(text = stringResource(R.string.market_sources), onClick = { sourcesOpen = true; sourceError = null })
            }
            Spacer(Modifier.height(8.dp))
            installMessage?.let { message ->
                Text(
                    text = message,
                    color = colors.labelTertiary,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = PluginsDimens.PageHorizontal, vertical = 4.dp),
                )
            }
            val error = listingError
            when {
                error != null -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(PluginsDimens.PageHorizontal, 16.dp),
                ) {
                    Text(error, color = colors.labelSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    MarketChip(text = stringResource(R.string.market_retry), onClick = { reloadKey += 1 })
                }
                listing == null && loading -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(PluginsDimens.PageHorizontal, 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.market_reading_index), color = colors.labelTertiary, fontSize = 13.sp)
                }
                listing != null -> {
                    val entries = listing!!.entries
                    if (entries.isEmpty()) {
                        Text(
                            text = if (queryText.isBlank()) stringResource(R.string.market_index_empty) else stringResource(R.string.market_no_results),
                            color = colors.labelTertiary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(PluginsDimens.PageHorizontal, 16.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = PluginsDimens.PageHorizontal,
                                end = PluginsDimens.PageHorizontal,
                                top = 4.dp,
                                bottom = PluginsDimens.PageBottom,
                            ),
                        ) {
                            itemsIndexed(entries, key = { index, entry -> "market_${index}_${entry.name}" }) { index, entry ->
                                MarketEntryRow(
                                    entry = entry,
                                    showDivider = index != entries.lastIndex,
                                    onOpen = { detail = entry },
                                    installing = installing,
                                    onInstall = { version ->
                                        installing = version.version
                                        scope.launch {
                                            val result = hooks.install(version.spec)
                                            installing = null
                                            installMessage = result.fold(
                                                onSuccess = { context.getString(R.string.market_install_success, it) },
                                                onFailure = { context.getString(R.string.market_install_failure, it.message ?: context.getString(R.string.market_no_results)) },
                                            )
                                            if (result.isSuccess) {
                                                onInstalled()
                                                reloadKey += 1
                                            }
                                        }
                                    },
                                )
                            }
                            val nextOffset = listing!!.nextOffset
                            if (nextOffset != null) {
                                item(key = "market_more") {
                                    MarketChip(
                                        text = stringResource(R.string.market_show_more),
                                        onClick = {
                                            val url = selectedUrl ?: return@MarketChip
                                            val shown = listing ?: return@MarketChip
                                            scope.launch {
                                                loading = true
                                                hooks.search(
                                                    url,
                                                    queryJson(queryText, nextOffset, shown.fetchedAt, false),
                                                ).fold(
                                                    onSuccess = { page ->
                                                        if (page != null) {
                                                            listing = shown.copy(
                                                                entries = shown.entries + page.entries,
                                                                nextOffset = page.nextOffset,
                                                                fetchedAt = page.fetchedAt,
                                                            )
                                                        }
                                                    },
                                                    onFailure = { listingError = context.getString(R.string.market_no_results) },
                                                )
                                                loading = false
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (sourcesOpen) {
        MarketSourcesDialog(
            indexes = current,
            draft = draftUrl,
            error = sourceError,
            onDraftChange = { draftUrl = it; sourceError = null },
            onAdd = {
                val url = draftUrl.trim()
                sourceError = when {
                    !(url.startsWith("http://") || url.startsWith("https://")) ->
                        context.getString(R.string.market_invalid_source_url)
                    current.any { it.url == url } -> context.getString(R.string.market_duplicate_source)
                    else -> null
                }
                if (sourceError == null) {
                    scope.launch {
                        val next = current + MarketIndexSummary(url = url, title = null, error = null)
                        hooks.saveIndexes(next.map { it.url }).fold(
                            onSuccess = {
                                draftUrl = ""
                                sourcesOpen = false
                                selectedUrl = url
                                reloadKey += 1
                            },
                            onFailure = { sourceError = context.getString(R.string.market_no_results) },
                        )
                    }
                }
            },
            onRemove = { target ->
                scope.launch {
                    val next = current.filterNot { it.url == target.url }
                    hooks.saveIndexes(next.map { it.url }).fold(
                        onSuccess = {
                            if (next.isEmpty()) {
                                selectedUrl = null
                                sourcesOpen = false
                            } else if (selectedUrl == target.url) {
                                selectedUrl = next.first().url
                            }
                            reloadKey += 1
                        },
                        onFailure = { sourceError = context.getString(R.string.market_no_results) },
                    )
                }
            },
            onDismiss = { sourcesOpen = false },
        )
    }
}

/** 市场的展示名：最近读取到的标题，否则 URL。 */
private fun marketLabel(index: MarketIndexSummary): String =
    index.title?.takeIf { it.isNotBlank() } ?: index.url

/** 空态：还没有索引源（或读取失败）。 */
@Composable
private fun EmptyMarket(
    error: String?,
    onAdd: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDshPanelColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(PluginsDimens.PageHorizontal, 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (error != null) stringResource(R.string.market_reading_index) else stringResource(R.string.market_no_market),
            color = colors.labelPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (error != null) {
                stringResource(R.string.market_no_service)
            } else {
                stringResource(R.string.market_add_index_hint)
            },
            color = colors.labelTertiary,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MarketChip(text = if (error != null) stringResource(R.string.market_retry) else stringResource(R.string.market_add_source), onClick = if (error != null) onRetry else onAdd)
        }
    }
}

/** 一个市场卡片行：图标 + 标题/标签/说明/元信息 + 动作。 */
@Composable
private fun MarketEntryRow(
    entry: MarketEntry,
    showDivider: Boolean,
    onOpen: () -> Unit,
    installing: String?,
    onInstall: (MarketVersion) -> Unit,
) {
    val colors = LocalDshPanelColors.current
    val version = entry.presentedVersion
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpen)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgModulePlatform),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.title.take(1),
                    color = colors.labelPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = entry.title,
                    color = colors.labelPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val description = entry.description
                if (description != null) {
                    Text(
                        text = description,
                        color = colors.labelTertiary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val meta = buildString {
                    append(version?.version ?: "—")
                    entry.author?.let { append(" · ").append(it) }
                }
                Text(
                    text = meta,
                    color = colors.labelTertiary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                )
            }
            when {
                entry.installationStatus != "not-installed" -> MarketChip(text = stringResource(R.string.market_installed), onClick = onOpen)
                version != null && version.compatible -> MarketChip(
                    text = if (installing == version.version) stringResource(R.string.market_installing) else stringResource(R.string.market_install),
                    onClick = { if (installing == null) onInstall(version) },
                )
                else -> Text(stringResource(R.string.market_incompatible), color = colors.labelTertiary, fontSize = 11.sp)
            }
        }
        if (showDivider) {
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.borderL4))
        }
    }
}

/** 插件详情页：说明、来源信息、版本列表。 */
@Composable
private fun MarketEntryDetail(
    entry: MarketEntry,
    onBack: () -> Unit,
    installing: String?,
    onInstall: (MarketVersion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDshPanelColors.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = PluginsDimens.PageHorizontal,
            end = PluginsDimens.PageHorizontal,
            top = PluginsDimens.PageTop,
            bottom = PluginsDimens.PageBottom,
        ),
    ) {
        item(key = "detail_crumb") {
            Text(
                text = stringResource(R.string.market_discover),
                color = colors.labelTertiary,
                fontSize = 12.5.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(vertical = 4.dp),
            )
        }
        item(key = "detail_head") {
            Spacer(Modifier.height(12.dp))
            Text(entry.title, color = colors.labelPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            entry.presentedVersion?.let { version ->
                Spacer(Modifier.height(4.dp))
                Text("v${version.version}", color = colors.labelTertiary, fontSize = 12.sp)
            }
            Text(entry.name, color = colors.labelTertiary, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp)
            val detailDescription = entry.description
            if (detailDescription != null) {
                Spacer(Modifier.height(10.dp))
                Text(detailDescription, color = colors.labelSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
        item(key = "detail_source") {
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.market_source), color = colors.labelPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            entry.author?.let { DetailFactRow(stringResource(R.string.market_author), it) }
            entry.homepage?.let { DetailFactRow(stringResource(R.string.market_homepage), it, monospace = true) }
            DetailFactRow(stringResource(R.string.market_package), entry.name, monospace = true)
        }
        item(key = "detail_versions") {
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.market_version), color = colors.labelPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        itemsIndexed(entry.versions, key = { index, version -> "market_version_${index}_${version.version}" }) { _, version ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("v${version.version}", color = colors.labelPrimary, fontSize = 13.sp)
                    if (!version.compatible) {
                        Text(
                            text = stringResource(R.string.market_incompatible_version, version.requirement ?: stringResource(R.string.market_version)),
                            color = colors.labelTertiary,
                            fontSize = 11.sp,
                        )
                    }
                }
                if (version.compatible) {
                    MarketChip(
                        text = if (installing == version.version) stringResource(R.string.market_installing) else stringResource(R.string.market_install),
                        onClick = { if (installing == null) onInstall(version) },
                    )
                }
            }
        }
    }
}

/** 索引源管理对话框。 */
@Composable
private fun MarketSourcesDialog(
    indexes: List<MarketIndexSummary>,
    draft: String,
    error: String?,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (MarketIndexSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalDshPanelColors.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.bgLayer2)
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.market_sources), color = colors.labelPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.market_sources_desc_long),
                color = colors.labelTertiary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            indexes.forEach { index ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(index.title ?: index.url, color = colors.labelPrimary, fontSize = 12.5.sp)
                        Text(index.url, color = colors.labelTertiary, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    MarketChip(text = stringResource(R.string.market_remove), onClick = { onRemove(index) })
                }
            }
            Spacer(Modifier.height(8.dp))
            DshTextInput(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = "https://example.com/index.json",
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text(error, color = colors.stateErrorPrimary, fontSize = 11.5.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.market_third_party_note),
                color = colors.labelTertiary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MarketChip(text = stringResource(R.string.market_cancel), onClick = onDismiss)
                MarketChip(text = stringResource(R.string.market_add), onClick = onAdd)
            }
        }
    }
}

/** 小胶囊按钮（插件页同一套视觉）。 */
@Composable
private fun MarketChip(text: String, onClick: () -> Unit) {
    val colors = LocalDshPanelColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(colors.bgModulePlatform)
            .border(0.5.dp, colors.borderL2, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = text, color = colors.labelSecondary, fontSize = 12.sp)
    }
}
