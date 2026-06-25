package com.jtr.app.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
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
        val tag = currentTag(base)
        if (tag == null) {
            // « Langue du système » (v7.1.30) : on RÉALIGNE le défaut JVM sur la locale de
            // l'APPAREIL avant de retourner. [Locale.setDefault] est un état GLOBAL du process :
            // sans ce reset, après une langue explicite (ar/ja/zh…) il resterait figé dessus, et
            // TOUS les formateurs de dates (qui lisent [Locale.getDefault]) afficheraient encore
            // l'ancienne langue tant que le process n'est pas relancé — alors que les ressources
            // UI suivent déjà le système. [Resources.getSystem] reflète la config réelle de
            // l'appareil (jamais affectée par setDefault). Display-only, dans les deux sens.
            Locale.setDefault(Resources.getSystem().configuration.locales[0])
            return base
        }
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
