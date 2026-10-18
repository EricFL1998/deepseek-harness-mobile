package com.deepseek.dsh.runtime.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BridgeProtocolTest {

    @Test
    fun testBridgeProtocolDispatch() {
        // 直接验证调度逻辑与未知方法拦截
        val server = BridgeServer(
            context = android.app.Application()
        )

        val unknownReq = BridgeRequest(
            id = "req_2",
            method = "non_existent_method"
        )
        val unknownRes = server.dispatchRequest(unknownReq)
        assertNotNull(unknownRes.error)
        assertEquals(-32601, unknownRes.error!!.code)
    }
}
