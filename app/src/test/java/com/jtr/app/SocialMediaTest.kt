package com.jtr.app

import com.google.common.truth.Truth.assertThat
import com.jtr.app.utils.SocialPlatform
import com.jtr.app.utils.getSocialIcon
import org.junit.Test

/**
 * Détection des liens sociaux — SOURCE UNIQUE (icône + libellé). Couvre le bug v7.1.18 :
 * « snapchat.com » contient la sous-chaîne « t.co » (motif de X) → un `contains` naïf le
 * prenait pour X (libellé « X », mauvaise icône). La détection par host avec frontière de
 * domaine ([SocialPlatform.detect]) corrige la source. Tests 100 % JVM ([hostOf] pur).
 */
class SocialMediaTest {

    @Test
    fun `snapchat urls detect Snapchat platform icon and label`() {
        listOf(
            "https://www.snapchat.com/add",
            "https://snapchat.com/add/john.doe",
            "https://t.snapchat.com/abc123",
            "https://snap.com/xyz"
        ).forEach { url ->
            assertThat(SocialPlatform.detect(url)).isEqualTo(SocialPlatform.Snapchat)
            assertThat(SocialPlatform.detect(url)?.displayName).isEqualTo("Snapchat")
            assertThat(getSocialIcon(url)).isEqualTo(R.drawable.ic_snapchat)
        }
    }

    @Test
    fun `snapchat is not misdetected as X despite the t_co substring`() {
        val url = "https://www.snapchat.com/add"
        assertThat(SocialPlatform.detect(url)).isNotEqualTo(SocialPlatform.X)
        assertThat(getSocialIcon(url)).isNotEqualTo(R.drawable.ic_x)
        assertThat(getSocialIcon(url)).isNotEqualTo(R.drawable.ic_link)
    }

    @Test
    fun `instagram still detected (non-regression)`() {
        val url = "https://www.instagram.com/john"
        assertThat(SocialPlatform.detect(url)).isEqualTo(SocialPlatform.Instagram)
        assertThat(SocialPlatform.detect(url)?.displayName).isEqualTo("Instagram")
        assertThat(getSocialIcon(url)).isEqualTo(R.drawable.ic_instagram)
    }

    @Test
    fun `X urls still detected by their own hosts`() {
        assertThat(SocialPlatform.detect("https://x.com/john")).isEqualTo(SocialPlatform.X)
        assertThat(SocialPlatform.detect("https://twitter.com/john")).isEqualTo(SocialPlatform.X)
        assertThat(SocialPlatform.detect("https://t.co/abc")).isEqualTo(SocialPlatform.X)
        assertThat(getSocialIcon("https://x.com/john")).isEqualTo(R.drawable.ic_x)
    }

    @Test
    fun `unknown url falls back to generic link and null platform`() {
        assertThat(SocialPlatform.detect("https://example.com/page")).isNull()
        assertThat(getSocialIcon("https://example.com/page")).isEqualTo(R.drawable.ic_link)
    }

    @Test
    fun `host extraction handles scheme www and path`() {
        assertThat(SocialPlatform.hostOf("https://www.snapchat.com/add")).isEqualTo("snapchat.com")
        assertThat(SocialPlatform.hostOf("https://t.snapchat.com/abc")).isEqualTo("t.snapchat.com")
        assertThat(SocialPlatform.hostOf("snapchat.com/add")).isEqualTo("snapchat.com")
    }
}
