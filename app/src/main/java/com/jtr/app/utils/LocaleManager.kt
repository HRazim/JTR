package com.jtr.app.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Langue applicative — source de vérité IN-APP, fiable sur TOUTES les versions
 * d'Android (≠ AppCompatDelegate qui ne s'applique pas à une `ComponentActivity`
 * sans `AppCompatActivity`).
 *
 * Mécanique : le tag BCP-47 choisi est persisté en SharedPreferences ; chaque
 * Activity surcharge `attachBaseContext` pour envelopper son contexte avec la
 * locale via [wrap] ; un changement appelle [setLanguage] puis `recreate()`.
 * Le réglage survit donc au redémarrage (relu dans `attachBaseContext`).
 *
 * `null` / chaîne vide = « Langue du système » (aucune surcharge).
 */
object LocaleManager {

    private const val PREFS = "jtr_prefs"
    private const val KEY = "app_language"

    /** Tag de langue actif, ou `null` pour suivre la locale du système. */
    fun currentTag(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.takeIf { it.isNotEmpty() }

    /** Persiste le choix (`null` = langue du système). À suivre d'un `recreate()`. */
    fun setLanguage(context: Context, tag: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, tag.orEmpty())
            .apply()
    }

    /**
     * Enveloppe [base] avec la locale persistée (sens d'écriture inclus — RTL pour
     * l'arabe). Renvoie [base] tel quel si « langue du système ».
     */
    fun wrap(base: Context): Context {
        val tag = currentTag(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
