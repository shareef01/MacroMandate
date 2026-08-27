package com.sharek.macromandate.data.repository

import com.sharek.macromandate.data.local.AuditDao
import com.sharek.macromandate.data.local.AuditEntity
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicInteger

class AuditRepository(private val auditDao: AuditDao) {

    private companion object {
        const val MAX_AUDITS = 1000
        const val PRUNE_BATCH = 100
        // The buffer only ever grows one row per write, so checking every write is
        // a wasted COUNT(*). Checking once per batch cannot overshoot the ceiling
        // by more than PRUNE_BATCH rows.
        const val COUNT_CHECK_INTERVAL = PRUNE_BATCH
    }

    private val writesSinceCount = AtomicInteger(0)

    fun getRecentAudits(): Flow<List<AuditEntity>> = auditDao.getRecentAudits()

    suspend fun log(category: String, message: String) {
        val audit = AuditEntity(
            timestamp = System.currentTimeMillis(),
            category = category,
            message = message
        )
        auditDao.insertAudit(audit)

        if (writesSinceCount.incrementAndGet() >= COUNT_CHECK_INTERVAL) {
            writesSinceCount.set(0)
            if (auditDao.getAuditCount() > MAX_AUDITS) {
                auditDao.pruneOldAudits(PRUNE_BATCH)
            }
        }
    }

    suspend fun clearAllAudits() {
        auditDao.clearAllAudits()
    }
}
