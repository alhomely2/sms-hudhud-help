package com.hudhud.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignDispatchServiceTest {

    @Test
    fun `DispatchProgress model tracks campaign progress accurately`() {
        val initial = DispatchProgress(
            campaignId = 1L,
            campaignName = "Test Campaign",
            total = 100,
            sent = 0,
            failed = 0,
            status = DispatchStatus.RUNNING,
        )

        assertEquals(100, initial.total)
        assertEquals(0, initial.sent)
        assertEquals(DispatchStatus.RUNNING, initial.status)

        val updated = initial.copy(
            sent = 50,
            failed = 2,
            status = DispatchStatus.RUNNING,
        )

        assertEquals(50, updated.sent)
        assertEquals(2, updated.failed)

        val paused = updated.copy(status = DispatchStatus.PAUSED)
        assertEquals(DispatchStatus.PAUSED, paused.status)

        val completed = updated.copy(
            sent = 98,
            status = DispatchStatus.COMPLETED,
        )
        assertEquals(DispatchStatus.COMPLETED, completed.status)
        assertEquals(100, completed.sent + completed.failed)
    }

    @Test
    fun `DispatchStatus enum covers full execution lifecycle`() {
        val statuses = DispatchStatus.values()
        assertTrue(statuses.contains(DispatchStatus.IDLE))
        assertTrue(statuses.contains(DispatchStatus.RUNNING))
        assertTrue(statuses.contains(DispatchStatus.PAUSED))
        assertTrue(statuses.contains(DispatchStatus.COMPLETED))
    }
}
