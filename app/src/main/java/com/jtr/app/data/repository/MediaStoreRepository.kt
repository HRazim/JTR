package com.jtr.app.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Album (« bucket ») d'images du MediaStore.
 *
 * @param bucketId identifiant MediaStore du dossier.
 * @param name nom d'affichage du dossier (jamais nul ; « — » en repli si le
 *   ContentProvider ne renvoie pas de nom).
 * @param coverUri URI de l'image la plus récente du bucket — miniature de l'album.
 * @param imageCount nombre d'images contenues.
 */
data class GalleryAlbum(
    val bucketId: Long,
    val name: String,
    val coverUri: Uri,
    val imageCount: Int,
)

/**
 * Photothèque agrégée en UN passage de curseur : les dossiers (buckets) ET les
 * agrégats virtuels « Récents/Photos » (toutes les images) et « Favoris »
 * (IS_FAVORITE, API 30+). Permet de peupler le menu déroulant et la vue « Tous
 * les albums » sans relire le MediaStore.
 *
 * @param albums dossiers de l'appareil, ordonnés par image la plus récente.
 * @param allCount nombre total d'images.
 * @param allCover image la plus récente toutes catégories — couverture « Récents ».
 * @param favoritesSupported `true` dès Android 11 (API 30) où IS_FAVORITE existe.
 * @param favoriteCount nombre d'images favorites (0 si non supporté).
 * @param favoriteCover image favorite la plus récente, ou `null`.
 */
data class GalleryLibrary(
    val albums: List<GalleryAlbum>,
    val allCount: Int,
    val allCover: Uri?,
    val favoritesSupported: Boolean,
    val favoriteCount: Int,
    val favoriteCover: Uri?,
)

/**
 * Lecture seule du MediaStore pour la galerie in-app : photothèque (buckets +
 * agrégats) et URIs d'images triées par date d'ajout décroissante. Toutes les
 * requêtes s'exécutent sur [Dispatchers.IO] — aucun accès disque sur le main
 * thread.
 *
 * Seules des URIs `content://` légères sont retournées ; le décodage des
 * miniatures est délégué à Coil côté UI (sous-échantillonnage 300 px).
 */
class MediaStoreRepository(private val context: Context) {

    private val collection: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    private val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    /** IS_FAVORITE (« favoris système ») n'existe qu'à partir d'Android 11 (API 30). */
    private val favoritesSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Charge toute la photothèque en un seul passage de curseur : agrégation
     * manuelle par bucket (GROUP BY n'est plus accepté par le ContentProvider
     * depuis l'API 30) PLUS calcul des agrégats virtuels « Récents » et
     * « Favoris ». Les albums suivent l'ordre de leur image la plus récente.
     */
    suspend fun loadLibrary(): GalleryLibrary = withContext(Dispatchers.IO) {
        class BucketAccumulator(val name: String, val coverUri: Uri) {
            var count = 0
        }

        val buckets = LinkedHashMap<Long, BucketAccumulator>() // ordre DESC préservé
        var allCover: Uri? = null
        var allCount = 0
        var favoriteCover: Uri? = null
        var favoriteCount = 0

        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.BUCKET_ID)
            add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            if (favoritesSupported) add(MediaStore.MediaColumns.IS_FAVORITE)
        }.toTypedArray()

        context.contentResolver.query(collection, projection, null, null, sortOrder)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val favoriteCol = if (favoritesSupported)
                    cursor.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE) else -1
                while (cursor.moveToNext()) {
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                    if (allCover == null) allCover = uri
                    allCount++
                    val bucketId = cursor.getLong(bucketIdCol)
                    buckets.getOrPut(bucketId) {
                        BucketAccumulator(cursor.getString(nameCol) ?: "—", uri)
                    }.count++
                    if (favoriteCol >= 0 && cursor.getInt(favoriteCol) == 1) {
                        if (favoriteCover == null) favoriteCover = uri
                        favoriteCount++
                    }
                }
            }

        GalleryLibrary(
            albums = buckets.map { (bucketId, acc) ->
                GalleryAlbum(bucketId, acc.name, acc.coverUri, acc.count)
            },
            allCount = allCount,
            allCover = allCover,
            favoritesSupported = favoritesSupported,
            favoriteCount = favoriteCount,
            favoriteCover = favoriteCover,
        )
    }

    /**
     * URIs des images d'une source, triées par DATE_ADDED décroissante.
     *
     * @param bucketId dossier ciblé, ou `null` pour toutes les images
     *   (« Récents »/« Photos »). Ignoré si [favoritesOnly] est vrai.
     * @param favoritesOnly ne retourne que les favoris système (API 30+) ; liste
     *   vide sur les versions antérieures.
     */
    suspend fun loadImages(
        bucketId: Long?,
        favoritesOnly: Boolean = false,
    ): List<Uri> = withContext(Dispatchers.IO) {
        val (selection, selectionArgs) = when {
            favoritesOnly -> {
                if (!favoritesSupported) return@withContext emptyList()
                "${MediaStore.MediaColumns.IS_FAVORITE} = 1" to null
            }
            bucketId != null ->
                "${MediaStore.Images.Media.BUCKET_ID} = ?" to arrayOf(bucketId.toString())
            else -> null to null
        }

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
