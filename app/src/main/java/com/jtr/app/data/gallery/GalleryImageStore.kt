package com.jtr.app.data.gallery

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.scale
import com.jtr.app.ui.person.decodeOrientedBitmap
import java.io.File
import java.util.UUID

/**
 * Stockage des images de GALERIE (v7.1.89) — copie privacy-first d'une image choisie via le
 * Photo Picker vers `filesDir/gallery/`, en la REDIMENSIONNANT et la RECOMPRESSANT.
 *
 * Pourquoi compresser ici alors que la photo de profil est copiée telle quelle : une galerie
 * multiplie les fichiers par contact. L'audit avait mesuré 90 Mo de `photos/` pour 105 images
 * référencées, en copie brute ; au même régime, quelques dizaines de souvenirs par contact
 * feraient exploser le stockage ET le poids des sauvegardes `.jtr` (qui embarquent les fichiers).
 *
 * L'orientation EXIF est déléguée à [decodeOrientedBitmap] (déjà éprouvé par le recadrage) :
 * une photo paysage reste paysage. Le ratio est PRÉSERVÉ — on borne le plus grand côté, on ne
 * recadre JAMAIS en carré ; le carré n'existe qu'à l'affichage des vignettes.
 */
object GalleryImageStore {

    /** Plus grand côté conservé, en pixels : largement suffisant pour un plein écran. */
    const val MAX_DIMENSION = 2048

    /** Qualité JPEG : bon compromis net/poids, aligné sur l'esprit du recadrage (q92). */
    const val JPEG_QUALITY = 85

    private const val DIR_NAME = "gallery"

    /** Dossier des images de galerie, créé à la demande. */
    fun directory(context: Context): File =
        File(context.filesDir, DIR_NAME).also { it.mkdirs() }

    /**
     * Copie [uri] dans `filesDir/gallery/` après mise à l'échelle et compression.
     * Renvoie le chemin absolu du fichier créé, ou `null` si l'image est illisible
     * (meilleur effort : un ajout multiple ne doit jamais échouer en bloc).
     *
     * À appeler hors du thread principal.
     */
    fun importFrom(context: Context, uri: Uri): String? {
        val decoded = decodeOrientedBitmap(context, uri) ?: return null
        val scaled = scaleDown(decoded)
        return try {
            val dest = File(directory(context), "gallery_${UUID.randomUUID()}.jpg")
            dest.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            dest.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
    }

    /**
     * Réduit le bitmap pour que son plus grand côté tienne dans [MAX_DIMENSION], en gardant
     * les proportions. Une image déjà assez petite est renvoyée TELLE QUELLE (aucun
     * ré-échantillonnage inutile, donc aucune perte de netteté gratuite).
     */
    private fun scaleDown(source: Bitmap): Bitmap {
        val longestSide = maxOf(source.width, source.height)
        if (longestSide <= MAX_DIMENSION) return source
        val ratio = MAX_DIMENSION.toFloat() / longestSide
        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        val height = (source.height * ratio).toInt().coerceAtLeast(1)
        return source.scale(width, height)
    }

    /**
     * Supprime le fichier d'une image de galerie. Ne supprime QUE dans `filesDir/gallery/` :
     * un chemin venant d'ailleurs (photo de profil, média restauré dans `photos/`) est
     * ignoré — garde-fou contre toute suppression collatérale.
     */
    fun deleteFile(context: Context, path: String): Boolean {
        val file = File(path)
        val galleryDir = directory(context)
        val inGallery = runCatching {
            file.canonicalPath.startsWith(galleryDir.canonicalPath + File.separator)
        }.getOrDefault(false)
        if (!inGallery) return false
        return runCatching { file.delete() }.getOrDefault(false)
    }
}
