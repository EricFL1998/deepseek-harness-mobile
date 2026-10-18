package com.deepseek.dsh.plugin.mobileui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SduiValidatorTest {

    @Test
    fun testValidSduiBudget() {
        val page = SduiPage(
            title = "正常页面",
            children = listOf(
                SduiSection(
                    title = "基础信息",
                    children = listOf(
                        SduiText(text = "Hello SDUI"),
                        SduiButton(label = "Click", action = SduiAction.Copy("test"))
                    )
                )
            )
        )

        val result = SduiValidator.validate(page, rawSize = 1024)
        assertTrue(result.isSuccess)
    }

    @Test
    fun testExceedDepthBudget() {
        var deepest: SduiNode = SduiText(text = "Deep")
        for (i in 0..10) {
            deepest = SduiColumn(children = listOf(deepest))
        }
        val page = SduiPage(title = "超深页面", children = listOf(deepest))

        val result = SduiValidator.validate(page, rawSize = 2048)
        assertFalse(result.isSuccess)
    }

    @Test
    fun testExceedSizeBudget() {
        val page = SduiPage(title = "超大规格", children = emptyList())
        val result = SduiValidator.validate(page, rawSize = 300 * 1024)
        assertFalse(result.isSuccess)
    }
}
