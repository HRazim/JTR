package com.jtr.app.data.files

import android.content.Context
import com.jtr.app.BuildConfig
import com.jtr.app.data.local.AppDatabase
import java.io.File
import timber.log.Timber

/**
 * Résultat d'un [OrphanFileSweeper.scan] : la liste EXACTE des fichiers qui seraient
 * supprimés, figée AVANT toute confirmation. [execute][OrphanFileSweeper.execute] ne
 * supprime QUE ce plan — jamais un re-calcul silencieux entre le dialogue et l'action.
 */
data class SweepPlan(val files: List<File>, val totalBytes: Long) {
    val count: Int get() = files.size
}

/** Bilan d'un [OrphanFileSweeper.execute] : fichiers effectivement supprimés + octets libérés. */
data class SweepResult(val deletedCount: Int, val freedBytes: Long)

/**
 * Balayage MANUEL des fichiers image orphelins (v7.1.106, lot C2).
 *
 * Le lot C1 (v7.1.105) a colmaté les fuites EN AVANT (suppression du fichier avec l'entité) ;
 * ce moteur récupère le STOCK accumulé avant lui. Déclenché uniquement depuis Paramètres,
 * en deux temps étanches : [scan] (lecture seule, dry-run loggué) puis [execute] (après
 * confirmation humaine). Le balayage n'écrit JAMAIS en base.
 *
 * Garde-fous, dans l'ordre où ils s'appliquent :
 *  1. L'ensemble de référence couvre les 4 SEULS champs de fichier (audit lot C) —
 *     `persons.photoUri`, `categories.imagePath`, `category_groups.imagePath`,
 *     `gallery_images.path` — sur TOUTES les lignes, CORBEILLE INCLUSE : une fiche
 *     soft-deleted possède encore sa photo, elle n'est PAS orpheline.
 *  2. Chaque référence passe par [ImageFileStore.normalizeToPath] puis le chemin CANONIQUE
 *     (71 des 88 `photoUri` mesurés sont des URI `file://` — sans normalisation, le
 *     balayage croirait orphelines des photos affichées).
 *  3. Seuls `photos/`, `gallery/` et `crops/` de `filesDir` sont parcourus — jamais la
 *     racine (`mbgl-offline.db`, `profileInstalled`) ni l'extérieur.
 *  4. Un fichier modifié/créé PENDANT ou APRÈS le début du scan n'est jamais candidat
 *     (`lastModified >= scanStart`) : un crop d'édition ou une photo fraîche survivent.
 *  5. La suppression passe exclusivement par [ImageFileStore.deleteFileIfOwned] — le
 *     même garde-fou canonique que le go-forward C1, re-vérifié fichier par fichier.
 */
object OrphanFileSweeper {

    /**
     * Tag logcat du dry-run : la preuve AVANT suppression vit dans ces lignes. Posé
     * explicitement via `Timber.tag` — l'inférence de DebugTree donnerait le même nom,
     * mais le protocole de validation (`logcat -s OrphanFileSweeper`) ne doit pas
     * dépendre d'un détail d'implémentation de Timber.
     */
    const val TAG = "OrphanFileSweeper"

    /** Les SEULS dossiers balayés — strictement ceux que [ImageFileStore] possède. */
    private val SWEEP_DIRS = listOf("photos", "gallery", "crops")

    /**
     * Dry-run complet : lit les 4 champs de référence en base (corbeille incluse) puis
     * délègue à la variante testable. Ne supprime RIEN, n'écrit RIEN en base.
     */
    suspend fun scan(context: Context): SweepPlan {
        val db = AppDatabase.getInstance(context)
        val references = db.personDao().getAllPhotoUris() +
            db.categoryDao().getAllImagePaths() +
            db.categoryGroupDao().getAllImagePaths() +
            db.galleryImageDao().getAllPaths()
        return scan(context, references, System.currentTimeMillis())
    }

    /**
     * Cœur du balayage, découplé de Room pour les tests JVM : [references] = valeurs brutes
     * des 4 champs (chemins ou URI `file://`, mélangés), [scanStartMillis] = borne de la
     * garde anti-course. Orphelin = fichier d'un dossier balayé dont le chemin canonique
     * est ABSENT des références ET plus ancien que le début du scan.
     */
    fun scan(context: Context, references: List<String>, scanStartMillis: Long): SweepPlan {
        val filesDir = context.filesDir ?: return SweepPlan(emptyList(), 0L)
        val referenced = references
            .mapNotNull { ImageFileStore.normalizeToPath(it) }
            .mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }
            .toSet()

        val orphans = ArrayList<File>()
        var totalBytes = 0L
        var preserved = 0
        var tooRecent = 0
        for (dir in SWEEP_DIRS) {
            val root = File(filesDir, dir)
            if (!root.isDirectory) continue
            root.walkTopDown().filter { it.isFile }.forEach { file ->
                val canonical = runCatching { file.canonicalPath }.getOrNull() ?: return@forEach
                when {
                    canonical in referenced -> preserved++
                    file.lastModified() >= scanStartMillis -> tooRecent++
                    else -> {
                        orphans += File(canonical)
                        totalBytes += file.length()
                    }
                }
            }
        }

        // Preuve AVANT suppression (Phase A du protocole) : liste complète + totaux +
        // références préservées — tout se vérifie au logcat sans rien confirmer.
        // ⚠️ PII : les chemins embarquent des NOMS de contacts (`restore_<nom>…`) — la
        // liste fichier-par-fichier n'existe donc QUE dans les builds debug (la garde
        // BuildConfig.DEBUG retire le code du release, pas seulement le niveau de log) ;
        // les agrégats, eux, ne portent aucune donnée personnelle.
        if (BuildConfig.DEBUG) {
            orphans.forEach { Timber.tag(TAG).d("orphelin: ${it.path} (${it.length()} octets)") }
        }
        Timber.tag(TAG).i("orphelins: ${orphans.size} fichier(s), $totalBytes octets")
        Timber.tag(TAG).i("references trouvees: $preserved (toutes preservees)")
        if (tooRecent > 0) Timber.tag(TAG).i("ignores (plus recents que le scan): $tooRecent")
        return SweepPlan(orphans, totalBytes)
    }

    /**
     * Supprime les fichiers du [plan] — et uniquement eux — via
     * [ImageFileStore.deleteFileIfOwned] : chaque chemin re-passe le test de possession
     * canonique, un élément hors `photos/`/`gallery/`/`crops/` resterait intact même
     * s'il s'était glissé dans le plan. Aucune écriture en base.
     */
    fun execute(context: Context, plan: SweepPlan): SweepResult {
        var deleted = 0
        var freed = 0L
        for (file in plan.files) {
            val size = file.length()
            if (ImageFileStore.deleteFileIfOwned(context, file.path)) {
                deleted++
                freed += size
            }
        }
        Timber.tag(TAG).i("supprimes: $deleted fichier(s), $freed octets liberes")
        return SweepResult(deleted, freed)
    }
}
