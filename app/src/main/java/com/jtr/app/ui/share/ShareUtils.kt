package com.jtr.app.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.jtr.app.R
import com.jtr.app.domain.model.Person
import com.jtr.app.ui.person.FieldTypes
import com.jtr.app.ui.person.millisToRawDigits
import com.jtr.app.ui.person.resolveDateFormatSpec
import com.jtr.app.ui.person.storedDateToMillis
import com.jtr.app.ui.person.typeLabelResOrNull
import com.jtr.app.utils.DateCanonical
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Élément partageable côté Catégories : une catégorie (compteur de contacts) ou
 * un dossier (catégories membres + sous-groupes). Les libellés localisés sont
 * résolus au rendu (texte/PDF via Context, aperçu via stringResource).
 */
data class ShareCategoryItem(
    val name: String,
    val isFolder: Boolean,
    val personCount: Int = 0,
    val memberNames: List<String> = emptyList(),
    val subGroupCount: Int = 0
)

/**
 * Boîte à outils du partage contextuel (TEXTE / PNG / PDF) — 100 % APIs natives :
 * [PdfDocument] pour le document, Intent ACTION_SEND pour la diffusion, et le
 * [FileProvider] de l'app pour exposer les fichiers temporaires (cache/share).
 */
object ShareUtils {

    // ── Géométrie PDF : page A4 en points (72 dpi) avec marges symétriques ────
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 48f

    // ── Fichiers temporaires & FileProvider ───────────────────────────────────

    /** Dossier éphémère des exports (exposé par file_paths.xml). */
    private fun shareDir(context: Context): File =
        File(context.cacheDir, "share").also { it.mkdirs() }

    private fun fileProviderUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Purge les exports précédents — aucun fichier temporaire ne s'accumule. */
    fun clearStaleExports(context: Context) {
        shareDir(context).listFiles()?.forEach { it.delete() }
    }

