package com.jtr.app.data.local

import androidx.room.TypeConverter
import com.jtr.app.domain.model.DynamicLine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * TypeConverters Room (DB v12) — sérialise les listes répétables [DynamicLine]
 * en JSON pour les stocker dans des colonnes TEXT de l'entité Person.
 *
 * - `null` ↔ colonne SQL NULL (profils legacy avant migration des données).
 * - `encodeDefaults = true` : tous les champs (id, value, label) sont écrits.
 * - `ignoreUnknownKeys = true` + repli sur liste vide : robustesse à tout JSON
 *   partiel/corrompu (jamais de crash de désérialisation).
 */
class Converters {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun fromDynamicLines(value: List<DynamicLine>?): String? =
        value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toDynamicLines(value: String?): List<DynamicLine>? =
        if (value.isNullOrBlank()) null
        else try {
            json.decodeFromString<List<DynamicLine>>(value)
        } catch (_: Exception) {
            null
        }
}
