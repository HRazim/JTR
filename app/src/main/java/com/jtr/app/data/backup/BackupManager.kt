package com.jtr.app.data.backup

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.PersonCategoryJoin
import com.jtr.app.domain.model.SocialLinkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Charge utile JSON du fichier `.jtr` — instantané COMPLET de la base Room
 * (profils corbeille incluse, catégories, dossiers, liaisons N-N, réseaux).
 * Les listes sont nullables pour permettre la VALIDATION post-parse d'un
 * fichier corrompu (Gson laisse `null` les champs manquants).
 */
data class BackupPayload(
    val formatVersion: Int = 0,
    val exportedAt: Long = 0L,
    val persons: List<Person>? = null,
    val categories: List<Category>? = null,
    val groups: List<CategoryGroup>? = null,
    val joins: List<PersonCategoryJoin>? = null,
    val socialLinks: List<SocialLinkEntity>? = null
)

/**
 * Module de sauvegarde/restauration locale — fichier archive `.jtr` :
 * un ZIP contenant `backup.json` (Gson) + un dossier `media/` avec les photos
 * locales référencées (profils, catégories, dossiers).
 *
 * Mécanique fichiers :
 *  - à l'EXPORT, chaque champ image pointant vers un fichier local est réécrit
 *    en jeton `jtr-media://media/n_nom` et le fichier est packagé dans le ZIP ;
 *  - à l'IMPORT, les médias sont extraits vers filesDir/photos (nom unique,
 *    protection « zip slip ») et les jetons sont résolus vers les nouveaux
 *    chemins AVANT insertion Room (stratégie REPLACE → fusion, doublons écrasés).
 */
class BackupManager(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val gson = Gson()

    /** Exporte la base complète vers [uri] (choisi via CreateDocument). */
    suspend fun exportTo(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val persons = db.personDao().getAllSync()
            val categories = db.categoryDao().getAllSync()
            val groups = db.categoryGroupDao().getAllSync()
            val joins = db.personCategoryDao().getAllJoinsSync()
            val socialLinks = db.socialLinkDao().getAllSync()

            // Médias locaux référencés → entrées ZIP « media/n_nom » (dédupliquées).
            val mediaByPath = LinkedHashMap<String, String>()
            fun register(value: String?): String? {
                if (value.isNullOrBlank()) return value
                val file = resolveLocalFile(value) ?: return value
                val entryName = mediaByPath.getOrPut(value) {
                    "$MEDIA_PREFIX${mediaByPath.size}_${file.name}"
                }
                return MEDIA_TOKEN + entryName
            }

            val payload = BackupPayload(
                formatVersion = FORMAT_VERSION,
                exportedAt = System.currentTimeMillis(),
                persons = persons.map { it.copy(photoUri = register(it.photoUri)) },
                categories = categories.map { it.copy(imagePath = register(it.imagePath)) },
                groups = groups.map { it.copy(imagePath = register(it.imagePath)) },
                joins = joins,
                socialLinks = socialLinks
            )

            val output = appContext.contentResolver.openOutputStream(uri)
                ?: error("Flux d'écriture indisponible")
            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                zip.putNextEntry(ZipEntry(JSON_ENTRY))
                zip.write(gson.toJson(payload).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                mediaByPath.forEach { (originalPath, entryName) ->
                    val file = resolveLocalFile(originalPath) ?: return@forEach
                    zip.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            persons.size
        }
    }

    /**
     * Restaure une archive `.jtr` depuis [uri] (choisi via GetContent) :
     * extraction, VALIDATION de structure, réécriture des chemins médias,
     * puis insertion REPLACE (fusion : les fiches au même id sont écrasées).
     */
    suspend fun importFrom(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var json: String? = null
            val extracted = HashMap<String, String>() // entrée zip → chemin restauré

            val input = appContext.contentResolver.openInputStream(uri)
                ?: error("Fichier illisible")
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == JSON_ENTRY ->
                            json = zip.readBytes().toString(Charsets.UTF_8)
                        entry.name.startsWith(MEDIA_PREFIX) && !entry.isDirectory -> {
                            // Anti « zip slip » : seul le nom de base est conservé.
                            val safeName = File(entry.name).name
                            val dir = File(appContext.filesDir, "photos").also { it.mkdirs() }
                            val dest = File(dir, "restore_${UUID.randomUUID()}_$safeName")
                            dest.outputStream().use { zip.copyTo(it) }
                            extracted[entry.name] = dest.absolutePath
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            // Validation de structure (anti-corruption) AVANT toute écriture Room.
            val payload = gson.fromJson(
                json ?: error("backup.json absent de l'archive"),
                BackupPayload::class.java
            ) ?: error("JSON invalide")
            check(payload.formatVersion == FORMAT_VERSION) { "Version de sauvegarde inconnue" }
            val persons = checkNotNull(payload.persons) { "Structure invalide : profils absents" }
            check(persons.all { it.id.isNotBlank() && it.firstName.isNotBlank() }) {
                "Structure invalide : profil corrompu"
            }
            val categories = payload.categories.orEmpty()
            check(categories.all { it.id.isNotBlank() && it.name.isNotBlank() }) {
                "Structure invalide : catégorie corrompue"
            }
            val groups = payload.groups.orEmpty()
            val joins = payload.joins.orEmpty()
            val socialLinks = payload.socialLinks.orEmpty()

            // Réécriture des jetons médias vers les fichiers extraits.
            fun rewrite(value: String?, asFileUri: Boolean): String? {
                if (value == null || !value.startsWith(MEDIA_TOKEN)) return value
                val path = extracted[value.removePrefix(MEDIA_TOKEN)] ?: return null
                return if (asFileUri) Uri.fromFile(File(path)).toString() else path
            }

            // Insertion dans l'ordre des dépendances : dossiers → catégories →
            // profils → liaisons → réseaux sociaux.
            groups.forEach { db.categoryGroupDao().insert(it.copy(imagePath = rewrite(it.imagePath, false))) }
            categories.forEach { db.categoryDao().insert(it.copy(imagePath = rewrite(it.imagePath, false))) }
            persons.forEach { db.personDao().insert(it.copy(photoUri = rewrite(it.photoUri, true))) }
            db.personCategoryDao().insertAll(joins)
            socialLinks.forEach { db.socialLinkDao().insert(it) }

            persons.size
        }
    }

    /** Résout un champ image vers un fichier local existant (file:// ou chemin brut). */
    private fun resolveLocalFile(value: String): File? {
        val file = when {
            value.startsWith("file://") -> Uri.parse(value).path?.let { File(it) }
            value.startsWith("/") -> File(value)
            else -> null
        }
        return file?.takeIf { it.exists() && it.isFile }
    }

    companion object {
        const val FORMAT_VERSION = 1
        private const val JSON_ENTRY = "backup.json"
        private const val MEDIA_PREFIX = "media/"
        private const val MEDIA_TOKEN = "jtr-media://"

        /** Nom de fichier proposé au sélecteur CreateDocument (extension .jtr). */
        fun suggestedFileName(): String =
            "jtr_backup_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.jtr"
    }
}
