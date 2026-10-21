package com.deepseek.dsh.plugin.nativehost

import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.pages.PageContent
import com.deepseek.dsh.plugin.nativesdk.pages.PageEntry
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class NativePageRegistryTest {
    private val page = TestPage("subscriptions")
    private val subscription = NativePageRegistration(
        pluginId = "changfenhuang.subscription",
        jsPackage = "dsh-plugin-subscription-oauth",
        pages = listOf(page),
    )

    @Test
    fun `auth package and section id resolve to native page`() {
        assertSame(
            page,
            NativePageRegistry.find(
                "dsh-plugin-subscription-oauth#subscriptions",
                listOf(subscription),
            ),
        )
    }

    @Test
    fun `native plugin id remains a valid component owner`() {
        assertSame(
            page,
            NativePageRegistry.find("changfenhuang.subscription#subscriptions", listOf(subscription)),
        )
    }

    @Test
    fun `scoped package version does not change its component identity`() {
        val scoped = subscription.copy(jsPackage = "@scope/subscription@1.2.3")
        assertSame(
            page,
            NativePageRegistry.find("@scope/subscription#subscriptions", listOf(scoped)),
        )
    }

    @Test
    fun `unmatched section or malformed component stays on web fallback`() {
        assertNull(NativePageRegistry.find("dsh-plugin-subscription-oauth#other", listOf(subscription)))
        assertNull(NativePageRegistry.find("dsh-plugin-subscription-oauth", listOf(subscription)))
        assertNull(NativePageRegistry.find("#subscriptions", listOf(subscription)))
    }

    @Test
    fun `rescan without loaded companion removes native route`() {
        NativePageRegistry.replaceAll(listOf(subscription))
        assertSame(page, NativePageRegistry.find("dsh-plugin-subscription-oauth#subscriptions"))

        NativePageRegistry.replaceAll(emptyList())

        assertNull(NativePageRegistry.find("dsh-plugin-subscription-oauth#subscriptions"))
    }

    @Test
    fun `loaded companion without matching page does not take over web ui`() {
        assertNull(
            NativePageRegistry.find(
                "dsh-plugin-subscription-oauth#subscriptions",
                listOf(subscription.copy(pages = emptyList())),
            ),
        )
    }

    private class TestPage(id: String) : PageContent {
        override val entry = PageEntry(id, id, placement = PageEntry.Placement.SETTINGS)
        @androidx.compose.runtime.Composable
        override fun Content(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) = Unit
    }
}
