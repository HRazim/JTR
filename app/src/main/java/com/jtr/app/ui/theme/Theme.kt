package com.jtr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

@Composable
fun JTRTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    preset: ThemePreset = ThemePreset.JTR_SIGNATURE,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) preset.toDarkColorScheme() else preset.toLightColorScheme()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        // v7.1.74 — poignées de curseur/sélection TRANSPARENTES pour TOUTE l'app, posées à
        // la RACINE du thème (harmonisation validée par HR). Pourquoi ici et pas par champ :
        // Compose n'auto-masque jamais ce handle (goutte affichée indéfiniment, même clavier
        // fermé — vérifié S21) et DEUX mécanismes de PREMIER RENDU capturent la couleur
        // ambiante du moment, ce qui faisait réapparaître une goutte émeraude au tout
        // premier focus malgré un masquage par champ :
        //  1. M3 1.3.x fige `LocalTextSelectionColors.current` dans
        //     `defaultTextFieldColorsCached` à la PREMIÈRE résolution de
        //     `TextFieldDefaults.colors()` du process (ColorScheme.kt:519) et chaque
        //     TextField re-fournit ces couleurs figées en interne (TextField.kt:231) ;
        //  2. foundation fige `handleColor` dans le `drawWithCache` du handle
        //     (AndroidCursorHandle.android.kt, `ColorFilter.tint(handleColor)` construit
        //     à la première passe de dessin).
        // En rendant l'ambiant transparent À LA RACINE, toute capture — cache M3, popup du
        // handle, premier frame — résout transparent : il n'existe plus de couleur de
        // poignée à fuir, dans aucun écran. Le SURLIGNAGE de sélection (backgroundColor du
        // thème, fourni par MaterialTheme juste au-dessus) et le CURSEUR clignotant
        // (cursorColor, indépendant) sont CONSERVÉS : on voit où l'on tape, et
        // « tout sélectionner → copier » fonctionne (poignées invisibles mais
        // fonctionnelles).
        val base = LocalTextSelectionColors.current
        val handleFree = remember(base) {
            TextSelectionColors(
                handleColor = Color.Transparent,
                backgroundColor = base.backgroundColor
            )
        }
        CompositionLocalProvider(LocalTextSelectionColors provides handleFree, content = content)
    }
}
