package com.jtr.app

import com.google.common.truth.Truth.assertThat
import com.jtr.app.worker.ReminderScheduler
import org.junit.Test

/**
 * Plafond d'alarmes exactes (v7.1.57, correctif C4).
 *
 * Android 13+ limite une app à 500 alarmes exactes : au-delà, `setExactAndAllowWhileIdle`
 * lève `IllegalStateException`, qui remontait jusqu'à `rescheduleAll` — appelée au démarrage,
 * à chaque sauvegarde et au boot. Ces tests verrouillent la SÉLECTION : jamais plus de
 * [ReminderScheduler.MAX_EXACT_ALARMS] armements, et ce sont toujours les échéances les plus
 * proches qui sont retenues.
 */
class ReminderAlarmCapTest {

    /** Génère [count] candidats d'armement, volontairement dans le DÉSORDRE temporel. */
    private fun candidates(count: Int): List<Pair<Long, String>> =
        (0 until count).map { i ->
            // Les instants décroissent avec l'index : le dernier élément de la liste est le
            // plus proche → un simple `take()` sans tri échouerait au test suivant.
            (count - i).toLong() * 60_000L to "person$i|line$i"
        }

    @Test
    fun `au-dela du plafond, seules MAX_EXACT_ALARMS alarmes sont armees`() {
        val armed = ReminderScheduler.capByProximity(candidates(1_000))

        assertThat(armed).hasSize(ReminderScheduler.MAX_EXACT_ALARMS)
        assertThat(ReminderScheduler.MAX_EXACT_ALARMS).isLessThan(500) // marge sous le quota OS
    }

    @Test
    fun `sous le plafond, tout est arme`() {
        val armed = ReminderScheduler.capByProximity(candidates(120))

        assertThat(armed).hasSize(120)
    }

    @Test
    fun `le plafond retient les echeances les PLUS PROCHES`() {
        val armed = ReminderScheduler.capByProximity(candidates(1_000))

        // Les instants sont strictement croissants ET tous inférieurs au premier écarté.
        val instants = armed.map { it.first }
        assertThat(instants).isInOrder()
        assertThat(instants.max()).isEqualTo(ReminderScheduler.MAX_EXACT_ALARMS.toLong() * 60_000L)
    }

    @Test
    fun `la selection est deterministe a instants egaux`() {
        // Tous ex æquo : `sortedBy` étant stable, l'ordre de collecte doit être conservé —
        // deux recalculs successifs sur les mêmes données arment donc les mêmes clés.
        val exAequo = (0 until 1_000).map { 42L to "person$it|line$it" }

        val first = ReminderScheduler.capByProximity(exAequo)
        val second = ReminderScheduler.capByProximity(exAequo)

        assertThat(first).isEqualTo(second)
        assertThat(first.first().second).isEqualTo("person0|line0")
    }

    @Test
    fun `une liste vide n'arme rien`() {
        assertThat(ReminderScheduler.capByProximity(emptyList())).isEmpty()
    }
}
