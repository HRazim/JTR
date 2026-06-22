package com.jtr.app.utils

import androidx.annotation.DrawableRes
import com.jtr.app.R

/**
 * Icône de marque d'un lien social — résolue par la détection UNIQUE [SocialPlatform.detect]
 * (host avec frontière de domaine, Snapchat-aware). Repli [R.drawable.ic_link] si la
 * plateforme est inconnue. Rendu NON teinté côté UI (tint = Color.Unspecified) → couleurs
 * de marque conservées. Plus aucune liste de domaines dupliquée ici (source unique).
 */
@DrawableRes
fun getSocialIcon(url: String): Int =
    SocialPlatform.detect(url)?.iconRes ?: R.drawable.ic_link
