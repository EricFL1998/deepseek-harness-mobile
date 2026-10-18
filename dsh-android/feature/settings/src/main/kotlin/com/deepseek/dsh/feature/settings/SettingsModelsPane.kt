package com.deepseek.dsh.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 模型（右侧面板 ModelsSection）。
 *
 * 结构：标题「模型」16/24 500 + 引导语 14/22 + 提供商卡片（R20、0.5dp 描边、
 * 名称 14/500、账号状态绿色圆点 13、模型列表）+ 虚线「添加模型提供商」占位按钮。
 */
@Composable
internal fun SettingsModelsPane(
    models: List<String> = listOf("DeepSeek-V3", "DeepSeek-R1"),
    onAddProvider: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = LocalSettingsPalette.current
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
        item(key = "models_title") {
            SettingsPaneTitle(text = "模型", large = false)
        }
        item(key = "models_intro") {
            SettingsIntro(text = "填入各提供商的 API 密钥即可使用其模型。", large = true)
        }
        item(key = "models_provider") {
            // 列表在引导语之后额外留 12dp 呼吸，随后才是提供商卡片（gap 8）。
            Column(modifier = Modifier.padding(top = 14.dp)) {
                SettingsCard {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "DeepSeek 账号",
                                style = SettingsText.RowTitleMedium,
                                color = palette.labelPrimary,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SettingsStatusDot(color = palette.success)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "已登录",
                                    style = SettingsText.AccountBody,
                                    color = palette.success,
                                )
                            }
                        }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            models.forEachIndexed { index, model ->
                                if (index > 0) {
                                    SettingsCardDivider()
                                }
                                ModelRow(name = model)
                            }
                        }
                    }
                }
            }
        }
        item(key = "models_add") {
            SettingsDashedButton(
                text = "添加模型提供商",
                onClick = onAddProvider,
            )
        }
    }
}

/** 提供商卡片内的模型行：名称 14/22 + 右侧启用对勾。 */
@Composable
private fun ModelRow(name: String) {
    val palette = LocalSettingsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = SettingsText.RowTitle,
            color = palette.labelPrimary,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = palette.labelPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}
