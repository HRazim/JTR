package com.jtr.app.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import com.jtr.app.R

data class SocialLink(val platform: SocialPlatform, val url: String)

/**
 * Plateforme sociale reconnue — SOURCE UNIQUE de détection (icône de marque + libellé +
 * paquet d'ouverture) partagée par le dialogue d'ajout, l'Accueil et le profil.
 *
 * [iconRes] est le vector drawable de marque (rendu NON teinté → couleurs d'origine).
 * [hosts] sont des DOMAINES comparés au host de l'URL avec une frontière de domaine
 * (cf. [detect]) — jamais une sous-chaîne brute, sinon « snapchat.com » (qui contient
 * « t.co ») serait pris pour X.
 */
sealed class SocialPlatform(
    val displayName: String,
    val packageId: String,
    @DrawableRes val iconRes: Int,
    val hosts: List<String>
) {
    object Instagram : SocialPlatform("Instagram", "com.instagram.android", R.drawable.ic_instagram, listOf("instagram.com", "instagr.am"))
    object LinkedIn  : SocialPlatform("LinkedIn", "com.linkedin.android", R.drawable.ic_linkedin, listOf("linkedin.com", "lnkd.in"))
    object X         : SocialPlatform("X", "com.twitter.android", R.drawable.ic_x, listOf("twitter.com", "x.com", "t.co"))
    object Facebook  : SocialPlatform("Facebook", "com.facebook.katana", R.drawable.ic_facebook, listOf("facebook.com", "fb.com", "fb.me"))
    object Snapchat  : SocialPlatform("Snapchat", "com.snapchat.android", R.drawable.ic_snapchat, listOf("snapchat.com", "snap.com"))
    // TikTok : pas d'icône de marque dédiée → repli ic_link (on n'invente pas de logo).
    object TikTok    : SocialPlatform("TikTok", "com.zhiliaoapp.musically", R.drawable.ic_link, listOf("tiktok.com", "vm.tiktok.com"))
    object Discord   : SocialPlatform("Discord", "com.discord", R.drawable.ic_discord, listOf("discord.com", "discord.gg"))
    object YouTube   : SocialPlatform("YouTube", "com.google.android.youtube", R.drawable.ic_youtube, listOf("youtube.com", "youtu.be"))

    companion object {
        val all: List<SocialPlatform> =
            listOf(Instagram, LinkedIn, X, Facebook, Snapchat, TikTok, Discord, YouTube)

        /**
         * Détecte la plateforme d'une URL par son HOST, avec FRONTIÈRE DE DOMAINE
         * (`host == d` ou `host` se termine par `.d`). Indispensable : un `contains` naïf
         * prendrait « snapchat.com » pour X car il contient la sous-chaîne « t.co ».
         * Couvre www., chemins (/add/<user>) et sous-domaines (t.snapchat.com).
         */
        fun detect(url: String): SocialPlatform? {
            val host = hostOf(url) ?: return null
            return all.firstOrNull { p -> p.hosts.any { host == it || host.endsWith(".$it") } }
        }

        /**
         * Extrait le host d'une URL en KOTLIN PUR (sans android.net.Uri → testable en JVM) :
         * retire le schéma, l'éventuel userinfo/port et le préfixe « www. ». Tolère une URL
         * sans schéma (« snapchat.com/add »). Renvoie `null` si vide.
         */
        internal fun hostOf(url: String): String? {
            val afterScheme = url.substringAfter("://", url)
            val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
            val host = authority.substringAfterLast('@').substringBefore(':')
                .lowercase().removePrefix("www.")
            return host.ifBlank { null }
        }
    }
}

private val urlRegex = Regex("""https?://[^\s,\n"'<>]+""")

/** Liens sociaux trouvés dans un texte libre (détection UNIQUE via [SocialPlatform.detect]). */
fun extractSocialLinks(text: String): List<SocialLink> =
    urlRegex.findAll(text)
        .mapNotNull { match -> SocialPlatform.detect(match.value)?.let { SocialLink(it, match.value) } }
        .distinctBy { it.url }
        .toList()

fun openSocialLink(context: Context, link: SocialLink) {
    val uri = Uri.parse(link.url)
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(link.platform.packageId))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
