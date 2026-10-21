package com.deepseek.dsh.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BundleChangeOutcomeTest {
    @Test
    fun `applied without restart reason does not need restart`() {
        assertFalse(outcome(application = "applied").restartRequired)
    }

    @Test
    fun `restart required result needs restart`() {
        assertTrue(outcome(application = "restart-required").restartRequired)
    }

    @Test
    fun `restart reason alone needs restart even when host says applied`() {
        assertTrue(outcome(application = "applied", restartReason = "package-moved").restartRequired)
    }

    @Test
    fun `other outcomes do not need restart`() {
        assertFalse(outcome(application = "overridden").restartRequired)
        assertFalse(outcome(application = "failed").restartRequired)
        assertFalse(outcome(application = "cancelled").restartRequired)
    }

    private fun outcome(application: String, restartReason: String? = null) = BundleChangeOutcome(
        application = application,
        restartReason = restartReason,
        warnings = emptyList(),
    )
}
