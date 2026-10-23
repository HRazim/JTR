package com.jtr.app.data.repository

import android.content.Context
import android.net.Uri
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
     */
    suspend fun addImages(personId: String, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext 0
        var position = dao.maxPosition(personId) + 1
        var added = 0
        uris.forEach { uri ->
            val path = GalleryImageStore.importFrom(appContext, uri) ?: return@forEach
            dao.insert(
                GalleryImage(
                    personId = personId,
                    path = path,
                    position = position,
                    createdAt = System.currentTimeMillis()
                )
            )
            position++
            added++
        }
        added
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
}
