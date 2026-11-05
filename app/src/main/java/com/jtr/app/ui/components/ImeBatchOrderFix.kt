package com.jtr.app.ui.components

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/*
 * v7.1.147 — RÉORDONNANCEMENT touche/action APRÈS le lot IME (contournement d'une course de
 * Compose foundation 1.7.6).
 *
 * SYMPTÔME : clavier Samsung, autocorrection sur Entrée → le mot corrigé s'insère APRÈS le saut
 * de ligne au lieu de remplacer l'original (`tres`⏎ → `Tres⏎Très`), puis la frappe suivante est
 * corrompue. Intermittent (≈ 1 fois sur 2 sur un écran chargé), tous champs multi-lignes.
 *
 * SÉQUENCE IME (tracée sur S21) — tout dans UN lot :
 *   beginBatchEdit, setComposingText("Tres"), commitText("Très"), finishComposingText,
 *   sendKeyEvent(ENTER ↓/↑), endBatchEdit
 *
 * COURSE :
 *  1. L'`InputConnection` de Compose (legacy `RecordingInputConnection`, ET le nouveau
 *     `StatelessInputConnection`) MET EN FILE les éditions et ne les applique qu'au
 *     `endBatchEdit` EXTERNE — contrairement à `EditText`, qui les applique tout de suite.
 *  2. La touche, elle, ne passe pas par cette file : `BaseInputConnection.sendKeyEvent` →
 *     `ViewRootImpl.dispatchKeyFromIme`, qui poste un message ASYNCHRONE ; les appels IME sont,
 *     eux, des messages synchrones (`RemoteInputConnectionImpl` → `Handler.post`).
 *  3. Si un cadre est en attente, la barrière de synchro du Choreographer bloque le
 *     `endBatchEdit` mais PAS la touche : le `\n` est appliqué sur un tampon qui ne contient pas
 *     encore la correction, puis le `commitText` en file, sans plus de composition à remplacer,
 *     s'insère au curseur — après le `\n`.
 *
 * CORRECTIF : [ImeBatchOrderingInputConnection] retient `sendKeyEvent` et `performEditorAction`
 * émis PENDANT un lot et les relivre, dans l'ordre, juste APRÈS le `endBatchEdit` externe (donc
 * après application des éditions). Hors lot : passage direct. TOUT le reste passe tel quel.
 * Posé UNE fois à la racine ([ImeBatchOrderingRoot]) : tous les champs Compose, dialogues et
 * feuilles compris (les CompositionLocals s'y propagent), en profitent.
 *
 * ⚠️ DETTE ASSUMÉE : `InterceptPlatformTextInput` est `@ExperimentalComposeUiApi` et le correctif
 * dépend de détails internes de foundation (file d'éditions par lot). À CHAQUE BUMP DU BOM
 * COMPOSE : revérifier que la file existe toujours et que la course persiste (rejouer `tres`⏎ /
 * mots accentués⏎ au clavier Samsung, avec et sans ce wrapper) ; RETIRER ce fichier et son
 * enveloppe dans `MainActivity` si Compose corrige l'ordre en amont.
 */

/**
 * Logique d'ordre PURE (sans Android) : profondeur de lot + file des actions différées.
 * Séparée du wrapper pour être testable en JVM (les classes Android y sont des stubs muets).
 */
internal class ImeBatchOrderer {
    private var depth = 0
    private val pending = ArrayList<() -> Unit>()

    /** Vrai tant qu'un lot IME est ouvert. */
    val inBatch: Boolean get() = depth > 0

    /** Nombre d'actions en attente (diagnostic/test). */
    val pendingCount: Int get() = pending.size

    fun onBeginBatch() {
        depth++
    }

    /**
     * À appeler APRÈS le `endBatchEdit` de la cible (les éditions du lot sont alors appliquées).
     * Un `endBatchEdit` en trop est ignoré (profondeur bornée à 0, pas de vidage parasite).
     * La file n'est vidée qu'au lot EXTERNE ; elle est copiée puis vidée AVANT la relivraison,
     * pour qu'une réentrance (une action relivrée qui rouvre un lot) ne la rejoue pas.
     */
    fun onEndBatch() {
        if (depth == 0) return
        depth--
        if (depth > 0 || pending.isEmpty()) return
        val toDeliver = pending.toList()
        pending.clear()
        toDeliver.forEach { it() }
    }

    /** Pendant un lot : met [deliver] en file et répond `true` (accepté) ; sinon le livre tout de suite. */
    fun deliverOrDefer(deliver: () -> Boolean): Boolean {
        if (depth > 0) {
            pending += { deliver() }
            return true
        }
        return deliver()
    }

    /** Connexion fermée : abandon propre (la cible est inactive, relivrer n'aurait aucun sens). */
    fun onClose() {
        pending.clear()
        depth = 0
    }
}

/**
 * [InputConnection] de Compose enveloppée : seuls `sendKeyEvent` et `performEditorAction` sont
 * réordonnés (cf. en-tête du fichier) ; tout le reste est délégué tel quel.
 */
internal class ImeBatchOrderingInputConnection(
    target: InputConnection
) : InputConnectionWrapper(target, false) {
    private val orderer = ImeBatchOrderer()

    override fun beginBatchEdit(): Boolean {
        orderer.onBeginBatch()
        return super.beginBatchEdit()
    }

    override fun endBatchEdit(): Boolean {
        val result = super.endBatchEdit()
        orderer.onEndBatch()
        return result
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean =
        orderer.deliverOrDefer { super.sendKeyEvent(event) }

    override fun performEditorAction(editorAction: Int): Boolean =
        orderer.deliverOrDefer { super.performEditorAction(editorAction) }

    override fun closeConnection() {
        orderer.onClose()
        super.closeConnection()
    }
}

/**
 * Intercepteur SANS ÉTAT, donc instance unique : un intercepteur différent d'une composition à
 * l'autre redémarrerait la session de saisie en cours (contrat d'[InterceptPlatformTextInput]).
 */
@OptIn(ExperimentalComposeUiApi::class)
private val ImeBatchOrderingInterceptor = PlatformTextInputInterceptor { request, nextHandler ->
    nextHandler.startInputMethod(
        PlatformTextInputMethodRequest { outAttributes: EditorInfo ->
            ImeBatchOrderingInputConnection(request.createInputConnection(outAttributes))
        }
    )
}

/** Pose le réordonnancement pour tout [content] (à appeler une fois, à la racine de `setContent`). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ImeBatchOrderingRoot(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(interceptor = ImeBatchOrderingInterceptor, content = content)
}
