package com.jtr.app.ui.gallery

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jtr.app.R
import com.jtr.app.data.repository.GalleryRepository
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.GalleryImage
import com.jtr.app.ui.components.JtrSortCriterion
import com.jtr.app.ui.components.JtrSortDirection
import com.jtr.app.ui.components.jtrSortCriterion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Sens d'affichage de la galerie (v7.1.101) : `createdAt`, et depuis v7.1.103 la date de
 * MODIFICATION — lue en `COALESCE(modifiedAt, createdAt)`, une image jamais éditée se
 * classant par sa création.
 *
 * v7.1.123 (R-a) — [CUSTOM] : l'ordre MANUEL (`position`), même modèle que
 * [CategorySortOrder.CUSTOM] (v7.1.78). Mono-sens (pas d'inverse utile) et PASS-THROUGH :
 * le DAO émet déjà `position ASC, createdAt ASC`, ce mode n'applique donc AUCUN re-tri.
 * Le drag & drop qui écrira ces positions arrive au lot R-b.
 */
enum class GallerySortOrder { CREATED_ASC, CREATED_DESC, MODIFIED_ASC, MODIFIED_DESC, CUSTOM }

/**
 * Critères de tri de la galerie — le MÊME modèle à deux axes que l'Accueil et les Catégories
 * ([jtrSortCriterion]) : re-tap du critère actif = inversion du sens. Ordre des critères
 * aligné sur le reste de l'app (v6.2.6) : « Date de modification » puis « Date de création ».
 */
internal fun gallerySortCriteria(
    current: GallerySortOrder,
    onSelect: (GallerySortOrder) -> Unit
): List<JtrSortCriterion> = listOf(
    jtrSortCriterion(R.string.sort_criterion_modified, current,
        GallerySortOrder.MODIFIED_ASC, GallerySortOrder.MODIFIED_DESC,
        defaultDescending = true, onSelect = onSelect),
    jtrSortCriterion(R.string.sort_criterion_created, current,
        GallerySortOrder.CREATED_ASC, GallerySortOrder.CREATED_DESC,
        defaultDescending = true, onSelect = onSelect),
    // v7.1.123 (R-a) — « Personnalisé », calqué sur le critère des catégories (v7.1.78) :
    // même libellé partagé [R.string.type_custom] (présent dans les 13 locales, 0 chaîne
    // nouvelle) et MONO-SENS (showDirection = false masque libellé de sens et chevron).
    JtrSortCriterion(
        labelRes = R.string.type_custom,
        selected = current == GallerySortOrder.CUSTOM,
        // Sans objet pour un critère mono-sens (masqué par showDirection = false).
        direction = JtrSortDirection.ASC,
        showDirection = false,
        onClick = { onSelect(GallerySortOrder.CUSTOM) }
    ),
)

/**
 * Clé du tri « Date de modification » : `COALESCE(modifiedAt, createdAt)` — `modifiedAt`
 * reste `null` tant que les métadonnées n'ont jamais été éditées (v25, pas de backfill).
 */
private fun GalleryImage.modifiedSortKey(): Long = modifiedAt ?: createdAt

/**
 * Applique le tri choisi. Tri STABLE : à clé égale, l'ordre DAO (`position` puis
 * `createdAt`) départage, donc les photos d'un même import gardent leur ordre d'ajout.
 */
internal fun sortGalleryImages(
    list: List<GalleryImage>,
    order: GallerySortOrder
): List<GalleryImage> = when (order) {
    GallerySortOrder.CREATED_ASC -> list.sortedBy { it.createdAt }
    GallerySortOrder.CREATED_DESC -> list.sortedByDescending { it.createdAt }
    GallerySortOrder.MODIFIED_ASC -> list.sortedBy { it.modifiedSortKey() }
    GallerySortOrder.MODIFIED_DESC -> list.sortedByDescending { it.modifiedSortKey() }
    // v7.1.123 (R-a) — PASS-THROUGH : la liste arrive du DAO déjà en `position ASC,
    // createdAt ASC` ; re-trier ici serait au mieux redondant, au pire divergent.
    GallerySortOrder.CUSTOM -> list
}

/**
 * ViewModel de la GALERIE d'un contact (v7.1.89). MVVM/UDF strict : l'écran n'observe que des
 * [StateFlow] en lecture seule et remonte des intentions. Room v23, aucune migration.
 */
class GalleryViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = GalleryRepository(application.applicationContext)
    private val personRepository = PersonRepository(application.applicationContext)
    private val prefs = application.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)

    val personId: String = checkNotNull(savedStateHandle["personId"])

    // v7.1.101 — tri d'AFFICHAGE persisté (préférence, aucune écriture en base), même
    // mécanisme que `home_sort_order`. v7.1.109 — défaut CREATED_DESC (dernière photo
    // prise en haut, façon Google Photos) : pertinent depuis que `createdAt` est la
    // vraie date de prise de vue (v7.1.108). SEUL LE REPLI change — une pref déjà
    // stockée est lue telle quelle et n'est jamais réécrite ici.
    private val _sortOrder = MutableStateFlow(
        runCatching { GallerySortOrder.valueOf(prefs.getString(SORT_PREF_KEY, null) ?: "") }
            .getOrDefault(GallerySortOrder.CREATED_DESC)
    )
    val sortOrder: StateFlow<GallerySortOrder> = _sortOrder.asStateFlow()

    fun setSortOrder(order: GallerySortOrder) {
        _sortOrder.value = order
        prefs.edit { putString(SORT_PREF_KEY, order.name) }
    }

    /**
     * Images du contact — source unique de la grille ET de la visionneuse, dans le TRI CHOISI.
     * Le carré d'entrée de la fiche, lui, lit le flow du repository directement : sa vignette
     * « plus récente » ne dépend pas du sens d'affichage choisi ici.
     */
    val images: StateFlow<List<GalleryImage>> =
        combine(repository.observeByPerson(personId), _sortOrder) { list, order ->
            sortGalleryImages(list, order)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Nom du contact pour le titre ; vide tant que la lecture n'a pas émis. */
    val personName: StateFlow<String> = personRepository.observeById(personId)
        .map { it?.fullName ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** Import en cours : masque le bouton d'ajout et affiche une progression. */
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    /**
     * Ajoute les images choisies (sélection multiple). Le redimensionnement, la compression
     * et l'écriture se font hors du thread principal (cf. [GalleryRepository]).
     */
    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            try {
                repository.addImages(personId, uris)
            } finally {
                _isImporting.value = false
            }
        }
    }

    /**
     * v7.1.94 — enregistre la description saisie dans le panneau de détails. Écriture hors
     * du thread principal (cf. [GalleryRepository]) ; le Flow [images] rediffuse la ligne.
     */
    fun setCaption(imageId: Long, caption: String?) {
        viewModelScope.launch { repository.setCaption(imageId, caption) }
    }

    /** v7.1.94 — enregistre la date + heure choisies dans le panneau de détails. */
    fun setCreatedAt(imageId: Long, createdAt: Long) {
        viewModelScope.launch { repository.setCreatedAt(imageId, createdAt) }
    }

    /**
     * v7.1.95 — enregistre le lieu choisi dans le sélecteur de carte, ou le RETIRE quand les
     * trois valeurs sont `null`.
     */
    fun setLocation(imageId: Long, latitude: Double?, longitude: Double?, placeLabel: String?) {
        viewModelScope.launch {
            repository.setLocation(imageId, latitude, longitude, placeLabel)
        }
    }

    /**
     * v7.1.125 (R-b) — ordre MANUEL déposé par le glisser-déposer : persiste les positions
     * 0..n-1, puis BASCULE le tri sur [GallerySortOrder.CUSTOM].
     *
     * Cette bascule n'est pas un confort : sans elle, le flux réémettrait aussitôt la liste
     * re-triée par date et le geste, pourtant bien écrit en base, resterait invisible à
     * l'écran (leçon v6.1.3, reprise telle quelle des catégories en v7.1.78).
     *
     * Deux précautions, toutes deux issues de la revue de ce lot :
     *  - ORDRE INCHANGÉ = NO-OP. `detectDragGesturesAfterLongPress` appelle `onDragEnd`
     *    même quand le doigt s'est levé sans bouger d'un pixel : un simple appui long sur
     *    une photo (le réflexe acquis HORS sélection, où il sert à y entrer) arrivait donc
     *    ici et basculait la pref GLOBALE `gallery_sort` sur « Personnalisé » — en écrivant
     *    n UPDATE pour rien. On compare à l'ordre affiché et on sort sans rien toucher.
     *  - BASCULE APRÈS L'ÉCRITURE, dans la MÊME coroutine. Basculer avant laissait le flux
     *    réémettre la liste porteuse des ANCIENNES `position` (CUSTOM est un pass-through) :
     *    la grille revenait une fraction de seconde à l'ordre d'avant le geste, avant que
     *    l'invalidation Room ne rétablisse le bon. Une seule transition, pas d'aller-retour.
     */
    fun persistOrder(orderedIds: List<Long>) {
        if (orderedIds.isEmpty()) return
        if (orderedIds == images.value.map { it.id }) return
        viewModelScope.launch {
            repository.persistOrder(orderedIds)
            setSortOrder(GallerySortOrder.CUSTOM)
        }
    }

    /** Supprime une image : la ligne puis son fichier. */
    fun delete(image: GalleryImage) {
        viewModelScope.launch { repository.delete(image) }
    }

    /** v7.1.90 — supprime toute une sélection, même chemin unitaire appliqué en lot. */
    fun deleteAll(images: List<GalleryImage>) {
        if (images.isEmpty()) return
        viewModelScope.launch { repository.deleteAll(images) }
    }

    companion object {
        // Pref GLOBALE (un seul sens pour toutes les galeries), comme `home_sort_order`.
        private const val SORT_PREF_KEY = "gallery_sort"
    }
}