    // ── Intents de partage ─────────────────────────────────────────────────────

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.share_action))
        )
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = fileProviderUri(context, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            // Permission de lecture RÉVOCABLE accordée à l'app cible uniquement.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.share_action))
        )
    }

    /** Sauvegarde un bitmap capturé (GraphicsLayer) en PNG dans le cache de partage. */
    fun saveBitmapPng(context: Context, bitmap: Bitmap): File {
        val file = File(shareDir(context), "jtr_share_${UUID.randomUUID()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    // ── Construction du contenu (lignes partagées TEXTE ↔ PDF) ────────────────

    /**
     * Formate une date STOCKÉE (ISO canonique, repli hérité géré) en chaîne localisée
     * « JJ/MM/AAAA » selon la locale courante. Interprétation LOCALE-LIBRE (v7.1.0).
     */
    private fun formatStoredDate(value: String): String {
        val millis = storedDateToMillis(value) ?: return value
        val spec = resolveDateFormatSpec(Locale.getDefault())
        val raw = millisToRawDigits(millis, spec.order)
        val sb = StringBuilder()
        var idx = 0
        spec.segmentLengths.forEachIndexed { i, len ->
            if (i > 0) sb.append(spec.separator)
            sb.append(raw, idx, idx + len)
            idx += len
        }
        return sb.toString()
    }

    private fun typeLabel(context: Context, types: List<com.jtr.app.ui.person.TypeOption>, key: String): String =
        typeLabelResOrNull(types, key)?.let { context.getString(it) } ?: key

    /** Lignes d'information d'un profil (sans le nom), localisées, champs vides omis. */
    private fun personFieldLines(context: Context, person: Person): List<String> {
        val lines = mutableListOf<String>()

        // Poste « chez » Entreprise · Département (même règle que la fiche détail).
        val at = context.getString(R.string.person_job_at)
        val jobHead = when {
            !person.jobTitle.isNullOrBlank() && !person.company.isNullOrBlank() ->
                "${person.jobTitle} $at ${person.company}"
            !person.jobTitle.isNullOrBlank() -> person.jobTitle
            !person.company.isNullOrBlank() -> person.company
            else -> null
        }
        listOfNotNull(jobHead, person.department?.takeIf { it.isNotBlank() })
            .joinToString(" · ").takeIf { it.isNotBlank() }
            ?.let { lines += "${context.getString(R.string.section_work)} : $it" }

        person.origin?.takeIf { it.isNotBlank() }
            ?.let { lines += "${context.getString(R.string.person_origin_label)} : $it" }
        person.city?.takeIf { it.isNotBlank() }
            ?.let { lines += "${context.getString(R.string.person_city_label)} : $it" }

        // Dates clés (lignes dynamiques, repli sur le scalaire birthdate legacy en ISO).
        val dates = person.dateLines?.filter { it.value.isNotBlank() }?.takeIf { it.isNotEmpty() }
            ?: person.birthdate?.let {
                listOf(com.jtr.app.domain.model.DynamicLine(
                    value = DateCanonical.millisToIso(it), label = FieldTypes.DATE_BIRTHDAY))
            }.orEmpty()
        dates.forEach { line ->
            lines += "${typeLabel(context, FieldTypes.DATE, line.label)} : ${formatStoredDate(line.value)}"
        }

        person.relationLines?.filter { it.value.isNotBlank() }?.forEach { line ->
            lines += "${typeLabel(context, FieldTypes.RELATION, line.label)} : ${line.value}"
        }

        val phones = person.phoneLines?.filter { it.value.isNotBlank() }?.map { it.value }
            ?: listOfNotNull(person.phoneNumber?.takeIf { it.isNotBlank() })
        if (phones.isNotEmpty()) {
            lines += "${context.getString(R.string.section_phones)} : ${phones.joinToString(", ")}"
        }
        val emails = person.emailLines?.filter { it.value.isNotBlank() }?.map { it.value }
            ?: listOfNotNull(person.email?.takeIf { it.isNotBlank() })
        if (emails.isNotEmpty()) {
            lines += "${context.getString(R.string.section_emails)} : ${emails.joinToString(", ")}"
        }

        // v7.0.3 — sections de notes personnalisables ; repli sur les notes héritées
        // (notes/likes) pour les profils non encore convertis.
        if (person.noteSections.isNotEmpty()) {
            person.noteSections.sortedBy { it.order }
                .filter { it.content.isNotBlank() }
                .forEach { lines += "${it.title} : ${it.content}" }
        } else {
            person.notes?.takeIf { it.isNotBlank() }
                ?.let { lines += "${context.getString(R.string.person_notes_label)} : $it" }
            person.likes?.takeIf { it.isNotBlank() }
                ?.let { lines += "${context.getString(R.string.person_likes_label)} : $it" }
        }

        return lines
    }

    /** Format TEXTE stylisé : fiches complètes séparées par un filet. */
    fun buildPersonsShareText(context: Context, persons: List<Person>): String =
        persons.joinToString("\n\n━━━━━━━━━━━━\n\n") { person ->
            buildString {
                appendLine("👤 ${person.fullName}")
                personFieldLines(context, person).forEach { appendLine("• $it") }
            }.trimEnd()
        }

    /** Format TEXTE des catégories/dossiers : nom, compteurs, membres. */
    fun buildCategoriesShareText(context: Context, items: List<ShareCategoryItem>): String =
        items.joinToString("\n\n") { item ->
            buildString {
                appendLine("${if (item.isFolder) "🗂" else "📁"} ${item.name} — ${itemSubtitle(context, item)}")
                item.memberNames.forEach { appendLine("• $it") }
            }.trimEnd()
        }

    /** Sous-titre localisé d'un élément (réutilise les pluriels existants). */
    fun itemSubtitle(context: Context, item: ShareCategoryItem): String =
        if (item.isFolder) {
            context.getString(R.string.categories_group_counter,
                item.memberNames.size, item.subGroupCount)
        } else {
            context.getString(R.string.categories_person_count, item.personCount)
        }

    // ── Génération PDF (PdfDocument natif) ─────────────────────────────────────

    private fun titlePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.BLACK
    }

    private fun bodyPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
        color = Color.DKGRAY
    }

    /**
     * Décodage SYNCHRONE et BORNÉ d'une image de profil (file:// ou content://)
     * pour l'embarquer dans le PDF : double passe BitmapFactory (bornes puis
     * inSampleSize ≥ cible) — jamais la pleine résolution en mémoire. Retourne
     * null si la source est illisible : le PDF reste généré, sans photo.
     */
    private fun decodeScaledBitmap(context: Context, value: String?, targetPx: Int): Bitmap? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val uri = Uri.parse(value)
            fun open() = context.contentResolver.openInputStream(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open()?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetPx &&
                bounds.outHeight / (sample * 2) >= targetPx) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            open()?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()
    }

    /**
     * Écrit les fiches PDF des profils : UNE page A4 par contact (suite sur la
     * page suivante si débordement), photo de profil incrustée (décodage
     * synchrone — l'export N'attend PAS Coil, les images sont toujours
     * visibles), titre en gras puis lignes d'information.
     */
    fun writePersonsPdf(context: Context, persons: List<Person>): File {
        val doc = PdfDocument()
        val writer = PdfWriter(doc)
        persons.forEachIndexed { index, person ->
            if (index > 0) writer.newPage()
            decodeScaledBitmap(context, person.photoUri, 256)?.let { photo ->
                writer.image(photo, 64f)
            }
            writer.line(person.fullName, titlePaint())
            writer.spacer(6f)
            personFieldLines(context, person).forEach { writer.line(it, bodyPaint()) }
        }
        return finishPdf(context, doc, writer, "jtr_contacts")
    }

    /** Écrit le résumé PDF des catégories/dossiers sélectionnés (flux continu). */
    fun writeCategoriesPdf(context: Context, items: List<ShareCategoryItem>): File {
        val doc = PdfDocument()
        val writer = PdfWriter(doc)
        items.forEachIndexed { index, item ->
            if (index > 0) writer.spacer(14f)
            writer.line("${item.name} — ${itemSubtitle(context, item)}", titlePaint())
            writer.spacer(4f)
            item.memberNames.forEach { writer.line("• $it", bodyPaint()) }
        }
        return finishPdf(context, doc, writer, "jtr_categories")
    }

    private fun finishPdf(context: Context, doc: PdfDocument, writer: PdfWriter, prefix: String): File {
        writer.finish()
        val file = File(shareDir(context), "${prefix}_${UUID.randomUUID()}.pdf")
        // try/finally : le document natif est TOUJOURS libéré, même si l'écriture échoue.
        try {
            file.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return file
    }

    /**
     * Curseur d'écriture PDF : gère la pagination (saut de page automatique) et
     * le retour à la ligne — la coupure se fait au dernier espace qui tient dans
     * la largeur utile (PAGE_W − 2×MARGIN), sinon au caractère près.
     */
    private class PdfWriter(private val doc: PdfDocument) {
        private var pageNumber = 1
        private var page: PdfDocument.Page = startPage()
        private var y = MARGIN

        private fun startPage(): PdfDocument.Page =
            doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())

        fun newPage() {
            doc.finishPage(page)
            pageNumber += 1
            page = startPage()
            y = MARGIN
        }

        fun spacer(height: Float) {
            y += height
        }

        /** Avatar carré (recadrage centré) incrusté à la marge, en points PDF. */
        fun image(bitmap: Bitmap, size: Float) {
            if (y + size > PAGE_H - MARGIN) newPage()
            val side = minOf(bitmap.width, bitmap.height)
            val src = Rect(
                (bitmap.width - side) / 2, (bitmap.height - side) / 2,
                (bitmap.width + side) / 2, (bitmap.height + side) / 2
            )
            val dst = RectF(MARGIN, y, MARGIN + size, y + size)
            page.canvas.drawBitmap(bitmap, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            y += size + 10f
        }

        fun line(text: String, paint: Paint) {
            val maxWidth = PAGE_W - 2 * MARGIN
            val lineHeight = paint.textSize * 1.45f
            var remaining = text
            while (remaining.isNotEmpty()) {
                var count = paint.breakText(remaining, true, maxWidth, null)
                if (count <= 0) break
                // Coupure propre : on recule au dernier espace si on tronque un mot.
                if (count < remaining.length) {
                    val lastSpace = remaining.lastIndexOf(' ', count - 1)
                    if (lastSpace > 0) count = lastSpace + 1
                }
                if (y + lineHeight > PAGE_H - MARGIN) newPage()
                y += lineHeight
                page.canvas.drawText(remaining.substring(0, count).trimEnd(), MARGIN, y, paint)
                remaining = remaining.substring(count)
            }
        }

        fun finish() {
            doc.finishPage(page)
        }
    }
}
