package com.deepseek.dsh.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * 归档会话（右侧面板 archived-sessions 分区）。
 *
 * 该分区由会话插件注册（当前 dsh-upstream 内置包未提供实现），
 * 这里沿用分区骨架：标题 18/24 600 + 引导语 13/20 + 空态文案。
 */
@Composable
internal fun SettingsArchivedPane(
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SettingsPaneDimens.Horizontal,
            end = SettingsPaneDimens.Horizontal,
            top = SettingsPaneDimens.Top,
            bottom = SettingsPaneDimens.Bottom,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "archived_title") {
            SettingsPaneTitle(text = stringResource(R.string.settings_section_archived))
        }
        item(key = "archived_intro") {
            SettingsIntro(text = stringResource(R.string.settings_archived_desc))
        }
        item(key = "archived_empty") {
            SettingsEmptyState(text = stringResource(R.string.settings_archived_empty))
        }
    }
}
