package com.hudhud.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseEntitiesAndMigrationTest {

    @Test
    fun `TemplateEntity defaults and constants adhere to specification`() {
        val template = TemplateEntity(
            id = 1L,
            name = "Test Template",
            body = "Hello {{Name}}",
        )

        assertEquals("USER", template.sourceType)
        assertEquals(1, template.sourceVersion)
        assertEquals(TemplateEntity.SOURCE_USER, template.sourceType)
        assertEquals("BUILT_IN", TemplateEntity.SOURCE_BUILT_IN)
        assertEquals("USER", TemplateEntity.SOURCE_USER)
    }

    @Test
    fun `CampaignEntity defaults and constants adhere to specification`() {
        val campaign = CampaignEntity(
            id = 1L,
            name = "Test Campaign",
        )

        assertEquals(CampaignEntity.MODE_RAW_DATA, campaign.importMode)
        assertNull(campaign.batchId)
        assertEquals(CampaignEntity.STATUS_PREPARING, campaign.status)
        assertEquals("READY_TEXT", CampaignEntity.MODE_READY_TEXT)
        assertEquals("RAW_DATA", CampaignEntity.MODE_RAW_DATA)
    }

    @Test
    fun `CampaignMessageEntity defaults and send statuses adhere to specification`() {
        val message = CampaignMessageEntity(
            id = 1L,
            campaignId = 10L,
            sourceRow = 1,
            phone = "+966500000000",
            text = "Welcome",
            status = CampaignMessageEntity.STATUS_READY,
        )

        assertEquals(CampaignMessageEntity.SEND_STATUS_PREPARED, message.sendStatus)
        assertNull(message.batchId)
        assertNull(message.platformRef)
        assertNull(message.sentAt)

        assertEquals("PREPARED", CampaignMessageEntity.SEND_STATUS_PREPARED)
        assertEquals("SENDING", CampaignMessageEntity.SEND_STATUS_SENDING)
        assertEquals("ACCEPTED", CampaignMessageEntity.SEND_STATUS_ACCEPTED)
        assertEquals("FAILED", CampaignMessageEntity.SEND_STATUS_FAILED)
        assertEquals("UNKNOWN", CampaignMessageEntity.SEND_STATUS_UNKNOWN)
    }

    @Test
    fun `ImportBatchEntity modes and statuses match specification`() {
        val batch = ImportBatchEntity(
            id = 1L,
            name = "Batch 1",
            fileName = "data.xlsx",
            fileUri = "content://path/to/data.xlsx",
        )

        assertEquals(ImportBatchEntity.MODE_RAW_DATA, batch.importMode)
        assertEquals(ImportBatchEntity.STATUS_PENDING, batch.status)
        assertEquals(0, batch.totalRows)
        assertEquals(0, batch.validRows)
        assertEquals(0, batch.errorRows)
        assertEquals("READY_TEXT", ImportBatchEntity.MODE_READY_TEXT)
        assertEquals("RAW_DATA", ImportBatchEntity.MODE_RAW_DATA)
    }

    @Test
    fun `ImportMappingEntity holds column mappings properly`() {
        val mapping = ImportMappingEntity(
            batchId = 1L,
            phoneColumn = "phone_number",
            variableMappingsJson = "{\"Name\":\"CustomerName\"}",
        )

        assertFalse(mapping.isComplete)
        assertEquals("phone_number", mapping.phoneColumn)
        assertEquals("phone", ImportMappingEntity.DEFAULT_PHONE_COLUMN)
    }

    @Test
    fun `OutgoingMessageEntity statuses match transmission lifecycle`() {
        assertEquals("PENDING", OutgoingMessageEntity.STATUS_PENDING)
        assertEquals("QUEUED", OutgoingMessageEntity.STATUS_QUEUED)
        assertEquals("SENDING", OutgoingMessageEntity.STATUS_SENDING)
        assertEquals("SENT", OutgoingMessageEntity.STATUS_SENT)
        assertEquals("FAILED", OutgoingMessageEntity.STATUS_FAILED)
        assertEquals("RETRY_WAITING", OutgoingMessageEntity.STATUS_RETRY_WAITING)
        assertEquals("BLOCKED", OutgoingMessageEntity.STATUS_BLOCKED)
    }

    @Test
    fun `recovery logic converts stuck sending messages to unknown status`() {
        val messages = mutableListOf(
            CampaignMessageEntity(
                id = 1L,
                campaignId = 1L,
                sourceRow = 1,
                phone = "966500000001",
                text = "Msg 1",
                status = CampaignMessageEntity.STATUS_READY,
                sendStatus = CampaignMessageEntity.SEND_STATUS_SENDING,
            ),
            CampaignMessageEntity(
                id = 2L,
                campaignId = 1L,
                sourceRow = 2,
                phone = "966500000002",
                text = "Msg 2",
                status = CampaignMessageEntity.STATUS_READY,
                sendStatus = CampaignMessageEntity.SEND_STATUS_ACCEPTED,
            ),
            CampaignMessageEntity(
                id = 3L,
                campaignId = 1L,
                sourceRow = 3,
                phone = "966500000003",
                text = "Msg 3",
                status = CampaignMessageEntity.STATUS_READY,
                sendStatus = CampaignMessageEntity.SEND_STATUS_PREPARED,
            ),
        )

        // Simulate the startup query: UPDATE campaign_messages SET sendStatus = 'UNKNOWN' WHERE sendStatus = 'SENDING'
        val updatedCount = messages.indices.count { i ->
            if (messages[i].sendStatus == CampaignMessageEntity.SEND_STATUS_SENDING) {
                messages[i] = messages[i].copy(sendStatus = CampaignMessageEntity.SEND_STATUS_UNKNOWN)
                true
            } else false
        }

        assertEquals(1, updatedCount)
        assertEquals(CampaignMessageEntity.SEND_STATUS_UNKNOWN, messages[0].sendStatus)
        assertEquals(CampaignMessageEntity.SEND_STATUS_ACCEPTED, messages[1].sendStatus)
        assertEquals(CampaignMessageEntity.SEND_STATUS_PREPARED, messages[2].sendStatus)
    }
}
