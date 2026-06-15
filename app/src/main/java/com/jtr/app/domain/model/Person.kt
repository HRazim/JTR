package com.jtr.app.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Person — Version finale (PP3).
 * Ajout des coordonnées GPS (cityLat, cityLng) pour le géofencing.
 */
@Entity(tableName = "persons")
data class Person(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val firstName: String,
    val lastName: String? = null,
    val gender: String? = null,
    val photoUri: String? = null,
    val birthdate: Long? = null,
    val birthdateNotify: Boolean = false,
    // v7.0 (DB v19) — délai de rappel de l'anniversaire en MINUTES avant l'ancre
    // minuit (0 = « Le jour J »). Projection scalaire dénormalisée de la ligne
    // anniversaire (cf. DynamicLine.reminderOffsetMinutes), au même titre que
    // birthdate / birthdateNotify. Repli pour les profils n'ayant que le scalaire.
    @ColumnInfo(defaultValue = "0")
    val birthdateReminderOffsetMinutes: Int = 0,
    val city: String? = null,
    val cityLat: Double? = null,           // NOUVEAU PP3 : coordonnées GPS
    val cityLng: Double? = null,           // NOUVEAU PP3 : pour le géofencing
    val cityNotify: Boolean = false,
    val isFavorite: Boolean = false,
    val lastContactedAt: Long? = null,
    val notes: String? = null,
    val likes: String? = null,
    // v7.0.3 (DB v20) — sections de notes personnalisables (titre + icône + contenu),
    // sérialisées en JSON. Remplacent `notes`/`likes` dans l'UI ; ces deux colonnes
    // sont CONSERVÉES (héritées) et backfillées sans perte (cf. [deriveNoteSections]).
    @ColumnInfo(defaultValue = "[]")
    val noteSections: List<NoteSection> = emptyList(),
    val origin: String? = null,
    val phoneNumber: String? = null,
    val email: String? = null,
    // v4.5 (DB v12) — sous-champs de nom avancés (1:1)
    val prefix: String? = null,
    val middleName: String? = null,
    val suffix: String? = null,
    val phonetic: String? = null,
    val nickname: String? = null,
    // v4.5 (DB v13) — informations professionnelles (style « Contacts Google »)
    val jobTitle: String? = null,
    val department: String? = null,
    val company: String? = null,
    // v4.5 (DB v12) — listes répétables (1:N) sérialisées en JSON via TypeConverters.
    // `phoneNumber` / `email` / `birthdate` restent la projection « 1ère ligne » lue
    // par les workers, les cartes et les actions rapides (dénormalisation).
    val phoneLines: List<DynamicLine>? = null,
    val emailLines: List<DynamicLine>? = null,
    val dateLines: List<DynamicLine>? = null,
    val relationLines: List<DynamicLine>? = null,
    val createdAt: Long = System.currentTimeMillis(),
    // v4.5 (DB v14) — horodatage de dernière modification, mis à jour à chaque save.
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
    // v5.4 (DB v17) — anti-spam du Moteur de Proximité : horodatage de la dernière
    // alerte envoyée pour CE contact (pas plus d'une notification par 48 h).
    val proximityNotifiedAt: Long? = null,
    val deletedAt: Long? = null
) {
    val fullName: String
        get() = listOfNotNull(firstName, lastName).joinToString(" ")

    val initials: String
        get() = buildString {
            append(firstName.firstOrNull()?.uppercaseChar() ?: "")
            append(lastName?.firstOrNull()?.uppercaseChar() ?: "")
        }

    /**
     * Indique si la personne a des coordonnées GPS valides pour le géofencing.
     */
    val hasGeoCoordinates: Boolean
        get() = cityLat != null && cityLng != null

    /**
     * Jours écoulés depuis le dernier contact (null si jamais contacté).
     */
    fun daysSinceLastContact(): Long? {
        if (lastContactedAt == null) return null
        val diff = System.currentTimeMillis() - lastContactedAt
        return diff / (1000L * 60 * 60 * 24)
    }
}
