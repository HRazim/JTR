package com.jtr.app.ui.person

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v7.1.122 — condition pure « faut-il géocoder ? » : une ville présente sans coordonnées se
 * géocode (y compris quand l'auto-save l'a déjà persistée, donc « inchangée ») ; une ville
 * absente, vide, ou déjà pourvue de coordonnées (saisie carte) ne déclenche rien.
 */
class ShouldGeocodeCityTest {

    @Test
    fun cityWithoutCoordinates_geocodes() {
        assertTrue(shouldGeocodeCity("Lyon", null))
    }

    @Test
    fun cityWithCoordinates_doesNotGeocode() {
        assertFalse(shouldGeocodeCity("Lyon", 45.764043))
    }

    @Test
    fun noCity_doesNotGeocode() {
        assertFalse(shouldGeocodeCity(null, null))
    }

    @Test
    fun blankCity_doesNotGeocode() {
        assertFalse(shouldGeocodeCity("   ", null))
    }

    @Test
    fun cityFromMap_keepsItsCoordinates() {
        // Une ville posée par la carte arrive avec lat/lng → aucun géocodage réseau.
        assertFalse(shouldGeocodeCity("Paris", 48.8534951))
    }
}
