package com.jtr.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Or constant de l'étoile favori — INDÉPENDANT du thème. */
private val FavoriteGold = Color(0xFFFFC400)

/**
 * Liseré sombre semi-opaque : l'étoile reste nette sur N'IMPORTE quel fond ou
 * couleur de thème (réglait l'or qui se fondait dans l'orange du preset Coral).
 */
private val FavoriteOutline = Color(0x8A000000)

/**
 * Indicateur « favori » lisible sur les 6 presets + mode sombre ET sur toute
 * photo : étoile or constante posée sur une étoile-liseré sombre légèrement plus
 * grande (contour de contraste). Centrée dans [modifier].
 *
 * @param modifier positionnement (align/padding…) du conteneur.
 * @param size taille de l'étoile or ; le liseré la déborde de quelques dp.
 * @param contentDescription accessibilité (null = purement décoratif).
 */
@Composable
fun FavoriteStar(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    contentDescription: String? = null,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            tint = FavoriteOutline,
            modifier = Modifier.size(size + 3.dp)
        )
        Icon(
            Icons.Filled.Star,
            contentDescription = contentDescription,
            tint = FavoriteGold,
            modifier = Modifier.size(size)
        )
    }
}
