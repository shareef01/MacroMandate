package com.sharek.macromandate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {
    @Insert
    suspend fun insertAudit(audit: AuditEntity)

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT 50")
    fun getRecentAudits(): Flow<List<AuditEntity>>

    @Query("SELECT COUNT(*) FROM audit_log")
    suspend fun getAuditCount(): Int

    @Query("DELETE FROM audit_log WHERE id IN (SELECT id FROM audit_log ORDER BY timestamp ASC LIMIT :count)")
    suspend fun pruneOldAudits(count: Int)

    @Query("DELETE FROM audit_log")
    suspend fun clearAllAudits()
}
