package com.jtr.app.domain.model

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
    val city: String? = null,
    val cityLat: Double? = null,           // NOUVEAU PP3 : coordonnées GPS
    val cityLng: Double? = null,           // NOUVEAU PP3 : pour le géofencing
    val cityNotify: Boolean = false,
    val isFavorite: Boolean = false,
    val categoryId: String? = null,        // NOUVEAU PP3 : lien vers catégorie
    val lastContactedAt: Long? = null,     // NOUVEAU PP3 : pour rappels
    val notes: String? = null,
    val likes: String? = null,
    val origin: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
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
