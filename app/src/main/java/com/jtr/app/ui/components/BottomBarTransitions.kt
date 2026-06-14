package com.jtr.app.ui.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically

/**
 * Transitions PARTAGÉES des barres basses : la `NavigationBar` globale (Navigation.kt)
 * ET tous les footers contextuels du mode sélection (Accueil, Catégories, détail de
 * catégorie, détail de dossier).
 *
 * Fade + expand/shrink vertical avec un MÊME `tween(300)`. Comme la barre normale et
 * le footer de sélection vivent dans deux `Scaffold` distincts (racine vs écran), un
 * `Crossfade` unique n'est pas possible ; à la place, en partageant strictement la
 * même courbe et la même durée, leurs hauteurs s'animent de façon SYNCHRONE et
 * opposée (l'une rétrécit pendant que l'autre grandit). La hauteur cumulée du bas
 * reste constante → la liste au-dessus ne subit plus de « saut de layout », et le
 * ressenti est celui d'un remplacement sur place, doux et continu.
 */
object JtrBottomBarTransitions {
    private const val DURATION_MS = 300

    val enter: EnterTransition =
        fadeIn(tween(DURATION_MS)) + expandVertically(tween(DURATION_MS))

    val exit: ExitTransition =
        fadeOut(tween(DURATION_MS)) + shrinkVertically(tween(DURATION_MS))
}
