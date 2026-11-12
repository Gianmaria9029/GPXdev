package com.gpxnavigator.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("""
        SELECT tracks.*, CASE WHEN tracks.id =
        (SELECT selectedTrackId FROM catalog_settings WHERE id = 0)
        THEN 1 ELSE 0 END AS isSelected
        FROM tracks ORDER BY importedAtMillis DESC, id ASC
    """)
    fun observeTracks(): Flow<List<TrackWithSelection>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun find(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE fileName = :fileName")
    suspend fun findByFileName(fileName: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id = (SELECT selectedTrackId FROM catalog_settings WHERE id = 0)")
    suspend fun selected(): TrackEntity?

    @Query("SELECT * FROM catalog_settings WHERE id = 0")
    suspend fun settings(): CatalogSettings?

    @Query("SELECT * FROM catalog_settings WHERE id = 0")
    fun observeSettings(): Flow<CatalogSettings?>

    @Query("UPDATE catalog_settings SET sortOrder = :order WHERE id = 0")
    suspend fun setSortOrder(order: String)

    @Query("UPDATE tracks SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean): Int

    @Query("UPDATE tracks SET customName = :name WHERE id = :id")
    suspend fun rename(id: String, name: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun createSettings(settings: CatalogSettings)

    @Insert
    suspend fun insert(track: TrackEntity)

    @Query("UPDATE catalog_settings SET selectedTrackId = :id WHERE id = 0")
    suspend fun select(id: String?)

    @Query("UPDATE catalog_settings SET legacyMigrationCompleted = 1 WHERE id = 0")
    suspend fun completeLegacyMigration()

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun delete(id: String)
}
