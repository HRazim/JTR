package com.jtr.app.worker

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.*

/**
 * Tests unitaires pour le calcul de distance du ProximityCheckWorker.
 */
class DistanceCalculationTest {

    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    @Test
    fun `distance between same point is zero`() {
        val distance = calculateDistance(45.5, -73.5, 45.5, -73.5)
        assertThat(distance).isWithin(0.01).of(0.0)
    }

    @Test
    fun `distance Montreal to Quebec City is approximately 234 km`() {
        // Montréal : 45.5017, -73.5673
        // Québec : 46.8139, -71.2080
        val distance = calculateDistance(45.5017, -73.5673, 46.8139, -71.2080)
        assertThat(distance).isWithin(10.0).of(234.0)
    }

    @Test
    fun `distance Chicoutimi to Saguenay is small`() {
        // Villes proches
        val distance = calculateDistance(48.4286, -71.0687, 48.3440, -70.9896)
        assertThat(distance).isLessThan(15.0)
    }
}
