package com.jtr.app.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class SocialLink(val platform: SocialPlatform, val url: String)

sealed class SocialPlatform(
    val displayName: String,
    val packageId: String,
    val argbColor: Long,
    val urlPatterns: List<String>
) {
    object Instagram : SocialPlatform("Instagram", "com.instagram.android",  0xFFE1306CL, listOf("instagram.com", "instagr.am"))
    object LinkedIn  : SocialPlatform("LinkedIn",  "com.linkedin.android",   0xFF0A66C2L, listOf("linkedin.com", "lnkd.in"))
    object X         : SocialPlatform("X",          "com.twitter.android",    0xFF1D9BF0L, listOf("twitter.com", "x.com", "t.co"))
    object Facebook  : SocialPlatform("Facebook",   "com.facebook.katana",    0xFF1877F2L, listOf("facebook.com", "fb.com", "fb.me"))
    object Snapchat  : SocialPlatform("Snapchat",   "com.snapchat.android",   0xFFFFFC00L, listOf("snapchat.com", "snap.com"))
    object TikTok    : SocialPlatform("TikTok",     "com.zhiliaoapp.musically", 0xFFFF0050L, listOf("tiktok.com", "vm.tiktok.com"))

    companion object {
        val all: List<SocialPlatform> = listOf(Instagram, LinkedIn, X, Facebook, Snapchat, TikTok)
    }
}

fun SocialPlatform.icon(): ImageVector = when (this) {
    SocialPlatform.Instagram -> Icons.Default.PhotoCamera
    SocialPlatform.LinkedIn  -> Icons.Default.Work
    SocialPlatform.X         -> Icons.Default.AlternateEmail
    SocialPlatform.Facebook  -> Icons.Default.Group
    SocialPlatform.Snapchat  -> Icons.Default.CameraAlt
    SocialPlatform.TikTok    -> Icons.Default.MusicNote
}

private val urlRegex = Regex("""https?://[^\s,\n"'<>]+""")

fun extractSocialLinks(text: String): List<SocialLink> =
    urlRegex.findAll(text)
        .mapNotNull { match ->
            val url = match.value
            SocialPlatform.all
                .firstOrNull { p -> p.urlPatterns.any { url.contains(it, ignoreCase = true) } }
                ?.let { SocialLink(it, url) }
        }
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
