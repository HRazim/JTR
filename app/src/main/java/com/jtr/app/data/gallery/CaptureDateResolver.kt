package com.jtr.app.data.gallery

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Date de PRISE DE VUE d'une image source (v7.1.108) — lue sur le URI du Photo Picker,
 * AVANT la compression : la copie JPEG recompressée par [GalleryImageStore.importFrom]
 * perd l'EXIF, c'est donc ici ou jamais. Chaîne de repli, du plus fiable au moins fiable :
 *
 *  1. EXIF `DateTimeOriginal` (l'appareil photo l'a écrit au déclenchement), interprété
 *     dans le fuseau de `OffsetTimeOriginal` quand il existe, sinon celui de l'appareil ;
 *  2. `DATE_TAKEN` de MediaStore (epoch ms, posé par la plupart des apps photo) ;
 *  3. `DATE_MODIFIED` de MediaStore (⚠️ en SECONDES — date du fichier, pas de la vue) ;
 *  4. l'heure d'import — l'ancien comportement, désormais dernier recours.
 *
 * Chaque étage est en meilleur effort : URI illisible, EXIF malformé, colonne absente ou
 * curseur vide font passer à l'étage suivant, JAMAIS échouer l'import. Le parsing et la
 * chaîne de repli sont PURS (testables en JVM) ; seule la lecture du contenu touche Android.
 */
object CaptureDateResolver {

    /** Format EXIF canonique — les chiffres sont invariants, d'où `ofPattern` sans locale. */
    private val EXIF_DATE_TIME: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    /**
     * Parse une date EXIF (`"2023:07:14 18:22:05"`) en epoch millis. [offset] est la valeur
     * de `OffsetTimeOriginal` (`"+02:00"`) : présent, il prime ; absent ou malformé, la date
     * est interprétée dans [zoneId] (le fuseau de l'appareil) — le choix le moins faux pour
     * une photo prise, le plus souvent, là où vit l'utilisateur. Toute valeur malformée
     * (dont le `0000:00:00 00:00:00` de certains appareils) rend `null`, jamais d'exception.
     */
    fun parseExifDateTime(
        dateTime: String?,
        offset: String? = null,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long? {
        val raw = dateTime?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        return runCatching {
            val local = LocalDateTime.parse(raw, EXIF_DATE_TIME)
            val zone = offset?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { runCatching { ZoneOffset.of(it) }.getOrNull() }
                ?: zoneId
            local.atZone(zone).toInstant().toEpochMilli()
        }.getOrNull()
    }

    /**
     * Chaîne de repli PURE : premier candidat exploitable (> 0 — MediaStore rend 0, pas
     * `null`, pour une colonne non renseignée), sinon [nowMillis]. C'est le contrat testé
     * en JVM ; [resolveForUri] ne fait que lui fournir les candidats.
     */
    fun resolve(
        exifMillis: Long?,
        dateTakenMillis: Long?,
        fileDateMillis: Long?,
        nowMillis: Long
    ): Long = exifMillis?.takeIf { it > 0 }
        ?: dateTakenMillis?.takeIf { it > 0 }
        ?: fileDateMillis?.takeIf { it > 0 }
        ?: nowMillis

    /** Résout la date de prise de vue de [uri]. À appeler hors du thread principal. */
    fun resolveForUri(
        context: Context,
        uri: Uri,
        nowMillis: Long = System.currentTimeMillis()
    ): Long = resolve(
        exifMillis = exifOriginalMillis(context, uri),
        dateTakenMillis = mediaStoreMillis(context, uri, MediaStore.Images.Media.DATE_TAKEN),
        fileDateMillis = mediaStoreMillis(context, uri, MediaStore.Images.Media.DATE_MODIFIED)
            ?.let { it * 1000L }, // DATE_MODIFIED est en secondes
        nowMillis = nowMillis
    )

    /** EXIF `DateTimeOriginal` (+ offset) du flux source ; `null` au moindre accroc. */
    private fun exifOriginalMillis(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            parseExifDateTime(
                exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
                exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
            )
        }
    }.getOrNull()

    /** Valeur brute d'une colonne MediaStore du URI ; `null` si absente, vide ou interdite. */
    private fun mediaStoreMillis(context: Context, uri: Uri, column: String): Long? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(column), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
                }
        }.getOrNull()
}
