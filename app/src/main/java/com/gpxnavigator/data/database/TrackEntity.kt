package com.gpxnavigator.data.database

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tracks", indices = [Index(value = ["fileName"], unique = true)])
data class TrackEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val displayName: String,
    val trackName: String?,
    val importedAtMillis: Long,
    val sizeBytes: Long,
    val pointCount: Int?,
    val segmentCount: Int?,
    val totalDistanceMeters: Double?,
    val problem: String? = null,
    val activityMode: String? = null,
    val customName: String? = null,
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false,
)

@Entity(tableName = "catalog_settings", foreignKeys = [ForeignKey(
    entity = TrackEntity::class, parentColumns = ["id"], childColumns = ["selectedTrackId"],
    onDelete = ForeignKey.SET_NULL,
)], indices = [Index("selectedTrackId")])
data class CatalogSettings(
    @PrimaryKey val id: Int = 0,
    val selectedTrackId: String? = null,
    val legacyMigrationCompleted: Boolean = false,
    @ColumnInfo(defaultValue = "'RECENT'") val sortOrder: String = "RECENT",
)

data class TrackWithSelection(@Embedded val track: TrackEntity, val isSelected: Boolean)
