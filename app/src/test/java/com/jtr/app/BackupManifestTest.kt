package com.jtr.app

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.jtr.app.data.backup.BackupBrand
import com.jtr.app.data.backup.BackupManager
import com.jtr.app.data.backup.BackupManifest
import org.junit.Test

/**
 * Contrat de l'EN-TÊTE DE MARQUE `.jtr` (v7.1.27). Vérifie la forme JSON exacte de
 * `manifest.json` (clés snake_case sous `_jtr`, signature « JTR-EXPORT ») et le
 * round-trip de détection à l'import. C'est cette signature de CONTENU — et non
 * l'extension — qui valide qu'un fichier est bien un export JTR.
 */
class BackupManifestTest {

    private val gson = Gson()

    @Test
    fun `manifest serializes the branding envelope with snake_case keys`() {
        val json = gson.toJson(
            BackupManifest(
                brand = BackupBrand(
                    magic = BackupManager.BRAND_MAGIC,
                    app = "Just To Remember",
                    createdBy = "JTR",
                    formatVersion = BackupManager.ENVELOPE_VERSION,
                    exportedAt = "2026-06-24T12:00:00Z"
                )
            )
        )

        assertThat(json).contains("\"_jtr\"")
        assertThat(json).contains("\"magic\":\"JTR-EXPORT\"")
        assertThat(json).contains("\"created_by\":\"JTR\"")
        assertThat(json).contains("\"format_version\":2")
        assertThat(json).contains("\"exported_at\":\"2026-06-24T12:00:00Z\"")
    }

    @Test
    fun `magic survives a serialize-parse round-trip`() {
        val original = BackupManifest(
            brand = BackupBrand(magic = BackupManager.BRAND_MAGIC, formatVersion = 2)
        )
        val parsed = gson.fromJson(gson.toJson(original), BackupManifest::class.java)
        assertThat(parsed.brand?.magic).isEqualTo(BackupManager.BRAND_MAGIC)
    }

    @Test
    fun `envelope version is distinct from the data schema version`() {
        // L'enveloppe de marque (2) ne doit JAMAIS être confondue avec la version du
        // schéma de données du payload (1) : sinon le check strict de l'import casse.
        assertThat(BackupManager.ENVELOPE_VERSION).isNotEqualTo(BackupManager.FORMAT_VERSION)
    }

    @Test
    fun `a non-JTR manifest is detectable by a wrong magic`() {
        // Un manifeste étranger (autre magic) ne porte pas la signature JTR → l'import
        // le rejette (validation par contenu), tandis qu'un fichier SANS manifeste
        // bascule sur le chemin legacy (testé sur appareil, pas ici car nécessite Room).
        val foreign = gson.fromJson("""{"_jtr":{"magic":"OTHER"}}""", BackupManifest::class.java)
        assertThat(foreign.brand?.magic).isNotEqualTo(BackupManager.BRAND_MAGIC)
    }
}
