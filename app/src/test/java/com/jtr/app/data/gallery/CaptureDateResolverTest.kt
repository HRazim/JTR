package com.jtr.app.data.gallery

import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

/**
 * v7.1.108 — contrat du résolveur de date de prise de vue [CaptureDateResolver] :
 *
 *  - PARSING EXIF : `"yyyy:MM:dd HH:mm:ss"` interprété dans le fuseau de l'appareil, ou
 *    dans `OffsetTimeOriginal` quand il est présent ; malformé (dont le `0000:…` de
 *    certains appareils), vide ou absent → `null`, jamais d'exception ;
 *  - CHAÎNE DE REPLI : EXIF → DATE_TAKEN → date du fichier → heure d'import, en sautant
 *    les `0` de MediaStore (colonne non renseignée) comme les `null`.
 */
class CaptureDateResolverTest {

    // ── Parsing EXIF ─────────────────────────────────────────────────────────────

    @Test
    fun `valid exif datetime parses in the device zone`() {
        val zone = ZoneId.of("Europe/Paris")
        val millis = CaptureDateResolver.parseExifDateTime("2023:07:14 18:22:05", null, zone)
        val expected = LocalDateTime.of(2023, 7, 14, 18, 22, 5)
            .atZone(zone).toInstant().toEpochMilli()
        assertThat(millis).isEqualTo(expected)
    }

    @Test
    fun `offset time original wins over the device zone`() {
        // Même instant écrit depuis deux fuseaux : l'offset EXIF doit primer sur zoneId.
        val millis = CaptureDateResolver.parseExifDateTime(
            "2024:01:01 00:00:00", "+05:00", ZoneId.of("Europe/Paris")
        )
        val expected = LocalDateTime.of(2024, 1, 1, 0, 0, 0)
            .atZone(ZoneOffset.of("+05:00")).toInstant().toEpochMilli()
        assertThat(millis).isEqualTo(expected)
    }

    @Test
    fun `malformed offset falls back to the device zone, not to failure`() {
        val zone = ZoneId.of("Asia/Tokyo")
        val withBadOffset =
            CaptureDateResolver.parseExifDateTime("2022:03:09 08:15:30", "pas-un-offset", zone)
        val withoutOffset =
            CaptureDateResolver.parseExifDateTime("2022:03:09 08:15:30", null, zone)
        assertThat(withBadOffset).isEqualTo(withoutOffset)
    }

    @Test
    fun `malformed, zeroed, blank and null exif values give null`() {
        assertThat(CaptureDateResolver.parseExifDateTime("0000:00:00 00:00:00")).isNull()
        assertThat(CaptureDateResolver.parseExifDateTime("2023-07-14 18:22:05")).isNull()
        assertThat(CaptureDateResolver.parseExifDateTime("n'importe quoi")).isNull()
        assertThat(CaptureDateResolver.parseExifDateTime("   ")).isNull()
        assertThat(CaptureDateResolver.parseExifDateTime(null)).isNull()
    }

    // ── Chaîne de repli ──────────────────────────────────────────────────────────

    @Test
    fun `exif wins when present`() {
        assertThat(CaptureDateResolver.resolve(1_000L, 2_000L, 3_000L, 4_000L))
            .isEqualTo(1_000L)
    }

    @Test
    fun `date taken is the fallback when exif is missing`() {
        assertThat(CaptureDateResolver.resolve(null, 2_000L, 3_000L, 4_000L))
            .isEqualTo(2_000L)
    }

    @Test
    fun `file date is the fallback when exif and date taken are missing`() {
        assertThat(CaptureDateResolver.resolve(null, null, 3_000L, 4_000L))
            .isEqualTo(3_000L)
    }

    @Test
    fun `import time is the last resort`() {
        assertThat(CaptureDateResolver.resolve(null, null, null, 4_000L))
            .isEqualTo(4_000L)
    }

    @Test
    fun `mediastore zeros are skipped like nulls`() {
        // MediaStore rend 0 (pas null) pour une colonne non renseignée : 0 n'est jamais
        // une date de prise de vue valable.
        assertThat(CaptureDateResolver.resolve(0L, 0L, 3_000L, 4_000L)).isEqualTo(3_000L)
        assertThat(CaptureDateResolver.resolve(0L, 0L, 0L, 4_000L)).isEqualTo(4_000L)
    }
}
