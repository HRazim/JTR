package com.jtr.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.PersonCategoryJoin
import com.jtr.app.domain.model.SocialLinkEntity
import com.jtr.app.utils.DateCanonical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
 * En-tête de MARQUE (v7.1.27) écrit en clair à la racine de l'archive `.jtr`
 * (entrée `manifest.json`). Double rôle : (a) signature de marque « JTR-EXPORT »,
 * (b) détection « est-ce un export JTR ? » par le CONTENU — jamais par l'extension,
 * qu'un `content://` masque. N'altère EN RIEN le payload `data` (= `backup.json`) :
 * c'est une ENVELOPPE additive, donc les archives ANTÉRIEURES (sans manifeste)
 * restent parfaitement importables (champ nullable → null → chemin legacy).
 *
 * `formatVersion` ici = version de l'ENVELOPPE de marque (2), distincte du
 * [BackupPayload.formatVersion] (= version du SCHÉMA DE DONNÉES, toujours 1).
 */
data class BackupManifest(
    @SerializedName("_jtr") val brand: BackupBrand? = null
)

/** Champs de marque sérialisés en snake_case (cf. spec d'enveloppe). */
data class BackupBrand(
    @SerializedName("magic") val magic: String = "",
    @SerializedName("app") val app: String = "",
    @SerializedName("created_by") val createdBy: String = "",
    @SerializedName("format_version") val formatVersion: Int = 0,
    @SerializedName("exported_at") val exportedAt: String = ""
)

/**
 * Causes d'échec de RESTAURATION (v7.1.8) — permettent un message utilisateur DISTINCT
 * au lieu du fourre-tout « invalid or inaccessible file ».
 */
enum class RestoreError {
    /** Le flux du fichier n'a pas pu être ouvert (URI révoquée, fichier déplacé…). */
    UNREADABLE,
    /** Pas une archive `.jtr` valide (ZIP illisible ou `backup.json` absent/illisible). */
    NOT_ARCHIVE,
    /** Format de sauvegarde non reconnu (formatVersion différent). */
    UNSUPPORTED_VERSION,
    /** Structure corrompue (profils absents / champs requis vides). */
    CORRUPT,
    /** Écriture en base impossible (transaction annulée). */
    WRITE_FAILED
}

/** Exception de restauration portant une [reason] typée pour l'UI. */
class RestoreException(val reason: RestoreError, cause: Throwable? = null) :
    Exception("Restore failed: $reason", cause)

/**
 * Coercition de robustesse (v7.1.8) : Gson ne respecte NI les valeurs par défaut Kotlin NI
 * la non-nullité — un champ liste ABSENT du JSON arrive `null` malgré un type non-null, ce qui
 * fait planter `data class copy()`/le constructeur (vérification non-null). Le paramètre est
 * volontairement nullable pour qu'aucun « useless call » ne soit émis au site d'appel.
 */
