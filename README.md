# 📱 JTR — Just To Remember

> **Carnet de contacts enrichi nouvelle génération** · Version `4.0-Final`  
> Projet personnel Android — Kotlin · Jetpack Compose · MVVM

---

## 🎯 Proposition de valeur

JTR (*Just To Remember*) va au-delà du simple répertoire téléphonique. L'application maintient une **mémoire sociale active** : elle enregistre le contexte humain de chaque relation (goûts, anniversaires, ville, notes, réseaux sociaux), géocode automatiquement les villes via OpenStreetMap, et notifie proactivement l'utilisateur lorsqu'il se retrouve physiquement proche d'un contact qu'il n'a pas vu depuis longtemps. Le tout, sans service cloud, sans clé API propriétaire, et avec un stockage 100 % local.

---

## 📋 Table des matières

1. [Aperçu visuel](#-aperçu-visuel)
2. [Nouveautés v4.0](#-nouveautés-v40)
3. [Arborescence du projet](#-arborescence-du-projet)
4. [Architecture MVVM](#-architecture-mvvm)
5. [Stack technologique](#-stack-technologique)
6. [Répertoire des classes](#-répertoire-des-classes-et-composants)
7. [Fonctionnalités clés](#-fonctionnalités-clés)
8. [Base de données Room](#-base-de-données-room)
9. [Guide d'installation](#-guide-dinstallation-et-configuration)
10. [Permissions requises](#-permissions-requises)
11. [Tests et qualité](#-tests-et-qualité)
12. [Optimisations de performance](#-optimisations-de-performance)
13. [Évolution par version](#-évolution-par-version)

---

## 🖼 Aperçu visuel

| Accueil | Détail contact | Carte MapLibre | Paramètres |
|---------|---------------|----------------|------------|
| Liste filtrée, icônes réseaux sociaux, favoris, recherche | Photo, mini-carte, liens sociaux brandés | Sélecteur GPS natif, zoom/pan libre | Thèmes, corbeille, rayon de proximité |

---

## 🚀 Nouveautés v4.0

### 1. Dynamic Social Icon Mapping

Les icônes de réseaux sociaux sont désormais affichées avec leurs **couleurs de marque originales** dans toute l'application, en remplacement des icônes génériques Material Design.

**Composants introduits :**

| Fichier | Rôle |
|---------|------|
| `utils/SocialMediaMapper.kt` | Fonction `getSocialIcon(url): @DrawableRes Int` — détection de plateforme via `Uri.parse().host` |
| `res/drawable/ic_instagram.xml` | Gradient orange → rouge → rose → violet (`aapt:attr`, API 24+) |
| `res/drawable/ic_facebook.xml` | Bleu Facebook `#475993` |
| `res/drawable/ic_linkedin.xml` | Bleu LinkedIn `#0077B7` |
| `res/drawable/ic_x.xml` | Fond noir, lettre X transparente (`fillType="evenOdd"`) |
| `res/drawable/ic_discord.xml` | Violet Discord `#5865F2` |
| `res/drawable/ic_youtube.xml` | Rouge YouTube `#F61C0D` |
| `res/drawable/ic_link.xml` | Fallback générique pour URL non reconnue |

**Intégration UI :**
- **`PersonCard` (HomeScreen)** — rangée d'icônes 18 dp, espacement 8 dp, `tint = Color.Unspecified` pour préserver les couleurs d'origine. Limitée à 6 icônes.
- **`SocialLinksSection` (PersonDetailScreen)** — icônes brandées 28 dp cliquables (mode lecture) ou liste éditable avec suppression (mode édition).

---

### 2. Réseaux sociaux dès la création d'un contact

Il est désormais possible d'ajouter des liens sociaux **lors de la création** d'un contact, sans avoir à passer par l'écran de détail.

**Mécanisme :**
- `AddPersonViewModel` gère une liste de `PendingLink(url, platform)` en mémoire avant que le contact soit persisté.
- Après l'insertion en base, chaque lien est inséré dans `social_links` en associant le `personId` UUID (connu localement dès la création de l'objet `Person`).
- Le dialogue `AddSocialLinkDialog` (prévisualisation de plateforme en temps réel) est réutilisé depuis `PersonDetailScreen`.

```kotlin
// AddPersonViewModel
fun addPendingLink(url: String) {
    val platform = extractSocialLinks(url).firstOrNull()?.platform?.displayName ?: "Lien"
    _pendingLinks.value = _pendingLinks.value + PendingLink(url, platform)
}

// Dans savePerson() — après insertion Person
_pendingLinks.value.forEach { link ->
    repository.addSocialLink(SocialLinkEntity(personId = person.id, url = link.url, platform = link.platform))
}
```

---

### 3. Correction UX carte MapLibre — Isolation des gestes

**Problème :** Le système de gestes de Compose interceptait les événements de toucher avant qu'ils n'atteignent le `MapView` natif, rendant le zoom et le déplacement inefficaces.

**Solution :** Un `setOnTouchListener` est appliqué sur chaque instance `MapView` (plein écran et mini-carte inline). Dès le premier contact (`ACTION_DOWN` / `ACTION_POINTER_DOWN`), il appelle `parent.requestDisallowInterceptTouchEvent(true)` pour céder le contrôle des gestes au SDK MapLibre. L'interception est restituée au parent à `ACTION_UP` / `ACTION_CANCEL`.

```kotlin
mapView.setOnTouchListener { v, event ->
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN,
        MotionEvent.ACTION_POINTER_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL       -> v.parent?.requestDisallowInterceptTouchEvent(false)
    }
    false // laisser MapView traiter l'événement
}
```

Corrigé dans `MapScreen.kt` (carte plein écran) **et** dans `MapLibreMiniMap` de `PersonDetailScreen.kt` (mini-carte à l'intérieur d'un `verticalScroll`).

---

## 🗂 Arborescence du projet

```
JTR_TP3/
├── app/
│   ├── build.gradle.kts                    # Dépendances, versionCode=4, minSdk=26
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml         # Permissions, déclaration workers/receiver
│       │   ├── res/drawable/
│       │   │   ├── ic_discord.xml          # Vector Drawable — couleurs de marque
│       │   │   ├── ic_facebook.xml
│       │   │   ├── ic_instagram.xml        # Gradient aapt:attr (3 paths)
│       │   │   ├── ic_linkedin.xml
│       │   │   ├── ic_x.xml               # fillType="evenOdd"
│       │   │   ├── ic_youtube.xml
│       │   │   └── ic_link.xml            # Fallback générique
│       │   └── java/com/jtr/app/
│       │       ├── JTRApplication.kt       # Application class — canaux + WorkManager
│       │       ├── MainActivity.kt         # Entry point Compose, thème global
│       │       │
│       │       ├── domain/
│       │       │   └── model/
│       │       │       ├── Person.kt           # Entité Room — 17 champs
│       │       │       ├── Category.kt         # Entité Room — 7 champs (+ imagePath)
│       │       │       ├── PersonCategoryJoin.kt # Table de jointure Many-to-Many
│       │       │       └── SocialLinkEntity.kt # Entité Room — liens sociaux (1:N Person)
│       │       │
│       │       ├── data/
│       │       │   ├── local/
│       │       │   │   ├── AppDatabase.kt      # Singleton Room, version 7
│       │       │   │   ├── PersonDao.kt        # DAO CRUD + recherche accent-insensitive
│       │       │   │   ├── CategoryDao.kt      # DAO CRUD + soft delete
│       │       │   │   ├── PersonCategoryDao.kt # DAO table de jointure M2M
│       │       │   │   └── SocialLinkDao.kt    # DAO CRUD liens sociaux
│       │       │   ├── remote/
│       │       │   │   ├── ApiClient.kt        # Retrofit + OkHttp, User-Agent Nominatim
│       │       │   │   ├── NominatimApi.kt     # Interface Retrofit — /search + /reverse
│       │       │   │   └── GeocodingResult.kt  # Modèle JSON @Serializable
│       │       │   └── repository/
│       │       │       ├── PersonRepository.kt     # CRUD, géocodage, M2M, social links
│       │       │       ├── CategoryRepository.kt   # CRUD catégories + cascade M2M
│       │       │       └── GeocodingRepository.kt  # Abstraction Nominatim
│       │       │
│       │       ├── utils/
│       │       │   ├── SocialMediaUtils.kt     # SocialPlatform, extractSocialLinks, openSocialLink
│       │       │   └── SocialMediaMapper.kt    # getSocialIcon(url): @DrawableRes Int
│       │       │
│       │       ├── ui/
│       │       │   ├── navigation/
│       │       │   │   └── Navigation.kt       # Routes, NavHost, BottomBar
│       │       │   ├── home/
│       │       │   │   ├── HomeScreen.kt       # PersonCard avec icônes réseaux sociaux
│       │       │   │   └── HomeViewModel.kt    # + socialLinksMap: StateFlow<Map<String,List<SocialLinkEntity>>>
│       │       │   ├── person/
│       │       │   │   ├── AddPersonScreen.kt  # Formulaire + section réseaux sociaux
│       │       │   │   ├── AddPersonViewModel.kt  # + pendingLinks, addPendingLink/removePendingLink
│       │       │   │   └── PersonDetailScreen.kt  # Mini-carte, SocialLinksSection, édition inline
│       │       │   │   └── EditPersonViewModel.kt # socialLinks réactif depuis Room
│       │       │   ├── category/
│       │       │   │   ├── CategoriesScreen.kt
│       │       │   │   ├── CategoryViewModel.kt
│       │       │   │   ├── CategoryDetailScreen.kt
│       │       │   │   └── CategoryDetailViewModel.kt
│       │       │   ├── map/
│       │       │   │   ├── MapScreen.kt        # Touch isolation MapLibre (requestDisallowIntercept)
│       │       │   │   └── MapViewModel.kt
│       │       │   ├── settings/
│       │       │   │   ├── SettingsScreen.kt
│       │       │   │   └── SettingsViewModel.kt
│       │       │   ├── theme/
│       │       │   │   ├── Color.kt
│       │       │   │   ├── Theme.kt
│       │       │   │   ├── ThemePreset.kt
│       │       │   │   ├── ThemeViewModel.kt
│       │       │   │   └── Type.kt
│       │       │   └── trash/
│       │       │       ├── TrashScreen.kt
│       │       │       └── TrashViewModel.kt
│       │       │
│       │       └── worker/
│       │           ├── ProximityCheckWorker.kt    # Haversine, 6h, rayon configurable, 90j
│       │           ├── BirthdayCheckWorker.kt     # Vérification quotidienne anniversaires
│       │           └── GeofenceBroadcastReceiver.kt
│       │
│       ├── test/                           # Tests unitaires (JVM)
│       │   └── java/com/jtr/app/
│       │       ├── PersonRepositoryTest.kt
│       │       ├── DistanceCalculationTest.kt
│       │       └── GeocodingResultTest.kt
│       │
│       └── androidTest/                    # Tests instrumentés (Room DAO)
│           └── java/com/jtr/app/
│               └── ExampleInstrumentedTest.kt
│
├── build.gradle.kts                        # Configuration projet racine
└── settings.gradle.kts
```

---

## 🏛 Architecture MVVM

JTR implémente le pattern **Model-View-ViewModel** recommandé par Google, renforcé d'une couche Repository pour l'isolation complète des sources de données.

```
┌─────────────────────────────────────────────────────────────────┐
│                          UI LAYER                               │
│   Composables Jetpack Compose  ←→  ViewModels (StateFlow)       │
│   HomeScreen · PersonDetailScreen · AddPersonScreen · MapScreen │
└───────────────────────────┬─────────────────────────────────────┘
                            │ observe / call
┌───────────────────────────▼─────────────────────────────────────┐
│                       DOMAIN LAYER                              │
│   Person · Category · PersonCategoryJoin · SocialLinkEntity     │
└───────────────────────────┬─────────────────────────────────────┘
                            │ inject
┌───────────────────────────▼─────────────────────────────────────┐
│                        DATA LAYER                               │
│   PersonRepository · CategoryRepository · GeocodingRepository   │
│        │                 │                      │               │
│   PersonDao         CategoryDao          NominatimApi (HTTP)    │
│   PersonCategoryDao SocialLinkDao                               │
│        └──────────────── AppDatabase (v7) ──────────┘          │
└─────────────────────────────────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                    BACKGROUND LAYER                             │
│   ProximityCheckWorker (6h)  ·  BirthdayCheckWorker (1j)       │
│   GeofenceBroadcastReceiver                                     │
└─────────────────────────────────────────────────────────────────┘
```

### Flux de données unidirectionnel (UDF)

```
User action ──► ViewModel.fun() ──► Repository.suspend() ──► DAO / API
                     ▲                                          │
                     └─────── Flow<T> (Room reactive) ◄────────┘
```

Les ViewModels exposent uniquement des `StateFlow<T>` en lecture seule via `stateIn(WhileSubscribed(5000))`. Les Composables observent ces flows avec `collectAsStateWithLifecycle()` pour suspendre automatiquement la collecte en arrière-plan et éviter les fuites mémoire.

---

## 🛠 Stack technologique

| Catégorie | Bibliothèque | Version | Usage |
|-----------|-------------|---------|-------|
| **UI** | Jetpack Compose BOM | `2024.12.01` | Interface déclarative 100 % Compose |
| **UI** | Material3 | via BOM | Design system, thèmes dynamiques |
| **UI** | Material Icons Extended | via BOM | Icônes vectorielles |
| **Navigation** | Navigation Compose | `2.8.5` | NavHost, BottomBar, SavedStateHandle |
| **Persistence** | Room Runtime + KTX | `2.6.1` | ORM SQLite, Flows réactifs |
| **Persistence** | KSP | `2.1.0-1.0.29` | Génération de code Room (remplace KAPT) |
| **Carte** | MapLibre Android SDK | `11.5.0` | Carte native plein écran + mini-carte intégrée, 16 KB pages |
| **Réseau** | Retrofit | `2.11.0` | Client HTTP typé pour Nominatim |
| **Réseau** | OkHttp + Logging Interceptor | `4.12.0` | Transport HTTP + logs debug |
| **Réseau** | kotlinx.serialization | `1.7.3` | Désérialisation JSON sans réflexion |
| **Background** | WorkManager | `2.10.0` | Tâches périodiques garanties (proximité + anniversaires) |
| **Localisation** | Play Services Location | `21.3.0` | FusedLocationProviderClient + Geofencing |
| **Image** | Coil Compose | `2.7.0` | Chargement asynchrone photos de profil |
| **Préférences** | DataStore Preferences | `1.1.1` | Thème et mode sombre persistants |
| **Préférences** | SharedPreferences | SDK | Rayon de proximité, toggles notifications |
| **Coroutines** | Kotlinx Coroutines Android | `1.8.1` | Async non-bloquant |
| **Coroutines** | Coroutines Play Services | `1.8.1` | `await()` sur `Task<T>` Google |
| **Tests** | JUnit 4 | `4.13.2` | Cadre de test unitaire |
| **Tests** | MockK | `1.13.12` | Mocking idiomatique Kotlin |
| **Tests** | Turbine | `1.2.0` | Test de `Flow` Kotlin |
| **Tests** | Truth | `1.4.4` | Assertions fluentes lisibles |

**Langages :** Kotlin 2.1.0 · JVM target 17  
**SDK :** `compileSdk = 35` · `targetSdk = 35` · `minSdk = 26` (Android 8.0+)  
**Tuiles cartographiques :** OpenFreeMap (`tiles.openfreemap.org/styles/liberty`) — sans clé API

---

## 📦 Répertoire des classes et composants

### 🔷 Couche Domain — Modèles

#### `Person.kt`
Entité Room centrale avec 17 champs couvrant l'identité, la géolocalisation, les préférences de notification et les métadonnées de cycle de vie.

| Champ | Type | Description |
|-------|------|-------------|
| `id` | `String` (UUID) | Clé primaire générée localement |
| `firstName`, `lastName` | `String` / `String?` | Nom complet |
| `gender` | `String?` | `"male"`, `"female"`, `"non-binary"` |
| `photoUri` | `String?` | Chemin absolu vers `filesDir/photos/` |
| `birthdate` | `Long?` | Timestamp Unix (ms), stocké en heure locale (midi) |
| `birthdateNotify` | `Boolean` | Active le canal `CHANNEL_BIRTHDAY` |
| `city` | `String?` | Nom de ville (texte libre ou issu du géocodage) |
| `cityLat`, `cityLng` | `Double?` | Coordonnées GPS (Nominatim ou sélection carte) |
| `cityNotify` | `Boolean` | Active le canal `CHANNEL_PROXIMITY` |
| `isFavorite` | `Boolean` | Épinge en haut de liste |
| `lastContactedAt` | `Long?` | Timestamp de la dernière consultation de fiche |
| `notes`, `likes`, `origin` | `String?` | Champs texte libres enrichis |
| `createdAt` | `Long` | Timestamp de création (auto) |
| `deletedAt` | `Long?` | `null` = actif · non-null = en corbeille |

**Propriétés calculées :**
```kotlin
val fullName: String            // "Alice Dupont"
val initials: String            // "AD"
val hasGeoCoordinates: Boolean  // cityLat != null && cityLng != null
fun daysSinceLastContact(): Long?  // (now - lastContactedAt) / 86_400_000
```

#### `Category.kt`
Groupe logique pour organiser les contacts.

| Champ | Type | Description |
|-------|------|-------------|
| `id` | `String` (UUID) | Clé primaire |
| `name` | `String` | Libellé affiché |
| `color` | `String` | Code hexadécimal (`"#2E86C1"`) |
| `icon` | `String` | Nom icône Material (`"folder"`) |
| `imagePath` | `String?` | Chemin photo de couverture |
| `order` | `Int` | Ordre d'affichage |
| `deletedAt` | `Long?` | Soft delete |

#### `PersonCategoryJoin.kt`
Table de jointure **Many-to-Many** entre `Person` et `Category`. Clé primaire composite `(personId, categoryId)`. Contraintes `CASCADE` des deux côtés : la suppression physique d'une personne ou d'une catégorie nettoie automatiquement les liens orphelins.

```kotlin
@Entity(
    tableName = "person_category_join",
    primaryKeys = ["personId", "categoryId"],
    foreignKeys = [
        ForeignKey(entity = Person::class,   ..., onDelete = CASCADE),
        ForeignKey(entity = Category::class, ..., onDelete = CASCADE)
    ]
)
data class PersonCategoryJoin(val personId: String, val categoryId: String)
```

#### `SocialLinkEntity.kt`
Entité Room représentant un lien vers un réseau social. Relation **1:N** avec `Person` (plusieurs liens par contact). La suppression physique d'un contact déclenche un `CASCADE DELETE` sur ses liens.

```kotlin
@Entity(
    tableName = "social_links",
    foreignKeys = [ForeignKey(entity = Person::class, ..., onDelete = CASCADE)],
    indices = [Index("personId")]
)
data class SocialLinkEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val personId: String,
    val url: String,
    val platform: String   // "Instagram", "LinkedIn", "Lien", etc.
)
```

---

### 🔷 Couche Data — Accès aux données

#### `AppDatabase.kt`
Singleton Room (double-checked locking). Version actuelle : **7**.

```kotlin
@Database(
    entities = [Person::class, Category::class, PersonCategoryJoin::class, SocialLinkEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun categoryDao(): CategoryDao
    abstract fun personCategoryDao(): PersonCategoryDao
    abstract fun socialLinkDao(): SocialLinkDao
}
```
> `fallbackToDestructiveMigration()` est activé pour faciliter les mises à jour en développement.

#### `PersonDao.kt`

| Méthode | Retour | Description |
|---------|--------|-------------|
| `getAllActive()` | `Flow<List<Person>>` | Contacts actifs, triés favoris `DESC`, prénom `ASC` |
| `getById(id)` | `suspend Person?` | Lookup par UUID |
| `insert(person)` | `suspend` | Insertion avec `REPLACE` |
| `update(person)` | `suspend` | Mise à jour complète |
| `softDelete(id)` | `suspend` | Positionne `deletedAt = now` |
| `softDeleteMultiple(ids)` | `suspend` | Soft-delete en lot |
| `restore(id)` | `suspend` | `deletedAt = NULL` |
| `hardDelete(id)` | `suspend` | Suppression physique |
| `hardDeleteAllDeleted()` | `suspend` | Vide la corbeille |
| `getDeleted()` | `Flow<List<Person>>` | Corbeille triée par `deletedAt DESC` |
| `markAsContacted(id)` | `suspend` | Met à jour `lastContactedAt` |
| `purgeOldDeleted(cutoff)` | `suspend` | Supprime les éléments > 30 jours |

#### `SocialLinkDao.kt`

| Méthode | Retour | Description |
|---------|--------|-------------|
| `getForPerson(personId)` | `Flow<List<SocialLinkEntity>>` | Liens d'un contact, triés par insertion |
| `getAll()` | `Flow<List<SocialLinkEntity>>` | Tous les liens (utilisé par `HomeViewModel.socialLinksMap`) |
| `insert(link)` | `suspend` | Insertion avec `REPLACE` |
| `deleteById(id)` | `suspend` | Suppression par ID de lien |
| `deleteAllForPerson(personId)` | `suspend` | Supprime tous les liens d'un contact |

#### `PersonCategoryDao.kt`
DAO dédié à la table de jointure. **Aucune opération de ce DAO ne supprime de personne ou de catégorie** — il gère uniquement les associations.

| Méthode | Description |
|---------|-------------|
| `insert(join)` / `insertAll(joins)` | Crée un ou plusieurs liens personne-catégorie |
| `removePersonsFromCategory(ids, categoryId)` | Retire plusieurs contacts d'une catégorie |
| `getActivePersonsInCategory(categoryId)` | `Flow<List<Person>>` — contacts actifs |
| `getCategoryIdsForPersonSync(personId)` | `suspend` — catégories d'un contact |
| `getAllJoins()` | `Flow<List<PersonCategoryJoin>>` — tous les liens |

#### `ApiClient.kt`
Configuration Retrofit pour Nominatim. Conforme aux [conditions d'utilisation OSM](https://operations.osmfoundation.org/policies/nominatim/) : User-Agent requis, intercepteur de logs en mode debug.

```kotlin
.header("User-Agent", "JTR-App/4.0 (contact-manager Android)")
```

---

### 🔷 Couche Utilitaires — Réseaux sociaux

#### `SocialMediaUtils.kt`
Définit l'énumération `SocialPlatform` (Instagram, LinkedIn, X, Facebook, Snapchat, TikTok) avec leurs métadonnées (couleur ARGB, package Android, patterns d'URL). Expose :
- `extractSocialLinks(text)` : détecte les URLs sociales dans un texte libre
- `openSocialLink(context, link)` : lance l'app native si disponible, sinon navigateur
- `SocialPlatform.icon()` : retourne `Icons.Default.*` correspondant (utilisé dans les dialogues)

#### `SocialMediaMapper.kt`
Mappe une URL vers le drawable de marque correspondant. Résistant aux URLs malformées (bloc `try/catch`, retour fallback).

```kotlin
@DrawableRes
fun getSocialIcon(url: String): Int = try {
    val host = Uri.parse(url).host?.removePrefix("www.") ?: ""
    when {
        host.contains("instagram.com") || host.contains("instagr.am") -> R.drawable.ic_instagram
        host.contains("facebook.com")  || host.contains("fb.com")     -> R.drawable.ic_facebook
        host.contains("linkedin.com")  || host.contains("lnkd.in")    -> R.drawable.ic_linkedin
        host.contains("twitter.com")   || host.contains("x.com")      -> R.drawable.ic_x
        host.contains("discord.com")   || host.contains("discord.gg") -> R.drawable.ic_discord
        host.contains("youtube.com")   || host.contains("youtu.be")   -> R.drawable.ic_youtube
        else -> R.drawable.ic_link
    }
} catch (_: Exception) { R.drawable.ic_link }
```

---

### 🔷 Couche UI — ViewModels

| ViewModel | StateFlows exposés | Méthodes clés |
|-----------|--------------------|---------------|
| `HomeViewModel` | `persons`, `selectedIds`, `isSelectionMode`, `categories`, `isLocationEnabled`, **`socialLinksMap`** | `onSearchQueryChanged()`, `toggleFavorite()`, `toggleSelection()`, `deleteSelected()`, `assignCategoryToSelected()` |
| `AddPersonViewModel` | `firstName`…`photoUri`, `firstNameError`, `cityLat`, **`pendingLinks`** | `onCityFromMap()`, `onPhotoSelected()`, `savePerson()`, **`addPendingLink()`**, **`removePendingLink()`** |
| `EditPersonViewModel` | idem + `isLoading`, `isEditing`, **`socialLinks`** | `loadPerson()`, `commitAllEdits()`, `cancelEdit()`, **`addSocialLink()`**, **`removeSocialLink()`** |
| `CategoryViewModel` | `categories`, `personCountByCategory` | `addCategory()`, `updateCategory()`, `deleteCategoryWithCascade()` |
| `CategoryDetailViewModel` | `category`, `persons`, `searchQuery`, `selectedIds` | `toggleSelection()`, `removeSelectedFromCategory()`, `assignPersonsToCategory()` |
| `SettingsViewModel` | `notificationsEnabled`, `proximityEnabled`, `birthdayEnabled`, `proximityRadiusKm` | `setNotificationsEnabled()`, `setProximityEnabled()`, `setBirthdayEnabled()`, `setProximityRadiusKm()` |
| `ThemeViewModel` | `isDarkMode`, `selectedPreset` | `setDarkMode()`, `setPreset()` |
| `TrashViewModel` | `deletedPersons`, `deletedCategories` | `restore()`, `hardDelete()`, `hardDeleteAll()` |
| `MapViewModel` | `searchResults`, `isSearching`, `selectedLocation`, `cameraEvent` | `search()` (debounce 400 ms), `selectFromSearch()`, `onMapClick()` |

---

### 🔷 Couche UI — Écrans détaillés

#### `HomeScreen.kt` — PersonCard avec réseaux sociaux

La carte de contact affiche désormais une rangée d'icônes de réseaux sociaux sous les métadonnées du contact. Les icônes sont chargées via `painterResource(getSocialIcon(link.url))` avec `tint = Color.Unspecified` pour préserver les couleurs d'origine. Les liens sociaux sont alimentés par `HomeViewModel.socialLinksMap` — un `StateFlow<Map<String, List<SocialLinkEntity>>>` obtenu par `groupBy { it.personId }` sur le flow Room global.

Le **mode multi-sélection** (appui long) reste inchangé : barre d'actions contextuelle, assignation de catégorie à la volée, suppression en lot.

#### `PersonDetailScreen.kt` — SocialLinksSection

La section liens sociaux s'adapte au mode courant :

- **Mode lecture** : icônes brandées 28 dp cliquables → ouvre l'application native ou le navigateur via `openSocialLink()`. Bouton `+` toujours accessible.
- **Mode édition** (double-tap ou bouton Modifier) : liste éditable avec icône + nom de plateforme + URL tronquée + bouton de suppression. Bouton `OutlinedButton("Ajouter un lien social")` en bas.

La mini-carte MapLibre intégrée (`MapLibreMiniMap`) bénéficie de la correction de touch isolation (v4.0), rendant le pan et le zoom fonctionnels y compris lorsqu'elle est affichée au sein du `verticalScroll` de la fiche contact.

#### `MapScreen.kt` — Sélecteur GPS natif

Interface plein écran avec :
- **Barre de recherche en overlay** : appels Nominatim avec debounce 400 ms, résultats cliquables dans un `LazyColumn` flottant
- **Tap direct sur la carte** : placement de marqueur + géocodage inverse → bandeau de confirmation bas
- **Animation caméra** : `easeCamera()` avec interruption propre sur geste utilisateur (`CancelableCallback`)
- **Niveau de zoom adaptatif** : pays (4.0) → ville (11.5) → quartier (14.5), calculé depuis le champ `addressType` de Nominatim

---

### 🔷 Background — Workers

#### `ProximityCheckWorker.kt`
`CoroutineWorker` planifié toutes les **6 heures**. Rayon de détection configurable (1–50 km, défaut 5 km) lu depuis `SharedPreferences` à chaque exécution. Conditions de déclenchement d'une notification :

| Condition | Valeur |
|-----------|--------|
| Distance contact | `< rayon configuré` |
| Inactivité | `> 90 jours` (`daysSinceLastContact() > 90`) |
| Opt-in notification | `cityNotify = true` |
| Coordonnées valides | `hasGeoCoordinates = true` |
| Permission localisation | `ACCESS_FINE_LOCATION` accordée |
| Toggle global | `notifications_enabled = true` |

#### `BirthdayCheckWorker.kt`
Vérification quotidienne du `DAY_OF_MONTH` et `MONTH` de chaque contact. Notification `IMPORTANCE_HIGH` sur le canal `CHANNEL_BIRTHDAY`. Respecte le toggle `birthday_enabled`.

#### `GeofenceBroadcastReceiver.kt`
`BroadcastReceiver` déclaré dans le Manifest, prêt à recevoir les événements `GeofencingEvent` de l'API Play Services Location. Complète le polling logiciel de `ProximityCheckWorker` pour les déclenchements matériels en temps réel.

---

## ⭐ Fonctionnalités clés

### 1. 📍 Rappel de proximité sociale

L'application surveille en arrière-plan si l'utilisateur est géographiquement proche d'une ville associée à un contact qu'il n'a pas contacté depuis 90 jours.

#### Formule de Haversine

Distance entre deux points GPS (φ₁, λ₁) et (φ₂, λ₂), précise à ±0,5 % pour des distances < 1 000 km :

```
a  = sin²(Δφ/2) + cos(φ₁) · cos(φ₂) · sin²(Δλ/2)
d  = 2R · atan2(√a, √(1-a))   avec R = 6 371 km
```

```kotlin
private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return R * 2 * atan2(sqrt(a), sqrt(1 - a))
}
```

---

### 2. 🗺 Géocodage automatique + carte MapLibre

Le géocodage est déclenché **automatiquement** si une ville est saisie sans coordonnées GPS, ou si la ville est modifiée.

```
Saisie "Chicoutimi"
    │
    ▼
PersonRepository.addWithGeocoding(person)
    │
    ▼
GET nominatim.openstreetmap.org/search?q=Chicoutimi&format=json
    Header: User-Agent: JTR-App/4.0 (contact-manager Android)
    │
    ▼
person.copy(cityLat = 48.4286, cityLng = -71.0687) → Room
```

Le résultat de sélection depuis `MapScreen` transite via `SavedStateHandle` → `LaunchedEffect` dans le formulaire appelant (pattern consume-once, compatible sélection répétée de la même ville).

---

### 3. 🔗 Réseaux sociaux multi-plateforme

**Création de contact** : liens ajoutés avant la persistance via `PendingLink`, insérés dans Room après la création de la personne (UUID connu localement).

**Fiche contact** : icônes brandées cliquables (couleurs de marque via `Color.Unspecified`), liste éditable en mode édition, détection automatique de plateforme depuis l'URL.

**Plateformes reconnues** : Instagram · LinkedIn · X (Twitter) · Facebook · Snapchat · TikTok · Discord · YouTube · fallback générique.

---

### 4. 🔔 Système de notifications dual-canal

| Canal | ID | Importance | Déclencheur |
|-------|----|------------|-------------|
| Proximité | `proximity_channel` | `DEFAULT` | `ProximityCheckWorker` — toutes les 6 h |
| Anniversaires | `birthday_channel` | `HIGH` | `BirthdayCheckWorker` — quotidien |

Tous les toggles (notifications globales, proximité, anniversaires, rayon 1–50 km) sont persistés dans `SharedPreferences` et lus par les workers à chaque exécution sans redémarrage.

---

### 5. 🗃 Catégories Many-to-Many

- Un contact peut appartenir à **plusieurs catégories** simultanément via `PersonCategoryJoin`
- **Suppression en cascade** : soft-delete catégorie → soft-delete des membres actifs via `PersonCategoryDao`
- **Réactivité** : `getPersonCountsPerCategory()` combine deux flows Room (`getAllJoins()` + `getAllActive()`) sans requête supplémentaire
- **Photo de couverture** : stockée dans `filesDir/photos/`, affichée via Coil `AsyncImage`

---

### 6. 🗑 Corbeille avec purge automatique

Toutes les suppressions sont **logiques** (`deletedAt = timestamp`). La `TrashScreen` permet la restauration ou la suppression définitive. `purgeOldDeleted()` élimine physiquement les éléments en corbeille depuis plus de **30 jours** au démarrage.

---

## 🗄 Base de données Room

### Schéma — Table `persons`

```sql
CREATE TABLE persons (
    id              TEXT PRIMARY KEY,
    firstName       TEXT NOT NULL,
    lastName        TEXT,
    gender          TEXT,
    photoUri        TEXT,
    birthdate       INTEGER,
    birthdateNotify INTEGER NOT NULL DEFAULT 0,
    city            TEXT,
    cityLat         REAL,
    cityLng         REAL,
    cityNotify      INTEGER NOT NULL DEFAULT 0,
    isFavorite      INTEGER NOT NULL DEFAULT 0,
    lastContactedAt INTEGER,
    notes           TEXT,
    likes           TEXT,
    origin          TEXT,
    createdAt       INTEGER NOT NULL,
    deletedAt       INTEGER
);
```

### Schéma — Table `categories`

```sql
CREATE TABLE categories (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    color       TEXT NOT NULL DEFAULT '#2E86C1',
    icon        TEXT NOT NULL DEFAULT 'folder',
    imagePath   TEXT,
    `order`     INTEGER NOT NULL DEFAULT 0,
    deletedAt   INTEGER
);
```

### Schéma — Table `person_category_join`

```sql
CREATE TABLE person_category_join (
    personId    TEXT NOT NULL,
    categoryId  TEXT NOT NULL,
    PRIMARY KEY (personId, categoryId),
    FOREIGN KEY (personId)   REFERENCES persons(id)    ON DELETE CASCADE,
    FOREIGN KEY (categoryId) REFERENCES categories(id) ON DELETE CASCADE
);
CREATE INDEX index_person_category_join_categoryId ON person_category_join(categoryId);
```

### Schéma — Table `social_links` *(v7)*

```sql
CREATE TABLE social_links (
    id        TEXT PRIMARY KEY,
    personId  TEXT NOT NULL,
    url       TEXT NOT NULL,
    platform  TEXT NOT NULL,
    FOREIGN KEY (personId) REFERENCES persons(id) ON DELETE CASCADE
);
CREATE INDEX index_social_links_personId ON social_links(personId);
```

### Historique des versions

| Version | Changement principal |
|---------|---------------------|
| 1 | Création table `persons` |
| 2 | Ajout table `categories` |
| 3 | Ajout `cityLat`, `cityLng`, `lastContactedAt` dans `persons` |
| 4 | Refactoring champs notifications |
| 5 | Ajout `imagePath` dans `categories` |
| 6 | Ajout table `person_category_join` (Many-to-Many), suppression `categoryId` de `persons` |
| **7** | Ajout table `social_links` (1:N Person, CASCADE delete) · Ajout `SocialLinkDao` *(version actuelle)* |

---

## 🚀 Guide d'installation et configuration

### Prérequis

| Outil | Version minimale |
|-------|-----------------|
| Android Studio | Hedgehog (2023.1.1) ou supérieur |
| JDK | 17 |
| Android SDK | API 35 (compileSdk) |
| Appareil / Émulateur | API 26 (Android 8.0 Oreo) minimum |
| Connexion internet | Requise pour le géocodage Nominatim et les tuiles carte |

### Étapes de build

```bash
# 1. Cloner le dépôt
git clone <url-du-repo>
cd JTR_TP3

# 2. Ouvrir dans Android Studio
# File → Open → sélectionner le dossier JTR_TP3

# 3. Synchroniser Gradle (automatique à l'ouverture)
# Build → Sync Project with Gradle Files

# 4. Lancer sur appareil ou émulateur
# Run → Run 'app'   (ou Shift+F10)
```

### Configuration Gradle notable

```kotlin
// app/build.gradle.kts
android {
    namespace  = "com.jtr.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.jtr.app"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 4
        versionName   = "4.0-Final"
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        jniLibs { useLegacyPackaging = false }  // alignement 16 KB pages (Android 15+)
    }
}
```

### Lancer les tests

```bash
./gradlew test                    # Tests JVM (PersonRepositoryTest, DistanceCalculationTest…)
./gradlew connectedAndroidTest    # Tests instrumentés Room (requiert appareil/émulateur)
```

---

## 🔐 Permissions requises

```xml
<!-- Localisation précise — FusedLocationProvider dans le Worker -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Localisation en arrière-plan — ProximityCheckWorker (Android 10+) -->
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- Notifications — requise sur Android 13+ (API 33+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Internet — géocodage Nominatim + tuiles OpenFreeMap -->
<uses-permission android:name="android.permission.INTERNET" />
```

> Les permissions de localisation et de notification sont demandées en runtime. L'application fonctionne en mode dégradé si ces permissions sont refusées : les workers retournent `Result.success()` silencieusement sans crash.

---

## 🧪 Tests et qualité

### Stratégie de test

| Niveau | Framework | Portée |
|--------|-----------|--------|
| **Unitaire JVM** | JUnit 4 + MockK + Truth + Turbine | Logique métier, calculs, modèles |
| **Instrumenté** | AndroidJUnit4 + Room Testing | DAO en base in-memory réelle |
| **Manuel** | Scénarios définis | Flows complets, UX, permissions |

### Tests unitaires — Classes couvertes

#### `PersonRepositoryTest.kt`
```
getAllActive returns flow from dao
daysSinceLastContact returns null when never contacted
daysSinceLastContact returns correct days  // 3 jours = 3 × 86_400_000 ms
fullName combines firstName and lastName
initials uses first letter of first and last name
hasGeoCoordinates returns true when both coordinates set
softDelete calls dao with correct id
```

#### `DistanceCalculationTest.kt`
```
distance between same point is zero                         // d = 0.0 ± 0.01 km
distance Montreal to Quebec City is approximately 234 km    // ± 10 km
distance Chicoutimi to Saguenay is small                    // < 15 km
```

### Scénarios manuels validés

| # | Scénario | Résultat attendu |
|---|----------|-----------------|
| M-01 | Créer un contact avec ville → vérifier coordonnées | Nominatim géocode automatiquement |
| M-02 | Supprimer contact → ouvrir corbeille → restaurer | Contact réapparaît dans la liste |
| M-03 | Supprimer catégorie avec 3 contacts → vérifier corbeille | 4 éléments (1 catégorie + 3 contacts) |
| M-04 | Activer `cityNotify` → simuler proximité | Notification "dans les parages" reçue |
| M-05 | Changer de thème → fermer → rouvrir | Thème persisté via DataStore |
| M-06 | Sélectionner ville sur la carte → vérifier formulaire | Champ ville + coordonnées GPS remplis |
| M-07 | Sélectionner deux fois la même ville | Formulaire rempli correctement les deux fois |
| M-08 | Refuser permission localisation | Aucun crash, worker silencieux |
| M-09 | Ajouter photo de couverture à une catégorie | Image remplace la couleur dans le cercle |
| M-10 | Assigner un contact à plusieurs catégories | Contact visible dans chaque catégorie |
| M-11 | Pan + zoom sur la carte MapLibre | Gestes fluides sans conflit Compose |
| M-12 | Pan + zoom sur la mini-carte en fiche contact | Gestes fluides dans le scroll vertical |
| M-13 | Ajouter un lien Instagram lors de la création | Lien et icône visibles dans la fiche |
| M-14 | Ajouter un lien inconnu (URL générique) | Icône chaîne fallback affichée |
| M-15 | Rechercher "therese" dans la liste | Trouve "Thérèse" — accent-insensitive |

---

## ⚡ Optimisations de performance

### Debounce sur la recherche et les appels réseau

- **HomeScreen** : debounce 500 ms via `Flow.debounce()` dans `HomeViewModel`
- **MapScreen** : debounce 400 ms via `delay()` + annulation du `Job` précédent à chaque frappe

### Singleton Room thread-safe

```kotlin
fun getInstance(context: Context): AppDatabase =
    INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(...)
            .fallbackToDestructiveMigration()
            .build()
            .also { INSTANCE = it }
    }
```

### `stateIn(WhileSubscribed(5000))`

Évite les reconnexions Room lors des rotations ou des transitions de navigation. Libère les ressources si l'UI est absente depuis plus de 5 secondes.

### `collectAsStateWithLifecycle()`

Suspend automatiquement la collecte du flow lorsque l'app passe en arrière-plan (lifecycle `STARTED`), réduisant la consommation CPU et batterie.

### Copie de photos sur `Dispatchers.IO`

Toutes les opérations de copie de fichier image sont exécutées hors du thread principal via `withContext(Dispatchers.IO)`.

### Alignement mémoire 16 KB (Android 15)

MapLibre 11.5.0 + `useLegacyPackaging = false` garantit que les `.so` sont stockés non compressés et alignés sur des pages de 16 KB, conformément aux exigences d'Android 15 (API 35+).

---

## 📈 Évolution par version

| Version | Fonctionnalités introduites |
|---------|-----------------------------|
| **PP1 / v1.0** | CRUD contacts basique, stockage JSON, liste simple |
| **PP2 / v2.0** | Migration vers Room, photos Coil, favoris, recherche, corbeille, Navigation Compose, thèmes DataStore |
| **PP3 / v3.0** | Géocodage Nominatim, coordonnées GPS, sélecteur carte **MapLibre natif**, catégories **Many-to-Many** (DB v6), WorkManager (proximité + anniversaires), notifications dual-canal, rayon configurable, recherche accent-insensitive, tests MockK/Turbine/Truth, édition catégories + photo de couverture |
| **v4.0-Final** | **Dynamic Social Icon Mapping** (7 drawables brandés, `getSocialIcon`), **Liens sociaux à la création** (`PendingLink`, `AddPersonViewModel`), **Fix gestes MapLibre** (`requestDisallowInterceptTouchEvent` sur plein écran + mini-carte), **DB v7** (`social_links`, `SocialLinkDao`), User-Agent mis à jour |

---

## 👤 Auteur

| Champ | Information |
|-------|-------------|
| **Nom** | Hazim R. |
| **Courriel** | rhaziim78@gmail.com |
| **GitHub** | HRazim |

---

## 📄 Licence

Ce projet est à usage personnel. Tous droits réservés.  
Les données cartographiques sont fournies par © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright) sous licence ODbL.  
Les tuiles sont servies par [OpenFreeMap](https://openfreemap.org) (licence libre, sans clé API).

---

*JTR v4.0-Final — Kotlin · Jetpack Compose · MVVM*
