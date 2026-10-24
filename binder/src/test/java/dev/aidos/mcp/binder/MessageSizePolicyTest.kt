package dev.aidos.mcp.binder

import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSizePolicyTest {
    @Test
    fun defaultMessageLimitIsFinite() {
        assertTrue(BinderMcpTransport.DEFAULT_MAX_MESSAGE_BYTES > 0)
        assertTrue(BinderMcpTransport.DEFAULT_MAX_MESSAGE_BYTES <= 1024 * 1024)
    }

    @Test
    fun connectionTimeoutIsFinite() {
        assertTrue(BinderMcpTransport.DEFAULT_CONNECT_TIMEOUT_MILLIS in 1..60_000)
    }
}
