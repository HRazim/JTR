package com.jtr.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Coche de sélection UNIVERSELLE (v5.5) : icône cochée/décochée posée sur un
 * disque de protection contrastant, garantissant la lisibilité sur N'IMPORTE
 * QUEL fond — photo claire, accent gris, voile de sélection, dégradé sombre.
 *
 * - décochée : anneau `onSurfaceVariant` sur disque `surface` quasi opaque —
 *   visible aussi bien sur fond gris clair que sur image sombre ;
 * - cochée : `CheckCircle` teinté `primary` sur disque `primaryContainer` —
 *   la coche évidée du glyphe laisse transparaître le disque, d'où un
 *   contraste maximal même quand `primary` se confond avec le voile bleuté.
 *
 * Tout nouvel affichage (grille, liste, mosaïque…) DOIT passer par ce
 * composant plutôt que par une `Icon` nue, pour rester évolutif.
 */
@Composable
fun JtrSelectionCheck(
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (selected) Icons.Default.CheckCircle
            else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
