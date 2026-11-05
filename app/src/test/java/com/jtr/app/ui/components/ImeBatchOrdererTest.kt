package com.jtr.app.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * v7.1.147 — contrat de [ImeBatchOrderer], la logique d'ordre du wrapper
 * [ImeBatchOrderingInputConnection] (cf. ImeBatchOrderFix.kt) :
 *
 *  - hors lot : touche/action livrée TOUT DE SUITE, valeur de retour de la cible propagée ;
 *  - pendant un lot : différée (réponse `true`), relivrée dans l'ORDRE juste après le
 *    `endBatchEdit` EXTERNE — jamais au `endBatchEdit` d'un lot imbriqué ;
 *  - un `endBatchEdit` en trop est borné à 0 et ne vide rien ;
 *  - connexion fermée : file abandonnée ;
 *  - réentrance : une action relivrée qui rouvre un lot ne rejoue pas la file.
 *
 * `log` simule la cible : on y écrit l'ordre réel d'arrivée, éditions du lot comprises
 * (« endBatch » = le moment où la cible applique le lot).
 */
class ImeBatchOrdererTest {

    private val log = mutableListOf<String>()
    private val orderer = ImeBatchOrderer()

    private fun key(name: String): Boolean = orderer.deliverOrDefer { log += name; true }

    private fun begin() = orderer.onBeginBatch()

    /** Comme le wrapper : la cible applique le lot, PUIS l'orderer est notifié. */
    private fun end() {
        log += "endBatch"
        orderer.onEndBatch()
    }

    @Test
    fun `outside a batch the key is delivered immediately`() {
        assertThat(key("ENTER")).isTrue()
        assertThat(log).containsExactly("ENTER")
        assertThat(orderer.pendingCount).isEqualTo(0)
    }

    @Test
    fun `outside a batch the target return value is propagated`() {
        assertThat(orderer.deliverOrDefer { false }).isFalse()
    }

    @Test
    fun `inside a batch the key is deferred until after endBatchEdit, in order`() {
        begin()
        log += "commit(Très)"
        assertThat(key("ENTER_DOWN")).isTrue()
        assertThat(key("ENTER_UP")).isTrue()
        assertThat(log).containsExactly("commit(Très)")
        assertThat(orderer.pendingCount).isEqualTo(2)

        end()
        assertThat(log).containsExactly("commit(Très)", "endBatch", "ENTER_DOWN", "ENTER_UP").inOrder()
        assertThat(orderer.pendingCount).isEqualTo(0)
        assertThat(orderer.inBatch).isFalse()
    }

    @Test
    fun `nested batches flush only at the outer endBatchEdit`() {
        // Séquence Samsung réelle : lot externe, lot imbriqué autour du commit, touche, fin externe.
        begin()
        begin()
        log += "commit(Très)"
        end() // fin du lot IMBRIQUÉ
        key("ENTER")
        assertThat(log).containsExactly("commit(Très)", "endBatch")
        end() // fin du lot EXTERNE
        assertThat(log).containsExactly("commit(Très)", "endBatch", "endBatch", "ENTER").inOrder()
    }

    @Test
    fun `key deferred inside an inner batch waits for the outer one`() {
        begin()
        begin()
        key("ENTER")
        end()
        assertThat(orderer.pendingCount).isEqualTo(1)
        end()
        assertThat(log.last()).isEqualTo("ENTER")
    }

    @Test
    fun `extra endBatchEdit is clamped and does not flush anything`() {
        end()
        end()
        assertThat(orderer.inBatch).isFalse()
        assertThat(key("ENTER")).isTrue()
        assertThat(log).containsExactly("endBatch", "endBatch", "ENTER").inOrder()

        // Après un déséquilibre, un lot normal se comporte normalement.
        begin()
        key("DEL")
        assertThat(orderer.pendingCount).isEqualTo(1)
        end()
        assertThat(log.last()).isEqualTo("DEL")
    }

    @Test
    fun `editor action is deferred like a key`() {
        begin()
        orderer.deliverOrDefer { log += "action(NEXT)"; true }
        log += "finishComposing"
        end()
        assertThat(log).containsExactly("finishComposing", "endBatch", "action(NEXT)").inOrder()
    }

    @Test
    fun `closing the connection drops pending deliveries and resets depth`() {
        begin()
        key("ENTER")
        orderer.onClose()
        assertThat(orderer.pendingCount).isEqualTo(0)
        assertThat(orderer.inBatch).isFalse()
        end() // endBatchEdit tardif sur une connexion fermée : rien à relivrer
        assertThat(log).containsExactly("endBatch")
    }

    @Test
    fun `a redelivered key that reopens a batch does not replay the queue`() {
        begin()
        orderer.deliverOrDefer {
            log += "ENTER"
            // Réentrance : la livraison rouvre un lot et y diffère une nouvelle touche.
            begin()
            key("NESTED")
            true
        }
        key("UP")
        end()
        // ENTER et UP relivrés une seule fois ; NESTED attend la fin du lot qu'ENTER a ouvert.
        assertThat(log).containsExactly("endBatch", "ENTER", "UP").inOrder()
        assertThat(orderer.pendingCount).isEqualTo(1)
        end()
        assertThat(log).containsExactly("endBatch", "ENTER", "UP", "endBatch", "NESTED").inOrder()
    }
}
