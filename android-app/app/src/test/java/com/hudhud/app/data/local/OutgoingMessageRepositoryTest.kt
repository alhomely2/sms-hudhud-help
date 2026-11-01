package com.hudhud.app.data.local

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutgoingMessageRepositoryTest {

    @Test
    fun `queueCampaign creates outgoing rows for all campaign messages`() = runBlocking {
        val fakeDao = FakeOutgoingMessageDao()
        val repository = OutgoingMessageRepository(fakeDao)

        val messages = listOf(
            CampaignMessageEntity(
                id = 101L,
                campaignId = 1L,
                sourceRow = 1,
                phone = "966500000001",
                text = "Message 1",
                status = CampaignMessageEntity.STATUS_READY,
            ),
            CampaignMessageEntity(
                id = 102L,
                campaignId = 1L,
                sourceRow = 2,
                phone = "966500000002",
                text = "Message 2",
                status = CampaignMessageEntity.STATUS_READY,
            ),
        )

        val ids = repository.queueCampaign(
            campaignId = 1L,
            siteProfileId = "staging",
            messages = messages,
        )

        assertEquals(2, ids.size)
        assertEquals(2, fakeDao.messages.size)
        assertEquals("staging", fakeDao.messages[0].siteProfileId)
        assertEquals(OutgoingMessageEntity.STATUS_PENDING, fakeDao.messages[0].status)
        assertEquals(101L, fakeDao.messages[0].messageId)
        assertEquals("Message 1", fakeDao.messages[0].payloadJson)
    }

    @Test
    fun `markSent updates status to SENT and records correlationId`() = runBlocking {
        val fakeDao = FakeOutgoingMessageDao()
        val repository = OutgoingMessageRepository(fakeDao)

        fakeDao.insert(
            OutgoingMessageEntity(
                id = 1L,
                campaignId = 1L,
                messageId = 100L,
                siteProfileId = "staging",
                status = OutgoingMessageEntity.STATUS_PENDING,
            ),
        )

        repository.markSent(id = 1L, retryCount = 1, correlationId = "corr-12345")

        val updated = fakeDao.messages.first { it.id == 1L }
        assertEquals(OutgoingMessageEntity.STATUS_SENT, updated.status)
        assertEquals(1, updated.retryCount)
        assertEquals("corr-12345", updated.correlationId)
    }

    @Test
    fun `markBlocked updates status to BLOCKED with reason`() = runBlocking {
        val fakeDao = FakeOutgoingMessageDao()
        val repository = OutgoingMessageRepository(fakeDao)

        fakeDao.insert(
            OutgoingMessageEntity(
                id = 2L,
                campaignId = 1L,
                messageId = 200L,
                siteProfileId = "staging",
                status = OutgoingMessageEntity.STATUS_PENDING,
            ),
        )

        repository.markBlocked(id = 2L, reason = "Rate limit exceeded")

        val updated = fakeDao.messages.first { it.id == 2L }
        assertEquals(OutgoingMessageEntity.STATUS_BLOCKED, updated.status)
        assertEquals("Rate limit exceeded", updated.lastError)
    }

    private class FakeOutgoingMessageDao : OutgoingMessageDao {
        private var nextId = 1L
        val messages = mutableListOf<OutgoingMessageEntity>()

        override suspend fun insert(entity: OutgoingMessageEntity): Long {
            val id = if (entity.id > 0) entity.id else nextId++
            val saved = entity.copy(id = id)
            messages.removeAll { it.id == id }
            messages.add(saved)
            return id
        }

        override suspend fun insertAll(entities: List<OutgoingMessageEntity>): List<Long> {
            return entities.map { insert(it) }
        }

        override suspend fun loadPendingBatch(siteProfileId: String, limit: Int): List<OutgoingMessageEntity> {
            return messages
                .filter { it.siteProfileId == siteProfileId && it.status in setOf(OutgoingMessageEntity.STATUS_PENDING, OutgoingMessageEntity.STATUS_RETRY_WAITING) }
                .take(limit)
        }

        override suspend fun loadForCampaign(campaignId: Long): List<OutgoingMessageEntity> {
            return messages.filter { it.campaignId == campaignId }
        }

        override suspend fun countPending(siteProfileId: String): Int =
            messages.count { it.siteProfileId == siteProfileId && it.status == OutgoingMessageEntity.STATUS_PENDING }

        override suspend fun countRetryWaiting(siteProfileId: String): Int =
            messages.count { it.siteProfileId == siteProfileId && it.status == OutgoingMessageEntity.STATUS_RETRY_WAITING }

        override suspend fun countBlocked(siteProfileId: String): Int =
            messages.count { it.siteProfileId == siteProfileId && it.status == OutgoingMessageEntity.STATUS_BLOCKED }

        override suspend fun updateStatus(
            id: Long,
            status: String,
            retryCount: Int,
            updatedAt: Long,
            lastAttemptAt: Long?,
            lastError: String,
            correlationId: String?,
        ) {
            val index = messages.indexOfFirst { it.id == id }
            if (index >= 0) {
                messages[index] = messages[index].copy(
                    status = status,
                    retryCount = retryCount,
                    updatedAt = updatedAt,
                    lastAttemptAt = lastAttemptAt,
                    lastError = lastError,
                    correlationId = correlationId,
                )
            }
        }

        override suspend fun markBlocked(id: Long, status: String, updatedAt: Long, lastError: String) {
            val index = messages.indexOfFirst { it.id == id }
            if (index >= 0) {
                messages[index] = messages[index].copy(
                    status = status,
                    updatedAt = updatedAt,
                    lastError = lastError,
                )
            }
        }
    }
}
