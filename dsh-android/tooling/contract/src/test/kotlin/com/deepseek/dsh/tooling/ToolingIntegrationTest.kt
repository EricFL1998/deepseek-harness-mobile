package com.deepseek.dsh.tooling

import com.deepseek.dsh.tooling.codegen.DescriptorSet
import com.deepseek.dsh.tooling.codegen.InvocationDescriptor
import com.deepseek.dsh.tooling.codegen.KotlinCodeGenerator
import com.deepseek.dsh.tooling.contract.ContractReplayer
import com.deepseek.dsh.tooling.frontendpatch.FrontendPatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ToolingIntegrationTest {

    @Test
    fun testCodegenPipeline() {
        val descriptorSet = DescriptorSet(
            version = "4.0.0",
            setDigest = "sha256_mock_123",
            endpoints = listOf(
                InvocationDescriptor(
                    name = "session.list",
                    description = "获取当前会话列表",
                    digest = "ep_digest_1"
                ),
                InvocationDescriptor(
                    name = "session.send",
                    description = "发送指令",
                    digest = "ep_digest_2"
                )
            )
        )

        val code = KotlinCodeGenerator.generateClientInterface(descriptorSet, "com.deepseek.dsh.generated")
        assertTrue(code.contains("interface GeneratedDshRemoteEndpoints"))
        assertTrue(code.contains("suspend fun session_list"))
        assertTrue(code.contains("suspend fun session_send"))
    }

    @Test
    fun testFrontendPatcher() {
        val tempHtml = File(System.getProperty("java.io.tmpdir"), "test_index_${System.currentTimeMillis()}.html")
        tempHtml.writeText("<!DOCTYPE html><html><head><title>Test</title></head><body></body></html>")

        val patchJs = "window.__DSH_NATIVE_SURFACE__ = { ready: true };"
        val patchResult = FrontendPatcher.injectPatch(tempHtml, patchJs)

        assertTrue(patchResult.isSuccess)
        val patchedContent = tempHtml.readText()
        assertTrue(patchedContent.contains("__DSH_NATIVE_SURFACE__"))

        tempHtml.delete()
    }

    @Test
    fun testContractFixtureLoading() {
        val replayer = ContractReplayer()
        val tempFixture = File(System.getProperty("java.io.tmpdir"), "test_fixture_${System.currentTimeMillis()}.json")
        tempFixture.writeText("""
            {
                "contractVersion": "4.0.0",
                "expectedDigest": "digest_abc",
                "mockEvents": [
                    { "type": "Ready", "hostId": "local-host" }
                ]
            }
        """.trimIndent())

        val result = replayer.loadAndVerifyFixture(tempFixture)
        assertTrue(result.isSuccess)
        val fixture = result.getOrNull()
        assertNotNull(fixture)
        assertEquals("4.0.0", fixture!!.contractVersion)
        assertEquals(1, fixture.mockEvents.size)

        tempFixture.delete()
    }
}
