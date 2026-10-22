package com.deepseek.dsh

import androidx.compose.runtime.Composable
import com.deepseek.dsh.plugin.nativehost.rightPaneTabDescriptors
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.slots.NativeSlot
import com.deepseek.dsh.plugin.nativesdk.slots.SlotContribution
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** 右侧栏插件标签 API 的纯函数部分：过滤、排序、稳定 key。 */
class RightPaneTabTest {

    private class FakeContribution(
        override val slot: NativeSlot,
        override val tabTitle: String?,
        override val order: Int,
    ) : SlotContribution() {
        override val supportedTypes: Set<String> = setOf("test")
        override val isOfficial: Boolean = false

        @Composable
        override fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) = Unit
    }

    @Test
    fun titledRightPaneTabsAreOrderedAndFiltered() {
        val descriptors = rightPaneTabDescriptors(
            listOf(
                FakeContribution(NativeSlot.RIGHT_PANEL, "B", 20),
                FakeContribution(NativeSlot.SIDEBAR_PANEL, "Left", 1),
                FakeContribution(NativeSlot.RIGHT_PANEL, null, 0),
                FakeContribution(NativeSlot.RIGHT_PANEL, "A", 10),
            ),
        )
        assertEquals(listOf("A", "B"), descriptors.map { it.second })
        assertEquals(descriptors.size, descriptors.map { it.first }.toSet().size)
    }
}
