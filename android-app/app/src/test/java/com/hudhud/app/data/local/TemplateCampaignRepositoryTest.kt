package com.hudhud.app.data.local

import com.hudhud.app.domain.model.MessageTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class TemplateCampaignRepositoryTest {
    @Test
    fun `draft stays hidden until confirmed and exposes messages in pages`() = runBlocking {
        val dao = RecordingCampaignDao()
        val repository = TemplateCampaignRepository(CampaignStorage(dao))

        val draft = repository.prepareDraftFromWorkbook(
            name = "Customers.xlsx",
            input = ByteArrayInputStream(workbookBytes()),
            template = template(),
        )

        assertEquals(CampaignEntity.STATUS_DRAFT_REVIEW, draft.status)
        assertTrue(dao.observeAll().toList().single().isEmpty())
        assertEquals(draft.id, dao.observeDrafts().toList().single().single().id)
        assertEquals(draft.id, repository.loadDraft(draft.id)?.id)
        assertEquals(1, repository.loadDraftMessages(draft.id, page = 0, pageSize = 1).size)
        assertEquals(1, repository.loadDraftMessages(draft.id, page = 1, pageSize = 1).size)

        repository.confirmDraft(draft.id)

        assertEquals(CampaignEntity.STATUS_READY_LOCAL, dao.campaigns.single().status)
        assertEquals(1, dao.observeAll().toList().single().size)
    }

    @Test
    fun `discarding a draft deletes its messages`() = runBlocking {
        val dao = RecordingCampaignDao()
        val repository = TemplateCampaignRepository(CampaignStorage(dao))
        val draft = repository.prepareDraftFromWorkbook(
            name = "Customers.xlsx",
            input = ByteArrayInputStream(workbookBytes()),
            template = template(),
        )

        repository.discardDraft(draft.id)

        assertTrue(dao.campaigns.isEmpty())
        assertTrue(dao.messages.isEmpty())
    }

    @Test
    fun `creates a local campaign from workbook rows using the selected template`() = runBlocking {
        val dao = RecordingCampaignDao()
        val repository = TemplateCampaignRepository(CampaignStorage(dao))
        val template = MessageTemplate(
            name = "Account reminder",
            body = "Hello {{Name}}, your balance is {{Balance}}",
            phoneColumn = "MobileNo",
            variableColumns = mapOf(
                "Name" to "Customer",
                "Balance" to "Amount",
            ),
        )

        val campaign = repository.createFromWorkbook(
            name = "Customers.xlsx",
            input = ByteArrayInputStream(workbookBytes()),
            template = template,
        )

        assertEquals(CampaignEntity.STATUS_READY_LOCAL, campaign.status)
        assertEquals(2, campaign.totalMessages)
        assertEquals(2, campaign.validMessages)
        assertEquals(
            listOf(
                "Hello Sara, your balance is 125",
                "Hello Lina, your balance is 250",
            ),
            dao.messages.map { it.text },
        )
        assertEquals(
            listOf("966500000001", "966500000002"),
            dao.messages.map { it.phone },
        )
        assertEquals(
            List(2) { CampaignMessageEntity.STATUS_READY },
            dao.messages.map { it.status },
        )
    }

    private fun workbookBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Customers")
            sheet.createRow(0).apply {
                createCell(0).setCellValue("MobileNo")
                createCell(1).setCellValue("Customer")
                createCell(2).setCellValue("Amount")
            }
            sheet.createRow(1).apply {
                createCell(0).setCellValue("966500000001")
                createCell(1).setCellValue("Sara")
                createCell(2).setCellValue("125")
            }
            sheet.createRow(2).apply {
                createCell(0).setCellValue("966500000002")
                createCell(1).setCellValue("Lina")
                createCell(2).setCellValue("250")
            }
            workbook.write(output)
        }
        return output.toByteArray()
    }

    private fun template() = MessageTemplate(
        name = "Account reminder",
        body = "Hello {{Name}}, your balance is {{Balance}}",
        phoneColumn = "MobileNo",
        variableColumns = mapOf("Name" to "Customer", "Balance" to "Amount"),
    )

    private class RecordingCampaignDao : CampaignDao {
        private var nextId = 1L
        val messages = mutableListOf<CampaignMessageEntity>()
        val campaigns = mutableListOf<CampaignEntity>()

        override fun observeAll(): Flow<List<CampaignEntity>> =
            flowOf(campaigns.filter {
                it.status !in setOf(
                    CampaignEntity.STATUS_PREPARING,
                    CampaignEntity.STATUS_DRAFT_REVIEW,
                )
            })

        override fun observeDrafts(): Flow<List<CampaignEntity>> =
            flowOf(campaigns.filter { it.status == CampaignEntity.STATUS_DRAFT_REVIEW })

        override fun loadDraft(campaignId: Long): CampaignEntity? =
            campaigns.firstOrNull {
                it.id == campaignId && it.status == CampaignEntity.STATUS_DRAFT_REVIEW
            }

        override fun findById(campaignId: Long): CampaignEntity? =
            campaigns.firstOrNull { it.id == campaignId }

        override fun observeById(campaignId: Long): Flow<CampaignEntity?> =
            flowOf(findById(campaignId))

        override fun count(): Int = campaigns.size

        override fun insertCampaign(campaign: CampaignEntity): Long {
            val id = nextId++
            campaigns += campaign.copy(id = id)
            return id
        }

        override fun insertMessages(messages: List<CampaignMessageEntity>) {
            this.messages += messages
        }

        override fun loadMessagesPage(
            campaignId: Long,
            limit: Int,
            offset: Int,
        ): List<CampaignMessageEntity> =
            messages.filter { it.campaignId == campaignId }.drop(offset).take(limit)

        override fun loadAllMessages(campaignId: Long): List<CampaignMessageEntity> =
            messages.filter { it.campaignId == campaignId }

        override fun observeMessages(campaignId: Long): Flow<List<CampaignMessageEntity>> =
            flowOf(loadAllMessages(campaignId))

        override fun updateMessageSendStatus(
            messageId: Long,
            sendStatus: String,
            platformRef: String?,
            sentAt: Long?,
        ): Int = 0

        override fun updateMessagesSendStatus(messageIds: List<Long>, sendStatus: String): Int = 0

        override fun resetStuckSendingMessages(): Int = 0

        override fun countMessagesBySendStatus(campaignId: Long, sendStatus: String): Int = 0

        override fun loadMessagesBySendStatus(campaignId: Long, sendStatus: String): List<CampaignMessageEntity> =
            emptyList()

        override fun loadMessagesByBatch(batchId: Long): List<CampaignMessageEntity> =
            messages.filter { it.batchId == batchId }

        override fun updateCampaignStatus(campaignId: Long, status: String): Int {
            val index = campaigns.indexOfFirst { it.id == campaignId }
            if (index < 0) return 0
            campaigns[index] = campaigns[index].copy(status = status)
            return 1
        }

        override fun completeCampaign(
            campaignId: Long,
            status: String,
            totalMessages: Int,
            validMessages: Int,
            reviewMessages: Int,
        ) {
            val index = campaigns.indexOfFirst { it.id == campaignId }
            if (index < 0) return
            campaigns[index] = campaigns[index].copy(
                status = status,
                totalMessages = totalMessages,
                validMessages = validMessages,
                reviewMessages = reviewMessages,
            )
        }

        override fun deleteCampaign(campaignId: Long) {
            campaigns.removeAll { it.id == campaignId }
            messages.removeAll { it.campaignId == campaignId }
        }

        override fun finalizeDraft(campaignId: Long): Int {
            val index = campaigns.indexOfFirst {
                it.id == campaignId && it.status == CampaignEntity.STATUS_DRAFT_REVIEW
            }
            if (index < 0) return 0
            campaigns[index] = campaigns[index].copy(
                status = if (campaigns[index].reviewMessages > 0) {
                    CampaignEntity.STATUS_NEEDS_REVIEW
                } else {
                    CampaignEntity.STATUS_READY_LOCAL
                },
            )
            return 1
        }

        override fun deleteDraft(campaignId: Long): Int {
            val exists = campaigns.any {
                it.id == campaignId && it.status == CampaignEntity.STATUS_DRAFT_REVIEW
            }
            if (exists) deleteCampaign(campaignId)
            return if (exists) 1 else 0
        }
    }
}
