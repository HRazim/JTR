package com.jtr.app.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Album (« bucket ») d'images du MediaStore.
 *
 * @param bucketId identifiant MediaStore du dossier, ou `null` pour l'album
 *   virtuel « Récents » qui agrège TOUTES les images de l'appareil.
 * @param name nom d'affichage du dossier ; `null` pour l'album virtuel
 *   (l'UI substitue le libellé localisé « Récents »).
 * @param coverUri URI de l'image la plus récente — miniature de l'album.
 * @param imageCount nombre d'images contenues.
 */
data class GalleryAlbum(
    val bucketId: Long?,
    val name: String?,
    val coverUri: Uri,
    val imageCount: Int,
)

/**
 * Lecture seule du MediaStore pour la galerie in-app : albums (buckets) et
 * URIs d'images triées par date d'ajout décroissante. Toutes les requêtes
 * s'exécutent sur [Dispatchers.IO] — aucun accès disque sur le main thread.
 *
 * Seules des URIs `content://` légères sont retournées ; le décodage des
 * miniatures est délégué à Coil côté UI.
 */
class MediaStoreRepository(private val context: Context) {

    private val collection: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    private val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    /**
     * Liste des albums de l'appareil, précédée de l'album virtuel « Récents »
     * (toutes les images). Les albums suivent l'ordre de leur image la plus
     * récente. Liste vide si la galerie ne contient aucune image.
     */
    suspend fun loadAlbums(): List<GalleryAlbum> = withContext(Dispatchers.IO) {
        // Un seul passage de curseur : agrégation manuelle par bucket, car
        // GROUP BY n'est plus accepté par le ContentProvider depuis l'API 30.
        class BucketAccumulator(val name: String?, val coverUri: Uri) {
            var count = 0
        }

        val buckets = LinkedHashMap<Long, BucketAccumulator>() // ordre DESC préservé
        var firstUri: Uri? = null
        var total = 0

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        context.contentResolver.query(collection, projection, null, null, sortOrder)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                    if (firstUri == null) firstUri = uri
                    total++
                    val bucketId = cursor.getLong(bucketIdCol)
                    buckets.getOrPut(bucketId) {
                        BucketAccumulator(cursor.getString(nameCol), uri)
                    }.count++
                }
            }

        val cover = firstUri ?: return@withContext emptyList()
        buildList {
            add(GalleryAlbum(bucketId = null, name = null, coverUri = cover, imageCount = total))
            buckets.forEach { (bucketId, acc) ->
                add(GalleryAlbum(bucketId, acc.name, acc.coverUri, acc.count))
            }
        }
    }

    /**
     * URIs des images d'un album, triées par DATE_ADDED décroissante.
     *
     * @param bucketId album ciblé, ou `null` pour toutes les images (« Récents »).
     */
    suspend fun loadImages(bucketId: Long?): List<Uri> = withContext(Dispatchers.IO) {
        val selection = bucketId?.let { "${MediaStore.Images.Media.BUCKET_ID} = ?" }
        val selectionArgs = bucketId?.let { arrayOf(it.toString()) }
        val uris = mutableListOf<Uri>()
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                uris += ContentUris.withAppendedId(collection, cursor.getLong(idCol))
            }
        }
        uris
    }
}
