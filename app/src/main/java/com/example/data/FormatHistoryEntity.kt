package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "format_history")
data class FormatHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val driveName: String,
    val capacityBytes: Long,
    val fileSystem: String, // FAT32, exFAT, NTFS
    val isSuccess: Boolean,
    val isVerified: Boolean,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
