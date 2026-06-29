package com.jtr.app.data.contacts

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.jtr.app.R
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.domain.model.DynamicLine
import com.jtr.app.domain.model.NOTE_ICON_NOTES
import com.jtr.app.domain.model.NoteSection
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
 * 7 mimetypes utiles (nom structuré, surnom, téléphone, email, organisation,
 * évènement, note), puis regroupement en mémoire par `CONTACT_ID`. Les colonnes génériques
 * DATA1..DATA9 portent la valeur selon le mimetype :
 *  - StructuredName : DATA2 = prénom, DATA3 = nom, DATA4 = préfixe (Dr…),
 *    DATA5 = 2ᵉ prénom, DATA6 = suffixe (Jr…), DATA7/8/9 = phonétiques ;
 *  - Organization : DATA1 = société, DATA4 = poste, DATA5 = département ;
 *  - Phone / Email : DATA1 = valeur, DATA2 = TYPE, DATA3 = libellé perso (B2) ;
 *  - Event : DATA1 = date (ISO yyyy-MM-dd), DATA2 = TYPE, DATA3 = libellé perso (B3) ;
 *  - Nickname / Note : DATA1 = valeur principale.
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
    val photoUri: String?
)

/**
 * Bilan d'une importation (v7.1.28) : [imported] = contacts effectivement insérés,
 * [skipped] = contacts IGNORÉS car déjà présents dans JTR (dédoublonnage minimal,
 * cf. [ContactsImporter.import]). Permet à l'UI d'afficher un récap « X importés ·
 * Y ignorés (déjà dans JTR) » et garantit l'invariant « ré-importer ne duplique pas ».
 */
