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
     * v7.1.103 — chaque édition de métadonnée STAMPE aussi `modifiedAt` (= [now]) : c'est la
     * matière du tri « Date de modification » (`COALESCE(modifiedAt, createdAt)`).
     */
    @Query("UPDATE gallery_images SET caption = :caption, modifiedAt = :now WHERE id = :id")
    suspend fun updateCaption(id: Long, caption: String?, now: Long)

    /**
     * v7.1.94 — DATE + HEURE de l'image (epoch millis). ⚠️ `createdAt` est aussi le critère
     * de tri secondaire de [observeByPerson] : la modifier RÉORDONNE la galerie et peut donc
     * changer la vignette « la plus récente » du carré d'entrée. C'est voulu.
     * v7.1.103 — stampe aussi `modifiedAt` (cf. [updateCaption]).
     */
    @Query("UPDATE gallery_images SET createdAt = :createdAt, modifiedAt = :now WHERE id = :id")
    suspend fun updateCreatedAt(id: Long, createdAt: Long, now: Long)

    /**
     * v7.1.95 — LOCALISATION de l'image (colonnes posées en v24). Les trois valeurs vont
     * ENSEMBLE : passer `null` partout retire le lieu, et il n'existe aucun chemin par
     * lequel une coordonnée survivrait à son libellé, ou l'inverse.
     * v7.1.103 — stampe aussi `modifiedAt` (cf. [updateCaption]) — retirer le lieu EST une
     * édition.
     */
    @Query(
        """
        UPDATE gallery_images
        SET latitude = :latitude, longitude = :longitude, placeLabel = :placeLabel,
            modifiedAt = :now
        WHERE id = :id
        """
    )
    suspend fun updateLocation(
        id: Long,
        latitude: Double?,
        longitude: Double?,
        placeLabel: String?,
        now: Long
    )

    /** Toutes les lignes — lecture ponctuelle pour l'export `.jtr`. */
    @Query("SELECT * FROM gallery_images")
    suspend fun getAllSync(): List<GalleryImage>

    /**
     * v7.1.105 (lot C1) — chemins des fichiers d'un contact, lus AVANT son hard-delete :
     * la CASCADE FK efface les lignes avec le contact, il serait trop tard après.
     */
    @Query("SELECT path FROM gallery_images WHERE personId = :personId")
    suspend fun pathsForPerson(personId: String): List<String>

    /** v7.1.105 — chemins galerie de TOUS les contacts en corbeille (vidage corbeille). */
    @Query(
        "SELECT path FROM gallery_images WHERE personId IN " +
            "(SELECT id FROM persons WHERE deletedAt IS NOT NULL)"
    )
    suspend fun pathsForDeletedPersons(): List<String>

    /** v7.1.105 — chemins galerie des contacts en corbeille depuis plus de [cutoff] (purge). */
    @Query(
        "SELECT path FROM gallery_images WHERE personId IN " +
            "(SELECT id FROM persons WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff)"
    )
    suspend fun pathsForPersonsDeletedBefore(cutoff: Long): List<String>

    /**
     * v7.1.106 (lot C2) — ensemble de référence du balayage d'orphelins : les chemins de
     * TOUTES les images de galerie, y compris celles de contacts en corbeille (aucune
     * jointure filtrante sur `persons.deletedAt`).
     */
    @Query("SELECT path FROM gallery_images")
    suspend fun getAllPaths(): List<String>

    /**
     * v7.1.89 — plus grande `position` déjà utilisée pour ce contact ; `-1` s'il n'a aucune
     * image, de sorte que la première ajoutée prenne la position 0. Permet d'ajouter EN FIN
     * de liste sans relire toute la galerie.
     */
    @Query("SELECT COALESCE(MAX(position), -1) FROM gallery_images WHERE personId = :personId")
    suspend fun maxPosition(personId: String): Int

    /**
     * v7.1.125 (R-b) — ordre MANUEL du glisser-déposer. Écrit la seule colonne `position`,
     * jamais la ligne entière : un réordonnancement ne peut toucher ni un `path`, ni une
     * date, ni une métadonnée — et ne supprime rien. Volontairement SANS `modifiedAt` :
     * ranger ses photos n'est pas éditer leurs métadonnées, le tri « Date de modification »
     * ne doit donc pas bouger sous un drag.
     */
    @Query("UPDATE gallery_images SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)
}
