package com.jtr.app.data.repository

import com.jtr.app.data.remote.ApiClient
import com.jtr.app.data.remote.GeocodingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GeocodingRepository — Pont entre l'API externe et l'app.
 *
 * [PP3] Convertit un nom de ville en coordonnées GPS pour le géofencing.
 */
class GeocodingRepository {

    private val api = ApiClient.nominatimApi

    /**
     * Recherche les coordonnées d'une ville.
     * Retourne null en cas d'échec ou si la ville est introuvable.
     *
     * v7.1.46 — SÉLECTION DÉTERMINISTE. L'ancienne version demandait `limit=1` et gardait
     * `firstOrNull()` : elle subissait donc l'ordre de Nominatim. Or cet ordre n'est PAS stable
     * pour une ville — deux objets OSM d'une même agglomération reviennent avec la MÊME
     * `importance`, et c'est l'en-tête `Accept-Language` (posé par l'intercepteur v7.1.31 pour
     * traduire les LIBELLÉS) qui les départageait. Conséquence mesurée sur « Safi أسفي » :
     *  - UI en anglais → objet `city`           à 32.2994, -9.2395 ;
     *  - UI en arabe   → objet `administrative` à 32.2650, -9.2305 (~4 km plus au sud).
     * La coordonnée ENREGISTRÉE dépendait donc de la langue de l'app au moment de la saisie.
     *
     * On demande maintenant plusieurs candidats et on tranche NOUS-MÊMES, par un ordre TOTAL :
     *  1. la nature du lieu — une localité (`city`/`town`/`village`/`municipality`, ou `class=place`)
     *     l'emporte sur une frontière administrative, dont le point est un centroïde de polygone ;
     *  2. `importance` décroissante ;
     *  3. `osm_type`+`osm_id` croissants — départage arbitraire mais INVARIANT, qui garantit qu'un
     *     ex æquo parfait donne toujours le même résultat. On n'utilise PAS `place_id` : mesuré,
     *     il change d'une requête à l'autre pour un même objet (identifiant interne au nœud du
     *     cluster Nominatim), il n'aurait donné qu'une illusion de déterminisme.
     * Le tri ne dépend d'aucune donnée localisée ⇒ même ville, même coordonnée, quelle que soit
     * la langue. L'intercepteur global et le picker de carte ne sont pas touchés.
     *
     * LIMITE ASSUMÉE : on ne peut trancher qu'entre les candidats REÇUS. Le service renvoie parfois
     * un jeu de résultats amputé (nœuds désynchronisés) ; si la localité en est absente, le meilleur
     * choix possible reste la frontière. C'est pourquoi le zoom « ville » de la mini-carte
     * (v7.1.46 également) est l'autre moitié du correctif : il rend l'écart kilométrique résiduel
     * sans conséquence visuelle.
     */
    suspend fun getCityCoordinates(cityName: String): GeocodingResult? {
        return withContext(Dispatchers.IO) {
            try {
                api.searchCity(cityName, limit = CANDIDATE_LIMIT).bestCityMatch()
            } catch (e: Exception) {
                null
            }
        }
    }

    private companion object {
        /** Assez de candidats pour que la vraie localité soit présente, assez peu pour rester léger. */
        const val CANDIDATE_LIMIT = 8

        /** Types Nominatim désignant une localité habitée — préférés à une frontière. */
        val LOCALITY_TYPES = setOf("city", "town", "village", "municipality")
    }

    /**
     * Meilleur candidat « ville » d'une réponse Nominatim, selon l'ordre total décrit dans
     * [getCityCoordinates]. Les résultats sans coordonnée exploitable sont écartés d'emblée.
     */
    private fun List<GeocodingResult>.bestCityMatch(): GeocodingResult? =
        filter { it.latitude != null && it.longitude != null }
            .minWithOrNull(
                compareBy<GeocodingResult> { if (it.isLocality) 0 else 1 }
                    .thenByDescending { it.importance ?: 0.0 }
                    .thenBy { it.osmType.orEmpty() }
                    .thenBy { it.osmId ?: Long.MAX_VALUE }
            )

    /** Vrai si le résultat décrit une localité habitée plutôt qu'une frontière administrative. */
    private val GeocodingResult.isLocality: Boolean
        get() = addressType?.lowercase() in LOCALITY_TYPES ||
            placeClass?.lowercase() == "place"
}
