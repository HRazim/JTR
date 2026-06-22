# 📱 JTR — Just To Remember

> Un carnet de contacts Android **100 % local** qui se souvient du contexte humain de vos relations. · Version **7.1.10**

JTR (*Just To Remember*) va au-delà du répertoire téléphonique : il garde une **mémoire sociale** de chaque personne (goûts, anniversaires, ville, notes, réseaux sociaux, relations) et vous rappelle proactivement les dates importantes ainsi que les contacts dont vous êtes physiquement proche.

---

## Sommaire

1. [Présentation](#présentation)
2. [Fonctionnalités](#fonctionnalités)
3. [Pile technique & architecture](#pile-technique--architecture)
4. [Installation & build](#installation--build)
5. [Structure du projet](#structure-du-projet)
6. [Historique des versions](#historique-des-versions)
7. [Confidentialité](#confidentialité)
8. [Licence](#licence)

---

## Présentation

- **100 % local, privé par conception** : aucune donnée n'est envoyée à un serveur, **aucun compte**, **aucune publicité**, aucun traceur. Tout vit sur l'appareil (base SQLite via Room, photos dans le stockage interne).
- **Seules sorties réseau, sans clé API et sans donnée personnelle** : le **géocodage Nominatim** (nom de ville → coordonnées) et l'affichage des fonds de carte **OpenFreeMap** (OpenStreetMap).
- **Internationalisé** : 13 langues, dont l'**arabe en RTL complet**.
- **`minSdk 26` (Android 8.0+)**, cible Android 15 (`targetSdk 35`).

---

## Fonctionnalités

### Contacts & profils
- Profil riche façon « Contacts Google » : téléphones, e-mails, dates et relations **multiples** (listes dynamiques), détails de nom (préfixe, second prénom, suffixe, phonétique, surnom) et informations professionnelles.
- **Liens de réseaux sociaux** avec icônes aux couleurs de marque, ouvrant l'application native ou le navigateur.
- **Relations miroirs** synchronisées automatiquement (ajouter « frère » crée la relation réciproque) ; la propagation d'un changement de nom est réactive.
- Édition **en place** (double-tap), visionneuse photo plein écran (zoom/pan, double-tap ancré).

### Catégories & organisation
- Catégories **plusieurs-à-plusieurs** et **dossiers récursifs** (sous-groupes).
- Favoris, **glisser-déposer** (fusion / déplacement), mode sélection multiple « façon Galerie ».
- **3 modes d'affichage** persistés : Liste · Détails · Grille.
- **Tri à deux axes** : critère (Nom · Date de modification · Date de création) + sens **croissant/décroissant**, partagé par l'accueil et les catégories ; les favoris restent épinglés en tête.

### Rappels (notifications locales)
- **Anniversaires** et **dates importantes** : notification *heads-up* avec un **délai de rappel configurable par date** (le jour J · 10 min · 1 h · 1 jour · 1 semaine · personnalisé), déclenchée à la minute près via une **alarme exacte** ancrée à minuit du jour J, réarmée à la sauvegarde et au redémarrage.
- **Moteur de proximité** : alerte lorsqu'on passe près d'un contact (Worker périodique + geofencing, rayon ~10 km, anti-spam 48 h, ouverture directe de la fiche au tap).

### Sécurité locale (optionnelle)
- **Verrou par schéma** 3×3, **déverrouillage biométrique** (avec repli sur l'identifiant de l'appareil) et **code de secours** de récupération.
- Seules des **empreintes salées (PBKDF2)** du schéma et du code sont conservées, **chiffrées sur l'appareil** (EncryptedSharedPreferences / Android Keystore) — jamais en clair, jamais transmises. Aucune donnée biométrique n'est lue par l'app (gérée par le système).
- Écran de verrouillage au démarrage à froid et au retour d'arrière-plan, avec temporisation anti-force-brute.

### Personnalisation
- **6 thèmes** (JTR · Azure · Emerald · Coral · Violet · Rose) + **mode sombre**, surfaces accordées à la palette.
- **Taille de police** réglable (plafonnée) et **langue de l'application** indépendante du système.

### Médias & données
- **Sélecteur de photos intégré** (lecture du MediaStore par albums, style galerie), sans quitter l'app, avec **recadrage** (cercle/rectangle).
- **Sauvegarde / restauration** complète dans un fichier `.jtr` (profils, catégories, dossiers, liens, photos) via un partage de fichier sécurisé.
- **Carte MapLibre** native pour choisir/valider une ville, avec géocodage Nominatim.

---

## Pile technique & architecture

**Architecture MVVM + Repository, UDF strict** : les `ViewModel` exposent des `StateFlow` en lecture seule, observés par les `@Composable` via `collectAsStateWithLifecycle()` ; les `Flow` Room rendent l'interface réactive de bout en bout.

```
UI (Jetpack Compose) ─► ViewModel (StateFlow) ─► Repository ─► Room DAO / API
        ▲                                                          │
        └──────────────── Flow<T> réactif ◄────────────────────────┘
```

| Domaine | Technologies |
|--------|--------------|
| Langage / SDK | Kotlin 2.1.0 · JVM 17 · `minSdk 26` · `compile/targetSdk 35` · `versionName 7.1.10` / `versionCode 64` |
| UI | Jetpack Compose (BOM 2024.12.01) · Material 3 · Navigation Compose · listes réordonnables (`sh.calvin.reorderable` 2.4.3) |
| Persistance | Room 2.6.1 (via **KSP**) — base **v21** · DataStore · EncryptedSharedPreferences |
| Tâches de fond | WorkManager · `AlarmManager` (alarmes exactes) · `ProcessLifecycleOwner` |
| Carte & réseau | MapLibre 11.5 · Retrofit / OkHttp · kotlinx.serialization (Nominatim) |
| Localisation | Play Services Location (FusedLocation + Geofencing) |
| Sécurité | `androidx.security:security-crypto` · `androidx.biometric` |
| Images | Coil |
| Tests | JUnit · MockK · Turbine · Truth · `room-testing` (test de migration) |

> ⚠️ La base Room est en **v21**. Toutes les évolutions de schéma sont couvertes par des **migrations explicites** (`MIGRATION_11_12` → `MIGRATION_20_21`, non destructives) ; `fallbackToDestructiveMigration()` n'est qu'un **filet de sécurité jamais atteint**. Toute future évolution **doit** fournir sa `Migration` explicite (sinon perte de données utilisateur).

---

## Installation & build

**Prérequis** : Android Studio (récent), **JDK 17**, SDK Android 35.

```bash
git clone <repo>
cd JTR_TP3
./gradlew :app:assembleDebug      # APK debug
```

Ou ouvrir le dossier dans Android Studio puis **Run ▶** sur un appareil/émulateur **Android 8.0+**. Aucune clé API ni secret à configurer (Nominatim et OpenFreeMap sont sans clé).

```bash
./gradlew test                    # tests unitaires JVM
./gradlew connectedAndroidTest    # tests instrumentés Room (appareil/émulateur requis)
```

---

## Structure du projet

```
app/src/main/java/com/jtr/app/
├── domain/model/      # Entités Room (Person, Category, CategoryGroup, joins, SocialLink)
├── data/
│   ├── local/         # AppDatabase (v21) + DAOs
│   ├── remote/        # Nominatim (Retrofit)
│   ├── repository/    # Person / Category / Geocoding…
│   ├── backup/        # Sauvegarde & restauration .jtr
│   └── contacts/      # Import des contacts natifs
├── ui/                # navigation · home · person · category · map · settings ·
│                      # theme · trash · welcome · components · backup · share
├── security/          # Verrou : SecurityManager, PatternLockView, biométrie, écrans
├── worker/            # ReminderScheduler + AlarmReceiver/BootReceiver · ImportantDateCheckWorker · ProximityCheckWorker · Geofence receiver
└── utils/             # LocaleManager, LocationUtils, helpers
```

---

## Historique des versions

> Faits marquants par cycle. Versions taguées : `v5.5.1`, `v6.3.1`, `v7.0.7`, `v7.1.4`, `v7.1.8`, `v7.1.10`. Jalon courant : **`versionName 7.1.10` · `versionCode 64` · Room v21**.

### Cycle v7.1.x — Robustesse de la saisie, intégrité & sécurité des données

- **v7.1.0 — Dates indépendantes de la langue.** Les dates importantes sont stockées en **ISO `yyyy-MM-dd`** (fin du stockage en chiffres ordonnés par locale, qui cassait la validation au changement de langue). **Migration *data-only* `MIGRATION_20_21`** (Room v21) non destructive, désambiguïsation **sans jamais fabriquer de fausse date** ; round-trip ISO dans les archives `.jtr`.
- **v7.1.1 — Auto-scroll des notes.** La ligne en cours de saisie suit le curseur et reste au-dessus du clavier (`BringIntoViewRequester`).
- **v7.1.2 — Visualiseur photo : fermeture par glissement « façon Instagram ».** La photo suit le doigt, le fond s'estompe, léger rétrécissement, seuils de distance/vélocité et retour élastique ; désactivé lorsque la photo est zoomée.
- **v7.1.3 — Champ vide au focus corrigé.** À l'ouverture du clavier, le champ note **monte en synchronisation** avec l'inset IME (`collectLatest` + `bringIntoView` animé) ; suppression de la machinerie fragile pilotée image par image.
- **v7.1.4 — Sauvegarde automatique (*auto-save*).** Écriture débouncée ; flush garanti à la sortie via un **scope applicatif survivant** et **`ON_STOP`** (`ProcessLifecycleOwner`) ; brouillon à **`draftId` stable** (jamais de contact fantôme) ; la croix ✗ d'édition devient une **flèche retour ←**. Le bouton Enregistrer subsiste (flush immédiat).
- **v7.1.5 — Gestion des catégories depuis la fiche.** Menu débordant → libellé réactif (« Ajouter à une catégorie » / « Gérer les catégories ») → dialogue Material 3 multi-sélection (hiérarchie dossiers/sous-groupes) + création rapide réutilisant `AddCategoryDialog`. Réutilise la table de jointure `PersonCategoryJoin` ; persistance réactive (Flow Room).
- **v7.1.6 — Relations par identifiant stable.** `DynamicLine.linkedPersonId` met fin à l'ambiguïté des **homonymes** (« Ryan » vs « Ryan Sugarry ») ; navigation, relations miroirs, renommage et `.jtr` reposent désormais sur l'**id** ; badge « à vérifier » pour une relation héritée ambiguë. **Sans migration** (relations stockées en JSON).
- **v7.1.7 — Sécurité des données : sauvegarde OS désactivée.** `android:allowBackup="false"` + `dataExtractionRules` (exclusion `<cloud-backup>` **et** `<device-transfer>`) : la base Room ne peut plus être **écrasée par un instantané périmé** lors d'une réinstallation ou d'un transfert d'appareil. L'**export `.jtr` manuel** devient l'unique source de vérité.
- **v7.1.8 — Restauration `.jtr` réparée.** Une archive antérieure aux sections de notes désérialisait `noteSections` à `null` sur un type non-nullable → `Person.copy()` plantait (message générique). Correctif : **coercition** des champs hérités à l'import + **erreurs typées** (`RestoreError` : illisible · pas une archive · version incompatible · corrompu · écriture).
- **v7.1.9 — Moteur de recherche global unifié.** **Normalisation** (trim, espaces multiples, casse, diacritiques latins) + **tokenisation multi-mots** (ET sur les tokens, OU sur les champs, en sous-chaîne) sur un **blob multi-champs** ; **même logique partout** (accueil, catégories, sous-groupes, relations, sélecteurs) ; suppression de deux moteurs SQL morts ; debounce 250 ms + filtrage sur `Dispatchers.Default`. Corrige « Nathan Jamel », l'espace final, la casse et les accents.
- **v7.1.10 — Feedback du déplacement des sections de notes.** Élévation + léger *scale* (~1.03) + **retour haptique `LongPress`** à l'entrée du glisser, **lift tonal** (visibilité en thème sombre) et **cible de suppression** non ambiguë (liseré primaire jusqu'à la confirmation).

> 🛡️ **Intégrité des données** — l'incident de perte de données est clos : la base est désormais à la fois **protégée de tout écrasement par l'OS** (v7.1.7) **et** ses sauvegardes `.jtr` sont de nouveau **réellement restaurables** (v7.1.8).

### Évolution par version

| Version | Faits marquants |
|---------|-----------------|
| v5.5.1 | Sélecteur galerie « façon Instagram », notifications d'anniversaire, focus clavier, unification visuelle. |
| v6.0 – v6.2 | Permissions 100 % côté OS, recadrage avancé, **sécurité locale** (verrou par schéma + biométrie + code de secours), **arabe / RTL complet**, tri à deux axes, contraste du mode sombre. |
| v6.3.1 | Montée de version et **réécriture complète** du README (concis, à jour). |
| v7.0.0 – v7.0.7 | **Rappels à délai configurable** (alarmes exactes), refonte du style des profils, **sections de notes personnalisables** + réordonnancement par glisser, sélecteur de rappel en molette, footer au niveau écran. |
| v7.1.0 | Dates en **ISO** indépendantes de la langue (`MIGRATION_20_21`, Room v21). |
| v7.1.1 | Auto-scroll : la ligne saisie suit le clavier. |
| v7.1.2 | Visualiseur photo : fermeture par glissement « façon Instagram ». |
| v7.1.3 | Correctif du champ vide au focus (montée synchronisée avec le clavier). |
| v7.1.4 | **Sauvegarde automatique** des profils et notes (debounce + flush à la sortie). |
| v7.1.5 | Gestion des catégories depuis la fiche d'une personne. |
| v7.1.6 | **Relations par identifiant stable** (fin de l'ambiguïté des homonymes). |
| v7.1.7 | **Sauvegarde OS désactivée** : la base ne peut plus être écrasée. |
| v7.1.8 | **Restauration `.jtr` réparée** + erreurs typées. |
| v7.1.9 | **Moteur de recherche global unifié** (multi-mots, accents, espaces). |
| v7.1.10 | Feedback visuel/haptique du déplacement des sections de notes. |

---

## Confidentialité

JTR est **local-first** : vos données restent sur votre appareil et sont **définitivement supprimées** à la désinstallation. La base est en outre **exclue de toute sauvegarde ou transfert du système** (`allowBackup="false"` + règles d'extraction de données) : aucun instantané de l'OS ne peut écraser vos données — l'**export `.jtr` manuel** est l'unique moyen de migrer d'un appareil à l'autre. Les seules requêtes réseau concernent le géocodage de ville (Nominatim) et les fonds de carte (OpenFreeMap), **sans transmettre de donnée personnelle**. Les permissions (notifications, localisation, photos) sont demandées **à l'usage** et révocables à tout moment. La politique complète est consultable dans l'app : **Paramètres → Politique de confidentialité**.

---

## Licence

Projet **personnel** à vocation d'apprentissage, non publié sous licence open source (tous droits réservés).

Données cartographiques © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright) (ODbL) ; tuiles servies par [OpenFreeMap](https://openfreemap.org).

---

<sub>**JTR v7.1.10** · Room v21 · `versionCode 64` — carnet de contacts 100 % local.</sub>
