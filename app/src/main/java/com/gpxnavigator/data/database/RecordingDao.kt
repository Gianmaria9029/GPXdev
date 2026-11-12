package com.gpxnavigator.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY startedAtMillis LIMIT 1")
    suspend fun current(): RecordingEntity?
    @Insert suspend fun insert(session: RecordingEntity)
    @Insert suspend fun insert(point: RecordingPointEntity)
    @Query("SELECT * FROM recording_points WHERE recordingId = :id ORDER BY sequence")
    suspend fun points(id: String): List<RecordingPointEntity>
    @Query("UPDATE recordings SET pending = 1, endedAtMillis = COALESCE(endedAtMillis, :endedAtMillis) WHERE id = :id")
    suspend fun finish(id: String, endedAtMillis: Long)
    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: String)
    @Query("SELECT value FROM recording_counter WHERE id = 0")
    suspend fun counter(): Long?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun counter(counter: RecordingCounter)
}
