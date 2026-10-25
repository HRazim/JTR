package com.jtr.app.data.files

import android.content.Context
import java.io.File
import java.net.URI
import java.nio.file.Paths

/**
 * Suppression CONTRÔLÉE des fichiers image possédés par l'app (v7.1.105, lot C1).
 *
 * Généralise le garde-fou de la galerie ([com.jtr.app.data.gallery.GalleryImageStore.deleteFile],
 * restreint à `gallery/`) aux trois dossiers d'images de `filesDir` : `photos/` (profils,
 * catégories, dossiers, restaurations), `gallery/` (images par contact) et `crops/` (sas du
 * recadrage). Tout le reste est REFUSÉ par construction — notamment la racine de `filesDir`
 * (`mbgl-offline.db`, `profileInstalled`, `persons.json`) et tout chemin extérieur.
 *
 * ⚠️ NORMALISATION OBLIGATOIRE (audit lot C) : `Person.photoUri` mélange chemins bruts
 * (`/data/…`) et URI `file:///data/…` (71 des 88 références mesurées). Toute comparaison ou
 * suppression passe donc par [normalizeToPath] puis par le chemin CANONIQUE — sans quoi une
 * URI serait prise pour un chemin inexistant et, inversement, un balayage croirait orphelines
 * des photos affichées.
 */
object ImageFileStore {

    /** Les SEULS dossiers de `filesDir` où une suppression est permise. */
    private val OWNED_DIRS = listOf("photos", "gallery", "crops")

    /**
     * Ramène une référence d'image de la base à un CHEMIN de fichier : `file://…` est décodé
     * (séquences `%xx` comprises), un chemin brut passe tel quel, un blanc/`null` donne `null`.
     * Un schéma non-fichier (`content://…`) est rendu tel quel — il échouera plus loin sur le
     * test de possession, jamais sur une suppression.
     */
    fun normalizeToPath(value: String?): String? {
        val v = value?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        // `file:` couvre TOUTES les écritures d'URI fichier : `file:///data/…` (la forme des
        // 71 photoUri mesurés), `file://…` et `file:/…` (celle que produit `File.toURI()`).
        return if (v.startsWith("file:")) {
            // `Paths.get(URI)` décode les %xx et rend un chemin NATIF de la plateforme —
            // `file:///data/…` → `/data/…` sur Android, et le même code reste juste dans
            // les tests JVM (où `URI.path` seul prendrait « C: » pour un nom d'hôte).
            // URI malformée (espace nu, host parasite) → repli : retrait littéral du schéma.
            runCatching { Paths.get(URI(v)).toString() }.getOrNull()
                ?: v.substringAfter("file:").removePrefix("//")
        } else v
    }

    /**
     * Supprime le fichier désigné par [value] (chemin brut ou URI `file://`) SEULEMENT s'il
     * vit dans un des [OWNED_DIRS] de `filesDir` — comparaison en chemins CANONIQUES, donc
     * insensible aux `../` et aux liens. Tout autre chemin est un no-op (`false`), comme un
     * fichier déjà absent : l'appelant n'a jamais d'erreur à gérer.
     */
    fun deleteFileIfOwned(context: Context, value: String?): Boolean {
        val path = normalizeToPath(value) ?: return false
        val canonical = runCatching { File(path).canonicalPath }.getOrNull() ?: return false
        val filesDir = context.filesDir ?: return false
        val owned = OWNED_DIRS.any { dir ->
            val root = runCatching { File(filesDir, dir).canonicalPath }.getOrNull()
            root != null && canonical.startsWith(root + File.separator)
        }
        if (!owned) return false
        return runCatching { File(canonical).delete() }.getOrDefault(false)
    }

    /**
     * Supprime l'ANCIEN fichier quand une référence vient d'être remplacée : no-op si la
     * valeur n'a pas changé (même fichier après normalisation) — on ne supprime jamais ce
     * qui vient d'être écrit. À appeler APRÈS la persistance de la nouvelle valeur.
     */
    fun deleteReplacedFile(context: Context, oldValue: String?, newValue: String?): Boolean {
        val old = normalizeToPath(oldValue) ?: return false
        if (old == normalizeToPath(newValue)) return false
        return deleteFileIfOwned(context, old)
    }
}
