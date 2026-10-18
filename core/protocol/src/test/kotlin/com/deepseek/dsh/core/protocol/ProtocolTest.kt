package com.deepseek.dsh.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl

class ProtocolTest {

    @Test
    fun testConnectionStateMachineGenerationDrop() {
        val sm = ConnectionStateMachine()
        assertEquals(ConnectionState.Idle, sm.state.value)

        val gen1 = sm.nextGeneration()
        assertEquals(1L, gen1)

        val transitioned = sm.transitionTo(ConnectionState.Resolving("local-host", gen1))
        assertTrue(transitioned)
        assertEquals(ConnectionState.Resolving("local-host", gen1), sm.state.value)

        val gen2 = sm.nextGeneration()
        assertEquals(2L, gen2)

        // 尝试用旧 generation 跃迁应当被拒绝
        val oldTransition = sm.transitionTo(ConnectionState.Ready("local-host", gen1))
        assertFalse(oldTransition)

        // 使用新 generation 跃迁成功
        val newTransition = sm.transitionTo(ConnectionState.Ready("local-host", gen2))
        assertTrue(newTransition)
        assertTrue(sm.isReady())
    }

    @Test
    fun testHostCookieJarIsolation() {
        val cookieJar = HostCookieJar()
        val url = "http://127.0.0.1:8080/".toHttpUrl()

        val cookie = Cookie.Builder()
            .name("dsh_session")
            .value("token_test_123")
            .domain("127.0.0.1")
            .path("/")
            .build()

        cookieJar.saveFromResponse(url, listOf(cookie))
        val loaded = cookieJar.loadForRequest(url)

        assertEquals(1, loaded.size)
        assertEquals("dsh_session", loaded[0].name)
        assertEquals("token_test_123", loaded[0].value)

        // 测试清理
        cookieJar.clearHost("127.0.0.1", 8080)
        assertTrue(cookieJar.loadForRequest(url).isEmpty())
    }
}

