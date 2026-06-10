# 📱 JTR — Just To Remember

> **Carnet de contacts enrichi nouvelle génération** · Version `5.4.0`  
> Projet personnel Android — Kotlin · Jetpack Compose · MVVM

---

## 🎯 Proposition de valeur

JTR (*Just To Remember*) va au-delà du simple répertoire téléphonique. L'application maintient une **mémoire sociale active** : elle enregistre le contexte humain de chaque relation (goûts, anniversaires, ville, notes, réseaux sociaux), géocode automatiquement les villes via OpenStreetMap, et notifie proactivement l'utilisateur lorsqu'il se retrouve physiquement proche d'un contact qu'il n'a pas vu depuis longtemps. Le tout, sans service cloud, sans clé API propriétaire, et avec un stockage 100 % local.

> **État actuel — Juin 2026.** Le cycle de développement de la **Version 4 (v4.x)** est officiellement **clos** : stable, mature, et couronné par un moteur d'ergonomie tactile abouti (Drag & Drop fluide, dossiers récursifs, mode sélection « Galerie »). Le cycle **Version 5** est en plein essor : la **v5.4.0** active la fonctionnalité reine — le **Moteur de Proximité** en tâche de fond (Worker 3 h + geofencing unifiés, rayon 10 km, anti-spam 48 h, notifications heads-up avec deep link vers la fiche du contact) — après une branche 5.3 dédiée à l'onboarding, l'importation native et le polish UX. Voir [Le Grand Bilan de la Version 4](#-le-grand-bilan-de-la-version-4) et [Version 5 — Cycle en cours](#-version-50--en-cours-de-développement).

---

## 📋 Table des matières

