package com.deepseek.dsh

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.deepseek.dsh.core.model.ModelProviderGroup
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 上次读到的模型目录缓存（冷启动时先让模型菜单可用）。 */
data class CachedModelCatalog(
    val groups: List<ModelProviderGroup>,
    val names: List<String>,
    val ids: List<String>,
)

/** 模型目录缓存的可观察状态；读写都集中在这里，主 Composable 不再持有三段状态。 */
class ModelCatalogCacheState(initial: CachedModelCatalog) {
    var groups by mutableStateOf(initial.groups)
    var names by mutableStateOf(initial.names)
    var ids by mutableStateOf(initial.ids)
}

private const val PREFS = "dsh_model_catalog_cache"

/** 从 SharedPreferences 读一次缓存；损坏的数据退化为空。 */
fun readCachedModelCatalog(context: Context): CachedModelCatalog {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val groups = runCatching {
        Json.decodeFromString(
            ListSerializer(ModelProviderGroup.serializer()),
            prefs.getString("groups", "[]").orEmpty(),
        )
    }.getOrDefault(emptyList())
    val names = prefs.getString("names", "").orEmpty()
        .split('\u0001').filter { it.isNotBlank() }
    val ids = prefs.getString("ids", "").orEmpty()
        .split('\u0001').filter { it.isNotBlank() }
    return CachedModelCatalog(groups, names, ids)
}

/** 写回 provider 分组缓存。 */
fun writeCachedModelGroups(context: Context, groups: List<ModelProviderGroup>) {
    runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(
            "groups",
            Json.encodeToString(ListSerializer(ModelProviderGroup.serializer()), groups),
        ).apply()
    }
}

/** 写回模型名/ID 缓存。 */
fun writeCachedModelNames(context: Context, names: List<String>, ids: List<String>) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString("names", names.joinToString("\u0001"))
        .putString("ids", ids.joinToString("\u0001"))
        .apply()
}