data class ImportResult(val imported: Int, val skipped: Int)

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
                result.add(DeviceContact(
                    id = cursor.getLong(idIdx),
                    displayName = name,
                    photoUri = cursor.getString(photoIdx)
                ))
            }
        }
        result
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
    }

    /**
     * Lit la base native, convertit en [Person] et insère par LOTS de
     * [BATCH_SIZE] dans Room (REPLACE). [onProgress] est rappelé après chaque
     * lot avec (traités, total) pour piloter la barre de progression de l'UI.
     *
     * DÉDOUBLONNAGE MINIMAL (v7.1.28) — SKIP uniquement, jamais d'écrasement ni de
     * mise à jour : on construit UNE FOIS un index des contacts JTR ACTIFS
     * (`deletedAt == null`) par téléphone normalisé ([phoneKey]) et email minuscule,
     * puis tout contact natif partageant un téléphone OU un email avec un contact
     * existant est IGNORÉ. L'index est aussi alimenté au fil des insertions → deux
     * contacts natifs identiques dans le MÊME import ne se dupliquent pas non plus.
     * Conséquence voulue : ré-importer les mêmes contacts n'ajoute aucun doublon.
     *
     * @param selectedIds importation SÉLECTIVE (v5.4.1) : seuls ces contacts
     *   natifs sont agrégés (les autres lignes sont ignorées dès le curseur —
     *   aucune allocation pour les non-cochés). `null` = tout importer.
     *   La déduplication INTRA-contact v5.3.3 (téléphones/emails d'un même contact)
     *   reste active dans [toPerson].
     * @return [ImportResult] = (insérés, ignorés car déjà présents).
     */
    suspend fun import(
        selectedIds: Set<Long>? = null,
        onProgress: (done: Int, total: Int) -> Unit
    ): Result<ImportResult> =
        withContext(Dispatchers.IO) {
            val result = runCatching {
                val drafts = readDeviceContacts(selectedIds)
                val total = drafts.size

                // Index des contacts JTR ACTIFS (clés de dédoublonnage). `getAllSync()`
                // renvoie TOUTES les lignes (corbeille incluse) → on exclut les supprimés :
                // un contact mis à la corbeille ne bloque pas un ré-import volontaire.
                val existingPhones = HashSet<String>()
                val existingEmails = HashSet<String>()
                personDao.getAllSync().forEach { p ->
                    if (p.deletedAt != null) return@forEach
                    indexKeys(p, existingPhones, existingEmails)
                }

                // v7.1.39 (B4) — titre localisé de la section « Adresse », résolu UNE fois.
                // CORRECTIF locale (finding device) : ce titre est du texte LIBRE FIGÉ dans les
                // données → il doit refléter la langue IN-APP active, pas la locale SYSTÈME. On
                // résout donc via un contexte enveloppé par [LocaleManager] (qui relit le tag
                // BCP-47 persisté ; renvoie le contexte système si « langue du système »), au lieu
                // d'`appContext` brut (Application = locale système, ignore l'override in-app).
                val addressTitle =
                    LocaleManager.wrap(appContext).getString(R.string.note_section_address)

                var done = 0
                var imported = 0
                var skipped = 0
                onProgress(0, total)
                drafts.chunked(BATCH_SIZE).forEach { batch ->
                    val toInsert = ArrayList<Person>(batch.size)
                    // v7.1.39 (B4) — liens sociaux des Persons RÉELLEMENT insérés (non skippés) :
                    // insérés APRÈS personDao.insertAll pour respecter la FK social_links → persons.
                    val linksToInsert = ArrayList<SocialLinkEntity>()
                    batch.forEach { draft ->
                        val person = draft.toPerson(addressTitle) ?: return@forEach
                        if (isDuplicate(person, existingPhones, existingEmails)) {
                            skipped++
                        } else {
                            // Alimente l'index AVANT le prochain contact → anti-doublon
                            // intra-import (deux entrées natives au même numéro/email).
                            indexKeys(person, existingPhones, existingEmails)
                            toInsert.add(person)
                            // Sites web → liens sociaux (URL dédoublonnée par le LinkedHashSet ;
                            // plateforme auto-détectée, repli « Lien » comme la saisie manuelle ;
                            // aucune URL perdue). FK satisfaite : la ligne Person sera insérée juste avant.
                            draft.websites.forEach { url ->
                                linksToInsert += SocialLinkEntity(
                                    personId = person.id,
                                    url = url,
                                    platform = SocialPlatform.detect(url)?.displayName ?: "Lien"
                                )
                            }
                            imported++
                        }
                    }
                    if (toInsert.isNotEmpty()) {
                        personDao.insertAll(toInsert)
                        if (linksToInsert.isNotEmpty()) socialLinkDao.insertAll(linksToInsert)
                    }
                    done += batch.size
                    onProgress(done, total)
                }
                ImportResult(imported = imported, skipped = skipped)
            }
            // v7.1.36 (B3) — après un import RÉUSSI ayant inséré ≥1 contact, on (ré)arme
            // les alarmes exactes des rappels : un anniversaire importé (notify=true) doit
            // voir son rappel ANNUEL planifié DÈS l'import, sans attendre la prochaine
            // ouverture de l'app — parité avec Add/EditPersonViewModel qui replanifient
            // après chaque sauvegarde. Le calcul passé/futur reste celui, INCHANGÉ, de
            // ReminderScheduler (idempotent). Erreur de planification ISOLÉE : elle ne
            // doit jamais transformer un import réussi en échec.
            result.getOrNull()?.takeIf { it.imported > 0 }?.let {
                runCatching { ReminderScheduler.rescheduleAll(appContext) }
            }
            result
        }

    /** Ajoute les clés de dédoublonnage (téléphones normalisés, emails minuscules) de [p] aux index. */
    private fun indexKeys(p: Person, phones: HashSet<String>, emails: HashSet<String>) {
        (listOfNotNull(p.phoneNumber) + (p.phoneLines?.map { it.value } ?: emptyList()))
            .map { phoneKey(it) }.filter { it.isNotBlank() }
            .forEach { phones.add(it) }
        (listOfNotNull(p.email) + (p.emailLines?.map { it.value } ?: emptyList()))
            .map { it.trim().lowercase() }.filter { it.isNotBlank() }
            .forEach { emails.add(it) }
    }

    /** Vrai si [person] partage un téléphone normalisé OU un email avec l'index existant. */
    private fun isDuplicate(person: Person, phones: Set<String>, emails: Set<String>): Boolean {
        val pk = (listOfNotNull(person.phoneNumber) + (person.phoneLines?.map { it.value } ?: emptyList()))
            .map { phoneKey(it) }.filter { it.isNotBlank() }
        if (pk.any { it in phones }) return true
        val ek = (listOfNotNull(person.email) + (person.emailLines?.map { it.value } ?: emptyList()))
            .map { it.trim().lowercase() }.filter { it.isNotBlank() }
        return ek.any { it in emails }
    }

    /** Requête unique sur Data + agrégation par contact (filtrée si [selectedIds]). */
    private fun readDeviceContacts(selectedIds: Set<Long>? = null): List<ContactDraft> {
        val drafts = LinkedHashMap<Long, ContactDraft>()
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
        val selection = "${ContactsContract.Data.MIMETYPE} IN (?, ?, ?, ?, ?, ?, ?, ?, ?)"
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
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE
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
            dateLines = importedDateLines.takeIf { it.isNotEmpty() }
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