1. [Aperçu visuel](#-aperçu-visuel)
2. [Version 5.0 — En cours de développement](#-version-50--en-cours-de-développement)
3. [Le Grand Bilan de la Version 4](#-le-grand-bilan-de-la-version-4)
4. [Historique des versions antérieures (v1 → v3)](#-historique-des-versions-antérieures-v1--v3)
5. [Version 4.4](#version-44)
6. [Version 4.3](#version-43)
7. [Version 4.2](#version-42)
8. [Version 4.1](#version-41)
9. [Version 4.0](#version-40)
10. [Arborescence du projet](#-arborescence-du-projet)
11. [Architecture MVVM](#-architecture-mvvm)
12. [Stack technologique](#-stack-technologique)
13. [Répertoire des classes](#-répertoire-des-classes-et-composants)
14. [Fonctionnalités clés](#-fonctionnalités-clés)
15. [Base de données Room](#-base-de-données-room)
16. [Guide d'installation](#-guide-dinstallation-et-configuration)
17. [Permissions requises](#-permissions-requises)
18. [Tests et qualité](#-tests-et-qualité)
19. [Optimisations de performance](#-optimisations-de-performance)
20. [Évolution par version](#-évolution-par-version)

---

## 🖼 Aperçu visuel

| Accueil | Détail contact | Carte MapLibre | Paramètres |
|---------|---------------|----------------|------------|
| Liste filtrée, icônes réseaux sociaux, favoris, recherche | Photo, mini-carte, liens sociaux brandés, édition Note-First | Sélecteur GPS natif, zoom/pan libre | Thèmes, corbeille, notifications |

---

## 🚧 Version 5.0 — En cours de développement

> **Cycle actif — dernière livraison : v5.4.0**

La Version 5 ouvre une nouvelle ère pour JTR, après la clôture définitive et stable du cycle v4.x. Cette section est enrichie au fil du développement.

| Statut | Détail |
|--------|--------|
| 🏗️ **Jalon** | `versionName = "5.4.0"` · `versionCode = 16` · Room v17 |
| 🧱 **Fondations héritées** | Moteur tactile « Galerie » + dossiers récursifs (Room v16) consolidés en v4, étendus en v5 |
| ✅ **Livré (v5.0 → v5.1)** | TopAppBar harmonisée avec recherche intégrée (`JtrSearchableTopAppBar`), menu Tri/Affichage unifié, 3 modes de vue persistés (Liste/Grille/Détail), footer de sélection transformable à l'Accueil, déplacement de contacts sans dialogue, recadrage d'image refondu (EXIF, cadre déplaçable/redimensionnable), Drag & Drop grille/liste harmonisé (zone centrale = fusion) |
| 🎯 **Cap** | Capitaliser sur l'ergonomie tactile mature pour la prochaine génération de fonctionnalités |

### 🚀 Version 5.4.0 — Moteur de Proximité Actif & Unification Géographique

* **⚙️ Background Worker Optimisé (`ProximityCheckWorker`) :**
    * Refonte globale du cycle d'arrière-plan via un `CoroutineWorker` planifié toutes les 3 heures (politique `UPDATE` pour écraser à la volée les anciennes configurations 6h).
    * Collecte GPS ultra-légère par simple lecture du cache système via `FusedLocationProviderClient`, garantissant un impact batterie proche de zéro.
    * Optimisation des requêtes Room via `PersonDao.getProximityCandidates()` pour cibler exclusivement les profils actifs avec géolocalisation et notifications activées.
* **🚨 Algorithme de Seuil & Cooldown de 48h (Anti-Spam) :**
    * Recalibrage du rayon d'action à `≤ 10 km` (géodésie native WGS84 via `Location.distanceBetween`).
    * Migration de la base de données vers la **version 17** (`ALTER TABLE` sécurisé) pour intégrer la colonne `proximityNotifiedAt`.
    * Implémentation d'une sécurité d'idempotence stricte (`NOTIFY_COOLDOWN_MS = 48h`) pour empêcher le harcèlement de notifications pour un même contact présent dans la zone.
* **🔔 Système de Notification Interactif & Deep Linking (`JtrNotificationManager`) :**
    * Création du canal Material 3 à haute importance `jtr_proximity_alerts` (heads-up) et nettoyage automatique des anciens canaux obsolètes au démarrage.
    * Intégration d'un Deep Link avec transmission de l'identifiant via `EXTRA_PERSON_ID` permettant, au clic sur la notification, une ouverture instantanée sur la fiche `PersonDetailScreen`.
    * Unification logicielle : le `GeofenceBroadcastReceiver` (temps réel) et le Worker partagent désormais le même pipeline d'alerte et les mêmes règles de cooldown.
* **🔑 Tunnel de Permissions Explicite (Play Store Compliant) :**
    * Mise en conformité Android 10 à 14+ avec cinématique de demande asynchrone par étapes : `ACCESS_FINE_LOCATION` -> Dialogue explicatif matériel de la valeur de l'arrière-plan -> `ACCESS_BACKGROUND_LOCATION`. Maintien en veille silencieuse du Worker si la permission de fond est révoquée.

### 🚀 Version 5.3.4 — Navigation Bidirectionnelle, Grilles Responsives & Galerie Avancée

* **🧭 Navigation Contextuelle Profil ──► Catégorie :**
    * Refonte des ponts de navigation (`Navigation.kt`) pour encapsuler des paires réactives (ID, Nom).
    * Activation du clic sur les badges `SuggestionChip` au sein de `PersonDetailScreen` pour permettre une redirection instantanée vers le détail de la catégorie cible avec gestion native du BackStack.
* **🗂️ Hub d'Événements Fluide et Adaptatif :**
    * Restructuration de la mise en page de l'Accueil : le bandeau des événements imminents (fenêtre de 7 jours) est converti en en-tête dynamique interne à `PersonListContent`.
    * En mode Grille, calcul automatique de la surface via un span complet (`GridItemSpan(maxLineSpan)`) garantissant un alignement esthétique parfait avec les tuiles carrées. Le composant s'intègre au défilement global pour libérer l'espace écran.
* **📸 Rognage Mathématique Stabilisé & Sélecteur d'Albums "Style Instagram" :**
    * Correction de la physique du crop (`ImageCropDialog`) : ancrage matriciel du zoom pincé sur le centre géométrique du cadre de rognage (`offset' = d·(1−k) + offset·k`). Blocage strict des dérives et interdiction absolue pour l'image de découvrir le fond du cadre.
    * Remplacement du PhotoPicker plat par `rememberGalleryImagePicker` via une intention native `ACTION_PICK` indexée sur le `EXTERNAL_CONTENT_URI` du `MediaStore`. Offre un accès instantané à la structure par dossiers de l'appareil (Albums, WhatsApp, Caméra, Captures) avec repli sur `ACTION_GET_CONTENT`.
    * Préservation de l'architecture UDF : l'aller-retour via le registre de résultats d'activité isole complètement l'état du formulaire, garantissant zéro perte des textes pré-saisis.

### 🩹 Hotfix 5.3.3 — Intégrité des Données

* **Déduplication à l'importation native :** comparaison normalisée des numéros (espaces/tirets/parenthèses ignorés) et des emails (minuscules) — plus aucun doublon `phoneLines`/`emailLines` par contact.
* **Protection du formulaire au retour de la Map :** garde d'idempotence armée dans `EditPersonViewModel.loadPerson` — le formulaire n'est peuplé qu'une fois par cycle de vie ; le résultat de la carte fusionne uniquement ville + coordonnées, sans écraser notes, origine ou relations.

### ⚡ Version 5.3.2 — Flux de Sélection à Grande Échelle

* **Recherche contextuelle persistante en mode sélection (Accueil) :** loupe disponible pendant la sélection multiple ; la requête filtre l'affichage sans jamais toucher aux éléments cochés (`selectAll` additif, actions de masse sur la liste non filtrée).
* **Création de catégorie à la volée :** bouton fixe « ➕ Nouvelle catégorie » en tête du mode cible du déplacement — formulaire unifié (nom/couleur/image), association par lots sur `Dispatchers.IO`, redirection automatique vers la nouvelle catégorie.

### 🩹 Hotfix 5.3.1 — Épuration UI & Minimalisme

* **Header (TopAppBar) minimaliste :** Retrait des boutons d'accès rapide "Trier" et "Affichage" du header global pour désencombrer l'interface visuelle.
* **Centralisation des actions :** Réintégration stricte de toutes les options de Tri (`Sort`) et d'Affichage (`View mode` : Liste/Grille/Détail) au sein du menu principal "3 points" (`JtrOverflowMenu`) sur l'Accueil, les Catégories et les Dossiers.
* **Nettoyage du code :** Suppression définitive des composants UI obsolètes pour garantir l'absence de code mort, tout en maintenant intacte la persistance UDF (`StateFlow`).

### 🚀 Version 5.3.0 — Onboarding, Importation Native & Alignement UX (QoL)

* **🚀 Module d'Onboarding & Importation Native (`ContactsContract`) :**
    * Création d'un écran de bienvenue (`WelcomeScreen`) exclusif au premier lancement (`is_first_launch`).
    * Moteur d'importation asynchrone (`ContactsImporter`) sur `Dispatchers.IO` lisant la base native d'Android via une requête optimisée à plat (mimetypes groupés).
    * Extraction complète : Prénom, Nom (avec fallback), Téléphones/Emails multiples, Entreprise, Poste et Notes.
    * Duplication physique sécurisée de la `PHOTO_URI` native vers le stockage interne (`filesDir/photos/`) afin de prémunir l'application contre les révocations ultérieures de permissions.
    * Traitement par lots de 25 via `PersonDao.insertAll` avec indicateur de progression en temps réel (`LinearProgressIndicator`).
* **✍️ Perfectionnement des Formulaires & Validation :**
    * Gestion réactive du clavier virtuel : implémentation de `BringIntoViewRequester` sur le champ Notes pour un auto-scroll automatique et fluide à la saisie.
    * Sécurité des données : Blocage de la sauvegarde et levée d'une Snackbar corrective si le prénom obligatoire est manquant.
    * Guidage contextuel : Ajout d'un `supportingText` d'aide sous le champ Ville si l'adresse n'a pas fait l'objet d'un géocodage/validation via l'icône carte.
* **🗂️ Refonte de l'Architecture des Catégories & Menus :**
    * Alignement fonctionnel : Prise en charge de l'attribution d'image et du recadrage immédiat dès la création d'une catégorie (`CategoryFormDialog` unifié).
    * Actions internes complètes : Intégration des options "Modifier" et "Mettre à la corbeille" à l'intérieur même du détail des catégories et dossiers (avec gestion de la cascade pour éviter les éléments orphelins).
    * Clarté sémantique : Distinction stricte dans toute l'UI (5 langues) entre "Retirer" (rupture de liaison) et "Mettre à la corbeille" (suppression).
    * Accessibilité : Extraction des contrôles "Trier" (`JtrSortMenuButton`) et "Affichage" (`JtrViewMenuButton`) du menu global pour une exposition directe dans le Header.
    * Catégorie Virtuelle "Favoris" : Génération automatique d'une section dorée prioritaire dès le seuil de 2 profils favoris atteint, encapsulant un mode lecture filtré.
* **📍 Permissions de Localisation :**
    * Demande à la volée (`ACCESS_FINE_LOCATION`) lors de l'activation du toggle de proximité avec message de redirection explicite vers les paramètres système en cas de refus.

### 🚀 Version 5.2.0 — Partage Graphique, Moteur de Recherche Étendu & Module de Sauvegarde (.jtr)

* **📤 Système de Partage Hybride Contextuel (TEXTE / PNG / PDF) :**
    * Intégration d'un `ModalBottomSheet` de partage (`ShareFormatSheet`) accessible depuis le mode sélection de l'Accueil et des Catégories/Dossiers.
    * **Format Texte :** Fiches structurées et localisées (icônes Unicode, formats de dates et types de relations respectant la locale système).
    * **Format PNG :** Capture à la volée pixel-exacte de l'interface Compose via l'API réactive `rememberGraphicsLayer()`.
    * **Format PDF :** Génération vectorielle native via `PdfDocument` (grille A4 à 72 dpi, marges de 48 pt, calcul automatique des retours à la ligne et sauts de page dynamiques).
    * **Sécurité :** Déclaration d'un `FileProvider` isolé sur le répertoire `cache/share/` avec purge systématique des fichiers temporaires (`clearStaleExports`).
* **🔍 Moteur de Recherche Multi-Critères :**
    * Extension complète de la fonction de correspondance `Person.matchesSearch()` : le filtre de la TopAppBar scanne désormais le Nom, Prénom, Surnom, Entreprise, Poste, Département, Villes (Ville/Région/Origine), Notes et les relations (noms liés et types localisés).
    * Optimisation des performances via `.flowOn(Dispatchers.Default)` pour décharger entièrement le thread UI.
* **📅 Bandeau Dynamique des Événements à venir :**
    * Implémentation du composant réactif `UpcomingEventsBanner` (LazyRow) en haut de l'Accueil (masqué si vide ou en mode sélection).
    * Scan chronologique et algorithmique de la base (gestion du 29 février et du changement d'année) sur une fenêtre glissante de 0 à 7 jours.
    * Code couleur d'imminence (Primaire : aujourd'hui, Secondaire : ≤ 2 jours, Tertiaire : ≤ 7 jours).
* **💾 Module de Sauvegarde Intégral Autonome (`.jtr`) :**
    * Conception d'un gestionnaire de sauvegarde `BackupManager` compressant un fichier `backup.json` (Gson, incluant les profils archivés, liaisons N-N, catégories, dossiers) et un dossier `media/`.
    * Abstraction des chemins d'accès aux images locales via un système de jetons virtuels `jtr-media://` résolus dynamiquement à l'importation.
    * Sécurité renforcée : Validation anti-corruption de la structure du JSON avant écriture, insertion transactionnelle ordonnée (`OnConflictStrategy.REPLACE`) et protection stricte contre les failles d'arborescence de type *Zip Slip*.
    * Intégration système propre via les contrats d'activité `CreateDocument` et `GetContent`.
* **🌍 Internationalisation (i18n) :**
    * Ajout de 23 nouvelles clés de chaînes de caractères déclinées dans les 5 langues cibles (`en`/`fr`/`es`/`zh`/`ja`). Zéro dépendance tierce additionnelle.

---

## 🏆 Le Grand Bilan de la Version 4

> **L'Âge d'Or de l'Ergonomie Tactile**

La Version 4 restera celle qui a transformé JTR d'un carnet fonctionnel en une expérience tactile de référence. Au-delà des fonctionnalités sociales (icônes brandées, i18n, RGPD) détaillées dans les sous-versions ci-dessous, c'est le **moteur d'interaction directe** qui définit cet âge d'or. Six piliers techniques en forment l'héritage :

### 🎞️ Moteur Graphique *Flawless*

Le Drag & Drop des catégories et dossiers s'appuie exclusivement sur `Modifier.graphicsLayer` (translation X/Y de la tuile suivie, sans déclencher de mesure/layout) et `Modifier.animateItem()` (réagencement fluide des voisins). Résultat : un glissement **à 120 Hz**, sans recomposition parasite ni saccade, même sur des grilles densément peuplées.

### 🛡️ Système Anti-Crash & Découplage Synchrone

Le traitement du *Drop* ne s'exécute **jamais** à l'intérieur du callback tactile. L'action est figée dans un état scellé `DropAction` (`Merge` / `Insert` / `Reorder`), déposée dans un `pendingDrop`, puis traitée **hors du canal tactile** par un `LaunchedEffect` asynchrone. Tout le corps du `onDragEnd` est protégé par `try/catch/finally` et une garde de validité de layout (`isAttached`). L'`InputDispatcher` Android n'est ainsi jamais bloqué : **0 crash au drop** (élimination du fatal « Channel broken »).

### 📐 Géométrie de Précision Globale

Abandon des coordonnées locales à la tuile (faussées par les réagencements intermédiaires) au profit des **coordonnées Window absolues** via `positionInWindow()` et `localToWindow()`. La grille capture ses coordonnées avec `onGloballyPositioned`, et la collision doigt ↔ tuile utilise une **hitbox tolérante à 100 %** de la bounding box globale — la fusion s'allume dès l'entrée du doigt, à coup sûr.

### 🗂️ Structure de Dossiers Récursive (Migration Room v16)

Introduction de la colonne `parentGroupId: Long?` sur `category_groups` (`MIGRATION_15_16`), ouvrant une **hiérarchie infinie** : on crée des sous-groupes à l'intérieur des dossiers, par simple superposition de deux catégories au drop. Les compteurs (« N catégories, M sous-groupes ») sont **réactifs automatiquement** via `combine()` sur les Flows Room — aucune requête manuelle, aucun rafraîchissement explicite.

### ☑️ Mode Sélection Persistant (Style *Samsung Galerie*)

Une `TopAppBar` de sélection unifiée à la racine comme dans les dossiers : bouton **« Tout sélectionner »** (gauche), compteur central gérant explicitement l'état **« 0 sélectionné »**, et **« Annuler »** (droite). Le mode ne se referme plus tout seul quand la sélection se vide — seul « Annuler » en sort. La **réorganisation positionnelle** (Drag & Drop) est totalement **découplée** de la coche : on glisse n'importe quelle tuile, cochée ou non.

### 🌍 Internationalisation

Support **intégral et localisé en 5 langues** : Anglais (défaut), Français, Espagnol, Mandarin simplifié et Japonais (~195 clés chacune). Format strings positionnels (`%1$s`, `%5$d`…) pour réordonner librement les mots selon la grammaire de chaque langue, `@StringRes` pour les labels hors `@Composable`, et politique de confidentialité RGPD localisée.

---

## 📜 Historique des versions antérieures (v1 → v3)

Avant l'âge d'or tactile, JTR s'est construit par strates successives. Résumé succinct :

| Version | Essence | Apports clés |
|---------|---------|--------------|
| **v1.0** | *Le carnet* | CRUD de contacts basique, **stockage JSON**, liste simple |
| **v2.0** | *La persistance* | Migration vers **Room DB**, **photos** (Coil), favoris, recherche, corbeille (soft-delete), Navigation Compose, thèmes DataStore |
| **v3.0** | *Le contexte social* | Géocodage **Nominatim** + GPS, sélecteur **carte MapLibre** natif, **catégories Many-to-Many** (DB v6), **WorkManager** (rappels de proximité + anniversaires), notifications dual-canal, recherche accent-insensitive |

> Le détail exhaustif des sous-versions de la branche v4 (4.0 → 4.4) suit ci-dessous.

---

## Version 4.4

Phase majeure de refonte « zéro friction », d'unification des formulaires et de durcissement des permissions, suivie d'une passe de nettoyage technique. Le build est **sans aucun warning** dans `com.jtr.app`.

### 1. Refonte UX des formulaires — « Note-First » unifié

Création et édition partagent désormais un composant **unique** `ProfileFormFields`, garantissant une ergonomie 100 % identique.

- **Hiérarchie Note-First** : seuls 3 blocs sont visibles à l'ouverture — Prénom/Nom (discrets), grand champ **Notes**, et **Ce qu'il aime**.
- Tous les champs secondaires (Genre, Anniversaire, Ville, Origine, Téléphone, Email) sont rangés dans une section repliable **« Ajouter d'autres informations »**.
- Widgets de notification unifiés : un seul `Switch` Material 3 (coche interne `thumbContent`) partout, en remplacement du mélange Checkbox/Switch.
- **Focus clavier** corrigé : `imePadding()` + `ImeAction.Next` enchaînent les champs sans refermer le clavier.
- **Sélecteur de date** corrigé (`BirthdayPickerDialog` partagé) : changer l'année conserve instantanément le jour/mois (état scopé au dialogue, conversion midi-local ↔ minuit-UTC).

### 2. Catégories — double affichage Liste / Grille

- Bascule **Liste ⇄ Grille** via un bouton de la `TopAppBar` (icône dynamique `List` / `GridView`), persistée avec `rememberSaveable`.
- Mode **Grille** : tuiles « galerie » carrées (`CategoryGridTile`) — photo de couverture plein cadre, dégradé sombre et nom superposé.
- Menu d'actions **« 3 points »** (`MoreVert` → `DropdownMenu` Modifier / Supprimer) sur chaque élément, en complément du clic long conservé.

### 3. Proximité « zéro friction »

- **Suppression** de toute sélection manuelle de rayon (slider, paliers km) dans les Paramètres et les formulaires.
- Rayon désormais **fixe et automatique** : `JTRApplication.PROXIMITY_RADIUS_KM = 20f` (couvre une métropole et sa périphérie), lu par le `ProximityCheckWorker` et la synchronisation des geofences.
- Côté contact, une seule option claire : *« M'alerter si je passe à proximité de cette ville »*.

### 4. Permissions système robustes

- **Verrou de cohérence** : le toggle de proximité d'un contact est grisé (+ texte d'avertissement + Snackbar) si les notifications/proximité sont désactivées globalement ; la valeur ne peut **jamais** passer à `true` en base si le système l'interdit.
- **Flux localisation en 2 étapes** : permission au premier plan (pop-up système) → dialogue de rationale personnalisé → redirection vers les paramètres système pour l'arrière-plan (`ACCESS_BACKGROUND_LOCATION`).
- **Synchronisation dynamique** (`ON_RESUME` via `LifecycleEventObserver`) : si `ACCESS_FINE_LOCATION` **et** `ACCESS_BACKGROUND_LOCATION` ne sont pas réellement accordées au retour de l'app, le Switch retombe immédiatement à `false`, sans clignotement.
- **Galerie** : Photo Picker moderne (`ActivityResultContracts.PickVisualMedia`) — **aucune** permission de stockage requise. Recadrage maison via `ImageCropDialog` (pinch-zoom/pan, cercle ou rectangle).

### 5. Suppression de dette technique

- **Système de rappels périodiques** (2/3/6 mois) entièrement retiré : champs `Person`, `ContactReminderWorker`, canal de notification et chaînes i18n.
- **Journal d'interactions** retiré : entité `InteractionLog`, DAO, méthodes repository, section UI et chaînes associées.
- **Room v9 → v11** au fil de ces suppressions (`fallbackToDestructiveMigration`).
- Nettoyage final : imports morts purgés, icônes migrées vers `Icons.AutoMirrored.Filled.*`, dépréciations MapLibre encapsulées — **build 100 % vert**.

---

## Version 4.3

### 1. Simplification du système de thèmes

La fonctionnalité de couleur personnalisée — preset `CUSTOM`, color picker à 3 canaux et persistance des couleurs hex — a été **entièrement supprimée** pour réduire la complexité du code et de l'interface.

**Ce qui a été retiré :**

| Composant supprimé | Localisation |
|--------------------|-------------|
| Preset `CUSTOM` | `ThemePreset.kt` — l'enum passe de 7 à 6 entrées |
| `buildCustomColorScheme()` + helpers `lighten()`, `darken()`, `colorLuminance()` | `ThemePreset.kt` |
| `ColorPickerDialog` et `ColorSwatchPicker` | `SettingsScreen.kt` |
| Liste `colorPickerSwatches` (24 pastilles ARGB) | `SettingsScreen.kt` |
| `customColor`, `customSecondary`, `customTertiary` StateFlows + clés SharedPreferences | `ThemeViewModel.kt` |
| Paramètres `customColor` / `customSecondary` / `customTertiary` | `JTRTheme`, `JTRMainScaffold`, `MainActivity` |
| Chaînes `theme_name_custom`, `color_picker_title`, `color_role_*` | 5 fichiers `strings.xml` (EN/FR/ES/ZH/JA) |

**Résultat — `Theme.kt` réduit à une expression unique :**

```kotlin
val colorScheme = if (darkTheme) preset.toDarkColorScheme() else preset.toLightColorScheme()
```

**`ThemeViewModel` ne persiste plus que deux états :**

| Clé SharedPreferences | Type | Valeur par défaut |
|-----------------------|------|------------------|
| `dark_mode` | `Boolean` | `false` |
| `theme_preset` | `String` (nom enum) | `JTR_SIGNATURE` |

**Presets disponibles (6) :** JTR Signature · Azure · Emerald · Coral · Violet · Rose

Les noms localisés (`@StringRes labelRes`) et l'affichage des trois pastilles de prévisualisation dans `ThemePresetCard` sont conservés tels quels depuis v4.2.

---

## Version 4.2

### 1. Unification de l'UX d'édition

Le mode d'édition "terne" accessible via l'icône stylo (qui naviguait vers un `EditPersonScreen` séparé) a été supprimé. L'édition en place riche (double-tap) est désormais **le seul chemin d'édition**.

**Changements :**
- `onEditClick` retiré de la signature de `PersonDetailScreen` — le bouton stylo appelle directement `editVm.enterEditMode()`
- Route `EDIT_PERSON` et fonction `Routes.editPerson()` supprimées de `Navigation.kt`
- La gestion des résultats de la carte MapLibre (ville, latitude, longitude via `savedStateHandle`) a été ajoutée au composable `PERSON_DETAIL`, qui était auparavant uniquement disponible via l'écran d'édition séparé

---

### 2. Toggles de notification dans AddPersonScreen

Les champs `birthdateNotify` (notifier pour l'anniversaire) et `cityNotify` (notifier si à proximité) sont désormais disponibles **dès la création d'un contact**, alignant le formulaire avec l'écran d'édition.

**Ordre des champs unifié (création et édition) :**

```
Photo → Prénom → Nom → [Réseaux sociaux] → Genre
→ Date de naissance + [Toggle anniversaire]
→ Ville + Carte + [Toggle proximité] → Notes → Sauvegarder
```

`AddPersonViewModel` expose maintenant `birthdateNotify` et `cityNotify` comme `StateFlow<Boolean>` avec setters dédiés, et les inclut dans le constructeur `Person` lors de `savePerson()`.

---

### 3. Refonte du système de thèmes

| Changement | Détail |
|------------|--------|
| Nouveau thème par défaut | **JTR Signature** — fond anthracite `#121212`, textes blancs `#ECECEC` en mode sombre |
| Suppression | Palette `SLATE` retirée de l'enum `ThemePreset` |
| Nouveau preset | `CUSTOM` — active un sélecteur de couleur libre dans `SettingsScreen` |
| Nouvelle fonction | `buildCustomColorScheme(primary: Color, dark: Boolean): ColorScheme` — dérive `onPrimary` par luminance (seuil 0.55) |
| Persistance | `customColor: Long` persisté dans `SharedPreferences` via `ThemeViewModel.setCustomColor()` |

**Color picker (`ColorPickerDialog`) :**
- Grille `LazyVerticalGrid(GridCells.Fixed(6))` de 24 pastilles ARGB prédéfinies
- Champ `OutlinedTextField` pour saisie hexadécimale libre (`#RRGGBB`)
- La carte du preset `CUSTOM` dans `SettingsScreen` ouvre automatiquement le dialogue

---

### 4. Recherche et tri dans les Catégories

- `CategoriesScreen` : barre de recherche `OutlinedTextField` avec icône de recherche et bouton d'effacement
- `CategoryViewModel` : flux `categories` combiné via `combine(repo.getAllActive(), _searchQuery)` — tri A-Z systématique, filtrage insensible à la casse
- Aucun changement de DAO requis — le filtrage/tri est effectué en mémoire dans le ViewModel

---

### 5. Audit technique — Workers et tests

#### BirthdayCheckWorker
- **Correctif** : vérification des préférences globales `notifications_enabled` et `birthday_enabled` ajoutée en tête de `doWork()` — les notifications d'anniversaire étaient envoyées même si les notifications étaient globalement désactivées
- `person.birthdate!!` remplacé par `val millis = person.birthdate ?: return@forEach`

#### ProximityCheckWorker
- `person.cityLat!!` / `person.cityLng!!` remplacés par des extractions sûres `?: return@forEach` (le filtre `hasGeoCoordinates` était le garde existant, mais la surface de crash est éliminée)

#### PersonRepositoryTest — 6 nouveaux tests
```
birthdateNotify defaults to false
cityNotify defaults to false
birthdateNotify true with null birthdate excluded from birthday filter
birthdateNotify true with birthdate included in birthday filter
cityNotify true with coordinates eligible for proximity
cityNotify true without coordinates excluded from proximity
```

### 6. Japonais — 5ᵉ langue supportée

Fichiers ajoutés : `res/values-ja/strings.xml` (195 clés traduites) et `res/raw-ja/privacy_policy.html`. Aucun changement de code requis — la localisation Android système suffit.

---

## Version 4.0

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

## Version 4.1

### 1. Internationalisation complète (i18n) — 4 langues

L'application prend désormais en charge **5 langues** : Anglais (défaut), Français, Espagnol, Mandarin simplifié, et Japonais. Aucune chaîne de caractères n'est plus codée en dur dans le code Kotlin/Compose.

**Fichiers de ressources créés :**

| Fichier | Locale | Couverture |
|---------|--------|-----------|
| `res/values/strings.xml` | 🇬🇧 Anglais (défaut) | ~195 clés |
| `res/values-fr/strings.xml` | 🇫🇷 Français | ~195 clés |
| `res/values-es/strings.xml` | 🇪🇸 Espagnol | ~195 clés |
| `res/values-zh/strings.xml` | 🇨🇳 Mandarin simplifié | ~195 clés |
| `res/values-ja/strings.xml` | 🇯🇵 Japonais | ~195 clés |

**Refactoring UI — couche Compose :**

Tous les écrans ont été refactorisés pour utiliser `stringResource(R.string.key)` :

| Écran | Changements notables |
|-------|---------------------|
| `HomeScreen.kt` | `Locale.FRENCH` → `Locale.getDefault()` pour le formatage des dates |
| `AddPersonScreen.kt` | Genre, labels champs, dialogues |
| `EditPersonScreen.kt` | Idem AddPersonScreen |
| `PersonDetailScreen.kt` | ~35 chaînes (genre, anniversaire, ville, dialogues, liens sociaux) |
| `CategoriesScreen.kt` | Dialogues de suppression avec format args `%1$s`, `%1$d` |
| `CategoryDetailScreen.kt` | Recherche, sélection multiple, actions bas de page |
| `MapScreen.kt` | Titre, recherche, bouton de sauvegarde |
| `SettingsScreen.kt` | 25+ chaînes, `semantics { contentDescription }` calculé avant le bloc |
| `TrashScreen.kt` | ~30 chaînes, `joinToString(stringResource(R.string.separator_and))` dynamique |

**Refactoring hors-Compose :**

```kotlin
// JTRApplication.kt — canaux de notification
getString(R.string.notif_channel_proximity_name)
getString(R.string.notif_channel_birthday_name)

// BirthdayCheckWorker.kt
context.getString(R.string.notif_birthday_text, firstName)

// ProximityCheckWorker.kt — args positionnels pour réordonner les mots selon la langue
context.getString(R.string.notif_proximity_text, distanceKm, city, radiusKm, firstName, days)

// GeofenceBroadcastReceiver.kt
context.getString(R.string.notif_geofence_text, city, daysSince)
```

**Pattern `@StringRes Int` pour BottomNavItem :**

Hors du scope `@Composable`, les labels de la barre de navigation ne peuvent pas utiliser `stringResource()`. Solution : stocker l'identifiant de ressource et le résoudre au moment du rendu.

```kotlin
data class BottomNavItem(val route: String, val icon: ImageVector, @StringRes val labelRes: Int)

// Résolution dans le Composable
Text(stringResource(item.labelRes))
```

**Format strings positionnels :**

Les notifications contenant plusieurs arguments (distance, ville, prénom, jours) utilisent des paramètres positionnels (`%1$d`, `%2$s`, `%3$d`, `%4$s`, `%5$d`) permettant à chaque locale de réordonner librement les données dans la phrase.

```xml
<!-- Anglais -->
<string name="notif_proximity_text">You are %1$d km from %2$s … %4$s in %5$d days!</string>
<!-- Français — le nombre de jours (%5$d) précède le prénom (%4$s) -->
<string name="notif_proximity_text">Tu es à %1$d km de %2$s … %5$d jours sans contacter %4$s !</string>
```

---

### 2. Conformité RGPD — Politique de confidentialité intégrée

**Objectif :** rendre l'application conforme au RGPD et aux exigences de transparence du Google Play Store.

**Fichier `res/raw/privacy_policy.html` :**

Document HTML autonome couvrant les 8 points légaux obligatoires :
- Données collectées, stockage 100 % local (Room/SQLite)
- Utilisation de la localisation (Haversine, ProximityCheckWorker)
- Services tiers (Nominatim — seul tiers utilisé)
- Notifications locales (WorkManager, Geofencing)
- Droits de l'utilisateur (accès, modification, suppression, révocation des permissions)
- Protection des mineurs, contact

Le fichier intègre un système de thèmes CSS via la classe `html.dark` :

```css
:root          { --bg: #FFFFFF; --text-primary: #1A1A1A; ... }  /* clair */
html.dark      { --bg: #121212; --text-primary: #E0E0E0; ... }  /* sombre */
```

**Intégration dans `SettingsScreen.kt` :**

Nouvelle section "Légalité" entre "Données" et "À propos" :

```
Paramètres
 ├── Notifications
 ├── Apparence
 ├── Personnalisation
 ├── Données  (Corbeille)
 ├── Légalité  ← nouveau
 │    └── 🔒 Politique de confidentialité  →  ModalBottomSheet
 └── À propos
```

Le composable `PrivacyPolicySheet` charge le HTML dans une `WebView` en injectant dynamiquement la classe `dark` selon l'état `isDarkMode` courant, sans dépendance supplémentaire ni JavaScript :

```kotlin
val themed = if (isDarkMode)
    raw.replace("<html ", "<html class=\"dark\" ")
else raw
webView.loadDataWithBaseURL(null, themed, "text/html", "UTF-8", null)
```

La `WebView` est configurée de manière sécurisée : `javaScriptEnabled = false`, `domStorageEnabled = false`, `builtInZoomControls = false`.

---

## 🗂 Arborescence du projet

```
JTR_TP3/
├── app/
│   ├── build.gradle.kts                    # Dépendances, versionCode=8, minSdk=26
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
│       │       │       ├── Person.kt           # Entité Room — 19 champs (+ phone/email)
│       │       │       ├── Category.kt         # Entité Room — 7 champs (+ imagePath)
│       │       │       ├── PersonCategoryJoin.kt # Table de jointure Many-to-Many
│       │       │       └── SocialLinkEntity.kt # Entité Room — liens sociaux (1:N Person)
│       │       │
│       │       ├── data/
│       │       │   ├── local/
│       │       │   │   ├── AppDatabase.kt      # Singleton Room, version 11
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
│       │       │   │   ├── AddPersonScreen.kt      # Formulaire « Note-First » (ProfileFormFields)
│       │       │   │   ├── AddPersonViewModel.kt   # pendingLinks, phone/email, garde proximité
│       │       │   │   ├── PersonDetailScreen.kt   # Mini-carte, liens sociaux, édition inline
│       │       │   │   ├── EditPersonViewModel.kt  # socialLinks réactif, persistance photo différée
│       │       │   │   ├── ProfileFormFields.kt    # Formulaire PARTAGÉ Add/Edit + BirthdayPickerDialog
│       │       │   │   ├── ImageCropDialog.kt      # Recadrage maison (cercle/rectangle, pinch-zoom)
│       │       │   │   └── ImageCropShape.kt       # Enum CropShape (CIRCLE / RECTANGLE)
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
│       │           ├── ProximityCheckWorker.kt    # Haversine, 6h, rayon auto 20 km, 90j
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
│        └──────────────── AppDatabase (v11) ─────────┘          │
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
| **Préférences** | SharedPreferences | SDK | Toggles notifications (global / proximité / anniversaire) |
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
Entité Room centrale avec 19 champs couvrant l'identité, la géolocalisation, les coordonnées de contact rapide, les préférences de notification et les métadonnées de cycle de vie.

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
| `phoneNumber`, `email` | `String?` | Coordonnées de contact rapide |
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
Singleton Room (double-checked locking). Version actuelle : **11** (v10 : retrait des champs de rappel périodique de `Person` ; v11 : retrait de l'entité `InteractionLog`).

```kotlin
@Database(
    entities = [Person::class, Category::class, PersonCategoryJoin::class, SocialLinkEntity::class],
    version = 11,
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
.header("User-Agent", "JTR-App/4.1 (contact-manager Android)")
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
| `AddPersonViewModel` | `firstName`…`photoUri`, `firstNameError`, `cityLat`, **`pendingLinks`**, **`birthdateNotify`**, **`cityNotify`** | `onCityFromMap()`, `onPhotoSelected()`, `savePerson()`, **`addPendingLink()`**, **`removePendingLink()`**, **`onBirthdateNotifyChanged()`**, **`onCityNotifyChanged()`** |
| `EditPersonViewModel` | idem + `isLoading`, `isEditing`, **`socialLinks`** | `loadPerson()`, `commitAllEdits()`, `cancelEdit()`, **`addSocialLink()`**, **`removeSocialLink()`** |
| `CategoryViewModel` | `categories`, `personCountByCategory`, **`searchQuery`** | `addCategory()`, `updateCategory()`, `deleteCategoryWithCascade()`, **`setSearchQuery()`** |
| `CategoryDetailViewModel` | `category`, `persons`, `searchQuery`, `selectedIds` | `toggleSelection()`, `removeSelectedFromCategory()`, `assignPersonsToCategory()` |
| `SettingsViewModel` | `notificationsEnabled`, `proximityEnabled`, `birthdayEnabled` | `setNotificationsEnabled()`, `setProximityEnabled()`, `setBirthdayEnabled()` |
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
`CoroutineWorker` planifié toutes les **6 heures**. Rayon de détection **fixe et automatique** : `JTRApplication.PROXIMITY_RADIUS_KM = 20f` (plus aucune sélection manuelle depuis v4.4). Conditions de déclenchement d'une notification :

| Condition | Valeur |
|-----------|--------|
| Distance contact | `< 20 km` |
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
    Header: User-Agent: JTR-App/4.1 (contact-manager Android)
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

Tous les toggles (notifications globales, proximité, anniversaires) sont persistés dans `SharedPreferences` et lus par les workers à chaque exécution sans redémarrage. Le rayon de proximité n'est plus configurable depuis v4.4 — il est fixé à 20 km (`PROXIMITY_RADIUS_KM`).

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
    phoneNumber     TEXT,
    email           TEXT,
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

### Schéma — Table `social_links` *(ajoutée en v7)*

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
| 7 | Ajout table `social_links` (1:N Person, CASCADE delete) · Ajout `SocialLinkDao` |
| 8 | Ajout `phoneNumber` et `email` sur `persons` (actions rapides Appel/SMS/Email) |
| 9 | Ajout des champs de rappel périodique sur `persons` (système ensuite abandonné) |
| 10 | Retrait de `reminderIntervalMonths` / `lastReminderSentAt` de `persons` (rappels supprimés) |
| **11** | Retrait de l'entité `InteractionLog` (journal d'interactions abandonné) *(version actuelle)* |

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
        versionCode   = 9
        versionName   = "5.0.0"
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
daysSinceLastContact returns correct days        // 3 jours = 3 × 86_400_000 ms
fullName combines firstName and lastName
fullName uses only firstName when lastName is null
initials uses first letter of first and last name
hasGeoCoordinates returns false when coordinates missing
hasGeoCoordinates returns true when both coordinates set
softDelete calls dao with correct id
birthdateNotify defaults to false
cityNotify defaults to false
birthdateNotify true with null birthdate excluded from birthday filter
birthdateNotify true with birthdate included in birthday filter
cityNotify true with coordinates eligible for proximity
cityNotify true without coordinates excluded from proximity
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
| **v4.1** | **Internationalisation i18n** (EN/FR/ES/ZH, ~195 clés, format args positionnels, `@StringRes` BottomNavItem, `Locale.getDefault()`), **RGPD** (politique de confidentialité HTML dark/light, `PrivacyPolicySheet` WebView sans JS), `versionCode = 5` |
| **v4.2** | **Unification UX édition** (suppression route `EDIT_PERSON`, édition inline unique), **Toggles notif en création** (`birthdateNotify`/`cityNotify` dans `AddPersonScreen`), **Refonte thèmes** (`JTR_SIGNATURE` par défaut, suppression `SLATE`, preset `CUSTOM` + color picker libre, `buildCustomColorScheme`), **Recherche catégories** (barre + tri A-Z dans `CategoryViewModel`), **Audit workers** (guards globaux `BirthdayCheckWorker`, safe nulls `ProximityCheckWorker`), **Japonais** (5ᵉ langue, `values-ja/`), `versionCode = 6` |
| **v4.3** | **Simplification thèmes** — suppression du preset `CUSTOM`, de `buildCustomColorScheme`, du `ColorPickerDialog` et des 3 StateFlows de couleur dans `ThemeViewModel` ; 6 presets fixes uniquement (JTR Signature · Azure · Emerald · Coral · Violet · Rose) ; `Theme.kt` réduit à une expression unique ; nettoyage des chaînes `color_role_*` / `theme_name_custom` dans les 5 locales, `versionCode = 7` |
| **v4.4** | **Formulaires « Note-First » unifiés** (`ProfileFormFields` partagé Add/Edit, section repliable, `Switch` Material 3, `BirthdayPickerDialog`, `imePadding`), **Catégories Liste ⇄ Grille** (`CategoryGridTile`, menu `MoreVert`, persistance `rememberSaveable`), **Proximité « zéro friction »** (rayon fixe `PROXIMITY_RADIUS_KM = 20f`, suppression du slider et de `proximityRadiusKm`), **Permissions robustes** (verrou de cohérence, flux localisation 2 étapes, resync `ON_RESUME`, Photo Picker + `ImageCropDialog`), **Dette technique purgée** (rappels périodiques + journal d'interactions retirés, Room v9→v11, icônes `AutoMirrored`, build sans warning), `versionCode = 8` |
| **v4.5 / Bilan v4** | **Moteur d'ergonomie tactile** (l'âge d'or) : Drag & Drop `graphicsLayer` + `animateItem()` à 120 Hz, **anti-crash** par état scellé `DropAction` + `LaunchedEffect` (0 crash au drop), **géométrie globale** `localToWindow()` / hitbox 100 %, **dossiers récursifs** `parentGroupId` (**Room v16**) avec compteurs réactifs `combine`, **mode sélection persistant** style Galerie (Tout sélectionner / 0 sélectionné / Annuler, DnD découplé de la coche), **formulaire dynamique** style Contacts Google. `versionCode = 8` |
| **🚧 v5.0.0** | **Nouveau jalon majeur** — *En cours de développement.* Socle posé sur les fondations tactiles consolidées de la v4. `versionCode = 9` |

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

*JTR v5.0.0 — Kotlin · Jetpack Compose · MVVM · La v4 est close, la v5 commence.*
