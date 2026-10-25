package com.jtr.app.data.repository

import android.content.Context
import android.net.Uri
import com.jtr.app.data.gallery.CaptureDateResolver
import com.jtr.app.data.gallery.GalleryImageStore
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.domain.model.GalleryImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Galerie d'images par contact (v7.1.89) — seule porte d'entrée de l'UI vers la table
 * `gallery_images` (DB v23) et vers les fichiers de `filesDir/gallery/`.
 *
 * Les deux vont TOUJOURS ensemble : une ligne sans fichier afficherait une vignette morte,
 * un fichier sans ligne serait un orphelin invisible. D'où l'ordre choisi ici — on écrit le
 * fichier d'abord, la ligne ensuite ; on efface la ligne d'abord, le fichier ensuite.
 */
class GalleryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.getInstance(appContext).galleryImageDao()

    /** Images d'un contact, ordre manuel (`position`) puis chronologique. */
    fun observeByPerson(personId: String): Flow<List<GalleryImage>> =
        dao.observeByPerson(personId)

    /**
     * Importe plusieurs images d'un coup (sélection multiple du Photo Picker) : chacune est
     * redimensionnée et recompressée dans `filesDir/gallery/`, puis enregistrée.
     *
     * MEILLEUR EFFORT : une image illisible est simplement ignorée, les autres passent —
     * un ajout de 4 photos ne doit pas échouer en bloc à cause d'une seule.
     * [position] continue la liste existante pour que le réordonnancement du lot B2 parte
     * d'un ordre déjà stable. Renvoie le nombre d'images réellement ajoutées.
     *
     * v7.1.108 — `createdAt` = DATE DE PRISE DE VUE réelle ([CaptureDateResolver] : EXIF →
     * DATE_TAKEN → date du fichier → heure d'import), résolue sur le URI SOURCE et AVANT
     * [GalleryImageStore.importFrom] : la copie recompressée perd l'EXIF. Chaque image du
     * lot est datée indépendamment ; `modifiedAt` n'est toujours PAS stampé à l'ajout.
     * Go-forward : les lignes déjà en base gardent leur valeur (leur EXIF est perdu).
     */
    suspend fun addImages(personId: String, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext 0
        var position = dao.maxPosition(personId) + 1
        var added = 0
        uris.forEach { uri ->
            val capturedAt = CaptureDateResolver.resolveForUri(appContext, uri)
            val path = GalleryImageStore.importFrom(appContext, uri) ?: return@forEach
            dao.insert(
                GalleryImage(
                    personId = personId,
                    path = path,
                    position = position,
                    createdAt = capturedAt
                )
            )
            position++
            added++
        }
        added
    }

    /**
     * v7.1.94 — enregistre la DESCRIPTION d'une image. Une chaîne vide (ou blanche) est
     * normalisée en `null` : « pas de description » n'a qu'UNE représentation en base, ce qui
     * évite qu'un champ vidé puis relu ressorte comme une chaîne vide significative.
     * v7.1.103 — toute édition de métadonnée stampe `modifiedAt = maintenant` (idem pour la
     * date et la localisation ci-dessous) ; l'AJOUT d'une image, lui, ne stampe pas.
     */
    suspend fun setCaption(id: Long, caption: String?) = withContext(Dispatchers.IO) {
        dao.updateCaption(
            id, caption?.trim()?.takeIf { it.isNotEmpty() }, System.currentTimeMillis()
        )
    }

    /** v7.1.94 — enregistre la DATE + HEURE d'une image (epoch millis). */
    suspend fun setCreatedAt(id: Long, createdAt: Long) = withContext(Dispatchers.IO) {
        dao.updateCreatedAt(id, createdAt, System.currentTimeMillis())
    }

    /**
     * v7.1.95 — enregistre (ou RETIRE, avec des `null`) la localisation d'une image. Le
     * libellé est normalisé comme la description : vide ⇒ `null`, une seule représentation
     * de « pas de lieu ».
     */
    suspend fun setLocation(
        id: Long,
        latitude: Double?,
        longitude: Double?,
        placeLabel: String?
    ) = withContext(Dispatchers.IO) {
        dao.updateLocation(
            id, latitude, longitude, placeLabel?.trim()?.takeIf { it.isNotEmpty() },
            System.currentTimeMillis()
        )
    }

    /**
     * Supprime une image : la LIGNE d'abord (l'UI se met à jour immédiatement via le Flow),
     * le FICHIER ensuite. Dans cet ordre, un échec de suppression du fichier laisse au pire
     * un orphelin invisible, jamais une vignette pointant dans le vide.
     */
    suspend fun delete(image: GalleryImage) = withContext(Dispatchers.IO) {
        dao.delete(image.id)
        GalleryImageStore.deleteFile(appContext, image.path)
    }

    /**
     * v7.1.90 — suppression EN LOT (mode sélection de la grille). Même chemin exactement que
     * [delete], appliqué à chaque image : ligne puis fichier, avec le garde-fou de chemin.
     * Un seul aller-retour hors du thread principal pour toute la sélection.
     */
    suspend fun deleteAll(images: List<GalleryImage>) = withContext(Dispatchers.IO) {
        images.forEach { image ->
            dao.delete(image.id)
            GalleryImageStore.deleteFile(appContext, image.path)
        }
    }
}
