# 📱 JTR — Justin To Remember

> **Carnet de contacts enrichi nouvelle génération** · Version `3.0-Final`  
> Cours **8INF257 — Développement Mobile** · Université du Québec à Chicoutimi (UQAC) · Hiver 2026

---

## 🎯 Proposition de valeur

JTR (*Justin To Remember*) va au-delà du simple répertoire téléphonique. L'application maintient une **mémoire sociale active** : elle enregistre le contexte humain de chaque relation (goûts, anniversaires, ville, notes), géocode automatiquement les villes via OpenStreetMap, et notifie proactivement l'utilisateur lorsqu'il se retrouve physiquement proche d'un contact qu'il n'a pas vu depuis longtemps. Le tout, sans service cloud, sans clé API propriétaire, et avec un stockage 100 % local chiffrable.

---

## 📋 Table des matières

1. [Captures d'écran](#-aperçu-visuel)
2. [Arborescence du projet](#-arborescence-du-projet)
3. [Architecture MVVM](#-architecture-mvvm)
4. [Stack technologique](#-stack-technologique)
5. [Répertoire des classes](#-répertoire-des-classes-et-composants)
6. [Fonctionnalités clés PP3](#-fonctionnalités-clés-pp3)
7. [Base de données Room](#-base-de-données-room)
8. [Guide d'installation](#-guide-dinstallation-et-configuration)
9. [Permissions requises](#-permissions-requises)
10. [Tests et qualité](#-tests-et-qualité)
11. [Optimisations de performance](#-optimisations-de-performance)
12. [Évolution par livrable](#-évolution-par-livrable)

---

## 🖼 Aperçu visuel

| Accueil | Détail contact | Catégories | Paramètres |
|---------|---------------|------------|------------|
| Liste filtrée, favoris, recherche debounce | Photo, carte OSM, historique | Gestion avec photo de couverture | Thèmes, corbeille |

---

## 🗂 Arborescence du projet

```
JTR_TP3/
├── app/
│   ├── build.gradle.kts                    # Dépendances, versionCode=3, minSdk=26
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml         # Permissions, déclaration workers/receiver
│       │   └── java/com/jtr/app/
│       │       ├── JTRApplication.kt       # Application class — canaux + WorkManager
│       │       ├── MainActivity.kt         # Entry point Compose, thème global
│       │       │
│       │       ├── domain/
│       │       │   └── model/
│       │       │       ├── Person.kt       # Entité Room — 18 champs
│       │       │       └── Category.kt     # Entité Room — 8 champs (+ imagePath)
│       │       │
│       │       ├── data/
│       │       │   ├── local/
│       │       │   │   ├── AppDatabase.kt  # Singleton Room, version 5
│       │       │   │   ├── PersonDao.kt    # DAO CRUD + recherche + cascade
│       │       │   │   └── CategoryDao.kt  # DAO CRUD + soft delete
│       │       │   ├── remote/
│       │       │   │   ├── ApiClient.kt    # Retrofit + OkHttp, User-Agent conforme Nominatim
│       │       │   │   ├── NominatimApi.kt # Interface Retrofit — endpoint /search
│       │       │   │   └── GeocodingResult.kt # Modèle JSON @Serializable
│       │       │   └── repository/
│       │       │       ├── PersonRepository.kt    # CRUD, géocodage, migration JSON
│       │       │       ├── CategoryRepository.kt  # CRUD catégories + cascade
│       │       │       └── GeocodingRepository.kt # Couche d'abstraction Nominatim
│       │       │
│       │       ├── ui/
│       │       │   ├── navigation/
│       │       │   │   └── Navigation.kt   # Routes, NavHost, BottomBar
│       │       │   ├── home/
│       │       │   │   ├── HomeScreen.kt   # Liste contacts, recherche, FAB
│       │       │   │   └── HomeViewModel.kt
│       │       │   ├── person/
│       │       │   │   ├── AddPersonScreen.kt
│       │       │   │   ├── AddPersonViewModel.kt  # State formulaire + SavedStateHandle
│       │       │   │   ├── EditPersonScreen.kt
│       │       │   │   ├── EditPersonViewModel.kt
│       │       │   │   └── PersonDetailScreen.kt  # Carte OSM intégrée (Leaflet/WebView)
│       │       │   ├── category/
│       │       │   │   ├── CategoriesScreen.kt    # Liste + édition + photo couverture
│       │       │   │   ├── CategoryViewModel.kt
│       │       │   │   ├── CategoryDetailScreen.kt
│       │       │   │   └── CategoryDetailViewModel.kt
│       │       │   ├── map/
│       │       │   │   ├── MapScreen.kt    # Sélecteur GPS via Leaflet
│       │       │   │   └── MapViewModel.kt
│       │       │   ├── settings/
│       │       │   │   └── SettingsScreen.kt  # Mode sombre, thèmes, corbeille
│       │       │   ├── theme/
│       │       │   │   ├── Color.kt
│       │       │   │   ├── Theme.kt
│       │       │   │   ├── ThemePreset.kt  # Presets de couleurs (DataStore)
│       │       │   │   ├── ThemeViewModel.kt
│       │       │   │   └── Type.kt
│       │       │   └── trash/
│       │       │       ├── TrashScreen.kt  # Corbeille — restauration / suppression
│       │       │       └── TrashViewModel.kt
│       │       │
│       │       └── worker/
│       │           ├── ProximityCheckWorker.kt    # Haversine, 6h, 10 km, 90 jours
│       │           ├── BirthdayCheckWorker.kt     # Vérif quotidienne anniversaires
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

JTR implémente le pattern **Model-View-ViewModel** recommandé par Google, renforcé d'une couche Repository pour l'isolation des sources de données.

```
┌─────────────────────────────────────────────────────────────────┐
│                          UI LAYER                               │
│   Composables Jetpack Compose  ←→  ViewModels (StateFlow)       │
│   (CategoriesScreen, HomeScreen, PersonDetailScreen, ...)       │
└───────────────────────────┬─────────────────────────────────────┘
                            │ observe / call
┌───────────────────────────▼─────────────────────────────────────┐
│                       DOMAIN LAYER                              │
│   Person.kt  ·  Category.kt  (entités pures, logique métier)    │
└───────────────────────────┬─────────────────────────────────────┘
                            │ inject
┌───────────────────────────▼─────────────────────────────────────┐
│                        DATA LAYER                               │
│   PersonRepository  ·  CategoryRepository  ·  GeocodingRepo     │
│        │                    │                      │            │
│   PersonDao (Room)    CategoryDao (Room)    NominatimApi (HTTP) │
│        └──────────────── AppDatabase ──────────────┘           │
└─────────────────────────────────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                    BACKGROUND LAYER                             │
│   ProximityCheckWorker (6h)  ·  BirthdayCheckWorker (1j)       │
└─────────────────────────────────────────────────────────────────┘
```

### Flux de données unidirectionnel

```
User action ──► ViewModel.fun() ──► Repository.suspend() ──► DAO / API
                     ▲                                          │
                     └─────── Flow<T> (Room reactive) ◄────────┘
```

Les ViewModels exposent uniquement des `StateFlow<T>` en lecture seule via `stateIn(WhileSubscribed(5000))`. Les Composables observent ces flows avec `collectAsStateWithLifecycle()` pour éviter les fuites mémoire.

---

## 🛠 Stack technologique

| Catégorie | Bibliothèque | Version | Usage |
|-----------|-------------|---------|-------|
| **UI** | Jetpack Compose BOM | `2024.12.01` | Interface déclarative 100 % Compose |
| **UI** | Material3 | via BOM | Design system, thèmes dynamiques |
| **UI** | Material Icons Extended | via BOM | Icônes vectorielles |
| **Navigation** | Navigation Compose | `2.8.5` | NavHost, bottom bar, SavedStateHandle |
| **Persistence** | Room Runtime + KTX | `2.6.1` | ORM SQLite, Flows réactifs |
| **Persistence** | KSP | `2.1.0-1.0.29` | Génération code Room (remplace KAPT) |
| **Réseau** | Retrofit | `2.11.0` | Client HTTP typé pour Nominatim |
| **Réseau** | OkHttp | `4.12.0` | Couche transport + intercepteurs |
| **Réseau** | kotlinx.serialization | `1.7.3` | Désérialisation JSON sans réflexion |
| **Background** | WorkManager | `2.10.0` | Tâches périodiques garanties |
| **Localisation** | Play Services Location | `21.3.0` | FusedLocationProviderClient |
| **Image** | Coil Compose | `2.7.0` | Chargement asynchrone photos |
| **Préférences** | DataStore Preferences | `1.1.1` | Thème, mode sombre persistants |
| **Coroutines** | Kotlinx Coroutines Android | `1.8.1` | Async non-bloquant |
| **Coroutines** | Coroutines Play Services | `1.8.1` | `await()` sur Task Google |
| **Tests** | JUnit 4 | `4.13.2` | Cadre de test unitaire |
| **Tests** | MockK | `1.13.12` | Mocking idiomatique Kotlin |
| **Tests** | Turbine | `1.2.0` | Test de `Flow` Kotlin |
| **Tests** | Truth | `1.4.4` | Assertions fluentes lisibles |

**Langages :** Kotlin 2.1.0 · JVM target 17  
**SDK :** `compileSdk = 35` · `targetSdk = 35` · `minSdk = 26` (Android 8.0+)

---

## 📦 Répertoire des classes et composants

### 🔷 Couche Domain — Modèles

#### `Person.kt`
Entité Room centrale avec 18 champs couvrant l'identité, la géolocalisation, les préférences de notification et les métadonnées de gestion.

| Champ | Type | Description |
|-------|------|-------------|
| `id` | `String` (UUID) | Clé primaire générée automatiquement |
| `firstName`, `lastName` | `String` / `String?` | Nom complet |
| `gender` | `String?` | `"male"`, `"female"`, `"non-binary"` |
| `photoUri` | `String?` | Chemin absolu vers `filesDir/photos/` |
| `birthdate` | `Long?` | Timestamp Unix (ms) |
| `birthdateNotify` | `Boolean` | Active le canal `CHANNEL_BIRTHDAY` |
| `city` | `String?` | Nom de ville (texte libre) |
| `cityLat`, `cityLng` | `Double?` | Coordonnées GPS (géocodage Nominatim) |
| `cityNotify` | `Boolean` | Active le canal `CHANNEL_PROXIMITY` |
| `isFavorite` | `Boolean` | Épingle en haut de liste |
| `categoryId` | `String?` | FK vers `Category.id` |
| `lastContactedAt` | `Long?` | Timestamp de la dernière consultation de fiche |
| `notes`, `likes`, `origin` | `String?` | Champs texte enrichis |
| `createdAt` | `Long` | Timestamp de création |
| `deletedAt` | `Long?` | Null = actif, non-null = en corbeille |

**Propriétés calculées :**
```kotlin
val fullName: String          // "Alice Dupont"
val initials: String          // "AD"
val hasGeoCoordinates: Boolean // cityLat != null && cityLng != null
fun daysSinceLastContact(): Long? // (now - lastContactedAt) / 86_400_000
```

#### `Category.kt`
Groupe logique pour organiser les contacts.

| Champ | Type | Description |
|-------|------|-------------|
| `id` | `String` (UUID) | Clé primaire |
| `name` | `String` | Libellé affiché |
| `color` | `String` | Code hexadécimal (`"#2E86C1"`) |
| `icon` | `String` | Nom icône Material (`"folder"`) |
| `imagePath` | `String?` | Chemin photo de couverture (optionnel) |
| `order` | `Int` | Ordre d'affichage |
| `deletedAt` | `Long?` | Soft delete |

---

### 🔷 Couche Data — Accès aux données

#### `AppDatabase.kt`
Singleton Room (pattern double-checked locking). Version actuelle : **5**.

```kotlin
@Database(entities = [Person::class, Category::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun categoryDao(): CategoryDao
    // getInstance(context) — Singleton thread-safe
}
```
> `fallbackToDestructiveMigration()` est activé pour simplifier les migrations en développement.

#### `PersonDao.kt`
Interface Room exposant toutes les opérations sur la table `persons`.

| Méthode | Type retour | Description |
|---------|-------------|-------------|
| `getAllActive()` | `Flow<List<Person>>` | Contacts non supprimés, triés par `isFavorite DESC, firstName ASC` |
| `search(query)` | `Flow<List<Person>>` | Recherche LIKE sur nom + ville |
| `getByCategory(id)` | `Flow<List<Person>>` | Filtre par catégorie |
| `getDeleted()` | `Flow<List<Person>>` | Corbeille triée par `deletedAt DESC` |
| `insert(person)` | `suspend` | Insertion avec `REPLACE` |
| `update(person)` | `suspend` | Mise à jour complète |
| `softDelete(id)` | `suspend` | Positionne `deletedAt = now` |
| `restore(id)` | `suspend` | `deletedAt = NULL` |
| `hardDelete(id)` | `suspend` | Suppression physique |
| `markAsContacted(id)` | `suspend` | Met à jour `lastContactedAt` |
| `purgeOldDeleted(cutoff)` | `suspend` | Supprime physiquement les entrées > 30 jours |
| `softDeleteByCategory(id, ts)` | `suspend` | Cascade catégorie → contacts |
| `assignCategory(ids, catId)` | `suspend` | Affectation en masse |
| `countActiveByCategory(id)` | `suspend Int` | Comptage pour UI confirmation |

#### `CategoryDao.kt`
| Méthode | Description |
|---------|-------------|
| `getAll()` | Flow de catégories actives (ORDER BY `order` ASC, `name` ASC) |
| `getDeleted()` | Flow corbeille catégories |
| `insert` / `update` / `hardDelete` | CRUD standard |
| `softDelete(id, ts)` | Mise en corbeille |
| `restore(id)` | Restauration |
| `getById(id)` | Lookup synchrone (suspend) |

#### `ApiClient.kt`
Configuration Retrofit pour Nominatim. Conforme aux [conditions d'utilisation](https://operations.osmfoundation.org/policies/nominatim/) : User-Agent obligatoire, intercepteur HTTP pour logs debug.

```kotlin
object ApiClient {
    // User-Agent conforme politique Nominatim
    private val userAgentInterceptor = Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", "JTR-App/3.0 (UQAC 8INF257)")
                .build()
        )
    }

    val nominatimApi: NominatimApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NominatimApi::class.java)
    }
}
```

#### `NominatimApi.kt`
Interface Retrofit — endpoint `/search` de l'API OpenStreetMap Nominatim.

```kotlin
interface NominatimApi {
    @GET("search")
    suspend fun searchCity(
        @Query("q") cityName: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 1,
        @Query("addressdetails") addressDetails: Int = 1
    ): List<GeocodingResult>
}
```

#### `GeocodingResult.kt`
Data class `@Serializable` mappant la réponse JSON Nominatim. Les coordonnées (`lat`, `lon`) sont transmises sous forme de `String` par l'API et converties en `Double?` via des propriétés calculées.

---

### 🔷 Couche Repository — Logique métier

#### `PersonRepository.kt`
Orchestre toutes les opérations sur les contacts. Points notables :
- `addWithGeocoding(person)` et `updateWithGeocoding(person, oldCity)` : appel conditionnel à Nominatim si les coordonnées sont absentes ou si la ville a changé.
- `migrateFromJson()` : migration automatique des données PP1 (fichier `persons.json`) vers Room.
- `purgeOldDeleted()` : suppression physique des éléments en corbeille depuis > 30 jours.
- `markAsContacted(personId)` : met à jour `lastContactedAt` à chaque ouverture d'une fiche contact.

#### `CategoryRepository.kt`
Gère les opérations en cascade catégorie ↔ contacts :

```kotlin
suspend fun softDeleteWithCascade(categoryId: String) {
    val ts = System.currentTimeMillis()
    categoryDao.softDelete(categoryId, ts)
    personDao.softDeleteByCategory(categoryId, ts) // même timestamp pour cohérence
}

suspend fun restoreWithCascade(categoryId: String) {
    categoryDao.restore(categoryId)
    personDao.restoreByCategory(categoryId)
}
```

#### `GeocodingRepository.kt`
Couche d'abstraction fine sur `ApiClient.nominatimApi`. Retourne `Pair<Double, Double>?` (lat, lng) depuis un nom de ville, ou `null` en cas d'échec réseau.

---

### 🔷 Couche UI — ViewModels

| ViewModel | StateFlows exposés | Méthodes clés |
|-----------|--------------------|---------------|
| `HomeViewModel` | `persons`, `searchQuery` | `onSearchQueryChanged()`, `toggleFavorite()` |
| `CategoryViewModel` | `categories`, `personCountByCategory` | `addCategory()`, `updateCategory()`, `deleteCategoryWithCascade()` |
| `CategoryDetailViewModel` | `category`, `persons`, `searchQuery`, `selectedIds` | `toggleSelection()`, `deleteSelected()`, `assignCategory()` |
| `AddPersonViewModel` | `firstName`…`photoUri`, `firstNameError`, `mapCity/Lat/Lng` | `onPhotoSelected()`, `savePerson()` |
| `EditPersonViewModel` | idem + `personId` | `loadPerson()`, `updatePerson()` |
| `ThemeViewModel` | `isDarkMode`, `selectedPreset` | `setDarkMode()`, `setPreset()` |
| `TrashViewModel` | `deletedPersons`, `deletedCategories` | `restore()`, `hardDelete()`, `hardDeleteAll()` |
| `MapViewModel` | `searchQuery`, `suggestions`, `selectedLocation` | `search()` (debounce 500ms) |

---

### 🔷 Background — Workers

#### `ProximityCheckWorker.kt`
`CoroutineWorker` planifié toutes les **6 heures** via `PeriodicWorkRequestBuilder`.

```kotlin
override suspend fun doWork(): Result {
    val location = fusedLocationClient.lastLocation.await() ?: return Result.success()

    repository.getAllActive().first()
        .filter { it.cityNotify && it.hasGeoCoordinates }
        .forEach { person ->
            val distance = calculateDistance(
                location.latitude, location.longitude,
                person.cityLat!!, person.cityLng!!
            )
            val shouldNotify = distance < PROXIMITY_RADIUS_KM &&       // < 10 km
                               (person.daysSinceLastContact() ?: Long.MAX_VALUE) > 90  // > 90 jours
            if (shouldNotify) sendProximityNotification(person, distance.toInt())
        }
    return Result.success()
}
```

#### `BirthdayCheckWorker.kt`
Vérifie quotidiennement si le `Calendar.DAY_OF_MONTH` et `Calendar.MONTH` du birthdate correspondent à aujourd'hui. Déclenche une notification `PRIORITY_HIGH` sur `CHANNEL_BIRTHDAY`.

---

### 🔷 Application — `JTRApplication.kt`

Point d'entrée global. Initialisé **une seule fois** au démarrage du processus.

```kotlin
class JTRApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()   // Android 8+ requis
        scheduleProximityChecks()      // WorkManager KEEP policy
    }

    private fun scheduleProximityChecks() {
        val request = PeriodicWorkRequestBuilder<ProximityCheckWorker>(
            6, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "proximity_check",
            ExistingPeriodicWorkPolicy.KEEP,  // Ne recrée pas si déjà planifié
            request
        )
    }

    companion object {
        const val CHANNEL_PROXIMITY = "proximity_channel"
        const val CHANNEL_BIRTHDAY  = "birthday_channel"
    }
}
```

---

## ⭐ Fonctionnalités clés PP3

### 1. 📍 Rappel de proximité sociale

**Concept :** L'application surveille en arrière-plan si l'utilisateur est géographiquement proche d'une ville associée à un contact qu'il n'a pas vu depuis longtemps.

#### Algorithme — Formule de Haversine

La distance entre deux points GPS (φ₁, λ₁) et (φ₂, λ₂) est calculée via la formule de Haversine, précise à ±0,5 % pour des distances < 1 000 km :

```
a  = sin²(Δφ/2) + cos(φ₁) · cos(φ₂) · sin²(Δλ/2)
d  = 2R · atan2(√a, √(1-a))   avec R = 6 371 km
```

Implémentation Kotlin :

```kotlin
private fun calculateDistance(
    lat1: Double, lon1: Double,
    lat2: Double, lon2: Double
): Double {
    val R = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2)
    return R * 2 * atan2(sqrt(a), sqrt(1 - a))
}
```

#### Conditions de déclenchement

| Condition | Valeur | Source |
|-----------|--------|--------|
| Distance | `< 10 km` | `PROXIMITY_RADIUS_KM = 10.0` |
| Inactivité | `> 90 jours` | `person.daysSinceLastContact() > 90` |
| Opt-in | `cityNotify = true` | Champ `Person` |
| Coordonnées | `hasGeoCoordinates = true` | `cityLat != null && cityLng != null` |
| Permission | `ACCESS_FINE_LOCATION` | Vérifiée en runtime dans le worker |

---

### 2. 🗺 Intégration API OpenStreetMap Nominatim

Le géocodage est déclenché **automatiquement** lors de l'ajout ou de la modification d'un contact si une ville est saisie sans coordonnées GPS.

```
Utilisateur saisit "Chicoutimi"
         │
         ▼
PersonRepository.addWithGeocoding(person)
         │
         ▼
GeocodingRepository.getCityCoordinates("Chicoutimi")
         │
         ▼
GET https://nominatim.openstreetmap.org/search
    ?q=Chicoutimi&format=json&limit=1&addressdetails=1
    Header: User-Agent: JTR-App/3.0 (UQAC 8INF257)
         │
         ▼
GeocodingResult { lat: "48.4286", lon: "-71.0687", ... }
         │
         ▼
person.copy(cityLat = 48.4286, cityLng = -71.0687) → Room
```

Le sélecteur de carte intégré (`MapScreen`) permet une sélection GPS manuelle via une carte Leaflet/OpenStreetMap dans un `WebView`. Le résultat est transmis à l'écran précédent via `SavedStateHandle` (pattern navigation Compose).

---

### 3. 🔔 Système de notifications

#### Canaux de notification

| Canal | ID | Importance | Déclencheur |
|-------|----|------------|-------------|
| **Proximité** | `proximity_channel` | `IMPORTANCE_DEFAULT` | `ProximityCheckWorker` toutes les 6h |
| **Anniversaires** | `birthday_channel` | `IMPORTANCE_HIGH` | `BirthdayCheckWorker` quotidien |

#### Planification WorkManager

```kotlin
// Démarrage unique garanti — KEEP préserve le worker existant au redémarrage
WorkManager.getInstance(this).enqueueUniquePeriodicWork(
    "proximity_check",
    ExistingPeriodicWorkPolicy.KEEP,
    PeriodicWorkRequestBuilder<ProximityCheckWorker>(6, TimeUnit.HOURS).build()
)
```

WorkManager garantit l'exécution même après un redémarrage de l'appareil ou un arrêt forcé de l'application (sous contraintes OEM).

---

### 4. 🗃 Gestion des catégories

- **Création** : nom + couleur hex parmi 6 presets
- **Édition** : dialogue de modification avec sélecteur de photo de couverture (`PickVisualMedia`)
- **Photo de couverture** : stockée dans `filesDir/photos/category_<UUID>.jpg`, affichée dans la liste à la place de la couleur via `AsyncImage` (Coil)
- **Suppression en cascade** : soft-delete catégorie + tous ses membres actifs en une transaction
- **Réactivité** : `personCountByCategory` est un `StateFlow<Map<String, Int>>` recalculé à chaque mutation de la table `persons`

---

### 5. 🗑 Corbeille avec purge automatique

Les suppressions sont toujours **logiques** (`deletedAt = timestamp`). La `TrashScreen` affiche les contacts et catégories supprimés avec la possibilité de restaurer ou supprimer définitivement. La méthode `purgeOldDeleted()` élimine physiquement les éléments en corbeille depuis plus de **30 jours**.

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
    categoryId      TEXT,
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

### Historique des versions

| Version | Changement principal |
|---------|---------------------|
| 1 | Création table `persons` |
| 2 | Ajout table `categories` |
| 3 | Ajout `cityLat`, `cityLng`, `categoryId`, `lastContactedAt` dans `persons` |
| 4 | Refactoring champs notifications |
| **5** | Ajout `imagePath` dans `categories` *(version actuelle)* |

---

## 🚀 Guide d'installation et configuration

### Prérequis

| Outil | Version minimale |
|-------|-----------------|
| Android Studio | Hedgehog (2023.1.1) ou supérieur |
| JDK | 17 |
| Android SDK | API 35 (compileSdk) |
| Appareil / Émulateur | API 26 (Android 8.0 Oreo) minimum |
| Connexion internet | Requise pour le géocodage Nominatim |

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
    namespace   = "com.jtr.app"
    compileSdk  = 35
    defaultConfig {
        applicationId  = "com.jtr.app"
        minSdk         = 26
        targetSdk      = 35
        versionCode    = 3
        versionName    = "3.0-Final"
    }
    kotlinOptions { jvmTarget = "17" }
}
```

### Lancer les tests unitaires

```bash
./gradlew test                    # Tests JVM (PersonRepositoryTest, DistanceCalculationTest...)
./gradlew connectedAndroidTest    # Tests instrumentés Room (requiert device/émulateur)
```

---

## 🔐 Permissions requises

Déclarées dans `AndroidManifest.xml` :

```xml
<!-- Localisation précise — requise pour FusedLocationProvider dans le Worker -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Localisation en arrière-plan — requise pour ProximityCheckWorker (Android 10+) -->
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- Notifications — requise sur Android 13+ (API 33+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Internet — géocodage Nominatim -->
<uses-permission android:name="android.permission.INTERNET" />
```

> **Note :** Les permissions de localisation et de notification sont demandées en runtime à l'utilisateur. L'application fonctionne en mode dégradé (sans notifications de proximité) si ces permissions sont refusées — les workers retournent `Result.success()` silencieusement.

---

## 🧪 Tests et qualité

### Stratégie de test

| Niveau | Framework | Portée |
|--------|-----------|--------|
| **Unitaire JVM** | JUnit 4 + MockK + Truth + Turbine | Logique métier, calculs, modèles |
| **Instrumenté** | AndroidJUnit4 + Room Testing | DAO en base réelle (in-memory) |
| **Manuel** | Scénarios définis | Flows complets, UX, permissions |

### Tests unitaires — Classes couvertes

#### `PersonRepositoryTest.kt` — 9 tests

```kotlin
// Exemples de tests
`getAllActive returns flow from dao`           // MockK + Turbine Flow assertion
`daysSinceLastContact returns null when never contacted`
`daysSinceLastContact returns correct days`   // 3 jours = 3 * 86_400_000 ms
`fullName combines firstName and lastName`
`initials uses first letter of first and last name`
`hasGeoCoordinates returns true when both coordinates set`
`softDelete calls dao with correct id`        // coVerify MockK
```

#### `DistanceCalculationTest.kt` — 3 tests

```kotlin
`distance between same point is zero`                          // d = 0.0 ± 0.01 km
`distance Montreal to Quebec City is approximately 234 km`     // ± 10 km
`distance Chicoutimi to Saguenay is small`                     // < 15 km
```

### Scénarios de tests manuels validés

| # | Scénario | Résultat attendu |
|---|----------|-----------------|
| M-01 | Créer un contact avec ville → vérifier coordonnées GPS | Nominatim géocode automatiquement |
| M-02 | Supprimer contact → ouvrir corbeille → restaurer | Contact réapparaît dans la liste |
| M-03 | Supprimer catégorie avec 3 contacts → vérifier corbeille | 4 éléments en corbeille (1 cat + 3 contacts) |
| M-04 | Activer `cityNotify` sur un contact → simuler proximité | Notification "dans les parages" reçue |
| M-05 | Changer de thème → fermer l'app → rouvrir | Thème persisté via DataStore |
| M-06 | Sélectionner une ville sur la carte → vérifier formulaire | Champ ville + coordonnées remplis |
| M-07 | Refuser permission localisation → vérifier Worker | Aucun crash, `Result.success()` silencieux |
| M-08 | Ajouter photo de profil à une catégorie → vérifier liste | Image remplace la couleur dans le cercle |
| M-09 | Éditer nom d'une catégorie → vérifier fiche contact liée | Nom mis à jour dynamiquement |
| M-10 | Ajouter 30 contacts, effectuer une recherche | Filtre réactif sans délai perceptible |

---

## ⚡ Optimisations de performance

### Debounce 500ms sur la recherche

Les requêtes de recherche (HomeScreen et MapScreen) sont throttlées via un `debounce` de 500ms pour éviter les appels DAO / réseau à chaque frappe clavier :

```kotlin
viewModelScope.launch {
    _searchQuery
        .debounce(500)
        .collect { query ->
            // Déclenche la requête Room ou Nominatim uniquement après 500ms d'inactivité
        }
}
```

### Pattern Singleton pour Room

`AppDatabase` utilise un singleton thread-safe (`@Volatile` + `synchronized`) pour garantir une unique instance de connexion SQLite sur toute la durée de vie du processus :

```kotlin
companion object {
    @Volatile private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(...)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
}
```

### `stateIn(WhileSubscribed(5000))`

Tous les flows Room dans les ViewModels utilisent la stratégie `WhileSubscribed(5000)` :

```kotlin
val categories: StateFlow<List<Category>> = repo.getAllActive()
    .stateIn(
        scope     = viewModelScope,
        started   = SharingStarted.WhileSubscribed(5000), // Maintient le flow 5s après disparition UI
        initialValue = emptyList()
    )
```

Cela évite les reconnexions inutiles à Room lors des rotations d'écran ou des brèves transitions de navigation, tout en libérant les ressources si l'UI est absente depuis plus de 5 secondes.

### `collectAsStateWithLifecycle()`

Tous les Composables utilisent `collectAsStateWithLifecycle()` (lifecycle-runtime-compose) à la place de `collectAsState()`, ce qui suspend automatiquement la collecte du flow lorsque l'app passe en arrière-plan (lifecycle `STARTED`), réduisant la consommation CPU et batterie.

### Copie de photos en `Dispatchers.IO`

Les opérations de copie de fichier image (contact et catégorie) sont systématiquement exécutées sur le dispatcher IO pour ne jamais bloquer le thread principal :

```kotlin
fun onPhotoSelected(uri: Uri) {
    viewModelScope.launch {
        val path = withContext(Dispatchers.IO) { copyPhotoToStorage(uri) }
        _photoUri.value = path
    }
}
```

---

## 📈 Évolution par livrable

| Livrable | Fonctionnalités introduites |
|----------|-----------------------------|
| **PP1** | CRUD contacts basique, stockage JSON, liste simple |
| **PP2** | Migration vers Room (SQLite), Photos Coil, Favoris, Recherche, Corbeille, Navigation Compose, Thèmes DataStore |
| **PP3** | Géocodage Nominatim, Coordonnées GPS, Sélecteur carte Leaflet, Catégories avec cascade, WorkManager (Proximité + Anniversaires), Notifications dual-channel, Tests unitaires MockK/Turbine/Truth, **Édition catégories + photo de couverture** |

---

## 👤 Auteur

| Champ | Information |
|-------|-------------|
| **Nom** | Hazim R. |
| **Courriel** | rhaziim78@gmail.com |
| **Institution** | UQAC — Université du Québec à Chicoutimi |
| **Cours** | 8INF257 — Développement d'applications mobiles |
| **Session** | Hiver 2026 |

---

## 📄 Licence

Ce projet est réalisé dans le cadre d'un cours universitaire. Tous droits réservés.  
Les données cartographiques sont fournies par © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright) sous licence ODbL.

---

*Généré avec ❤️ et Jetpack Compose — JTR v3.0-Final*