private fun <T> coerceList(list: List<T>?): List<T> = list ?: emptyList()

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

            // Médias référencés → entrées ZIP « media/n_nom » (dédupliquées).
            // PORTABILITÉ (v5.5) : toute source LISIBLE est embarquée — fichier
            // local (file:// / chemin brut) MAIS AUSSI content:// (photo issue de
            // l'importation des contacts natifs). Avant, seuls les fichiers
            // locaux étaient packagés : les photos content:// arrivaient sur le
            // nouvel appareil sous forme d'URIs mortes. Une source illisible
            // reste telle quelle (meilleur effort, jamais de plantage d'export).
            val mediaByPath = LinkedHashMap<String, String>()
            fun register(value: String?): String? {
                if (value.isNullOrBlank()) return value
                mediaByPath[value]?.let { return MEDIA_TOKEN + it }
                openMediaInput(value)?.use { /* sondage de lisibilité */ } ?: return value
                val entryName = "$MEDIA_PREFIX${mediaByPath.size}_${mediaBaseName(value)}"
                mediaByPath[value] = entryName
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
                // MARQUE (v7.1.27) : manifeste de tête, AVANT la charge utile, à la
                // racine de l'archive. Signature « JTR-EXPORT » + horodatage ISO-UTC.
                // Additif : ne modifie pas `backup.json`, donc rétrocompat préservée.
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(gson.toJson(buildManifest()).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(JSON_ENTRY))
                zip.write(gson.toJson(payload).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                mediaByPath.forEach { (originalPath, entryName) ->
                    val source = openMediaInput(originalPath) ?: return@forEach
                    zip.putNextEntry(ZipEntry(entryName))
                    source.use { it.copyTo(zip) }
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
            var manifestJson: String? = null // marque v7.1.27 (peut être absente = legacy)
            val extracted = HashMap<String, String>() // entrée zip → chemin restauré

            // 1) LECTURE via le FLUX SAF (content://) — jamais un chemin File brut. Toute
            //    erreur d'ouverture (URI révoquée, fichier déplacé) → UNREADABLE distinct.
            val input = try {
                appContext.contentResolver.openInputStream(uri)
            } catch (e: Exception) {
                throw RestoreException(RestoreError.UNREADABLE, e)
            } ?: throw RestoreException(RestoreError.UNREADABLE)

            // 2) PARCOURS DU ZIP EN STREAMING (jamais tout en mémoire) : backup.json lu en
            //    texte, médias extraits à la volée vers filesDir/photos. Un flux non-ZIP ou
            //    tronqué → NOT_ARCHIVE.
            try {
                ZipInputStream(BufferedInputStream(input)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == MANIFEST_ENTRY ->
                                manifestJson = zip.readBytes().toString(Charsets.UTF_8)
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
            } catch (e: java.util.zip.ZipException) {
                extracted.values.forEach { p -> runCatching { File(p).delete() } }
                throw RestoreException(RestoreError.NOT_ARCHIVE, e)
            }

            // À partir d'ici, tout échec doit NETTOYER les médias déjà extraits :
            // aucun fichier orphelin dans filesDir/photos après un import raté.
            try {
                // 3a) MARQUE v7.1.27 — VALIDATION PAR LE CONTENU. Si un manifeste est
                //     présent, sa signature DOIT être « JTR-EXPORT » (on ne se fie jamais
                //     à l'extension, qu'un content:// masque). ABSENT → archive ANTÉRIEURE
                //     à la marque : on bascule sur le parsing legacy de backup.json
                //     (rétrocompat absolue — un ancien export n'est JAMAIS rejeté ici).
                manifestJson?.let { mj ->
                    val brand = try {
                        gson.fromJson(mj, BackupManifest::class.java)?.brand
                    } catch (e: JsonParseException) {
                        throw RestoreException(RestoreError.NOT_ARCHIVE, e)
                    }
                    if (brand?.magic != BRAND_MAGIC)
                        throw RestoreException(RestoreError.NOT_ARCHIVE)
                }

                // 3b) PARSE + VALIDATION de structure AVANT toute écriture Room.
                val payload = try {
                    gson.fromJson(
                        json ?: throw RestoreException(RestoreError.NOT_ARCHIVE),
                        BackupPayload::class.java
                    )
                } catch (e: JsonParseException) {
                    throw RestoreException(RestoreError.NOT_ARCHIVE, e)
                } ?: throw RestoreException(RestoreError.NOT_ARCHIVE)

                if (payload.formatVersion != FORMAT_VERSION)
                    throw RestoreException(RestoreError.UNSUPPORTED_VERSION)
                val persons = payload.persons
                    ?: throw RestoreException(RestoreError.CORRUPT)
                if (persons.any { it.id.isBlank() || it.firstName.isBlank() })
                    throw RestoreException(RestoreError.CORRUPT)
                val categories = payload.categories.orEmpty()
                if (categories.any { it.id.isBlank() || it.name.isBlank() })
                    throw RestoreException(RestoreError.CORRUPT)
                val groups = payload.groups.orEmpty()
                val joins = payload.joins.orEmpty()
                val socialLinks = payload.socialLinks.orEmpty()

                // Réécriture TRANSPARENTE des jetons jtr-media:// vers les chemins
                // ABSOLUS de CE téléphone (les fichiers viennent d'être copiés dans
                // notre filesDir/photos) ; jeton sans média → champ null, jamais
                // d'URI morte en base.
                fun rewrite(value: String?, asFileUri: Boolean): String? {
                    if (value == null || !value.startsWith(MEDIA_TOKEN)) return value
                    val path = extracted[value.removePrefix(MEDIA_TOKEN)] ?: return null
                    return if (asFileUri) Uri.fromFile(File(path)).toString() else path
                }

                // Horodatages v6.1.7 (createdAt / addedAt) : une archive ANTÉRIEURE à
                // v18 ne les porte pas → Gson les laisse à 0. On retombe alors sur la
                // date de restauration (les archives récentes conservent leurs valeurs).
                val restoreTs = System.currentTimeMillis()
                fun orRestore(ts: Long) = if (ts > 0L) ts else restoreTs

                // 4) Insertion ATOMIQUE (v5.5) dans l'ordre des dépendances : dossiers →
                // catégories → profils → liaisons → réseaux sociaux. Un échec au
                // milieu annule TOUT (withTransaction) — jamais de base semi-restaurée.
                // Rétro-compat v7.1.6 : les relations héritées (par nom, sans linkedPersonId)
                // sont insérées telles quelles et résolues au runtime (homonymes → « à vérifier »).
                try {
                    db.withTransaction {
                        groups.forEach { db.categoryGroupDao().insert(it.copy(imagePath = rewrite(it.imagePath, false), createdAt = orRestore(it.createdAt))) }
                        // v7.1.48 : `updatedAt` reçoit le MÊME traitement que `createdAt` — une
                        // archive antérieure à v22 ne porte pas le champ, Gson le laisse à 0 et
                        // la fiche afficherait « 1 janvier 1970 » en « Dernière modification ».
                        categories.forEach { db.categoryDao().insert(it.copy(imagePath = rewrite(it.imagePath, false), createdAt = orRestore(it.createdAt), updatedAt = orRestore(it.updatedAt))) }
                        // v7.1.0 — normalise les dates des sauvegardes ANCIENNES (chiffres bruts
                        // locale-dépendants) vers l'ISO canonique : round-trip sûr, locale-libre.
                        persons.forEach {
                            // Gson n'honore NI les défauts Kotlin NI la non-nullité : un `.jtr`
                            // ANTÉRIEUR aux sections de notes (v7.0.3) n'a pas de `noteSections`
                            // → Gson le laisse `null` malgré le type non-null, ce qui faisait
                            // ÉCHOUER `copy()`/le constructeur (NPE « parameter noteSections »)
                            // → toute la restauration échouait. On coerce avant reconstruction.
                            val safe = it.copy(
                                noteSections = coerceList(it.noteSections),
                                photoUri = rewrite(it.photoUri, true)
                            )
                            db.personDao().insert(canonicalizeDates(safe))
                        }
                        db.personCategoryDao().insertAll(joins.map { it.copy(addedAt = orRestore(it.addedAt)) })
                        socialLinks.forEach { db.socialLinkDao().insert(it) }
                    }
                } catch (e: RestoreException) {
                    throw e
                } catch (e: Exception) {
                    throw RestoreException(RestoreError.WRITE_FAILED, e)
                }

                persons.size
            } catch (e: Throwable) {
                // Nettoyage des médias extraits : aucun fichier orphelin après un import raté.
                // La cause réelle reste portée par RestoreException.cause (→ message UI distinct).
                extracted.values.forEach { path -> runCatching { File(path).delete() } }
                throw e
            }
        }
    }

    /**
     * Normalise les dates d'un profil restauré vers l'ISO canonique (v7.1.0). Les archives
     * récentes sont déjà en ISO (no-op) ; les anciennes portent des chiffres bruts ordonnés
     * par la locale : l'anniversaire est dérivé du scalaire `birthdate` (vérité), les autres
     * dates désambiguïsées par validité — une valeur incertaine est PRÉSERVÉE (aucune perte).
     */
    private fun canonicalizeDates(p: Person): Person {
        val lines = p.dateLines ?: return p
        val tieBreak = DateCanonical.currentOrder(Locale.getDefault())
        var birthdayUsed = false
        val normalized = lines.map { line ->
            // v7.1.37 (B3b) — `--MM-dd` (date sans année) est DÉJÀ canonique → round-trip intact.
            if (line.value.isBlank() || DateCanonical.isIso(line.value) ||
                DateCanonical.isMonthDay(line.value)) return@map line
            // « birthday » : const FieldTypes.DATE_BIRTHDAY (literal pour découpler du module UI).
            val iso = if (line.label == "birthday" && !birthdayUsed && p.birthdate != null) {
                birthdayUsed = true
                DateCanonical.millisToIso(p.birthdate)
            } else {
                DateCanonical.legacyRawDigitsToIso(line.value, tieBreak)
            }
            if (iso != null) line.copy(value = iso) else line
        }
        return p.copy(dateLines = normalized)
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

    /**
     * Ouvre le flux de lecture d'une source média exportable : fichier local
     * (file:// / chemin brut) ou content:// via le ContentResolver (photo de
     * contact natif importée). Null si la source est illisible.
     */
    private fun openMediaInput(value: String): InputStream? {
        resolveLocalFile(value)?.let { file ->
            return runCatching { file.inputStream() }.getOrNull()
        }
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        if (uri.scheme != "content") return null
        return runCatching { appContext.contentResolver.openInputStream(uri) }.getOrNull()
    }

    /** Nom de base SÛR pour l'entrée ZIP (alphanumérique, borné, jamais vide). */
    private fun mediaBaseName(value: String): String {
        val raw = resolveLocalFile(value)?.name
            ?: runCatching { Uri.parse(value).lastPathSegment }.getOrNull()
                ?.substringAfterLast('/')
            ?: "img"
        return raw.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64).ifBlank { "img" }
    }

    /**
     * Construit l'en-tête de marque écrit dans `manifest.json` (v7.1.27).
     * `exported_at` en ISO-8601 UTC (`Instant.now()`), locale-indépendant.
     */
    private fun buildManifest() = BackupManifest(
        brand = BackupBrand(
            magic = BRAND_MAGIC,
            app = "Just To Remember",
            createdBy = "JTR",
            formatVersion = ENVELOPE_VERSION,
            exportedAt = Instant.now().toString()
        )
    )

    companion object {
        /** Version du SCHÉMA DE DONNÉES (payload `backup.json`). Inchangée. */
        const val FORMAT_VERSION = 1

        /** Signature de marque écrite/lue dans `manifest.json`. */
        const val BRAND_MAGIC = "JTR-EXPORT"
        /** Version de l'ENVELOPPE de marque (distincte du schéma de données). */
        const val ENVELOPE_VERSION = 2

        private const val JSON_ENTRY = "backup.json"
        private const val MANIFEST_ENTRY = "manifest.json"
        private const val MEDIA_PREFIX = "media/"
        private const val MEDIA_TOKEN = "jtr-media://"

        // Nommage de marque (v7.1.27) : « JTR_Backup_<date>_<heure>.jtr ». Date
        // locale-indépendante (pattern fixe + Locale.US), suffixe HHmmss anti-collision
        // pour deux exports le même jour.
        private val FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss", Locale.US)

        /** Nom de fichier proposé au sélecteur CreateDocument (extension .jtr). */
        fun suggestedFileName(): String =
            "JTR_Backup_${LocalDateTime.now().format(FILE_STAMP)}.jtr"
    }
}
