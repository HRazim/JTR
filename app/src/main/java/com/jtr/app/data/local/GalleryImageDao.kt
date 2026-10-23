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

    /**
     * v7.1.94 — DESCRIPTION de l'image. Écrit la seule colonne `caption`, jamais la ligne
     * entière : une mise à jour partielle ne peut pas écraser par mégarde un `path` ou un
     * `createdAt` lus plus tôt dans l'UI. `null` efface la description.
     */
    @Query("UPDATE gallery_images SET caption = :caption WHERE id = :id")
    suspend fun updateCaption(id: Long, caption: String?)

    /**
     * v7.1.94 — DATE + HEURE de l'image (epoch millis). ⚠️ `createdAt` est aussi le critère
     * de tri secondaire de [observeByPerson] : la modifier RÉORDONNE la galerie et peut donc
     * changer la vignette « la plus récente » du carré d'entrée. C'est voulu.
     */
    @Query("UPDATE gallery_images SET createdAt = :createdAt WHERE id = :id")
    suspend fun updateCreatedAt(id: Long, createdAt: Long)

    /**
     * v7.1.95 — LOCALISATION de l'image (colonnes posées en v24). Les trois valeurs vont
     * ENSEMBLE : passer `null` partout retire le lieu, et il n'existe aucun chemin par
     * lequel une coordonnée survivrait à son libellé, ou l'inverse.
     */
    @Query(
        """
        UPDATE gallery_images
        SET latitude = :latitude, longitude = :longitude, placeLabel = :placeLabel
        WHERE id = :id
        """
    )
    suspend fun updateLocation(
        id: Long,
        latitude: Double?,
        longitude: Double?,
        placeLabel: String?
    )

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
