package com.gpxnavigator.data.database

import androidx.room.Database
import androidx.room.AutoMigration
import androidx.room.RoomDatabase

@Database(entities = [TrackEntity::class, CatalogSettings::class, RecordingEntity::class,
    RecordingPointEntity::class, RecordingCounter::class], version = 6, exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6)])
abstract class NavigatorDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    abstract fun recordings(): RecordingDao
}
