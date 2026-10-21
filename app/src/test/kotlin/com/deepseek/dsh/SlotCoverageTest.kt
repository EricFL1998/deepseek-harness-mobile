package com.deepseek.dsh

import com.deepseek.dsh.core.model.DshSlotRegistry
import com.deepseek.dsh.plugin.nativesdk.slots.NativeSlotMapping
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 官方 slot 覆盖闸门：新增官方 slot 却忘记映射/显式降级时，这里必须失败。
 *
 * 数据来源：`DshSlotRegistry` 的常量（官方 slot 清单）+ `NativeSlotMapping`
 * 的显式映射表。测试不依赖界面，因此能在 UI 实现之前拦住遗漏。
 */
class SlotCoverageTest {

    private fun officialSlots(): Set<String> {
        return DshSlotRegistry::class.java.declaredFields
            .filter { field ->
                Modifier.isStatic(field.modifiers) && field.type == String::class.java
            }
            .map { field ->
                field.isAccessible = true
                field.get(null) as String
            }
            .toSet()
    }

    @Test
    fun everyOfficialSlotIsMappedOrExplicitlyUnsupported() {
        val official = officialSlots()
        val mapped = NativeSlotMapping.officialToNativeSlot.keys
        val unsupported = NativeSlotMapping.explicitlyUnsupported
        val uncovered = official - mapped - unsupported

        assertTrue(
            "官方 slot 未映射也未显式标记不支持：$uncovered",
            uncovered.isEmpty(),
        )
    }

    @Test
    fun mappingDoesNotReferenceUnknownSlots() {
        val official = officialSlots()
        val extra = NativeSlotMapping.allOfficialSlots - official

        assertTrue(
            "NativeSlotMapping 引用了 DshSlotRegistry 不存在的 slot：$extra",
            extra.isEmpty(),
        )
    }

    @Test
    fun mappedAndUnsupportedAreDisjoint() {
        val overlap = NativeSlotMapping.officialToNativeSlot.keys intersect
            NativeSlotMapping.explicitlyUnsupported

        assertEquals("同一 slot 不能既映射又不支持：$overlap", emptySet<String>(), overlap)
    }
}
