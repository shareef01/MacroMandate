package com.sharek.macromandate.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "audit_log", indices = [Index(value = ["timestamp"])])
data class AuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val category: String,
    val message: String
)
