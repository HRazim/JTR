package com.jtr.app.data.contacts

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.domain.model.DynamicLine
import com.jtr.app.domain.model.Person
import com.jtr.app.ui.person.FieldTypes
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
 * 5 mimetypes utiles (nom structuré, téléphone, email, organisation, note),
 * puis regroupement en mémoire par `CONTACT_ID`. Les colonnes génériques
 * DATA1/DATA2/DATA3 portent la valeur selon le mimetype :
 *  - StructuredName : DATA2 = prénom, DATA3 = nom de famille ;
 *  - Phone / Email / Organization / Note : DATA1 = valeur principale.
 * `PHOTO_URI` est une colonne jointe du contact, disponible sur chaque ligne.
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
        var photoUri: String? = null
        var company: String? = null
        var note: String? = null
        val phones = LinkedHashSet<String>()
        val emails = LinkedHashSet<String>()
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
            runCatching {
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

                var done = 0
                var imported = 0
                var skipped = 0
                onProgress(0, total)
                drafts.chunked(BATCH_SIZE).forEach { batch ->
                    val toInsert = ArrayList<Person>(batch.size)
                    batch.forEach { draft ->
                        val person = draft.toPerson() ?: return@forEach
                        if (isDuplicate(person, existingPhones, existingEmails)) {
                            skipped++
                        } else {
                            // Alimente l'index AVANT le prochain contact → anti-doublon
                            // intra-import (deux entrées natives au même numéro/email).
                            indexKeys(person, existingPhones, existingEmails)
                            toInsert.add(person)
                            imported++
                        }
                    }
                    if (toInsert.isNotEmpty()) personDao.insertAll(toInsert)
                    done += batch.size
                    onProgress(done, total)
                }
                ImportResult(imported = imported, skipped = skipped)
            }
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
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3
        )
        val selection = "${ContactsContract.Data.MIMETYPE} IN (?, ?, ?, ?, ?)"
        val selectionArgs = arrayOf(
            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
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
            val data1Idx = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA1)
            val data2Idx = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA2)
            val data3Idx = cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA3)

            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(idIdx)
                // Importation sélective : ignore les contacts non cochés AVANT
                // toute allocation (zéro surconsommation mémoire). NB : « selection »
                // est déjà pris par la clause SQL locale, d'où « selectedIds ».
                if (selectedIds != null && contactId !in selectedIds) continue
                val draft = drafts.getOrPut(contactId) { ContactDraft() }
                if (draft.displayName == null) draft.displayName = cursor.getString(nameIdx)
                if (draft.photoUri == null) draft.photoUri = cursor.getString(photoIdx)

                when (cursor.getString(mimeIdx)) {
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                        cursor.getString(data2Idx)?.takeIf { it.isNotBlank() }
                            ?.let { draft.givenName = it }
                        cursor.getString(data3Idx)?.takeIf { it.isNotBlank() }
                            ?.let { draft.familyName = it }
                    }
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE ->
                        cursor.getString(data1Idx)?.takeIf { it.isNotBlank() }
                            ?.let { draft.phones.add(it.trim()) }
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE ->
                        cursor.getString(data1Idx)?.takeIf { it.isNotBlank() }
                            ?.let { draft.emails.add(it.trim()) }
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE ->
                        cursor.getString(data1Idx)?.takeIf { it.isNotBlank() }
                            ?.let { draft.company = it }
                    ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE ->
                        cursor.getString(data1Idx)?.takeIf { it.isNotBlank() }
                            ?.let { draft.note = it }
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
     * Convertit un brouillon en [Person] JTR. Le prénom est OBLIGATOIRE :
     * repli sur le premier mot du nom affiché, sinon le contact est ignoré.
     * Les listes téléphones/emails alimentent les lignes dynamiques ET les
     * scalaires dénormalisés (« 1ʳᵉ ligne », lus par les workers et cartes).
     */
    private fun ContactDraft.toPerson(): Person? {
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
        // ralentissement du traitement par lots sur Dispatchers.IO.
        val uniquePhones = phones.toList().distinctBy { phoneKey(it) }
        val uniqueEmails = emails.toList().distinctBy { it.lowercase() }

        return Person(
            firstName = first,
            lastName = last,
            photoUri = copyNativePhoto(photoUri),
            phoneNumber = uniquePhones.firstOrNull(),
            email = uniqueEmails.firstOrNull(),
            company = company,
            notes = note,
            phoneLines = uniquePhones.map { DynamicLine(value = it, label = FieldTypes.PHONE_MOBILE) }
                .takeIf { it.isNotEmpty() },
            emailLines = uniqueEmails.map { DynamicLine(value = it, label = FieldTypes.EMAIL_HOME) }
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
