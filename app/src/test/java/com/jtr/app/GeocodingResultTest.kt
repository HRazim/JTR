package com.jtr.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.jtr.app.data.remote.GeocodingResult
import org.junit.Test

/**
 * Tests unitaires du modèle GeocodingResult.
 */
class GeocodingResultTest {

    @Test
    fun `latitude and longitude are correctly parsed from strings`() {
        val result = GeocodingResult(
            lat = "45.5017",
            lon = "-73.5673",
            displayName = "Montréal, Québec, Canada",
            placeId = 12345L
        )

        assertThat(result.latitude).isWithin(0.0001).of(45.5017)
        assertThat(result.longitude).isWithin(0.0001).of(-73.5673)
    }
}
