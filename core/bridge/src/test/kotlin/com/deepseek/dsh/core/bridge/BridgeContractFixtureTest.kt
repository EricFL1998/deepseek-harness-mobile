package com.deepseek.dsh.core.bridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 契约夹具测试。
 *
 * `fixtures/bridge-contract-fixture.json` 是脱敏的线协议样本，与 Host 插件的
 * `docs/protocol.md` 对齐。这里用真实的解码器读它：夹具一旦漂移，测试立刻失败，
 * 而不是等到设备上才发现字段名对不上。
 */
class BridgeContractFixtureTest {

    private val fixture: JsonObject by lazy {
        val candidates = listOf(
            File("../fixtures/bridge-contract-fixture.json"),
            File("fixtures/bridge-contract-fixture.json"),
            File("../../fixtures/bridge-contract-fixture.json"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("bridge contract fixture not found; looked in ${candidates.map { it.absolutePath }}")
        Json.parseToJsonElement(file.readText()).jsonObject
    }

    private fun decode(element: kotlinx.serialization.json.JsonElement): JsonObject = element.jsonObject

    @Test    fun `manifest decodes and negotiates`() {
        val manifest = BridgeJson.instance.decodeFromString(
            BridgeManifest.serializer(),
            fixture["manifest"].toString(),
        )
        assertEquals("1.0.0", manifest.bridgeVersion)
        assertTrue(manifest.isCompatible())
        assertEquals(
            listOf("client-assets", "client-slots", "package-inventory", "rpc"),
            manifest.providers.map { it.id },
        )
        assertTrue(manifest.serves(BridgeProtocol.PROVIDER_CLIENT_SLOTS, 1))
        assertEquals("host-session", manifest.provider(BridgeProtocol.PROVIDER_RPC)?.authorization)
    }

    @Test
    fun `boot graph decodes`() {
        val graph = BridgeJson.instance.decodeFromString(
            BridgeBootGraph.serializer(),
            fixture["bootGraph"].toString(),
        )
        assertEquals("9f2c1ab4d5e6", graph.rev)
        assertEquals("deadbeef", graph.release?.commit)
        assertEquals(1, graph.entries.size)
        assertTrue(graph.entries[0].immediately)
        assertEquals("bootstrap", graph.batches[0].phase)
    }

    @Test
    fun `slot list decodes with official labels and order`() {
        val list = BridgeJson.instance.decodeFromString(
            BridgeSlotList.serializer(),
            fixture["slotList"].toString(),
        )
        assertEquals("plugins.item", list.slot)
        assertEquals(listOf("agent-loop", "shell"), list.registrations.map { it.id })
        assertEquals(listOf("Agent 循环", "终端"), list.registrations.map { it.label })
        assertEquals(30, list.registrations[0].order)
        assertEquals("agent-loop", list.registrations[0].componentId)
        assertTrue(list.emptySlots.isEmpty())
        // 分组是客户端唯一需要的组织方式。
        assertEquals(2, list.bySlot()["plugins.item"]?.size)
    }

    @Test
    fun `render tree decodes nested elements and text`() {
        val tree = BridgeJson.instance.decodeFromString(
            BridgeRenderTree.serializer(),
            fixture["renderTree"].toString(),
        ).tree!!
        assertEquals("component:AgentLoopCard", tree.type)
        assertEquals("summary", tree.stringProp("view"))
        assertEquals("控制 Agent 派发工具调用的方式。", tree.textContent())
        assertTrue(tree.findByType("div") != null)
        assertNull(tree.findByType("svg"))
    }

    @Test
    fun `icon svg decodes and exposes its viewBox`() {
        val icon = BridgeJson.instance.decodeFromString(
            BridgeIconSvg.serializer(),
            fixture["iconSvg"].toString(),
        )
        assertEquals(40, icon.size)
        assertEquals("0 0 24 24", icon.viewBoxOrNull())
        assertTrue(icon.svg!!.contains("<path"))
        assertTrue(icon.svg!!.contains("fill-rule"))
    }

    @Test
    fun `inventory decodes bundles, rows, icons and error codes`() {
        val inventory = BridgeJson.instance.decodeFromString(
            BridgeInventory.serializer(),
            fixture["inventory"].toString(),
        )
        assertTrue(inventory.managementAvailable)
        assertEquals(listOf("dsh-purge", "retired"), inventory.bundles.map { it.name })
        assertEquals("1.1.64", inventory.bundles[0].version)
        assertTrue(inventory.bundles[0].icon!!.startsWith("data:image/png;base64,"))
        assertEquals("purge", inventory.bundles[0].rows[0].rowId)
        assertEquals("not-bundle", inventory.bundles[1].errorCode)
        assertTrue(inventory.bundles[1].official && inventory.bundles[1].optional)
    }

    @Test
    fun `every documented failure decodes with its stable code`() {
        val failures = fixture["failures"]!!.let {
            Json.parseToJsonElement(it.toString()).let { element ->
                element as kotlinx.serialization.json.JsonArray
            }
        }
        val codes = failures.map { entry ->
            BridgeJson.instance.decodeFromString(BridgeQueryError.serializer(), entry.toString()).code
        }
        assertEquals(
            listOf(
                BridgeErrorCode.PROVIDER_UNAVAILABLE,
                BridgeErrorCode.OP_UNKNOWN,
                BridgeErrorCode.ARGS_INVALID,
                BridgeErrorCode.BUDGET_EXCEEDED,
                BridgeErrorCode.AUTH_REQUIRED,
            ),
            codes,
        )
    }

    @Test
    fun `the fixture's error details carry the self-correction hints`() {
        val failures = fixture["failures"] as kotlinx.serialization.json.JsonArray
        val opUnknown = BridgeJson.instance.decodeFromString(
            BridgeQueryError.serializer(),
            failures[1].toString(),
        )
        val capabilities = (opUnknown.details as JsonObject)["capabilities"]!!
        assertTrue(capabilities.toString().contains("render-tree"))

        val argsInvalid = BridgeJson.instance.decodeFromString(
            BridgeQueryError.serializer(),
            failures[2].toString(),
        )
        assertTrue((argsInvalid.details as JsonObject)["known"]!!.toString().contains("agent-loop"))
    }

    @Test
    fun `a full query envelope decodes from the fixture shapes`() {
        val envelope = """
            { "ok": true, "provider": "client-slots", "version": 1,
              "data": ${fixture["slotList"]},
              "warnings": ["sample requested a module the bridge VM does not provide"] }
        """.trimIndent()
        val response = BridgeJson.instance.decodeFromString(BridgeQueryResponse.serializer(), envelope)
        assertTrue(response.ok)
        assertEquals("client-slots", response.provider)
        assertEquals(1, response.version)
        val list = BridgeJson.instance.decodeFromString(BridgeSlotList.serializer(), response.data.toString())
        assertEquals(2, list.registrations.size)
        assertEquals(1, response.warnings.size)
    }
}
