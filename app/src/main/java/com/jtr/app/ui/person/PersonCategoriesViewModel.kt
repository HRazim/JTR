package com.jtr.app.ui.person

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.repository.CategoryRepository
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Gestion des catégories d'UNE personne depuis sa fiche (v7.1.5).
 *
 * MVVM/UDF strict : n'expose que des [StateFlow] en lecture seule, alimentés par les
 * Flow Room EXISTANTS (table de jointure `person_category_join`). Toute mutation passe
 * par les repositories existants ([PersonRepository.assignCategory] /
 * [PersonRepository.removeFromCategory] / [CategoryRepository.add]) — AUCUNE duplication
 * de logique, AUCUN nouveau DAO, AUCUN changement de schéma (Room v21).
 *
 * Auto-persistance (cohérent v7.1.4) : cocher/décocher écrit immédiatement le lien ; les
 * badges de la fiche et les cases du sélecteur se mettent à jour de façon réactive.
 */
class PersonCategoriesViewModel(application: Application) : AndroidViewModel(application) {

    private val categoryRepo = CategoryRepository(application.applicationContext)
    private val personRepo = PersonRepository(application.applicationContext)

    /** Personne courante (liée par la fiche). `null` tant qu'aucune personne n'est chargée. */
    private val _personId = MutableStateFlow<String?>(null)

    /** Lie le sélecteur à une personne (idempotent : ré-appel sans effet si inchangé). */
    fun bind(personId: String) {
        if (_personId.value != personId) _personId.value = personId
    }

    /** Toutes les catégories actives (ordre DAO : favoris d'abord, puis position). */
    val allCategories: StateFlow<List<Category>> = categoryRepo.getAllActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Dossiers/groupes (hiérarchie récursive via `parentGroupId`), pour un affichage cohérent. */
    val groups: StateFlow<List<CategoryGroup>> = categoryRepo.getGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** IDs des catégories auxquelles la personne appartient — RÉACTIF (cases pré-cochées). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val memberCategoryIds: StateFlow<Set<String>> = _personId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else personRepo.observeCategoryIdsForPerson(id)
        }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /**
     * Badges (id, nom) de la fiche : intersection RÉACTIVE des catégories existantes et de
     * l'appartenance, ordonnée comme la liste des catégories. Source unique des chips
     * (remplace le chargement one-shot de la navigation) → toujours à jour.
     */
    val personCategoryChips: StateFlow<List<Pair<String, String>>> = combine(
        allCategories, memberCategoryIds
    ) { cats, member ->
        cats.filter { it.id in member }.map { it.id to it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Ajoute/retire la personne d'une catégorie (lien de jointure), persisté immédiatement. */
    fun setInCategory(categoryId: String, inCategory: Boolean) {
        val pid = _personId.value ?: return
        viewModelScope.launch {
            if (inCategory) personRepo.assignCategory(listOf(pid), categoryId)
            else personRepo.removeFromCategory(listOf(pid), categoryId)
        }
    }

    /**
     * Création rapide d'une catégorie (MÊME logique que l'écran Catégories :
     * [CategoryRepository.add], position = fin) PUIS auto-sélection : la personne y est
     * aussitôt liée. La nouvelle catégorie apparaît cochée via le Flow réactif.
     */
    fun createCategoryAndAssign(name: String, color: String, imagePath: String?) {
        val pid = _personId.value ?: return
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val category = Category(name = trimmed, color = color, imagePath = imagePath)
            categoryRepo.add(category)
            personRepo.assignCategory(listOf(pid), category.id)
        }
    }
}
