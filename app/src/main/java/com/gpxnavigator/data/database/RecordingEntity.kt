package com.gpxnavigator.data.database

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String,
    val startedAtMillis: Long,
    val sourceTrackId: String?,
    val title: String,
    val pending: Boolean = false,
    val endedAtMillis: Long? = null,
    val trackName: String? = null,
    val activityMode: String? = null,
)

@Entity(tableName = "recording_points", primaryKeys = ["recordingId", "sequence"],
    foreignKeys = [ForeignKey(entity = RecordingEntity::class, parentColumns = ["id"],
        childColumns = ["recordingId"], onDelete = ForeignKey.CASCADE)])
data class RecordingPointEntity(
    val recordingId: String,
    val sequence: Int,
    val segment: Int,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double?,
    val timestampMillis: Long,
    val elapsedMillis: Long,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float? = null,
    @ColumnInfo(defaultValue = "1") val isTrackPoint: Boolean = true,
)

@Entity(tableName = "recording_counter")
data class RecordingCounter(@PrimaryKey val id: Int = 0, val value: Long)
