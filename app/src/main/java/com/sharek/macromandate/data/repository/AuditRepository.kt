package com.sharek.macromandate.data.repository

import com.sharek.macromandate.data.local.AuditDao
import com.sharek.macromandate.data.local.AuditEntity
import kotlinx.coroutines.flow.Flow

class AuditRepository(private val auditDao: AuditDao) {
    fun getRecentAudits(): Flow<List<AuditEntity>> = auditDao.getRecentAudits()

    suspend fun log(category: String, message: String) {
        val audit = AuditEntity(
            timestamp = System.currentTimeMillis(),
            category = category,
            message = message
        )
        auditDao.insertAudit(audit)
        
        val count = auditDao.getAuditCount()
        if (count > 1000) {
            auditDao.pruneOldAudits(100)
        }
    }
}
