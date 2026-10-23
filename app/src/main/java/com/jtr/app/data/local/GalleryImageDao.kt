package com.jtr.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jtr.app.domain.model.GalleryImage
import kotlinx.coroutines.flow.Flow

/**
 * DAO de la galerie par contact (v7.1.88, DB v23). Volontairement MINIMAL : ce lot pose la
 * fondation (table + migration + sauvegarde) sans aucune UI, pour isoler le risque migration.
 */
@Dao
interface GalleryImageDao {

    /**
     * Images d'un contact, dans l'ordre manuel puis chronologique — `position` d'abord
     * (réordonnancement futur), `createdAt` ensuite pour départager à position égale,
     * donc un ordre STABLE tant qu'aucune position n'est posée.
     */
    @Query("""
        SELECT * FROM gallery_images
        WHERE personId = :personId
        ORDER BY position ASC, createdAt ASC
    """)
    fun observeByPerson(personId: String): Flow<List<GalleryImage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: GalleryImage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(images: List<GalleryImage>)

    @Query("DELETE FROM gallery_images WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM gallery_images WHERE personId = :personId")
    suspend fun deleteByPerson(personId: String)

    /** Toutes les lignes — lecture ponctuelle pour l'export `.jtr`. */
    @Query("SELECT * FROM gallery_images")
    suspend fun getAllSync(): List<GalleryImage>

    /**
     * v7.1.89 — plus grande `position` déjà utilisée pour ce contact ; `-1` s'il n'a aucune
     * image, de sorte que la première ajoutée prenne la position 0. Permet d'ajouter EN FIN
     * de liste sans relire toute la galerie.
     */
    @Query("SELECT COALESCE(MAX(position), -1) FROM gallery_images WHERE personId = :personId")
    suspend fun maxPosition(personId: String): Int
}
