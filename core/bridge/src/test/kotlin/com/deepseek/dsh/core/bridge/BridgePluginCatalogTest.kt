package com.deepseek.dsh.core.bridge

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 插件目录归一测试。
 *
 * 覆盖设计文档要求的降级顺序：官方 slot 注册优先，Host 原生清单兜底，两者都缺
 * 时是明确的空目录（`source = NONE`），绝不伪造分类。
 */
class BridgePluginCatalogTest {

    /** 用固定响应队列构造客户端；按顺序消费，队列耗尽返回 500。 */
    private fun clientWith(vararg responses: Pair<Int, String>): OkHttpClient {
        val queue = responses.toMutableList()
        return OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val (code, body) = if (queue.isEmpty()) 500 to "{}" else queue.removeAt(0)
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("fixture")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }).build()
    }

    private val manifestAllProviders = """
        { "bridgeVersion": "1.0.0", "dshVersion": "0.2.1-rc.2",
          "providers": [
            { "id": "client-slots", "version": 1, "capabilities": ["list", "icon-svg", "render-tree"], "readOnly": true },
            { "id": "package-inventory", "version": 1, "capabilities": ["bundles", "rows", "meta-icon"], "readOnly": true }
          ] }
    """.trimIndent()

    private val manifestInventoryOnly = """
        { "bridgeVersion": "1.0.0", "dshVersion": "0.2.1-rc.2",
          "providers": [
            { "id": "package-inventory", "version": 1, "capabilities": ["bundles", "rows", "meta-icon"], "readOnly": true }
          ] }
    """.trimIndent()

    private val manifestNoProviders = """
        { "bridgeVersion": "1.0.0", "dshVersion": null, "providers": [] }
    """.trimIndent()

    private val slotListBody = """
        { "ok": true, "provider": "client-slots", "version": 1, "warnings": [],
          "data": { "slot": "plugins.item",
                    "registrations": [
                      { "slot": "plugins.item", "id": "shell", "order": 40, "label": "终端",
                        "package": "@deepseek-ai/dsh-client-ui-settings-shell", "locale": "settings.shell",
                        "render": { "kind": "component", "componentId": "shell" } },
                      { "slot": "plugins.item", "id": "agent-loop", "order": 30, "label": "Agent 循环",
                        "package": "@deepseek-ai/dsh-client-ui-settings-agent-loop", "locale": "settings.agentLoop",
                        "render": { "kind": "component", "componentId": "agent-loop" } }
                    ],
                    "emptySlots": [] } }
    """.trimIndent()

    private val shellSvg = "<svg viewBox=\"0 0 24 24\"><path d=\"M1 1\" /></svg>"
    private val loopSvg = "<svg viewBox=\"0 0 24 24\"><circle r=\"2\" /></svg>"

    private fun iconBody(componentId: String, svg: String): String = """
        { "ok": true, "provider": "client-slots", "version": 1, "warnings": [],
          "data": { "componentId": "$componentId", "size": 40, "svg": ${jsonQuote(svg)} } }
    """.trimIndent()

    /** 把一段文本编成 JSON 字符串字面量，避免测试里手写转义。 */
    private fun jsonQuote(text: String): String = buildString {
        append('"')
        for (character in text) {
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                else -> append(character)
            }
        }
        append('"')
    }

    private val inventoryBody = """
        { "ok": true, "provider": "package-inventory", "version": 1, "warnings": [],
          "data": { "managementAvailable": true,
                    "bundles": [
                      { "name": "dsh-purge", "official": false, "optional": false, "removable": true,
                        "enabled": true, "installed": true, "availability": "profile",
                        "version": "1.1.64", "title": "dsh-purge", "description": "Clean-up plugin",
                        "icon": "data:image/png;base64,AAAA",
                        "rows": [ { "rowId": "purge", "moduleName": "dsh-purge", "title": "purge" } ] }
                    ] } }
    """.trimIndent()

    @Test
    fun `client slots drive the categories and their artwork`() = runBlocking {
        // 调用顺序：manifest → client-slots/list → package-inventory/bundles → 每个分类的图标
        // （图标按已排序的注册顺序取，因此 agent-loop 先于 shell）。
        val client = BridgeClient(
            "http://127.0.0.1:43121",
            clientWith(
                200 to manifestAllProviders,
                200 to slotListBody,
                200 to inventoryBody,
                200 to iconBody("agent-loop", loopSvg),
                200 to iconBody("shell", shellSvg),
            ),
        )
        val registry = BridgeRegistry(client)
        val result = BridgePluginCatalog.load(registry)
        val catalog = result.catalog
        assertEquals(BridgePluginCatalogSource.CLIENT_SLOTS, catalog.source)
        // 官方 order 决定顺序，客户端不重新排序。
        assertEquals(listOf("agent-loop", "shell"), catalog.categories.map { it.id })
        assertEquals(listOf("Agent 循环", "终端"), catalog.categories.map { it.label })
        assertEquals("@deepseek-ai/dsh-client-ui-settings-shell", catalog.categories[1].packageName)
        assertEquals("settings.agentLoop", catalog.categories[0].locale)
        assertTrue(catalog.categories[0].iconSvg!!.contains("<circle"))
        assertTrue(catalog.categories[1].iconSvg!!.contains("M1 1"))
        // bundle 清单照常可用，因此第三方插件仍然呈现。
        assertEquals(listOf("dsh-purge"), catalog.bundles.map { it.name })
        assertEquals("data:image/png;base64,AAAA", catalog.bundles[0].iconDataUrl)
    }

    @Test
    fun `a missing slot provider falls back to the host inventory`() = runBlocking {
        val client = BridgeClient(
            "http://127.0.0.1:43121",
            clientWith(200 to manifestInventoryOnly, 200 to inventoryBody),
        )
        val registry = BridgeRegistry(client)
        val catalog = BridgePluginCatalog.load(registry).catalog
        assertEquals(BridgePluginCatalogSource.PACKAGE_INVENTORY, catalog.source)
        assertTrue(catalog.categories.isEmpty())
        assertEquals(1, catalog.bundles.size)
        assertTrue(catalog.managementAvailable)
    }

    @Test
    fun `no provider at all yields an explicit empty catalog`() = runBlocking {
        val client = BridgeClient(
            "http://127.0.0.1:43121",
            clientWith(200 to manifestNoProviders),
        )
        val registry = BridgeRegistry(client)
        val result = BridgePluginCatalog.load(registry)
        assertEquals(BridgePluginCatalogSource.NONE, result.catalog.source)
        assertTrue(result.catalog.categories.isEmpty())
        assertTrue(result.catalog.bundles.isEmpty())
        assertTrue(!result.catalog.managementAvailable)
    }

    @Test
    fun `an icon failure keeps the category and records why`() = runBlocking {
        // 顺序同上：agent-loop 的图标取到，shell 的取不到。
        val client = BridgeClient(
            "http://127.0.0.1:43121",
            clientWith(
                200 to manifestAllProviders,
                200 to slotListBody,
                200 to inventoryBody,
                200 to iconBody("agent-loop", loopSvg),
                200 to """{ "ok": false, "error": { "code": "args/invalid", "message": "no captured component shell" } }""",
            ),
        )
        val registry = BridgeRegistry(client)
        val result = BridgePluginCatalog.load(registry)
        assertEquals(2, result.catalog.categories.size)
        assertTrue(result.catalog.categories[0].iconSvg!!.contains("<circle"))
        assertNull(result.catalog.categories[1].iconSvg)
        assertTrue(result.warnings.any { it.contains("icon shell") })
    }

    @Test
    fun `a slot provider failure degrades to inventory with a warning`() = runBlocking {
        val client = BridgeClient(
            "http://127.0.0.1:43121",
            clientWith(
                200 to manifestAllProviders,
                200 to """{ "ok": false, "error": { "code": "provider/unavailable", "message": "no client module registry" } }""",
                200 to inventoryBody,
            ),
        )
        val registry = BridgeRegistry(client)
        val result = BridgePluginCatalog.load(registry)
        assertEquals(BridgePluginCatalogSource.PACKAGE_INVENTORY, result.catalog.source)
        assertTrue(result.warnings.any { it.contains("client-slots unavailable") })
    }

    @Test
    fun `bundles keep their rows, versions, and error codes`() = runBlocking {
        val body = """
            { "ok": true, "provider": "package-inventory", "version": 1, "warnings": [],
              "data": { "managementAvailable": true,
                        "bundles": [
                          { "name": "broken", "official": true, "optional": true, "removable": false,
                            "enabled": false, "installed": false, "availability": "missing",
                            "errorCode": "not-bundle", "rows": [] }
                        ] } }
        """.trimIndent()
        val client = BridgeClient(
            "http://127.0.0.1:43121",
            clientWith(200 to manifestInventoryOnly, 200 to body),
        )
        val catalog = BridgePluginCatalog.load(BridgeRegistry(client)).catalog
        val bundle = catalog.bundles.single()
        assertEquals("broken", bundle.title) // 没有 title 时回落到包名，绝不空着
        assertEquals("not-bundle", bundle.errorCode)
        assertTrue(bundle.official && bundle.optional && !bundle.removable)
    }

    @Test
    fun `render item asks for the summary view by default`() = runBlocking {
        val body = """
            { "ok": true, "provider": "client-slots", "version": 1, "warnings": [],
              "data": { "componentId": "agent-loop",
                        "tree": { "kind": "element", "type": "component:AgentLoopCard", "key": null,
                                  "props": { "view": "summary" }, "children": [] } } }
        """.trimIndent()
        val client = BridgeClient(
            "http://127.0.0.1:43121",
            clientWith(200 to manifestAllProviders, 200 to body),
        )
        val registry = BridgeRegistry(client)
        val result = BridgePluginCatalog.renderItem(registry, "agent-loop")
        val tree = (result as BridgeResult.Success).value.tree!!
        assertEquals("summary", tree.stringProp("view"))
    }
}

