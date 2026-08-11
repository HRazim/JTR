package com.jtr.app.data.contacts

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.provider.ContactsContract
import com.jtr.app.R
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.domain.model.DynamicLine
import com.jtr.app.domain.model.NOTE_ICON_NOTES
import com.jtr.app.domain.model.NoteSection
import com.jtr.app.domain.model.deriveNoteSections
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.SocialLinkEntity
import com.jtr.app.ui.person.FieldTypes
import com.jtr.app.utils.DateCanonical
import com.jtr.app.utils.LocaleManager
import com.jtr.app.utils.SocialPlatform
import com.jtr.app.worker.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Importateur des contacts NATIFS du téléphone (onboarding v5.3) — 100 %
 * [android.content.ContentResolver], zéro dépendance tierce.
 *
 * Stratégie de lecture : UNE SEULE requête sur la table `Data` de
 * [ContactsContract] (au lieu d'une sous-requête par contact), filtrée sur les
 * 10 mimetypes utiles (nom structuré, surnom, téléphone, email, organisation,
 * évènement, note, site web, adresse postale, relation), puis regroupement en
 * mémoire par `CONTACT_ID`. Les colonnes génériques DATA1..DATA10 portent la
 * valeur selon le mimetype :
 *  - StructuredName : DATA2 = prénom, DATA3 = nom, DATA4 = préfixe (Dr…),
 *    DATA5 = 2ᵉ prénom, DATA6 = suffixe (Jr…), DATA7/8/9 = phonétiques ;
 *  - Organization : DATA1 = société, DATA4 = poste, DATA5 = département ;
 *  - Phone / Email : DATA1 = valeur, DATA2 = TYPE, DATA3 = libellé perso (B2) ;
 *  - Event : DATA1 = date (ISO yyyy-MM-dd), DATA2 = TYPE, DATA3 = libellé perso (B3) ;
 *  - Relation : DATA1 = nom de la personne liée, DATA2 = TYPE, DATA3 = libellé perso (B5) ;
 *  - Nickname / Note / Website : DATA1 = valeur principale.
 * `PHOTO_URI` (photo d'affichage pleine résolution, repli sur la vignette si
 * absente) et `PHOTO_THUMBNAIL_URI` sont des colonnes jointes du contact,
 * disponibles sur chaque ligne.
 *
 * Les photos sont COPIÉES dans filesDir/photos (fichiers `import_<uuid>.jpg`) :
 * les vignettes restent visibles même si la permission READ_CONTACTS est
 * révoquée plus tard. Tout s'exécute sur [Dispatchers.IO].
 */
/**
 * Contact natif LÉGER pour l'écran de sélection de l'onboarding (v5.4.1) :
 * id + nom + vignette — ~quelques dizaines d'octets par entrée, un répertoire
 * de 3 000+ contacts reste négligeable en mémoire (les photos sont chargées
 * paresseusement par Coil, ligne par ligne, dans la LazyColumn).
 */
data class DeviceContact(
    val id: Long,
    val displayName: String,
    val photoUri: String?,
    // v7.1.41 (B6) — bitmask des capacités natives ([CAP_PHONE]…[CAP_NOTE]) pour l'aperçu
    // (chips) de l'écran de sélection. Défaut 0 → tout autre site de construction reste valide ;
    // PUREMENT INDICATIF : jamais relu par [ContactsImporter.import] (qui re-lit tout).
    val capabilities: Int = 0
)

/**
 * v7.1.41 (B6) — Capacités d'un contact natif, encodées 1 bit / capacité dans
 * [DeviceContact.capabilities]. Alimentent l'aperçu (chips) de l'écran de sélection
 * d'import ; purement indicatives. [CAP_PHOTO] est déduit de la vignette (pas un mimetype) ;
 * l'anniversaire ([CAP_BIRTHDAY]) est distingué des autres dates ([CAP_DATE]) via `Event.TYPE`.
 */
const val CAP_PHONE = 1 shl 0
const val CAP_EMAIL = 1 shl 1
const val CAP_PHOTO = 1 shl 2
const val CAP_BIRTHDAY = 1 shl 3
const val CAP_DATE = 1 shl 4
const val CAP_ADDRESS = 1 shl 5
const val CAP_WEBSITE = 1 shl 6
const val CAP_COMPANY = 1 shl 7
const val CAP_RELATION = 1 shl 8
const val CAP_NOTE = 1 shl 9

/**
 * Bilan d'une importation (v7.1.28 ; [updated] ajouté en v7.1.42/B7) : [imported] =
 * contacts effectivement insérés, [updated] = contacts EXISTANTS fusionnés (mode UPDATE,
 * comptage DISTINCT), [skipped] = contacts IGNORÉS (déjà présents et non fusionnés :
 * mode SKIP, cible ambiguë, ou UPDATE no-op). Permet à l'UI d'afficher le récap et
 * garantit l'invariant « ré-importer ne duplique pas ».
 */
data class ImportResult(val imported: Int, val updated: Int, val skipped: Int)

/**
 * v7.1.42 (B7) — que faire d'un contact natif déjà présent dans JTR (détecté par
 * téléphone/email, JAMAIS par nom). [SKIP] = comportement historique (ignorer) ;
 * [UPDATE] = fusionner sans rien écraser (cf. [ContactsImporter.mergePerson]) ;
 * [IMPORT_ANYWAY] = insérer un doublon ASSUMÉ. Choix GLOBAL au lot, défaut [SKIP].
 */
enum class DuplicateStrategy { SKIP, UPDATE, IMPORT_ANYWAY }

class ContactsImporter(context: Context) {

    private val appContext = context.applicationContext
    private val personDao = AppDatabase.getInstance(appContext).personDao()
    // v7.1.39 (B4) — liens sociaux importés depuis les sites web natifs (table social_links).
    private val socialLinkDao = AppDatabase.getInstance(appContext).socialLinkDao()

    /**
     * Liste ALPHABÉTIQUE des contacts du répertoire natif (projection minimale
     * id / nom / vignette) — alimente l'écran d'importation sélective.
     */
    suspend fun listDeviceContacts(): List<DeviceContact> = withContext(Dispatchers.IO) {
        // v7.1.41 (B6) — bitmask des capacités par contact (UNE requête groupée, aucun N+1) ;
        // mergé ci-dessous dans chaque DeviceContact pour l'aperçu (chips) de la sélection.
        val capabilities = readContactCapabilities()
        val result = ArrayList<DeviceContact>()
        appContext.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
            ),
            null, null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx)?.trim().orEmpty()
                if (name.isBlank()) continue
                val id = cursor.getLong(idIdx)
                val photo = cursor.getString(photoIdx)
                result.add(DeviceContact(
                    id = id,
                    displayName = name,
                    photoUri = photo,
                    // v7.1.41 (B6) — capacités natives ; PHOTO déduite de la vignette déjà lue
                    // (pas de mimetype Photo dans la requête groupée), cohérent avec l'avatar du Row.
                    capabilities = (capabilities[id] ?: 0) or (if (photo != null) CAP_PHOTO else 0)
                ))
            }
        }
        result
    }

    /**
     * v7.1.41 (B6) — UNE requête groupée sur la table `Data` (projection minimale
     * `CONTACT_ID` + `MIMETYPE` + `DATA2`), repliée EN UNE PASSE en bitmask de capacités
     * par contact (`Map<contactId, Int>`). Aucun N+1 ; projection à 3 colonnes → coût
     * négligeable même sur 1000+ contacts. Alimente l'aperçu (chips) de l'écran de sélection.
     *
     * Mimetypes retenus = ceux qui produisent une capacité affichable (Phone, Email, Event,
     * Note, Website, StructuredPostal, Relation, Organization) ; PAS StructuredName/Nickname
     * (jamais une « capacité »), PAS Photo (déduite de la vignette dans [listDeviceContacts]).
     * Pour l'évènement, `DATA2` (TYPE) distingue l'anniversaire ([CAP_BIRTHDAY], `TYPE_BIRTHDAY`)
     * des autres dates ([CAP_DATE]). PUREMENT INDICATIF : jamais relu par [import].
     */
    private fun readContactCapabilities(): Map<Long, Int> {
        val caps = HashMap<Long, Int>()
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA2
        )
        val selection = "${ContactsContract.Data.MIMETYPE} IN (?, ?, ?, ?, ?, ?, ?, ?)"
        val selectionArgs = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
        )
        appContext.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection, selection, selectionArgs,
            ContactsContract.Data.CONTACT_ID
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val mimeIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
            val data2Idx = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA2)
            while (cursor.moveToNext()) {
                val bit = when (cursor.getString(mimeIdx)) {
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> CAP_PHONE
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> CAP_EMAIL
                    ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE ->
                        if (cursor.getInt(data2Idx) ==
                            ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY
                        ) CAP_BIRTHDAY else CAP_DATE
                    ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> CAP_NOTE
                    ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE -> CAP_WEBSITE
                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> CAP_ADDRESS
                    ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE -> CAP_RELATION
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> CAP_COMPANY
                    else -> 0
                }
                if (bit != 0) {
                    val id = cursor.getLong(idIdx)
                    caps[id] = (caps[id] ?: 0) or bit
                }
            }
        }
        return caps
    }

    /** Brouillon d'un contact natif en cours d'agrégation. */
    private class ContactDraft {
        var givenName: String? = null
        var familyName: String? = null
        var displayName: String? = null
        // v7.1.34 (B1) — sous-champs de nom avancés (mimetype StructuredName).
        var prefix: String? = null
        var middleName: String? = null
        var suffix: String? = null
        var phonetic: String? = null
        // v7.1.34 (B1) — surnom (mimetype Nickname).
        var nickname: String? = null
        var photoUri: String? = null
        var company: String? = null
        // v7.1.34 (B1) — infos pro complémentaires (mimetype Organization).
        var jobTitle: String? = null
        var department: String? = null
        var note: String? = null
        // v7.1.35 (B2) — valeur → clé de type JTR ([FieldTypes.PHONE]/[FieldTypes.EMAIL]
        // ou libellé personnalisé). LinkedHashMap = ordre natif préservé ; la 1ʳᵉ
        // occurrence d'une valeur garde son type (putIfAbsent).
        val phones = LinkedHashMap<String, String>()
        val emails = LinkedHashMap<String, String>()
        // v7.1.36 (B3) — dates importantes natives (mimetype Event) : (valeur ISO,
        // clé/label JTR). Ordre natif préservé ; dédup intra-contact par (date, type)
        // dans toPerson. Seules les dates ISO yyyy-MM-dd (année complète) sont ajoutées.
        val dates = ArrayList<Pair<String, String>>()
        // v7.1.39 (B4) — sites web (mimetype Website, DATA1=URL) → social_links. LinkedHashSet =
        // dédup par URL + ordre natif préservé. Insérés APRÈS le Person (FK) dans import().
        val websites = LinkedHashSet<String>()
        // v7.1.39 (B4) — adresses postales (mimetype StructuredPostal) → une NoteSection « Adresse »
        // par adresse (FORMATTED si présent, sinon composée). `postalCity` = 1ʳᵉ ville → Person.city.
        val postals = ArrayList<String>()
        var postalCity: String? = null
        // v7.1.40 (B5) — relations natives (mimetype Relation) : (nom de la personne liée,
        // label de type JTR). Multi-valué, ordre natif préservé ; dédup intra-contact par
        // (nom, label) dans toPerson. `linkedPersonId` reste null → résolu en 2ᵉ passe.
        val relations = ArrayList<Pair<String, String>>()
    }

    /**
     * Lit la base native, convertit en [Person] et insère par LOTS de
     * [BATCH_SIZE] dans Room (REPLACE). [onProgress] est rappelé après chaque
     * lot avec (traités, total) pour piloter la barre de progression de l'UI.
     *
     * DÉDOUBLONNAGE (v7.1.28, élargi en v7.1.42/B7) — détecté PAR TÉLÉPHONE/EMAIL
     * (jamais par nom) : on construit UNE FOIS un index des contacts JTR ACTIFS
     * (`deletedAt == null`) par téléphone normalisé ([phoneKey]) et email minuscule →
     * id ([indexKeys]). Pour chaque contact natif, [matchedIds] renvoie les contacts
     * JTR partagés. Selon [strategy] :
     *  - aucun match → INSERTION (quelle que soit la stratégie) ;
     *  - [DuplicateStrategy.SKIP] (défaut, comportement historique) → IGNORÉ ;
     *  - [DuplicateStrategy.UPDATE] → FUSION « ne jamais écraser » ([mergePerson]) dans
     *    l'unique cible ; cible AMBIGUË (≥2 contacts) → ignoré (jamais merger deux personnes) ;
     *  - [DuplicateStrategy.IMPORT_ANYWAY] → INSERTION d'un doublon ASSUMÉ.
     * L'index/snapshot est alimenté au fil des écritures → deux contacts natifs identiques
     * dans le MÊME import ne se dupliquent pas non plus. Ré-importer reste sans doublon ;
     * un ré-import en UPDATE est idempotent (no-op si rien à ajouter).
     *
     * @param selectedIds importation SÉLECTIVE (v5.4.1) : seuls ces contacts
     *   natifs sont agrégés (les autres lignes sont ignorées dès le curseur —
     *   aucune allocation pour les non-cochés). `null` = tout importer.
     *   La déduplication INTRA-contact v5.3.3 (téléphones/emails d'un même contact)
     *   reste active dans [toPerson].
     * @param strategy comportement sur doublon détecté (défaut [DuplicateStrategy.SKIP]).
     * @return [ImportResult] = (insérés, mis à jour [distincts], ignorés).
     */
    suspend fun import(
        selectedIds: Set<Long>? = null,
        strategy: DuplicateStrategy = DuplicateStrategy.SKIP,
        onProgress: (done: Int, total: Int) -> Unit
    ): Result<ImportResult> =
        withContext(Dispatchers.IO) {
            val result = runCatching {
                val drafts = readDeviceContacts(selectedIds)
                val total = drafts.size

                // Index des contacts JTR ACTIFS. v7.1.42 (B7) : clé → id (et non simple
                // présence) pour savoir DANS QUEL contact fusionner en mode UPDATE. `getAllSync()`
                // renvoie la corbeille → on l'exclut (un contact supprimé ne bloque pas un
                // ré-import volontaire). `personsById` = snapshot des Person actives, source de la
                // fusion et mis à jour au fil des écritures (anti-doublon/refusion intra-import).
                val phoneToId = HashMap<String, String>()
                val emailToId = HashMap<String, String>()
                val personsById = HashMap<String, Person>()
                personDao.getAllSync().forEach { p ->
                    if (p.deletedAt != null) return@forEach
                    personsById[p.id] = p
                    indexKeys(p, phoneToId, emailToId)
                }
                // v7.1.42 (B7) — URL des liens sociaux existants par contact (UNE lecture groupée,
                // pas de N+1) pour dédupliquer en mode UPDATE (ajout des URL ABSENTES uniquement).
                val linksByPerson = HashMap<String, MutableSet<String>>()
                socialLinkDao.getAllSync().forEach { link ->
                    link.url.trim().takeIf { it.isNotBlank() }?.let {
                        linksByPerson.getOrPut(link.personId) { HashSet() }.add(it)
                    }
                }

                // v7.1.39 (B4) — titres LIBRES FIGÉS dans les données (section « Adresse » +
                // titres legacy notes/likes pour la matérialisation B7) résolus en langue IN-APP
                // active (pas la locale SYSTÈME) via un contexte enveloppé par [LocaleManager].
                val localized = LocaleManager.wrap(appContext)
                val addressTitle = localized.getString(R.string.note_section_address)
                val notesTitle = localized.getString(R.string.note_section_default_notes)
                val likesTitle = localized.getString(R.string.person_likes_label)

                var done = 0
                var imported = 0
                var skipped = 0
                // Ids RÉELLEMENT touchés de ce run → 2ᵉ passe `linkedPersonId` (cf.
                // resolveImportedRelationLinks) + comptage des mises à jour DISTINCTES.
                val insertedIds = LinkedHashSet<String>()
                val updatedIds = LinkedHashSet<String>()
                onProgress(0, total)
                drafts.chunked(BATCH_SIZE).forEach { batch ->
                    val toInsert = ArrayList<Person>(batch.size)
                    val toUpdate = ArrayList<Person>()
                    // v7.1.39 (B4) — liens sociaux insérés APRÈS les Person (FK social_links →
                    // persons) ; v7.1.42 (B7) : confond inserts et compléments d'UPDATE.
                    val linksToInsert = ArrayList<SocialLinkEntity>()
                    batch.forEach { draft ->
                        val person = draft.toPerson(addressTitle) ?: return@forEach
                        val matches = matchedIds(person, phoneToId, emailToId)
                        if (matches.isEmpty() || strategy == DuplicateStrategy.IMPORT_ANYWAY) {
                            // INSERT : nouveau contact, OU doublon ASSUMÉ (IMPORT_ANYWAY). Alimente
                            // l'index/snapshot AVANT le prochain contact (anti-doublon intra-import).
                            indexKeys(person, phoneToId, emailToId)
                            personsById[person.id] = person
                            insertedIds.add(person.id)
                            toInsert.add(person)
                            val urls = linksByPerson.getOrPut(person.id) { HashSet() }
                            draft.websites.map { it.trim() }.filter { it.isNotBlank() && urls.add(it) }
                                .forEach { url ->
                                    linksToInsert += SocialLinkEntity(
                                        personId = person.id, url = url,
                                        platform = SocialPlatform.detect(url)?.displayName ?: "Lien"
                                    )
                                }
                            imported++
                        } else if (strategy == DuplicateStrategy.SKIP || matches.size >= 2) {
                            // SKIP explicite, OU cible AMBIGUË (le draft « ponte » ≥2 contacts JTR)
                            // → jamais de fusion (on ne peut pas merger deux personnes).
                            skipped++
                        } else {
                            // UPDATE : fusion « ne jamais écraser » dans l'UNIQUE contact ciblé.
                            val targetId = matches.first()
                            val target = personsById.getValue(targetId)
                            val merged = mergePerson(target, person, notesTitle, likesTitle)
                            val urls = linksByPerson.getOrPut(targetId) { HashSet() }
                            val newLinks = draft.websites.map { it.trim() }
                                .filter { it.isNotBlank() && urls.add(it) }
                                .map {
                                    SocialLinkEntity(
                                        personId = targetId, url = it,
                                        platform = SocialPlatform.detect(it)?.displayName ?: "Lien"
                                    )
                                }
                            if (merged != null || newLinks.isNotEmpty()) {
                                val finalPerson =
                                    (merged ?: target).copy(updatedAt = System.currentTimeMillis())
                                personsById[targetId] = finalPerson
                                toUpdate.add(finalPerson)
                                linksToInsert.addAll(newLinks)
                                updatedIds.add(targetId)
                            } else {
                                // Idempotence : rien à ajouter → no-op (updatedAt INCHANGÉ).
                                skipped++
                            }
                        }
                    }
                    // Persons d'abord (FK), puis les liens (inserts + compléments d'UPDATE).
                    // updateAll = @Update CIBLÉ (par PK, PAS REPLACE → pas de CASCADE social_links).
                    if (toInsert.isNotEmpty()) personDao.insertAll(toInsert)
                    if (toUpdate.isNotEmpty()) personDao.updateAll(toUpdate)
                    if (linksToInsert.isNotEmpty()) socialLinkDao.insertAll(linksToInsert)
                    done += batch.size
                    onProgress(done, total)
                }
                // v7.1.40 (B5) / v7.1.42 (B7) — 2ᵉ passe `linkedPersonId` sur TOUS les contacts
                // touchés (insérés ET mis à jour) une fois TOUTES les écritures faites. Erreur
                // ISOLÉE : un échec de résolution ne transforme jamais un import réussi en échec
                // (les liens non posés se rabattent sur la résolution par nom au clic).
                val touched = (insertedIds + updatedIds).map { personsById.getValue(it) }
                runCatching { resolveImportedRelationLinks(touched) }
                ImportResult(imported = imported, updated = updatedIds.size, skipped = skipped)
            }
            // v7.1.36 (B3) / v7.1.42 (B7) — après un import RÉUSSI ayant inséré OU mis à jour ≥1
            // contact, on (ré)arme les alarmes exactes : un anniversaire importé OU fraîchement
            // fusionné (notify=true) doit voir son rappel ANNUEL planifié DÈS l'import. Calcul
            // passé/futur INCHANGÉ (ReminderScheduler, idempotent). Erreur de planification ISOLÉE.
            result.getOrNull()?.takeIf { it.imported > 0 || it.updated > 0 }?.let {
                runCatching { ReminderScheduler.rescheduleAll(appContext) }
            }
            result
        }

    /**
     * Ajoute les clés de dédoublonnage (téléphones normalisés, emails minuscules) de [p] aux
     * index clé → id. `putIfAbsent` : la 1ʳᵉ occurrence d'une clé gagne (le contact JTR existant
     * prime sur un éventuel doublon inséré plus tard dans le même run).
     */
    private fun indexKeys(p: Person, phones: HashMap<String, String>, emails: HashMap<String, String>) {
        (listOfNotNull(p.phoneNumber) + (p.phoneLines?.map { it.value } ?: emptyList()))
            .map { phoneKey(it) }.filter { it.isNotBlank() }
            .forEach { phones.putIfAbsent(it, p.id) }
        (listOfNotNull(p.email) + (p.emailLines?.map { it.value } ?: emptyList()))
            .map { it.trim().lowercase() }.filter { it.isNotBlank() }
            .forEach { emails.putIfAbsent(it, p.id) }
    }

    /**
     * v7.1.42 (B7) — Ids des contacts JTR partageant un téléphone normalisé OU un email avec
     * [person]. Vide = pas un doublon (insertion). Un seul id = cible de fusion unique. Plusieurs
     * ids = le contact natif « ponte » plusieurs contacts JTR → ambigu (SKIP, jamais de merge).
     */
    private fun matchedIds(
        person: Person, phones: Map<String, String>, emails: Map<String, String>
    ): Set<String> {
        val ids = LinkedHashSet<String>()
        (listOfNotNull(person.phoneNumber) + (person.phoneLines?.map { it.value } ?: emptyList()))
            .map { phoneKey(it) }.filter { it.isNotBlank() }
            .forEach { phones[it]?.let(ids::add) }
        (listOfNotNull(person.email) + (person.emailLines?.map { it.value } ?: emptyList()))
            .map { it.trim().lowercase() }.filter { it.isNotBlank() }
            .forEach { emails[it]?.let(ids::add) }
        return ids
    }

    /**
     * v7.1.42 (B7) — Fusion « ne JAMAIS écraser » du contact natif [incoming] dans le contact
     * JTR existant [target]. Renvoie une copie modifiée SI au moins un champ a changé, sinon
     * `null` (idempotence). N'écrit JAMAIS (l'appelant le fait via [PersonDao.updateAll], UPDATE
     * ciblé) ; les liens sociaux (table FK) sont fusionnés à part dans [import].
     *
     * Règles :
     *  - Scalaires (nom secondaire, pro, ville, origine, photo…) remplis SEULEMENT si vides côté
     *    JTR ; jamais écrasés ni supprimés. `firstName` (obligatoire) intouché.
     *  - Listes (téléphone/email/date/relation) en UNION dédupliquée par clé adéquate (numéro
     *    normalisé / email minuscule / (valeur, label) / (nom, label)) ; existant et ordre conservés.
     *  - Scalaires « 1ʳᵉ ligne » `phoneNumber`/`email` remplis si null avec la 1ʳᵉ ligne fusionnée.
     *  - Anniversaire scalaire rempli seulement si absent côté JTR (sinon conservé).
     *  - `noteSections` : on MATÉRIALISE d'abord le legacy `notes`/`likes` (anti-disparition, car
     *    une liste non vide masque le legacy), PUIS on APPEND les nouvelles sections, et on force
     *    `notes`/`likes` à null (anti-résurrection, cf. B4). Si la cible a déjà des sections, on
     *    se contente d'append (legacy déjà migré).
     */
    private fun mergePerson(
        target: Person, incoming: Person, notesTitle: String, likesTitle: String
    ): Person? {
        var changed = false
        val mark: () -> Unit = { changed = true }
        fun fill(cur: String?, inc: String?): String? =
            if (cur.isNullOrBlank() && !inc.isNullOrBlank()) { changed = true; inc } else cur

        val lastName = fill(target.lastName, incoming.lastName)
        val prefix = fill(target.prefix, incoming.prefix)
        val middleName = fill(target.middleName, incoming.middleName)
        val suffix = fill(target.suffix, incoming.suffix)
        val phonetic = fill(target.phonetic, incoming.phonetic)
        val nickname = fill(target.nickname, incoming.nickname)
        val jobTitle = fill(target.jobTitle, incoming.jobTitle)
        val department = fill(target.department, incoming.department)
        val company = fill(target.company, incoming.company)
        val city = fill(target.city, incoming.city)
        val origin = fill(target.origin, incoming.origin)
        val photoUri = fill(target.photoUri, incoming.photoUri)

        val phoneLines = unionLines(target.phoneLines, incoming.phoneLines, { phoneKey(it.value) }, mark)
        val emailLines = unionLines(target.emailLines, incoming.emailLines, { it.value.trim().lowercase() }, mark)
        val dateLines = unionLines(target.dateLines, incoming.dateLines, { "${it.value} ${it.label}" }, mark)
        val relationLines = unionLines(
            target.relationLines, incoming.relationLines, { "${it.value.trim()} ${it.label}" }, mark
        )

        // Scalaires « 1ʳᵉ ligne » : remplir si null (cohérence scalaire ↔ liste fusionnée).
        val phoneNumber = if (target.phoneNumber.isNullOrBlank() && !phoneLines.isNullOrEmpty()) {
            changed = true; phoneLines.first().value
        } else target.phoneNumber
        val email = if (target.email.isNullOrBlank() && !emailLines.isNullOrEmpty()) {
            changed = true; emailLines.first().value
        } else target.email

        // Anniversaire scalaire : rempli SEULEMENT si absent côté JTR (jamais écrasé).
        // notify/offset dérivent alors de la même source que la date → cohérents (parité toPerson).
        var birthdate = target.birthdate
        var birthdateNotify = target.birthdateNotify
        var birthdateOffset = target.birthdateReminderOffsetMinutes
        if (target.birthdate == null && incoming.birthdate != null) {
            birthdate = incoming.birthdate
            birthdateNotify = incoming.birthdateNotify
            birthdateOffset = incoming.birthdateReminderOffsetMinutes
            changed = true
        }

        // noteSections : matérialiser le legacy (anti-disparition) PUIS append (anti-résurrection).
        // Dédup par CONTENU (titre+icône+contenu, l'`id` étant aléatoire à chaque construction) →
        // ré-importer le même contact n'ajoute pas de doublon (idempotence). On ne matérialise le
        // legacy et ne marque `changed` que si AU MOINS une section réellement nouvelle est ajoutée.
        var notes = target.notes
        var likes = target.likes
        var noteSections = target.noteSections
        if (incoming.noteSections.isNotEmpty()) {
            val hadSections = target.noteSections.isNotEmpty()
            val base = (if (hadSections) target.noteSections
                else deriveNoteSections(target.notes, target.likes, notesTitle, likesTitle))
                .toMutableList()
            val seen = HashSet<String>()
            base.forEach { seen.add(noteSectionKey(it)) }
            var addedSection = false
            incoming.noteSections.forEach { sec ->
                if (seen.add(noteSectionKey(sec))) {
                    base.add(sec.copy(order = base.size))
                    addedSection = true
                }
            }
            if (addedSection) {
                noteSections = base
                if (!hadSections) { notes = null; likes = null }
                changed = true
            }
        }

        if (!changed) return null
        return target.copy(
            lastName = lastName, prefix = prefix, middleName = middleName, suffix = suffix,
            phonetic = phonetic, nickname = nickname, jobTitle = jobTitle, department = department,
            company = company, city = city, origin = origin, photoUri = photoUri,
            phoneNumber = phoneNumber, email = email,
            birthdate = birthdate, birthdateNotify = birthdateNotify,
            birthdateReminderOffsetMinutes = birthdateOffset,
            phoneLines = phoneLines, emailLines = emailLines, dateLines = dateLines,
            relationLines = relationLines, notes = notes, likes = likes, noteSections = noteSections
        )
    }

    /**
     * v7.1.42 (B7) — Clé de dédup d'une [NoteSection] par CONTENU (titre + icône + contenu ;
     * l'`id`/`order` sont volatils). Deux sections « identiques » importées deux fois produisent
     * la même clé → pas de doublon en ré-import (idempotence de la fusion `noteSections`).
     */
    private fun noteSectionKey(s: NoteSection): String =
        "${s.title} ${s.iconKey} ${s.content}"

    /**
     * v7.1.42 (B7) — Union de deux listes de [DynamicLine] dédupliquée par [keyOf] : conserve
     * [existing] (valeur ET ordre), puis APPEND les lignes de [incoming] dont la clé est absente.
     * Appelle [onChange] pour chaque ligne réellement ajoutée. `null` si le résultat est vide.
     */
    private fun unionLines(
        existing: List<DynamicLine>?,
        incoming: List<DynamicLine>?,
        keyOf: (DynamicLine) -> String,
        onChange: () -> Unit
    ): List<DynamicLine>? {
        if (incoming.isNullOrEmpty()) return existing
        val out = existing?.toMutableList() ?: ArrayList()
        val seen = HashSet<String>()
        out.forEach { keyOf(it).takeIf { k -> k.isNotBlank() }?.let(seen::add) }
        incoming.forEach { line ->
            val k = keyOf(line)
            if (k.isNotBlank() && seen.add(k)) { out.add(line); onChange() }
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /**
     * v7.1.40 (B5) — 2ᵉ passe de résolution des relations importées : fige `linkedPersonId`
     * quand, et SEULEMENT quand, le nom de la relation désigne un contact UNIQUE.
     *
     * Périmètre (v7.1.42/B7) : les Person TOUCHÉES par CE run — insérées ET mises à jour
     * (qui peuvent avoir gagné de nouvelles `relationLines` en mode UPDATE). Les contacts
     * non touchés ne sont jamais réécrits — leurs relations héritées « pendantes »
     * (linkedPersonId null) se résolvent déjà par nom au clic ([EditPersonViewModel
     * .resolveRelationTarget]) dès que la cible existe.
     *
     * Résolution : réutilise EXACTEMENT le chemin runtime [PersonDao.findIdsByName] (prénom,
     * nom, ou « prénom nom », insensible à la casse) → sémantique IDENTIQUE au clic et au badge.
     * On exclut `person.id` (anti auto-relation). Exactement UN candidat restant ⇒ lien sûr ;
     * ZÉRO ou PLUSIEURS (homonymes) ⇒ `linkedPersonId` laissé null → le badge « À vérifier » et
     * la résolution au clic prennent le relais. JAMAIS de lien deviné.
     *
     * Écriture via [PersonDao.update] (UPDATE ciblé, PAS REPLACE) → aucun risque de CASCADE sur
     * les social_links fraîchement insérés. Seules les Person dont ≥1 ligne a été liée sont réécrites.
     * PAS de réciprocité (miroir) : l'import reste « tel quel » et ne passe pas par le repository.
     */
    private suspend fun resolveImportedRelationLinks(imported: List<Person>) {
        imported.forEach { person ->
            val lines = person.relationLines ?: return@forEach
            var changed = false
            val resolved = lines.map { line ->
                val name = line.value.trim()
                if (name.isBlank()) return@map line
                val target = personDao.findIdsByName(name)
                    .filter { it != person.id }
                    .singleOrNull()
                if (target != null) {
                    changed = true
                    line.copy(linkedPersonId = target)
                } else line
            }
            if (changed) personDao.update(person.copy(relationLines = resolved))
        }
    }

    /** Requête unique sur Data + agrégation par contact (filtrée si [selectedIds]). */
    private fun readDeviceContacts(selectedIds: Set<Long>? = null): List<ContactDraft> {
        val drafts = LinkedHashMap<Long, ContactDraft>()
        // v7.1.40 (B5) — ressources en langue IN-APP (pas système) pour les libellés de
        // relation que le framework localise (« Assistant », « Parent »… des types sans
        // équivalent JTR, cf. relationLabel) → même correctif locale que B4 (LocaleManager.wrap).
        val relationRes = LocaleManager.wrap(appContext).resources
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.Data.PHOTO_URI,
            ContactsContract.Data.PHOTO_THUMBNAIL_URI,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3,
            ContactsContract.Data.DATA4,
            ContactsContract.Data.DATA5,
            ContactsContract.Data.DATA6,
            ContactsContract.Data.DATA7,
            ContactsContract.Data.DATA8,
            ContactsContract.Data.DATA9,
            // v7.1.39 (B4) — StructuredPostal.COUNTRY (DATA10) pour composer l'adresse si FORMATTED absent.
            ContactsContract.Data.DATA10
        )
        val selection = "${ContactsContract.Data.MIMETYPE} IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        val selectionArgs = arrayOf(
            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
            // v7.1.39 (B4) — sites web → social_links ; adresses postales → city + section.
            ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
            // v7.1.40 (B5) — relations (Father/Manager/…) → relationLines (linkedPersonId 2ᵉ passe).
            ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE
        )

        appContext.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection, selection, selectionArgs,
            ContactsContract.Data.CONTACT_ID
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val mimeIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.DISPLAY_NAME)
            val photoIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.PHOTO_URI)
            val thumbIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.PHOTO_THUMBNAIL_URI)
            val data1Idx = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA1)
            val data2Idx = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA2)
            val data3Idx = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA3)
            val data4Idx = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA4)
            val data5Idx = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA5)
            val data6Idx = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA6)
            val data7Idx = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA7)
            val data8Idx = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA8)
            val data9Idx = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA9)
            val data10Idx = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA10)

            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(idIdx)
                // Importation sélective : ignore les contacts non cochés AVANT
                // toute allocation (zéro surconsommation mémoire). NB : « selection »
                // est déjà pris par la clause SQL locale, d'où « selectedIds ».
                if (selectedIds != null && contactId !in selectedIds) continue
                val draft = drafts.getOrPut(contactId) { ContactDraft() }
                if (draft.displayName == null) draft.displayName = cursor.getString(nameIdx)
                // Photo PLEINE RÉSOLUTION (v7.1.34) : PHOTO_URI pointe sur la photo
                // d'affichage et retombe lui-même sur la vignette si absente ; on
                // ajoute un repli explicite sur PHOTO_THUMBNAIL_URI par robustesse.
                if (draft.photoUri == null) {
                    draft.photoUri = cursor.getString(photoIdx)
                        ?.takeIf { it.isNotBlank() }
                        ?: cursor.getString(thumbIdx)
                }

                when (cursor.getString(mimeIdx)) {
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                        cursor.getString(data2Idx)?.takeIf { it.isNotBlank() }
                            ?.let { draft.givenName = it }
                        cursor.getString(data3Idx)?.takeIf { it.isNotBlank() }
                            ?.let { draft.familyName = it }
                        cursor.getString(data4Idx)?.takeIf { it.isNotBlank() }
                            ?.let { draft.prefix = it.trim() }
                        cursor.getString(data5Idx)?.takeIf { it.isNotBlank() }
                            ?.let { draft.middleName = it.trim() }
                        cursor.getString(data6Idx)?.takeIf { it.isNotBlank() }
                            ?.let { draft.suffix = it.trim() }
                        // Phonétiques prénom/2ᵉ prénom/nom (DATA7/8/9) fusionnés en
                        // un seul champ `phonetic` (Person n'en a qu'un).
                        listOfNotNull(
                            cursor.getString(data7Idx)?.takeIf { it.isNotBlank() },
                            cursor.getString(data8Idx)?.takeIf { it.isNotBlank() },
                            cursor.getString(data9Idx)?.takeIf { it.isNotBlank() }
                        ).joinToString(" ") { it.trim() }
                            .takeIf { it.isNotBlank() }
                            ?.let { draft.phonetic = it }
                    }
                    ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE ->
                        cursor.getString(data1Idx)?.takeIf { it.isNotBlank() }
                            ?.let { draft.nickname = it.trim() }
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE ->
                        cursor.getString(data1Idx)?.takeIf { it.isNotBlank() }?.let {
                            // v7.1.35 (B2) : TYPE (DATA2) + LABEL perso (DATA3) → type JTR.
                            draft.phones.putIfAbsent(
                                it.trim(),
                                phoneLabel(cursor.getInt(data2Idx), cursor.getString(data3Idx))
                            )
                        }
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE ->
                        cursor.getString(data1Idx)?.takeIf { it.isNotBlank() }?.let {
                            draft.emails.putIfAbsent(
                                it.trim(),
                                emailLabel(cursor.getInt(data2Idx), cursor.getString(data3Idx))
                            )
                        }
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                        cursor.getString(data1Idx)?.takeIf { it.isNotBlank() }
                            ?.let { draft.company = it }
                        cursor.getString(data4Idx)?.takeIf { it.isNotBlank() }
                            ?.let { draft.jobTitle = it.trim() }
                        cursor.getString(data5Idx)?.takeIf { it.isNotBlank() }
                            ?.let { draft.department = it.trim() }
                    }
                    ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
                        // v7.1.36/37 (B3/B3b) : START_DATE (DATA1) → ISO complet OU `--MM-dd` sans
                        // année ; TYPE (DATA2) + LABEL perso (DATA3) → label de date JTR. Format non
                        // reconnu = ignoré (eventDateToStored → null), pas de crash.
                        eventDateToStored(cursor.getString(data1Idx))?.let { stored ->
                            draft.dates.add(
                                stored to dateLabel(cursor.getInt(data2Idx), cursor.getString(data3Idx))
                            )
                        }
                    }
                    ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE ->
                        cursor.getString(data1Idx)?.takeIf { it.isNotBlank() }
                            ?.let { draft.note = it }
                    // v7.1.39 (B4) — site web (DATA1=URL) → lien social (dédup URL via LinkedHashSet).
                    ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE ->
                        cursor.getString(data1Idx)?.trim()?.takeIf { it.isNotBlank() }
                            ?.let { draft.websites.add(it) }
                    // v7.1.39 (B4) — adresse postale → 1ʳᵉ ville (Person.city) + adresse formatée
                    // (FORMATTED DATA1, sinon composée STREET/CITY REGION POSTCODE/COUNTRY) → section.
                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                        cursor.getString(data7Idx)?.trim()?.takeIf { it.isNotBlank() }
                            ?.let { if (draft.postalCity == null) draft.postalCity = it }
                        val formatted = cursor.getString(data1Idx)?.trim()?.takeIf { it.isNotBlank() }
                            ?: composePostal(
                                street = cursor.getString(data4Idx),
                                city = cursor.getString(data7Idx),
                                region = cursor.getString(data8Idx),
                                postcode = cursor.getString(data9Idx),
                                country = cursor.getString(data10Idx)
                            )
                        formatted?.takeIf { it.isNotBlank() }?.let { draft.postals.add(it) }
                    }
                    // v7.1.40 (B5) — relation : DATA1 = nom de la personne liée, DATA2 = TYPE,
                    // DATA3 = libellé perso. value = nom (affiché) ; label = type JTR. Nom vide =
                    // ignoré (une relation sans personne n'a pas de sens). linkedPersonId = null
                    // ici, fixé en 2ᵉ passe (import()) quand le nom désigne un contact UNIQUE.
                    ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE ->
                        cursor.getString(data1Idx)?.trim()?.takeIf { it.isNotBlank() }?.let {
                            draft.relations.add(
                                it to relationLabel(
                                    cursor.getInt(data2Idx), cursor.getString(data3Idx), relationRes
                                )
                            )
                        }
                }
            }
        }
        return drafts.values.toList()
    }

    /**
     * Clé de comparaison d'un numéro : chiffres (et préfixe « + ») uniquement —
     * espaces, tirets, points et parenthèses ignorés. « (514) 555-0001 » et
     * « 514 555 0001 » et « 514.555.0001 » produisent la même clé.
     */
    private fun phoneKey(raw: String): String = raw.filter { it.isDigit() || it == '+' }

    /**
     * v7.1.39 (B4) — Compose une adresse lisible quand `FORMATTED_ADDRESS` (DATA1) est absent :
     * `rue` / `ville région code-postal` / `pays`, chaque ligne omise si vide. `null` si tout est vide.
     */
    private fun composePostal(
        street: String?, city: String?, region: String?, postcode: String?, country: String?
    ): String? {
        val locality = listOfNotNull(
            city?.trim()?.takeIf { it.isNotBlank() },
            region?.trim()?.takeIf { it.isNotBlank() },
            postcode?.trim()?.takeIf { it.isNotBlank() }
        ).joinToString(" ").takeIf { it.isNotBlank() }
        return listOfNotNull(
            street?.trim()?.takeIf { it.isNotBlank() },
            locality,
            country?.trim()?.takeIf { it.isNotBlank() }
        ).joinToString("\n").takeIf { it.isNotBlank() }
    }

    /**
     * v7.1.35 (B2) — Mappe [ContactsContract.CommonDataKinds.Phone] TYPE (+ LABEL
     * natif pour le type personnalisé) vers une clé de type JTR (cf.
     * [FieldTypes.PHONE]). TYPE_CUSTOM → le libellé natif tel quel (label
     * personnalisé JTR) ; type sans équivalent (fax, pager…), inconnu ou absent
     * → « other » (jamais de crash). [type] vaut 0 (= TYPE_CUSTOM) si la colonne
     * est nulle → repli « other » via le libellé vide.
     */
    private fun phoneLabel(type: Int, customLabel: String?): String = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> FieldTypes.PHONE_MOBILE
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "home"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "work"
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN,
        ContactsContract.CommonDataKinds.Phone.TYPE_COMPANY_MAIN -> "main"
        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM ->
            customLabel?.trim()?.takeIf { it.isNotBlank() } ?: "other"
        else -> "other"
    }

    /**
     * v7.1.35 (B2) — Équivalent pour [ContactsContract.CommonDataKinds.Email]
     * (cf. [FieldTypes.EMAIL]). HOME/WORK mappés ; TYPE_CUSTOM → libellé natif ;
     * MOBILE/OTHER/inconnu/absent → « other » (pas d'équivalent JTR).
     */
    private fun emailLabel(type: Int, customLabel: String?): String = when (type) {
        ContactsContract.CommonDataKinds.Email.TYPE_HOME -> FieldTypes.EMAIL_HOME
        ContactsContract.CommonDataKinds.Email.TYPE_WORK -> "work"
        ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM ->
            customLabel?.trim()?.takeIf { it.isNotBlank() } ?: "other"
        else -> "other"
    }

    /**
     * v7.1.36 (B3) — Mappe [ContactsContract.CommonDataKinds.Event] TYPE (+ LABEL natif
     * pour le type personnalisé) vers une clé de type de date JTR (cf. [FieldTypes.DATE]).
     * TYPE_BIRTHDAY → « birthday » (collapse scalaire dans toPerson) ; ANNIVERSARY →
     * « anniversary » ; CUSTOM → libellé natif ; OTHER/inconnu → « other ».
     */
    private fun dateLabel(type: Int, customLabel: String?): String = when (type) {
        ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY -> FieldTypes.DATE_BIRTHDAY
        ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY -> "anniversary"
        ContactsContract.CommonDataKinds.Event.TYPE_CUSTOM ->
            customLabel?.trim()?.takeIf { it.isNotBlank() } ?: "other"
        else -> "other"
    }

    /**
     * v7.1.40 (B5) — Mappe [ContactsContract.CommonDataKinds.Relation] TYPE (+ LABEL natif pour
     * le type personnalisé) vers un label de relation JTR (cf. [FieldTypes.RELATION]).
     * Les types à équivalent direct (mother/father/parent/brother/sister/spouse/child/friend/
     * manager/partner) donnent leur clé JTR ; affichés localisés via `relation_type_*`.
     *
     * JTR n'a PAS de type « other » pour les relations (≠ tél/email/date). Donc :
     *  - TYPE_CUSTOM → le libellé natif (DATA3) tel quel (un label inconnu s'affiche VERBATIM,
     *    comme la saisie « custom » ; cf. typeLabelResOrNull) ; à défaut, le mot localisé du
     *    framework (« Personnalisé »…) ;
     *  - types intégrés SANS équivalent JTR (ASSISTANT, RELATIVE, REFERRED_BY)
     *    → libellé localisé du framework via [getTypeLabel], traité comme un label verbatim.
     *
     * v7.1.43 — TYPE_PARENT tombait dans ce dernier cas (« Parent » figé en texte libre, ni
     * traduit au changement de langue, ni inversible) : la clé `parent` existant désormais,
     * il devient canonique comme les autres.
     * [res] doit être en langue IN-APP (cf. `relationRes`) pour que ces libellés framework
     * suivent la langue de l'app, pas la locale système.
     */
    private fun relationLabel(type: Int, customLabel: String?, res: Resources): String = when (type) {
        ContactsContract.CommonDataKinds.Relation.TYPE_MOTHER -> "mother"
        ContactsContract.CommonDataKinds.Relation.TYPE_FATHER -> "father"
        ContactsContract.CommonDataKinds.Relation.TYPE_PARENT -> "parent"
        ContactsContract.CommonDataKinds.Relation.TYPE_BROTHER -> "brother"
        ContactsContract.CommonDataKinds.Relation.TYPE_SISTER -> "sister"
        ContactsContract.CommonDataKinds.Relation.TYPE_SPOUSE -> "spouse"
        ContactsContract.CommonDataKinds.Relation.TYPE_CHILD -> "child"
        ContactsContract.CommonDataKinds.Relation.TYPE_FRIEND -> FieldTypes.RELATION_FRIEND
        ContactsContract.CommonDataKinds.Relation.TYPE_MANAGER -> "manager"
        // v7.1.44 — les deux « partenaires » natifs (vie commune ou association) tombent sur le
        // même type JTR : `partner` est SYMÉTRIQUE dans les deux lectures, le miroir est donc
        // correct quelle que soit l'intention d'origine.
        ContactsContract.CommonDataKinds.Relation.TYPE_PARTNER,
        ContactsContract.CommonDataKinds.Relation.TYPE_DOMESTIC_PARTNER -> "partner"
        ContactsContract.CommonDataKinds.Relation.TYPE_CUSTOM ->
            customLabel?.trim()?.takeIf { it.isNotBlank() }
                ?: ContactsContract.CommonDataKinds.Relation
                    .getTypeLabel(res, type, null).toString().trim()
        else -> ContactsContract.CommonDataKinds.Relation
            .getTypeLabel(res, type, customLabel).toString().trim()
    }

    /**
     * v7.1.36/37 (B3/B3b) — Normalise une `Event.START_DATE` native en valeur de date JTR :
     *  - date À ANNÉE COMPLÈTE → ISO `yyyy-MM-dd` (validée par [DateCanonical.isoToMillis]) ;
     *  - date SANS année `--MM-dd` (B3b) → conservée TELLE QUELLE (validée par
     *    [DateCanonical.monthDayOf]) ; elle vit en `dateLines`, ANNUELLE par nature, scalaire
     *    `birthdate` laissé `null` (pas d'année).
     * Tout autre format (chiffres bruts, vide) ou date impossible → `null` = IGNORÉ, jamais de
     * crash. (Android stocke un Event soit en « yyyy-MM-dd » soit en « --MM-dd ».)
     */
    private fun eventDateToStored(raw: String?): String? {
        val v = raw?.trim().orEmpty()
        if (DateCanonical.isIso(v)) return if (DateCanonical.isoToMillis(v) != null) v else null
        if (DateCanonical.isMonthDay(v)) return if (DateCanonical.monthDayOf(v) != null) v else null
        return null
    }

    /**
     * Convertit un brouillon en [Person] JTR. Le prénom est OBLIGATOIRE :
     * repli sur le premier mot du nom affiché, sinon le contact est ignoré.
     * Les listes téléphones/emails alimentent les lignes dynamiques ET les
     * scalaires dénormalisés (« 1ʳᵉ ligne », lus par les workers et cartes).
     */
    private fun ContactDraft.toPerson(addressTitle: String): Person? {
        val display = displayName?.trim().orEmpty()
        val first = givenName?.trim()?.takeIf { it.isNotBlank() }
            ?: display.substringBefore(' ').takeIf { it.isNotBlank() }
            ?: return null
        val last = familyName?.trim()?.takeIf { it.isNotBlank() }
            ?: display.substringAfter(' ', "").trim().takeIf { it.isNotBlank() && givenName == null }

        // Déduplication STRICTE (hotfix v5.3.3) : un même numéro enregistré sous
        // plusieurs étiquettes natives (Mobile, Principal…) ou avec des formats
        // différents n'est conservé qu'une seule fois (comparaison normalisée) ;
        // les emails sont comparés en minuscules. Coût O(n) par contact — aucun
        // ralentissement du traitement par lots sur Dispatchers.IO. v7.1.35 (B2) :
        // les entrées portent désormais (valeur, type JTR) ; la 1ʳᵉ occurrence
        // gagne (type inclus), l'ordre natif est préservé.
        val uniquePhones = phones.entries.toList().distinctBy { phoneKey(it.key) }
        val uniqueEmails = emails.entries.toList().distinctBy { it.key.lowercase() }

        // v7.1.36 (B3) — dates : dédup intra-contact par (date ISO, type), ordre natif
        // préservé. DÉCISION PRODUIT : à l'import, l'ANNIVERSAIRE notifie d'office
        // (notify=true) ; les autres types (anniversary/other/custom) restent silencieux
        // (notify=false) jusqu'à activation manuelle. L'offset reste au DÉFAUT de la saisie
        // manuelle (DynamicLine.reminderOffsetMinutes = 0 = « Le jour J ») — aucun offset
        // inventé. La règle passé/futur (ReminderScheduler) reste automatique : un
        // anniversaire passé à notify=true → rappel ANNUEL planifié dès l'import.
        val importedDateLines = dates.distinctBy { it.first to it.second }
            .map { (iso, label) ->
                DynamicLine(value = iso, label = label, notify = label == FieldTypes.DATE_BIRTHDAY)
            }
        // Collapse anniversaire = PARITÉ FORMULAIRE (cf. Add/EditPersonViewModel) : les
        // scalaires `birthdate`/`birthdateNotify`/`birthdateReminderOffsetMinutes` dérivent
        // TOUS de la ligne « birthday » → le scalaire birthdateNotify est TOUJOURS cohérent
        // avec le flag notify de sa ligne (jamais l'un sans l'autre), et la date scalaire ne
        // diverge jamais de la ligne.
        val birthdayLine = importedDateLines.firstOrNull { it.label == FieldTypes.DATE_BIRTHDAY }
        // B3b : un birthday SANS année (`--MM-dd`) → isoToMillis null → scalaire `birthdate` null
        // (pas d'année à projeter) ; il vit en `dateLines` et notifie via la branche annuelle.
        val birthdateMillis = birthdayLine?.let { DateCanonical.isoToMillis(it.value) }

        // v7.1.39 (B4) — la note ET les adresses deviennent des NoteSection (jamais la colonne
        // legacy `notes`). CRITIQUE : `noteSections` non vide masque `notes`/`likes` à l'affichage
        // (effectiveNoteSections) → on y met TOUT et on force `notes = null` côté Person (source
        // unique, anti-résurrection si l'utilisateur supprime une section en édition).
        //  • note → section titre VIDE (placeholder M3 v7.1.33), icône « notes » ;
        //  • chaque adresse → une section « Adresse » (titre localisé, icône « place »).
        val importedSections = ArrayList<NoteSection>(1 + postals.size)
        note?.trim()?.takeIf { it.isNotBlank() }?.let {
            importedSections += NoteSection(
                title = "", iconKey = NOTE_ICON_NOTES, content = it, order = importedSections.size
            )
        }
        postals.forEach { addr ->
            importedSections += NoteSection(
                title = addressTitle, iconKey = "place", content = addr, order = importedSections.size
            )
        }

        return Person(
            firstName = first,
            lastName = last,
            // v7.1.34 (B1) — sous-champs de nom + surnom (mapping direct, null-safe).
            prefix = prefix,
            middleName = middleName,
            suffix = suffix,
            phonetic = phonetic,
            nickname = nickname,
            photoUri = copyNativePhoto(photoUri),
            // v7.1.36 (B3) — scalaires anniversaire dérivés de la ligne « birthday »
            // (parité formulaire) : date, cloche (notify d'office à l'import) et délai
            // de rappel (= défaut saisie manuelle, 0). birthdateNotify ↔ ligne cohérents.
            birthdate = birthdateMillis,
            birthdateNotify = birthdayLine?.notify ?: false,
            birthdateReminderOffsetMinutes = birthdayLine?.reminderOffsetMinutes ?: 0,
            phoneNumber = uniquePhones.firstOrNull()?.key,
            email = uniqueEmails.firstOrNull()?.key,
            // v7.1.34 (B1) — poste + département en plus de la société.
            jobTitle = jobTitle,
            department = department,
            company = company,
            // v7.1.39 (B4) — city = 1ʳᵉ ville postale (Person.city toujours vide à l'import) → alimente
            // le futur géocodage (NB : l'import ne géocode pas → pas de coords tant que non ré-enregistré).
            city = postalCity?.trim()?.takeIf { it.isNotBlank() },
            // v7.1.39 (B4) — note + adresses portées par noteSections ; legacy `notes` forcé null.
            notes = null,
            noteSections = importedSections,
            // v7.1.35 (B2) — label = type JTR issu du natif (fini le « mobile »/« home » figé).
            phoneLines = uniquePhones.map { DynamicLine(value = it.key, label = it.value) }
                .takeIf { it.isNotEmpty() },
            emailLines = uniqueEmails.map { DynamicLine(value = it.key, label = it.value) }
                .takeIf { it.isNotEmpty() },
            // v7.1.36 (B3) — dates importantes en ISO (anniversaire/anniversary/autres) ;
            // seule la ligne « birthday » porte notify=true (cf. importedDateLines).
            dateLines = importedDateLines.takeIf { it.isNotEmpty() },
            // v7.1.40 (B5) — relations natives : value = nom de la personne liée, label = type JTR.
            // Dédup intra-contact par (nom, label). linkedPersonId reste null (résolu en 2ᵉ passe
            // d'import() quand le nom désigne un contact UNIQUE ; sinon badge « À vérifier »).
            relationLines = relations.distinctBy { it.first to it.second }
                .map { DynamicLine(value = it.first, label = it.second) }
                .takeIf { it.isNotEmpty() }
        )
    }

    /**
     * Copie la photo native (content://…) vers filesDir/photos — pérenne même
     * après révocation de READ_CONTACTS. `null` (sans échec d'import) si la
     * photo est absente ou illisible.
     */
    private fun copyNativePhoto(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        return try {
            val dir = File(appContext.filesDir, "photos").also { it.mkdirs() }
            val dest = File(dir, "import_${UUID.randomUUID()}.jpg")
            appContext.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            } ?: return null
            Uri.fromFile(dest).toString()
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val BATCH_SIZE = 25
    }
}
