package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM format_history ORDER BY timestamp DESC")
    fun getAllFormatHistory(): Flow<List<FormatHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFormatHistory(history: FormatHistoryEntity)

    @Query("DELETE FROM format_history")
    suspend fun clearHistory()

    @Query("SELECT * FROM app_preferences WHERE `key` = :key LIMIT 1")
    fun getPreferenceFlow(key: String): Flow<AppPreferenceEntity?>

    @Query("SELECT * FROM app_preferences WHERE `key` = :key LIMIT 1")
    suspend fun getPreference(key: String): AppPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreference(pref: AppPreferenceEntity)
}
