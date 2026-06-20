package com.jtr.app

import com.google.gson.Gson
import com.jtr.app.domain.model.DynamicLine
import com.jtr.app.domain.model.Person
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v7.1.6 — INTÉGRITÉ DES RELATIONS : une relation est identifiée par un IDENTIFIANT
 * STABLE ([DynamicLine.linkedPersonId]), jamais par le nom. Ces tests garantissent que
 * cet id SURVIT à la sérialisation des DEUX chemins de persistance :
 *  - Room (kotlinx.serialization, colonne JSON `relationLines`) ;
 *  - sauvegarde `.jtr` (Gson) — round-trip relations au niveau données (Cas 4).
 * Et que les données HÉRITÉES (JSON sans le champ) restent lisibles (id ⇒ null).
 */
class RelationLinkTest {

    // Réplique EXACTE de la config Room (data/local/Converters.kt).
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val gson = Gson()

    @Test
    fun kotlinx_roundTrip_preservesLinkedPersonId() {
        val lines = listOf(
            DynamicLine(value = "Ryan", label = "friend", linkedPersonId = "id-ryan-123"),
            DynamicLine(value = "Cousin Bob", label = "custom") // texte libre → pas d'id
        )
        val encoded = json.encodeToString(lines)
        val back = json.decodeFromString<List<DynamicLine>>(encoded)
        assertEquals("id-ryan-123", back[0].linkedPersonId)
        assertEquals("Ryan", back[0].value)
        assertNull(back[1].linkedPersonId)
    }

    @Test
    fun kotlinx_legacyJson_withoutField_decodesToNullId() {
        // JSON ANTÉRIEUR à v7.1.6 (champ absent) → id null, aucune perte de value/label.
        val legacy =
            """[{"id":"1","value":"Ryan","label":"friend","notify":false,"reminderOffsetMinutes":0}]"""
        val back = json.decodeFromString<List<DynamicLine>>(legacy)
        assertNull(back[0].linkedPersonId)
        assertEquals("Ryan", back[0].value)
        assertEquals("friend", back[0].label)
    }

    @Test
    fun gson_jtrRoundTrip_keepsRelationIds() {
        // Cas 4 : export → import `.jtr` (niveau données) préserve la cible par id.
        val person = Person(
            id = "p1",
            firstName = "Alice",
            relationLines = listOf(
                DynamicLine(value = "Ryan", label = "friend", linkedPersonId = "id-ryan"),
                DynamicLine(value = "Inconnu", label = "custom") // non lié
            )
        )
        val encoded = gson.toJson(person)
        val back = gson.fromJson(encoded, Person::class.java)
        val rel = back.relationLines!!
        assertEquals("id-ryan", rel[0].linkedPersonId)
        assertNull(rel[1].linkedPersonId)
    }
}
