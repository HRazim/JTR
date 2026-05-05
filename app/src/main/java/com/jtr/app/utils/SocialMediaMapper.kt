package com.jtr.app.utils

import android.net.Uri
import androidx.annotation.DrawableRes
import com.jtr.app.R

@DrawableRes
fun getSocialIcon(url: String): Int = try {
    val host = Uri.parse(url).host?.removePrefix("www.") ?: ""
    when {
        host.contains("instagram.com") || host.contains("instagr.am") -> R.drawable.ic_instagram
        host.contains("facebook.com") || host.contains("fb.com") || host.contains("fb.me") -> R.drawable.ic_facebook
        host.contains("linkedin.com") || host.contains("lnkd.in") -> R.drawable.ic_linkedin
        host.contains("twitter.com") || host.contains("x.com") || host.contains("t.co") -> R.drawable.ic_x
        host.contains("discord.com") || host.contains("discord.gg") -> R.drawable.ic_discord
        host.contains("youtube.com") || host.contains("youtu.be") -> R.drawable.ic_youtube
        else -> R.drawable.ic_link
    }
} catch (_: Exception) {
    R.drawable.ic_link
}
