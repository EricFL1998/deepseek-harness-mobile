package com.deepseek.dsh.feature.hosts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePairingTest {

    @Test
    fun testParseQrCodePayload() {
        val manager = RemotePairingManager()

        val validQr = """
            {
                "v": 1,
                "name": "办公电脑 DSH",
                "url": "https://192.168.1.100:8443",
                "fp": "SHA256:abcd1234ef5678",
                "pair": "PAIR-9988",
                "exp": 0
            }
        """.trimIndent()

        val parsed = manager.parseQrCodePayload(validQr)
        assertTrue(parsed.isSuccess)
        val payload = parsed.getOrNull()
        assertNotNull(payload)
        assertEquals("办公电脑 DSH", payload!!.name)
        assertEquals("https://192.168.1.100:8443", payload.url)
        assertEquals("SHA256:abcd1234ef5678", payload.fp)
        assertEquals("PAIR-9988", payload.pair)
    }

    @Test
    fun testExpiredQrCodePayload() {
        val manager = RemotePairingManager()

        val expiredQr = """
            {
                "v": 1,
                "name": "过期主机",
                "url": "https://192.168.1.100:8443",
                "fp": "SHA256:abcd1234ef5678",
                "pair": "PAIR-9988",
                "exp": 1000
            }
        """.trimIndent()

        val parsed = manager.parseQrCodePayload(expiredQr)
        assertTrue(parsed.isFailure)
    }
}
