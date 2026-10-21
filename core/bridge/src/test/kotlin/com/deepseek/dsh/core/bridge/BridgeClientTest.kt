package com.deepseek.dsh.core.bridge

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * bridge/v1 客户端契约测试。
 *
 * 用一个拦截器伪造 Host 响应，因此测试覆盖的是解码、协商、降级与错误映射，
 * 不依赖真实 Host，也不需要网络。
 */
class BridgeClientTest {

    /** 用固定的响应队列构造一个 OkHttpClient。 */
    private fun clientWith(
        vararg responses: Pair<Int, String>,
        recorder: MutableList<String> = mutableListOf(),
    ): OkHttpClient {
        val queue = responses.toMutableList()
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            recorder.add("${request.method} ${request.url.encodedPath}")
            val (code, body) = if (queue.isEmpty()) 500 to "{}" else queue.removeAt(0)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("fixture")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    private val manifestBody = """
        {
          "bridgeVersion": "1.0.0",
          "dshVersion": "0.2.1-rc.2",
          "providers": [
            { "id": "client-assets", "version": 1, "capabilities": ["boot-graph", "module-source", "locale"], "readOnly": true },
            { "id": "client-slots", "version": 1, "capabilities": ["list", "describe", "render-tree", "icon-svg"], "readOnly": true },
            { "id": "package-inventory", "version": 1, "capabilities": ["bundles", "rows", "meta-icon"], "readOnly": true },
            { "id": "rpc", "version": 1, "capabilities": ["invoke"], "authorization": "host-session" }
          ]
        }
    """.trimIndent()

    @Test
    fun `manifest decodes every provider and negotiates the major version`() = runBlocking {
        val client = BridgeClient("http://127.0.0.1:43121", clientWith(200 to manifestBody))
        val result = client.manifest()
        val manifest = (result as BridgeResult.Success).value
        assertEquals("1.0.0", manifest.bridgeVersion)
        assertEquals("0.2.1-rc.2", manifest.dshVersion)
        assertEquals(4, manifest.providers.size)
        assertTrue(manifest.serves(BridgeProtocol.PROVIDER_CLIENT_SLOTS, 1))
        assertFalse(manifest.serves(BridgeProtocol.PROVIDER_CLIENT_SLOTS, 2))
        assertTrue(manifest.isCompatible())
    }

    @Test
    fun `manifest refuses an incompatible major version`() = runBlocking {
        val body = manifestBody.replace("\"1.0.0\"", "\"2.0.0\"")
        val client = BridgeClient("http://127.0.0.1:43121", clientWith(200 to body))
        val result = client.manifest()
        assertTrue(result is BridgeResult.Failure)
        assertEquals(BridgeErrorCode.PROVIDER_VERSION_MISMATCH, (result as BridgeResult.Failure).code)
    }

    @Test
    fun `manifest is cached until invalidated`() = runBlocking {
        val calls = mutableListOf<String>()
        val client = BridgeClient(
            "http://127.0.0.1:43121",
            clientWith(200 to manifestBody, 200 to manifestBody, recorder = calls),
        )
        client.manifest()
        client.manifest()
        assertEquals(1, calls.size)
        client.invalidate()
        client.manifest()
        assertEquals(2, calls.size)
    }

    @Test
    fun `an unreachable host reports transport unreachable`() = runBlocking {
        val failing = OkHttpClient.Builder().addInterceptor {
            throw java.io.IOException("connection refused")
        }.build()
        val client = BridgeClient("http://127.0.0.1:1", failing)
        val result = client.manifest()
        assertTrue(result is BridgeResult.Failure)
        assertEquals(BridgeErrorCode.TRANSPORT_UNREACHABLE, (result as BridgeResult.Failure).code)
    }

    @Test
    fun `a malformed body reports decode failed`() = runBlocking {
        val client = BridgeClient("http://127.0.0.1:43121", clientWith(200 to "{not json"))
        val result = client.manifest()
        assertTrue(result is BridgeResult.Failure)
        assertEquals(BridgeErrorCode.TRANSPORT_DECODE_FAILED, (result as BridgeResult.Failure).code)
    }

    @Test
    fun `a query posts to the query route and decodes its data`() = runBlocking {
        val calls = mutableListOf<String>()
        val body = """
            { "ok": true, "provider": "client-slots", "version": 1,
              "data": { "slot": "plugins.item",
                        "registrations": [ { "slot": "plugins.item", "id": "agent-loop", "order": 30,
                                             "label": "Agent 循环", "package": "pkg",
                                             "render": { "kind": "component", "componentId": "agent-loop" } } ],
                        "emptySlots": [] },
              "warnings": [] }
        """.trimIndent()
        val client = BridgeClient("http://127.0.0.1:43121", clientWith(200 to body, recorder = calls))
        val result = client.query("client-slots", "list", BridgeArgs.of("slot", "plugins.item")) { element ->
            BridgeJson.instance.decodeFromString(BridgeSlotList.serializer(), element.toString())
        }
        assertEquals(listOf("POST ${BridgeProtocol.QUERY_PATH}"), calls)
        val list = (result as BridgeResult.Success).value
        assertEquals("plugins.item", list.slot)
        assertEquals(1, list.registrations.size)
        assertEquals("agent-loop", list.registrations[0].identity)
        assertEquals("agent-loop", list.registrations[0].componentId)
        assertFalse(list.registrations[0].render!!.isFactory)
    }

    @Test
    fun `a failed query keeps the host's stable code`() = runBlocking {
        val body = """
            { "ok": false, "error": { "code": "provider/unavailable",
                                      "message": "this Host composition has no client module registry" } }
        """.trimIndent()
        val client = BridgeClient("http://127.0.0.1:43121", clientWith(200 to body))
        val result = client.query("client-assets", "boot-graph") { it }
        assertTrue(result is BridgeResult.Failure)
        assertEquals(BridgeErrorCode.PROVIDER_UNAVAILABLE, (result as BridgeResult.Failure).code)
        assertEquals("this Host composition has no client module registry", result.message)
    }

    @Test
    fun `a 404 means the bridge plugin is absent, not that the Host is down`() = runBlocking {
        // 回归：404 曾被报成 transport/unreachable，于是插件页提示去查网络，
        // 而真正要做的是在设置里安装插件。
        val client = BridgeClient("http://127.0.0.1:43121", clientWith(404 to "not found"))
        val result = client.query("client-slots", "list") { it }
        assertTrue(result is BridgeResult.Failure)
        assertEquals(BridgeErrorCode.PROVIDER_UNKNOWN, (result as BridgeResult.Failure).code)
        assertTrue((result as BridgeResult.Failure).message.contains("not installed"))
    }

    @Test
    fun `an ok response with no data is a failure, never an empty success`() = runBlocking {
        val client = BridgeClient(
            "http://127.0.0.1:43121",
            clientWith(200 to """{ "ok": true, "provider": "x", "version": 1, "warnings": [] }"""),
        )
        val result = client.query("x", "y") { it }
        assertTrue(result is BridgeResult.Failure)
        assertEquals(BridgeErrorCode.INTERNAL_ERROR, (result as BridgeResult.Failure).code)
    }

    @Test
    fun `warnings travel beside the data`() = runBlocking {
        val body = """
            { "ok": true, "provider": "client-slots", "version": 1,
              "data": { "slot": null, "registrations": [], "emptySlots": [] },
              "warnings": ["sample-plugin requested @deepseek-ai/dsh-client-ui-primitives"] }
        """.trimIndent()
        val client = BridgeClient("http://127.0.0.1:43121", clientWith(200 to body))
        val result = client.query("client-slots", "list") { element ->
            BridgeJson.instance.decodeFromString(BridgeSlotList.serializer(), element.toString())
        }
        assertEquals(
            listOf("sample-plugin requested @deepseek-ai/dsh-client-ui-primitives"),
            (result as BridgeResult.Success).warnings,
        )
    }

    @Test
    fun `the registry refuses a query when the provider is absent`() = runBlocking {
        val calls = mutableListOf<String>()
        val body = """{ "bridgeVersion": "1.0.0", "dshVersion": null, "providers": [] }"""
        val client = BridgeClient("http://127.0.0.1:43121", clientWith(200 to body, recorder = calls))
        val registry = BridgeRegistry(client)
        val result = registry.slotRegistrations("plugins.item")
        assertTrue(result is BridgeResult.Failure)
        assertEquals(BridgeErrorCode.PROVIDER_UNKNOWN, (result as BridgeResult.Failure).code)
        // Only the manifest was read; the missing provider never reached the wire.
        assertEquals(listOf("GET ${BridgeProtocol.MANIFEST_PATH}"), calls)
    }

    @Test
    fun `the registry refuses an op the provider does not advertise`() = runBlocking {
        val body = """
            { "bridgeVersion": "1.0.0", "dshVersion": null,
              "providers": [ { "id": "client-slots", "version": 1, "capabilities": ["list"] } ] }
        """.trimIndent()
        val client = BridgeClient("http://127.0.0.1:43121", clientWith(200 to body))
        val registry = BridgeRegistry(client)
        val result = registry.iconSvg("x")
        assertTrue(result is BridgeResult.Failure)
        assertEquals(BridgeErrorCode.OP_UNKNOWN, (result as BridgeResult.Failure).code)
    }

    @Test
    fun `the registry reports which providers a host serves`() = runBlocking {
        val client = BridgeClient("http://127.0.0.1:43121", clientWith(200 to manifestBody))
        val registry = BridgeRegistry(client)
        registry.negotiate()
        assertTrue(registry.hasClientSlots())
        assertTrue(registry.hasPackageInventory())
        assertTrue(registry.hasClientAssets())
        assertNotNull(registry.manifest)
    }

    @Test
    fun `boot graph decodes entries and batches`() = runBlocking {
        val body = """
            { "ok": true, "provider": "client-assets", "version": 1, "warnings": [],
              "data": { "rev": "abc", "release": { "version": "0.2.1-rc.2", "commit": "deadbeef" },
                        "entries": [ { "id": "pkg", "url": "plugins/??pkg/client.js&rev=1",
                                       "rev": "1", "immediately": true } ],
                        "batches": [ { "phase": "bootstrap", "url": "plugins/??x", "rev": "1",
                                       "entries": ["pkg"] } ] } }
        """.trimIndent()
        val client = BridgeClient("http://127.0.0.1:43121", clientWith(200 to body))
        val result = client.query("client-assets", "boot-graph") { element ->
            BridgeJson.instance.decodeFromString(BridgeBootGraph.serializer(), element.toString())
        }
        val graph = (result as BridgeResult.Success).value
        assertEquals("abc", graph.rev)
        assertEquals("deadbeef", graph.release?.commit)
        assertTrue(graph.entries[0].immediately)
        assertEquals(listOf("pkg"), graph.batches[0].entries)
    }

    @Test
    fun `package inventory decodes bundles with icons and rows`() = runBlocking {
        val body = """
            { "ok": true, "provider": "package-inventory", "version": 1, "warnings": [],
              "data": { "managementAvailable": true,
                        "bundles": [ { "name": "sample", "official": false, "optional": false,
                                       "removable": true, "enabled": true, "installed": true,
                                       "availability": "profile", "version": "1.2.3",
                                       "title": "Sample", "icon": "data:image/png;base64,AAAA",
                                       "rows": [ { "rowId": "r1", "moduleName": "sample" } ] } ] } }
        """.trimIndent()
        val client = BridgeClient("http://127.0.0.1:43121", clientWith(200 to body))
        val result = client.query("package-inventory", "bundles") { element ->
            BridgeJson.instance.decodeFromString(BridgeInventory.serializer(), element.toString())
        }
        val inventory = (result as BridgeResult.Success).value
        assertTrue(inventory.managementAvailable)
        assertEquals("sample", inventory.bundles[0].name)
        assertEquals("data:image/png;base64,AAAA", inventory.bundles[0].icon)
        assertEquals("r1", inventory.bundles[0].rows[0].rowId)
    }

    @Test
    fun `render tree decodes nested elements and text`() = runBlocking {
        val body = """
            { "ok": true, "provider": "client-slots", "version": 1, "warnings": [],
              "data": { "componentId": "card",
                        "tree": { "kind": "element", "type": "component:Card", "key": null,
                                  "props": { "view": "summary" },
                                  "children": [
                                    { "kind": "element", "type": "div", "key": null,
                                      "props": { "className": "card" },
                                      "children": [ { "kind": "text", "value": "hello" } ] },
                                    { "kind": "opaque", "reason": "depth-budget" } ] } } }
        """.trimIndent()
        val client = BridgeClient("http://127.0.0.1:43121", clientWith(200 to body))
        val result = client.query("client-slots", "render-tree") { element ->
            BridgeJson.instance.decodeFromString(BridgeRenderTree.serializer(), element.toString())
        }
        val tree = (result as BridgeResult.Success).value.tree!!
        assertEquals("component:Card", tree.type)
        assertEquals("hello", tree.textContent())
        assertEquals("card", tree.children[0].stringProp("className"))
        assertFalse(tree.isHostTag())
        assertTrue(tree.children[0].isHostTag())
        assertNotNull(tree.findByType("div"))
        assertNull(tree.findByType("svg"))
    }

    @Test
    fun `icon svg decodes the document and reads its viewBox`() = runBlocking {
        val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" " +
            "viewBox=\"0 0 24 24\" fill=\"currentColor\"><path d=\"M0 0h24v24H0z\" /></svg>"
        val body = """
            { "ok": true, "provider": "client-slots", "version": 1, "warnings": [],
              "data": { "componentId": "icon", "size": 24, "svg": ${JsonPrimitive(svg)} } }
        """.trimIndent()
        val client = BridgeClient("http://127.0.0.1:43121", clientWith(200 to body))
        val result = client.query("client-slots", "icon-svg") { element ->
            BridgeJson.instance.decodeFromString(BridgeIconSvg.serializer(), element.toString())
        }
        val icon = (result as BridgeResult.Success).value
        assertEquals(24, icon.size)
        assertEquals("0 0 24 24", icon.viewBoxOrNull())
    }

    @Test
    fun `rpc invoke unwraps the result field`() = runBlocking {
        val body = """
            { "ok": true, "provider": "rpc", "version": 1, "warnings": [],
              "data": { "namespace": "settings", "method": "describe",
                        "result": { "scope": "all" } } }
        """.trimIndent()
        val client = BridgeClient("http://127.0.0.1:43121", clientWith(200 to body))
        val result = client.query("rpc", "invoke") { element ->
            element.jsonObject["result"] ?: JsonPrimitive("")
        }
        val value: JsonElement = (result as BridgeResult.Success).value
        assertEquals("""{"scope":"all"}""", value.toString())
    }

    @Test
    fun `slot registrations group by slot in host order`() {
        val list = BridgeSlotList(
            registrations = listOf(
                BridgeSlotRegistration(slot = "plugins.item", id = "b", order = 20),
                BridgeSlotRegistration(slot = "settings.section", id = "s", order = 10),
                BridgeSlotRegistration(slot = "plugins.item", id = "a", order = 10),
            ),
        )
        val grouped = list.bySlot()
        assertEquals(listOf("b", "a"), grouped["plugins.item"]!!.map { it.id })
        assertEquals(listOf("s"), grouped["settings.section"]!!.map { it.id })
    }

    @Test
    fun `unknown fields from a newer host are ignored`() = runBlocking {
        val body = """
            { "bridgeVersion": "1.0.0", "dshVersion": "1.0.0",
              "providers": [ { "id": "client-slots", "version": 1, "capabilities": ["list"],
                               "futureField": { "nested": true } } ],
              "futureTop": [1, 2, 3] }
        """.trimIndent()
        val client = BridgeClient("http://127.0.0.1:43121", clientWith(200 to body))
        val manifest = (client.manifest() as BridgeResult.Success).value
        assertEquals(1, manifest.providers.size)
        assertEquals(listOf("list"), manifest.providers[0].capabilities)
    }

    @Test
    fun `an invalid base URL fails before any request`() = runBlocking {
        val calls = mutableListOf<String>()
        val client = BridgeClient("not-a-url", clientWith(200 to manifestBody, recorder = calls))
        val result = client.manifest()
        assertTrue(result is BridgeResult.Failure)
        assertEquals(BridgeErrorCode.TRANSPORT_UNREACHABLE, (result as BridgeResult.Failure).code)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `slot registration decodes its origin`() {
        val registration = BridgeJson.instance.decodeFromString(
            BridgeSlotRegistration.serializer(),
            """{ "slot": "settings.section", "id": "subscription", "origin": "static",
                 "render": { "kind": "component", "componentId": "pkg#subscription" } }""",
        )
        assertEquals("static", registration.origin)
        assertEquals("pkg#subscription", registration.componentId)
    }

    @Test
    fun `render tree props survive as json for a renderer`() = runBlocking {
        val props = buildJsonObject {
            put("view", "summary")
            put("size", 20)
        }
        val body = """
            { "ok": true, "provider": "client-slots", "version": 1, "warnings": [],
              "data": { "componentId": "card",
                        "tree": { "kind": "element", "type": "div", "key": null,
                                  "props": { "view": "summary", "size": 20 }, "children": [] } } }
        """.trimIndent()
        val client = BridgeClient("http://127.0.0.1:43121", clientWith(200 to body))
        val result = client.query("client-slots", "render-tree", BridgeArgs.ofObject("props", props)) { element ->
            BridgeJson.instance.decodeFromString(BridgeRenderTree.serializer(), element.toString())
        }
        val tree = (result as BridgeResult.Success).value.tree!!
        assertEquals("summary", tree.stringProp("view"))
        assertEquals("20", tree.stringProp("size"))
    }
}
