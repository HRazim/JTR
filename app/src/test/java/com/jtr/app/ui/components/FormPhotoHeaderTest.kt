package com.jtr.app.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * v7.1.111 (G3a) — contrat du repli de [FormPhotoHeader] ([photoHeaderInitial]) : la
 * logique d'initiale était DUPLIQUÉE dans les formulaires de catégorie et de groupe —
 * extraite avec le composant, elle est maintenant testée une seule fois.
 *
 *  - nom présent → PREMIER code point en capitale (une paire de substitution — emoji —
 *    reste entière, jamais coupée en deux moitiés illisibles) ;
 *  - nom vide/blanc/null → `null`, et le composant retombe sur l'icône.
 */
class FormPhotoHeaderTest {

    @Test
    fun `initial is the first code point uppercased`() {
        assertThat(photoHeaderInitial("famille")).isEqualTo("F")
        assertThat(photoHeaderInitial("  élise ")).isEqualTo("É")
        assertThat(photoHeaderInitial("عمل")).isEqualTo("ع")
    }

    @Test
    fun `surrogate pairs stay whole`() {
        assertThat(photoHeaderInitial("🎉 fête")).isEqualTo("🎉")
    }

    @Test
    fun `blank and null fall back to the icon`() {
        assertThat(photoHeaderInitial(null)).isNull()
        assertThat(photoHeaderInitial("")).isNull()
        assertThat(photoHeaderInitial("   ")).isNull()
    }
}
