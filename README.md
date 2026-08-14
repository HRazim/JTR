# 📱 JTR — Just To Remember

> Un carnet de contacts Android **100 % local** qui se souvient du contexte humain de vos relations. · Version **7.1.65**

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

- **100 % local, privé par conception** : vos **données de contact ne quittent jamais l'appareil**, **aucun compte**, **aucune publicité**, aucun traceur, aucun SDK d'analyse. Tout vit sur l'appareil (base SQLite via Room, photos dans le stockage interne).
- **Seules sorties réseau, sans clé API** : le **géocodage Nominatim** (nom de ville → coordonnées) et les fonds de carte **OpenFreeMap** (OpenStreetMap) — ils reçoivent un nom de ville / des coordonnées, la zone affichée et votre adresse IP, **jamais vos données de contact**.
- **Internationalisé** : 13 langues **réellement appliquées** (dont l'**indonésien**, corrigé en v7.1.65), avec l'**arabe en RTL complet** — toutes **embarquées dans l'APK de base** (`bundle { language { enableSplit = false } }`) pour une **bascule de langue in-app fiable sur App Bundle**.
- **`minSdk 26` (Android 8.0+)**, cible Android 16 (`targetSdk 36`, conformité Google Play).

---

## Fonctionnalités

### Contacts & profils
- Profil riche façon « Contacts Google » : téléphones, e-mails, dates et relations **multiples** (listes dynamiques), détails de nom (préfixe, second prénom, suffixe, phonétique, surnom) et informations professionnelles.
- **Liens de réseaux sociaux** avec icônes aux couleurs de marque, ouvrant l'application native ou le navigateur.
- **Relations miroirs** synchronisées automatiquement (ajouter « frère » crée la relation réciproque, avec le **rôle inverse correct** — « mère » ↦ « fils/fille » selon le contact) ; la propagation d'un changement de nom est réactive.
- **Catalogue de 25 rôles de relation** (+ « Personnalisé »), groupés **Famille / Pro / Social** dans le sélecteur.
- Édition **en place** (double-tap), visionneuse photo plein écran (zoom/pan, double-tap ancré).
- **Sections « plus d'informations » repliables** : fermées par défaut, leur état est **mémorisé par type de section** d'un contact à l'autre, et le bouton **défile en douceur** jusqu'au contenu qu'il vient d'ouvrir ou de refermer.
- **Recherche globale unifiée** : un seul moteur tokenisé (multi-mots, insensible à la casse et aux accents, sûr en arabe) sur tous les écrans — accueil, catégories, dossiers, relations, sélecteurs.

### Import des contacts du téléphone
- **Import depuis les Paramètres** (permission `READ_CONTACTS`) : les contacts importés **restent locaux**, rien n'est renvoyé nulle part.
- Reprend **tout ce que l'agenda Android expose** : noms complets (préfixe, second prénom, suffixe, phonétique, **surnom**), **poste / entreprise / département**, photo **pleine résolution**, **types et libellés** des téléphones et e-mails, **événements** (y compris les dates **sans année**), **notes → sections**, site web → liens sociaux, adresse postale → ville + section, et **relations** (résolues en second passage vers l'identifiant du contact lié).
- **Aperçu par contact** avant import (chips des données disponibles) et **dédoublonnage** par téléphone/e-mail : **Ignorer · Mettre à jour · Importer quand même** (une mise à jour n'écrase jamais une valeur existante par du vide).

### Catégories & organisation
- Catégories **plusieurs-à-plusieurs** et **dossiers récursifs** (sous-groupes), avec **couleur** et **image de couverture** (visionneuse plein écran, remplacement / retrait) et **dates de création / modification**.
- Favoris, **glisser-déposer** (fusion / déplacement), mode sélection multiple « façon Galerie ».
- **Assignation depuis la fiche** d'un contact : feuille montante avec **recherche repliable**, création rapide, compteur de sélection et **dossiers repliables** (tout replié à l'ouverture, la recherche court-circuitant le repli).
- **3 modes d'affichage** persistés : Liste · Détails · Grille.
- **Tri à deux axes** : critère (Nom · Date de modification · Date de création) + sens **croissant/décroissant**, partagé par l'accueil et les catégories ; les favoris restent épinglés en tête.

### Rappels (notifications locales)
- **Anniversaires** et **dates importantes** : notification *heads-up* avec un **délai de rappel configurable par date** (le jour J · 10 min · 1 h · 1 jour · 1 semaine · personnalisé), déclenchée à la minute près via une **alarme exacte** ancrée à minuit du jour J, réarmée à la sauvegarde et au redémarrage.
- **Plafond de 400 alarmes exactes** armées simultanément (Android 13+ en limite 500) : ce sont les **échéances les plus proches** qui sont armées, les suivantes le sont **au fur et à mesure** — aucune date n'est perdue.
- **Moteur de proximité** : alerte lorsqu'on passe près d'un contact (Worker périodique + geofencing, rayon ~10 km, anti-spam 48 h, ouverture directe de la fiche au tap).

### Sécurité locale (optionnelle)
- **Verrou par schéma** 3×3, **déverrouillage biométrique** (avec repli sur l'identifiant de l'appareil) et **code de secours** de récupération.
- Seules des **empreintes salées (PBKDF2)** du schéma et du code sont conservées, **chiffrées sur l'appareil** (EncryptedSharedPreferences / Android Keystore) — jamais en clair, jamais transmises. Aucune donnée biométrique n'est lue par l'app (gérée par le système).
- Écran de verrouillage au démarrage à froid et au retour d'arrière-plan, avec temporisation anti-force-brute.

### Personnalisation
- **6 thèmes** (JTR · Azure · Emerald · Coral · Violet · Rose) + **mode sombre**, **cohérence complète sur les 12 schémas** (6 presets × clair/sombre) : surfaces **et accents** (`tertiary*`, `outline*`) sont **dérivés du preset choisi** — feuilles, dialogues, menus, cartes et bordures ne retombent plus sur la palette Material par défaut.
- **Taille de police** réglable (plafonnée) et **langue de l'application** indépendante du système (sélecteur borné et défilable : les 13 langues sont toutes atteignables).

### Accessibilité
- Cibles tactiles **≥ 48 dp** et `contentDescription` localisé sur les boutons-icônes isolés.
- **Lignes à interrupteur annoncées par TalkBack avec leur libellé** : chaque réglage forme un **seul élément *toggleable*** (« Notifications, interrupteur, activé ») au lieu d'un texte muet suivi d'un interrupteur anonyme.

### Médias & données
- **Sélecteur de photos intégré** (lecture du MediaStore par albums, style galerie), sans quitter l'app, avec **recadrage** (cercle/rectangle).
- **Sauvegarde / restauration** complète dans un fichier `.jtr` (profils, catégories, dossiers, liens, photos) via un partage de fichier sécurisé.
- **Partage d'un ou plusieurs profils** en **texte**, **image PNG** ou **document PDF** (fiches complètes, photo incrustée), via un `FileProvider` à URI révocable et des fichiers temporaires purgés à chaque partage.
- **Corbeille** : les contacts supprimés y restent **30 jours** (restauration unitaire ou globale) avant purge automatique.
- **Carte MapLibre** native pour choisir/valider une ville, avec géocodage Nominatim (sélection de résultat déterministe, adresses complètes).

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
| Langage / SDK | Kotlin 2.1.0 · JVM 17 · AGP 8.13.2 · `minSdk 26` · `compile/targetSdk 36` · `versionName 7.1.65` / `versionCode 119` |
| UI | Jetpack Compose (BOM 2024.12.01) · Material 3 · Navigation Compose · listes réordonnables (`sh.calvin.reorderable` 2.4.3) |
| Persistance | Room 2.6.1 (via **KSP**) — base **v22** · DataStore · EncryptedSharedPreferences |
| Tâches de fond | WorkManager · `AlarmManager` (alarmes exactes) · `ProcessLifecycleOwner` |
| Carte & réseau | MapLibre 11.5 · Retrofit / OkHttp · kotlinx.serialization (Nominatim) |
| Localisation | Play Services Location (FusedLocation + Geofencing) |
| Sécurité | `androidx.security:security-crypto` · `androidx.biometric` |
| Images | Coil |
| Tests | JUnit · MockK · Turbine · Truth · `room-testing` (**les 11 migrations 11→22 couvertes**) |

> ⚠️ La base Room est en **v22**. Toutes les évolutions de schéma sont couvertes par des **migrations explicites** (`MIGRATION_11_12` → `MIGRATION_21_22`, non destructives), et **les 11 sont couvertes par des tests instrumentés**. Depuis la **v7.1.63**, il n'y a **plus aucun fallback destructif** : un chemin de migration absent fait **échouer l'ouverture de la base** (crash au démarrage, donnée intacte) au lieu de l'effacer silencieusement. Toute future évolution **doit** donc fournir sa `Migration` explicite — sans elle, l'application devient indémarrable.

---

## Installation & build

**Prérequis** : Android Studio (récent), **JDK 17**, SDK Android 36.

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
│   ├── local/         # AppDatabase (v22) + DAOs
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

> Faits marquants par cycle. Versions taguées : `v5.5.1`, `v6.3.1`, `v7.0.7`, `v7.1.4`, `v7.1.8`, `v7.1.10`, `v7.1.15`, `v7.1.20`, `v7.1.24`, `v7.1.26`, `v7.1.27`, `v7.1.65`. Jalon courant : **`versionName 7.1.65` · `versionCode 119` · `targetSdk 36` · Room v22**.

### Cycle v7.1.x — Robustesse de la saisie, intégrité & sécurité des données, polish UX & packaging

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
- **v7.1.11 — Insets unifiés (clavier & paysage).** Clavier : **source unique** de recentrage du champ focalisé (motif IME-synchronisé `snapshotFlow{ime} + collectLatest + bringIntoView`), avec **suppression des variantes one-shot** → fin du **« vide fantôme »**. Paysage : insets **horizontaux** partagés (`systemBars.only(Horizontal)`) → plus aucun contenu **sous la barre de navigation latérale** en mode 3 boutons.
- **v7.1.12 — Lisibilité des barres système.** L'apparence des icônes (barre d'état / barre de navigation) est désormais **pilotée par le thème in-app** (`DisposableEffect(isDarkMode)` → `isAppearanceLight*Bars = !isDarkMode`) → fin du **sombre-sur-sombre** ; réactive au basculement clair/sombre et **indépendante des 6 presets**.
- **v7.1.13 — Suppression d'une section de notes par glissement (*swipe-to-delete*).** Geste **horizontal** (seuil 40 %) → **dialogue de confirmation + annulation (*undo*)** réutilisés du footer ; **coexistence sans conflit** avec le glisser-déposer vertical (gating `!isDragging && !cardFocused`), fond `errorContainer` et corbeille **en miroir RTL**.
- **v7.1.14 — Fiabilité multilingue sur App Bundle.** `bundle { language { enableSplit = false } }` → les **13 langues** sont livrées dans l'**APK de base** ; la bascule de langue in-app **ne retombe plus en anglais** sur une distribution App Bundle (où les ressources de langue sont sinon scindées et téléchargées à la demande).
- **v7.1.15 — Recherche des notes & tests verts.** Le **titre et le contenu** des sections de notes sont désormais **indexés** par le moteur de recherche unifié → **trouvables partout** (accueil, catégories, sous-groupes, relations, sélecteurs). **Suite de tests unitaires 100 % verte** : le test `softDelete` est réécrit en **test de délégation réelle** (vérifie que le `Repository` délègue bien au DAO).
- **v7.1.16 — Accessibilité.** Cibles tactiles **≥ 48 dp** (pastilles de couleur en `FlowRow`), `contentDescription` localisé sur les boutons-icônes isolés, `performClick()` sur les zones gestuelles interop (carte MapLibre, WebView de la politique) → avertissement lint `ClickableViewAccessibility` ramené à **zéro**.
- **v7.1.17 — Icône de marque Snapchat.** Vector drawable **Snapchat** (fantôme jaune, couleurs de marque) ajouté au mécanisme de détection des liens sociaux.
- **v7.1.18 — Détection sociale unifiée (collision « t.co »).** La détection par sous-chaîne prenait toute URL `snapchat.com` pour X (« snapcha**t.co**m »). Corrigé **à la source** : détection par **host avec frontière de domaine** (`SocialPlatform.detect`), **source unique** pour l'icône **et** le libellé ; l'aperçu du dialogue d'ajout affiche enfin l'icône + « Snapchat ».
- **v7.1.19 — Tokens de couleur.** Deux couleurs de repli bleues codées en dur remplacées par `rememberCategoryColor` (repli `colorScheme.primary`) → adaptatif clair/sombre et cohérent sur les 6 presets.
- **v7.1.20 — Pluralisation CLDR.** 12 chaînes de comptage converties en `<plurals>` dans les **13 langues**, avec les catégories CLDR requises par langue (arabe : 6 formes ; russe : 4 ; etc.) ; lecture via `pluralStringResource` / `getQuantityString`.
- **v7.1.21 — Titre d'accueil non traduit (es).** « JTR Contacts » → « JTR Contactos » ; **audit** des chaînes identiques à l'anglais (présentes mais non traduites) sur les 13 locales — que `MissingTranslation` ne détecte pas.
- **v7.1.22 — i18n : compte à rebours & genre.** Compte à rebours d'événement conforme aux conventions locales (fr « J-%d », es/ko « D-%d »…) ; abréviation « non-binaire » localisée (es « No bin. »), les langues sans forme courte évidente étant signalées plutôt que devinées.
- **v7.1.23 — Conformité Google Play : API 36.** `compileSdk` / `targetSdk` portés à **36 (Android 16)** ; l'edge-to-edge étant déjà en place depuis la cible 35, **aucune régression** (insets clavier/paysage, barres système). `minSdk 26` inchangé.
- **v7.1.24 — Visionneuse photo : coins arrondis dynamiques.** Pendant le glissement de fermeture, les coins de la photo s'**arrondissent progressivement** (0 → 24 dp) avec un léger **rétrécissement**, **synchronisés** avec le fondu du fond car **dérivés de la même progression de drag** (lus en phase de dessin → fluide, sans recomposition ; aucun coin parasite quand la photo est zoomée).
- **v7.1.25 — Rappels de proximité : flux *par contact* aligné sur Android 11+.** Activer un rappel de proximité depuis une fiche redirige désormais vers les **Réglages système** (où « Toujours autoriser » s'accorde) au lieu d'une demande runtime ignorée (no-op) ; la logique d'octroi en arrière-plan est factorisée dans un **helper partagé** (`LocationUtils.requestBackgroundLocation`), commun au flux par contact et au flux global → plus de divergence possible. **Politique de confidentialité in-app corrigée** (mention des sorties réseau Nominatim / OpenFreeMap et de la localisation en arrière-plan) et ajout de **`docs/privacy.html`** (politique hébergeable, p. ex. GitHub Pages).
- **v7.1.26 — Politique de confidentialité dans les 13 langues.** La politique de confidentialité est désormais disponible dans les **13 langues** de l'app (**RTL complet** en arabe), avec **repli en anglais** pour toute locale sans traduction dédiée.
- **v7.1.27 — Identité de marque des fichiers exportés.** Les exports sont nommés **`JTR_Backup_<yyyy-MM-dd>_<HHmmss>.jtr`** et embarquent un **en-tête de marque** `manifest.json` (`{"_jtr":{"magic":"JTR-EXPORT", …}}`) en tête de l'archive ZIP, qui sert aussi de **validation par contenu** à l'import. **Association de fichier `.jtr`** best-effort et **scopée** (intent-filter `VIEW` couplant `application/octet-stream` à `pathPattern .jtr`, **jamais de catch-all**) : l'URI entrante **pré-arme le dialogue de confirmation** après déverrouillage (aucune écriture directe). **Rétrocompatibilité totale** — les anciens exports (sans `manifest.json`) suivent le chemin *legacy* inchangé — grâce au **versionnement dissocié** `FORMAT_VERSION` (schéma de données, =1) vs `ENVELOPE_VERSION` (marque, =2), l'app n'ayant jamais refusé ses propres fichiers. Limites Android documentées : icône/vignette JTR *sur* le fichier impossible ; association `content://` best-effort.

> 🛡️ **Intégrité des données** — l'incident de perte de données est clos : la base est désormais à la fois **protégée de tout écrasement par l'OS** (v7.1.7) **et** ses sauvegardes `.jtr` sont de nouveau **réellement restaurables** (v7.1.8).

### Cycle v7.1.28 → v7.1.55 — Import des contacts, relations justes, catégories & carte, cohérence du thème

- **v7.1.28 — Import des contacts depuis les Paramètres.** L'import, jusque-là réservé à l'accueil de bienvenue, devient une entrée permanente des Paramètres, avec **dédoublonnage SKIP** (un contact déjà présent n'est pas réimporté).
- **v7.1.29 — Dates futures & récurrence.** Le plafond d'année est levé : une date **passée** devient un rappel **annuel**, une date **future** un rappel **unique**.
- **v7.1.30 — Dates « collées » à l'ancienne langue.** Revenir sur « Langue du système » ne réinitialisait pas `Locale.setDefault` → les dates restaient formatées dans la langue précédente. Corrigé dans `LocaleManager.wrap`.
- **v7.1.31 — OSM : doublons et langue des lieux.** Dédoublonnage par `display_name` normalisé + intercepteur `Accept-Language` → les noms de lieux suivent la langue de JTR.
- **v7.1.32 — Adresses OSM complètes.** Fin du « numéro de rue seul » : l'étiquette est reconstruite depuis `address` (rue d'abord, numéro jamais isolé), `addressdetails` demandé aussi sur le *reverse*.
- **v7.1.33 — Titre de section de notes.** Fin du « Notes » prérempli : la nouvelle section naît avec un titre **vide** et un placeholder Material 3.
- **v7.1.34 → v7.1.42 — Import des contacts, série B1→B7.** Sept briques successives, **sans aucune migration**, réutilisant l'importateur existant : **B1** noms complets, surnom, poste/département, photo pleine résolution ; **B2** types et libellés des téléphones/e-mails ; **B3** événements → dates du contact (un anniversaire importé programme d'office son rappel) ; **B3b** dates **sans année** (`--MM-dd`) jusque dans le socle ; **B4** note → sections, site web → liens sociaux, adresse postale → ville + section ; **B5** relations → `relationLines` puis **2ᵉ passe** de résolution vers `linkedPersonId` ; **B6** aperçu des capacités par contact (chips, une seule requête) ; **B7** dédoublonnage **Ignorer / Mettre à jour / Importer quand même** par téléphone ou e-mail, la mise à jour n'écrasant jamais une valeur par du vide.
- **v7.1.38 — Saisie manuelle des dates sans année.** Édition directe du format `--MM-dd` ; les chiffres sont canonisés en `Locale.ROOT` (la saisie en arabe cassait la détection ISO).
- **v7.1.43 — Inversion correcte des relations (P1).** Un module dédié (`domain/relations/RelationRoles`) calcule l'inverse **neutre** et propose la **classe d'équivalence genrée** : « mère » ↦ « fils/fille » et non plus « mère ». Corrige aussi un bug latent où changer « mère » en « père » détruisait le miroir. Sans migration ni backfill.
- **v7.1.44 — Catalogue de relations étendu (P2).** 15 rôles supplémentaires (paires involutives et symétriques) portant le catalogue à **25 rôles + « Personnalisé »**, présentés **groupés** Famille / Pro / Social ; les catalogues téléphone/e-mail/date restent inchangés.
- **v7.1.45 — Corbeille : « categoriesand10 ».** AAPT **rogne les espaces** d'un `<string>` non guillemeté : la concaténation du dialogue « vider la corbeille » collait les mots. Remplacée par trois chaînes complètes dans les 13 langues.
- **v7.1.46 — Géocodage déterministe.** Tri **localité → importance → `osm_id`** (⚠️ `place_id` varie d'une requête à l'autre) et zoom de la mini-carte ajusté : le repère d'une ville ne saute plus d'un quartier à l'autre selon la langue.
- **v7.1.47 — Carte : bouton Enregistrer toujours accessible.** Le bandeau n'est visible que **clavier fermé** (`WindowInsets.ime`), `imePadding()` sur la seule `Surface` (jamais le `Scaffold`, qui redimensionnerait la `MapView`) + `consumeWindowInsets` pour ne pas compter l'inset de navigation deux fois.
- **v7.1.48 — Métadonnées de catégorie (Room v21 → v22).** Ajout de `Category.updatedAt` (**`MIGRATION_21_22`**, *backfill* = `createdAt` et non « maintenant »), source unique de date pour le tri **et** le dialogue « Informations », round-trip `.jtr`, et formatage date-heure conforme à la locale.
- **v7.1.49 — Image de catégorie interactive.** La couverture s'ouvre en **visionneuse plein écran** (`PhotoZoomDialog` extrait et rendu réutilisable) avec Remplacer / Retirer. ⚠️ Les insets valent 0 dans une fenêtre de `Dialog` → ils sont lus sur la fenêtre de l'**activité**.
- **v7.1.50 — Sélecteur de catégories en feuille montante.** `AlertDialog` → `ModalBottomSheet` : **recherche en loupe repliable**, « + » remonté dans l'en-tête, compteur de sélection, étoiles des favoris, chips en `FlowRow`. Recette clavier : `imePadding()` **et** `weight(1f, fill = false)` sur la liste, sinon « Terminé » est rogné.
- **v7.1.51 — Le coréen enfin sélectionnable.** Les 14 entrées de langue dans une `Column` non défilable dépassaient la hauteur d'écran : la dernière était **littéralement inatteignable**. Plafond sur la feuille + liste défilable + `skipPartiallyExpanded`.
- **v7.1.52 — Politique de confidentialité aux couleurs du thème.** Un `<style>` injecté avant `</head>` **surcharge les variables CSS** du HTML — les 13 traductions restent intactes ; fond aligné sur le conteneur réel de la feuille (pas de couture), liens **soulignés** (le preset Signature a une primaire quasi noire), **zéro couleur fixe**.
- **v7.1.53 — Fin du fond lavande en thème clair.** Les 5 rôles `surfaceContainer*` n'étaient définis que dans les schémas **sombres** : en clair, feuilles, **28 `AlertDialog`**, menus et cartes retombaient sur la baseline Material mauve, quel que soit le preset. Un helper unique **dérive** l'échelle des surfaces déjà choisies par chaque preset — aucune couleur inventée.
- **v7.1.54 — Dossiers repliables dans le sélecteur de catégories.** Tout part **replié** ; le repli est **récursif par construction** (on s'arrête au dossier fermé, donc son sous-arbre entier disparaît) et la **recherche court-circuite le repli** — une catégorie enfouie à deux niveaux reste trouvable. État local à la feuille, sans persistance.
- **v7.1.55 — Partage recentré + lien Play Store.** Le partage de **catégorie** est retiré (il n'exportait qu'un nom et un compteur, et le faire complètement reviendrait à exporter en masse les données de plusieurs contacts) ; le partage de **profil** — une fiche à la fois, choix délibéré — est conservé intact. « Partager JTR » joint désormais le **lien Play Store** (identifiant écrit en dur : la variante de test aurait produit un lien mort).

### Cycle v7.1.56 → v7.1.65 — Conformité, intégrité des données, i18n & accessibilité

- **v7.1.56 — Politique de confidentialité mise à niveau (13 langues).** La politique **embarquée** passe de **8 à 12 sections** et déclare désormais explicitement la permission **`READ_CONTACTS`**, le **partage de fiche** (texte / PNG / PDF) et l'**export `.jtr`** — les seules voies par lesquelles des données peuvent sortir de l'appareil, toutes deux à l'initiative de l'utilisateur. Insertion par **position des `<h2>`** et renumérotation finale, dans les **13 traductions**.
- **v7.1.57 — Durcissements de pré-publication (C2 · C4).** **Aucune PII en `logcat`** (noms, téléphones, e-mails, identifiants retirés des traces). **Plafond de 400 alarmes exactes** armées simultanément — Android 13+ en limite l'application à **500**, au-delà desquelles `setExactAndAllowWhileIdle` lève une exception : ce sont les **échéances les plus proches** qui sont armées, la sélection étant **stable** (deux recalculs sur les mêmes données donnent la même liste). La **replanification globale** résout désormais les contacts par `map` **totale** : plus aucune date ne peut être silencieusement omise.
- **v7.1.58 → v7.1.59 — Défilement automatique du bouton « plus / moins d'informations ».** Ouvrir ou fermer une section fait **défiler l'écran jusqu'au contenu concerné**, en une animation **fluide** synchronisée avec l'expansion (ancrée en haut de la section, plafonnée en durée réelle — un écran à 120 Hz ne la raccourcit pas).
- **v7.1.60 — Accordéons fermés par défaut, état mémorisé.** Les sections « plus d'informations » s'ouvrent **repliées** ; l'état est mémorisé **par type de section** (global, persisté), donc un utilisateur qui garde toujours « Téléphones » ouvert le retrouve ouvert sur **chaque** fiche.
- **v7.1.61 — H1 — Feuille de partage : les formats toujours visibles.** À l'ouverture, la feuille s'ancrait à **mi-hauteur** et masquait le choix **texte / PNG / PDF** ; elle s'ouvre désormais **entièrement dépliée** (`skipPartiallyExpanded`) — ce n'était pas une question de hauteur de contenu.
- **v7.1.62 — H5 — Fidélité du thème jusqu'aux accents.** Les rôles `tertiary*` et `outline*` restaient à la **baseline Material** (rose / mauve) sur les **12 schémas** (6 presets × clair-sombre) : ils sont désormais **dérivés du preset** — aucune couleur inventée. Les rôles `error*` restent volontairement à la baseline (le rouge d'erreur doit rester lisible comme tel).
- **H4 — Les 11 migrations Room testées.** Les migrations `11→12` … `21→22` sont **toutes** couvertes par des tests instrumentés (`room-testing`), **sans aucun bump de schéma** : la version de la base reste **v22**.
- **v7.1.63 — Retrait complet du fallback destructif (C3 clos).** `fallbackToDestructiveMigration()` disparaît du builder : **plus aucun effacement silencieux** possible. Un chemin de migration absent fait désormais **échouer l'ouverture de la base** — l'application refuse de démarrer, **les données restent intactes** — au lieu de les recréer à vide. Contrepartie assumée : toute future montée de schéma **doit** livrer sa `Migration`.
- **v7.1.64 — L'indonésien ne s'appliquait jamais.** Les traductions vivaient dans `values-b+id/` alors que la **table de ressources native apparie sur le code hérité `in`** — les chaînes n'étaient donc **jamais** chargées et l'app retombait en anglais. Dossier renommé en **`values-in/`**. ⚠️ Le piège est contre-intuitif : `Locale.getLanguage()` renvoie bien `id` en API 35+, mais ce n'est **pas** ce que la résolution de ressources utilise — « moderniser » en `values-id` recasserait la langue.
- **v7.1.65 — Accessibilité : les interrupteurs annoncés avec leur libellé.** TalkBack lisait le texte d'un réglage puis un interrupteur **anonyme** — sans jamais dire **quel** réglage il commandait. Les 7 lignes concernées (6 dans les Paramètres + la proximité) deviennent un **unique élément *toggleable*** portant libellé et état. ⚠️ Neutraliser l'interrupteur (`onCheckedChange = null`) lui retire sa **cible tactile de 48 dp** et remonte la ligne : `minimumInteractiveComponentSize()` est indispensable pour un rendu **strictement identique** au pixel près.

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
| v7.1.11 | **Insets unifiés** : clavier (source unique, fin du vide fantôme) + paysage (barre de navigation latérale). |
| v7.1.12 | Barres système **lisibles** : icônes pilotées par le thème in-app (fin du sombre-sur-sombre). |
| v7.1.13 | **Swipe-to-delete** des sections de notes (confirmation + undo, gating anti-conflit, RTL). |
| v7.1.14 | **Fiabilité multilingue sur App Bundle** : 13 langues dans l'APK de base (`enableSplit = false`). |
| v7.1.15 | **Recherche des sections de notes** (titre + contenu indexés) + suite de tests 100 % verte. |
| v7.1.16 | **Accessibilité** : cibles tactiles 48 dp, `contentDescription`, `performClick` (zones gestuelles). |
| v7.1.17 | Icône de marque **Snapchat** (vector drawable). |
| v7.1.18 | Détection **Snapchat** unifiée par domaine (fix collision « t.co »). |
| v7.1.19 | Thème : couleurs codées en dur → **tokens** (`rememberCategoryColor`). |
| v7.1.20 | **Pluralisation CLDR** des compteurs (13 langues). |
| v7.1.21 | i18n : titre d'accueil corrigé (es) + audit des chaînes traduites. |
| v7.1.22 | i18n : compte à rebours local (J-/D-) + abréviation « non-binaire » (es). |
| v7.1.23 | Conformité Play : cible **API 36** (Android 16). |
| v7.1.24 | Visionneuse photo : **coins arrondis dynamiques** au glissement de fermeture. |
| v7.1.25 | Proximité **par contact** : redirection Réglages (Android 11+) via **helper partagé** ; politique in-app corrigée + `docs/privacy.html` hébergeable. |
| v7.1.26 | **Politique de confidentialité dans les 13 langues** (RTL arabe, repli anglais). |
| v7.1.27 | **Identité de marque des exports `.jtr`** : nommage `JTR_Backup_<date>_<heure>`, en-tête `manifest.json` (magic `JTR-EXPORT`) + validation par contenu, association `.jtr` scopée best-effort, rétrocompat *legacy* préservée. |
| v7.1.28 | **Import des contacts depuis les Paramètres** (+ dédoublonnage SKIP). |
| v7.1.29 | Dates **futures** autorisées ; récurrence passé → annuel / futur → unique. |
| v7.1.30 | i18n : les dates suivent la langue active après retour sur « Langue du système ». |
| v7.1.31 | OSM : **dédoublonnage** des résultats + noms de lieux dans la langue de JTR. |
| v7.1.32 | OSM : **adresses complètes** (fin du numéro de rue isolé). |
| v7.1.33 | Notes : titre de section **vide** à la création (placeholder M3). |
| v7.1.34 – v7.1.42 | **Import des contacts, série B1→B7** : noms complets & photo pleine résolution · types/libellés tél-e-mail · événements → dates (dont **sans année**) · notes/site/adresse · **relations** (2ᵉ passe par identifiant) · aperçu par chips · **dédoublonnage Ignorer/Mettre à jour/Importer quand même**. Aucune migration. |
| v7.1.38 | Saisie manuelle des dates **sans année** + chiffres canoniques `Locale.ROOT` (arabe). |
| v7.1.43 | **Inversion correcte des relations** (P1) : « mère » ↦ « fils/fille », miroir préservé au changement de rôle. |
| v7.1.44 | **Catalogue de 25 rôles** de relation (+ « Personnalisé »), sélecteur groupé Famille/Pro/Social. |
| v7.1.45 | Corbeille : mots collés corrigés (**AAPT rogne les espaces** d'un `<string>` non guillemeté). |
| v7.1.46 | **Géocodage déterministe** (tri localité → importance → `osm_id`) + zoom de la mini-carte. |
| v7.1.47 | Carte : bouton **Enregistrer** toujours accessible face au clavier (sans redimensionner la `MapView`). |
| v7.1.48 | **Métadonnées de catégorie** : `updatedAt` + **`MIGRATION_21_22`** (Room **v22**), tri et « Informations » sur une source unique. |
| v7.1.49 | **Image de catégorie interactive** : visionneuse plein écran réutilisable (Remplacer / Retirer). |
| v7.1.50 | **Sélecteur de catégories en feuille montante** : recherche repliable, « + » en en-tête, compteur, favoris. |
| v7.1.51 | Sélecteur de langues **borné et défilable** : le **coréen** redevient atteignable. |
| v7.1.52 | **Politique de confidentialité aux couleurs du thème** (surcharge CSS injectée, 13 traductions intactes). |
| v7.1.53 | **Fin du fond lavande en thème clair** : `surfaceContainer*` dérivés pour les 6 presets (feuilles, 28 dialogues, menus, cartes). |
| v7.1.54 | **Dossiers repliables** dans le sélecteur de catégories (repli récursif, la recherche prime). |
| v7.1.55 | **Partage recentré** : partage de catégorie retiré, partage de profil conservé ; « Partager JTR » joint le **lien Play Store**. |
| v7.1.56 | **Politique de confidentialité 8 → 12 sections** dans les 13 langues (déclare `READ_CONTACTS`, le partage de fiche et l'export `.jtr`). |
| v7.1.57 | Durcissements de pré-publication : **PII retirée de `logcat`**, **plafond de 400 alarmes exactes** (limite Android 13+ = 500), replanification à résolution totale. |
| v7.1.58 – v7.1.59 | Défilement automatique **fluide** au bouton « plus / moins d'informations » (synchronisé avec l'expansion). |
| v7.1.60 | Accordéons « plus d'informations » **fermés par défaut** + **état mémorisé par type** de section. |
| v7.1.61 | **H1** — feuille de partage : formats **texte / PNG / PDF** visibles dès l'ouverture (`skipPartiallyExpanded`). |
| v7.1.62 | **H5** — fidélité du thème jusqu'aux **accents** : `tertiary*` / `outline*` dérivés du preset sur les **12 schémas**. |
| (H4) | **Les 11 migrations Room 11→22 couvertes** par des tests instrumentés, **sans bump** de schéma (base toujours v22). |
| v7.1.63 | **C3 clos** — **retrait complet du fallback destructif** : plus aucun effacement silencieux ; un chemin manquant fait échouer l'ouverture, données intactes. |
| v7.1.64 | **Indonésien enfin appliqué** : `values-b+id` → **`values-in`** (la table de ressources apparie sur le code hérité `in`). |
| v7.1.65 | **Accessibilité** : lignes à interrupteur rendues ***toggleable*** → TalkBack annonce enfin **quel** réglage est commandé (48 dp préservés). |

---

## Confidentialité

JTR est **local-first** : vos **données de contact ne quittent jamais l'appareil**, et elles sont **définitivement supprimées** à la désinstallation. La base est en outre **exclue de toute sauvegarde ou transfert du système** (`allowBackup="false"` + règles d'extraction de données) : aucun instantané de l'OS ne peut écraser vos données — l'**export `.jtr` manuel** est l'unique moyen de migrer d'un appareil à l'autre.

Les **seules connexions réseau** servent aux fonctions de lieu : le **géocodage** (OpenStreetMap Nominatim) et les **fonds de carte** (OpenFreeMap). Leur sont transmis un **nom de ville / des coordonnées**, la **zone affichée** et votre **adresse IP** — **jamais vos données de contact**. **Aucun compte, aucune publicité, aucun outil d'analyse**, aucun SDK de rapport de plantage.

Les permissions (contacts, notifications, localisation, photos) sont demandées **à l'usage** et révocables à tout moment ; les contacts importés depuis le téléphone **restent locaux**. Les **deux seules voies de sortie** des données sont **à votre initiative** et vers la destination que vous choisissez : le **partage d'une fiche** (texte / PNG / PDF) et l'**export `.jtr`** — JTR ne téléverse, ne synchronise et ne transmet jamais de lui-même.

La politique complète est consultable **dans l'app** (**Paramètres → Politique de confidentialité**, 12 sections, 13 langues) et **en ligne** : <https://hrazim.github.io/JTR/privacy.html>.

---

## Licence

Projet **personnel** à vocation d'apprentissage, non publié sous licence open source (tous droits réservés).

Données cartographiques © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright) (ODbL) ; tuiles servies par [OpenFreeMap](https://openfreemap.org).

---

<sub>**JTR v7.1.65** · Room v22 · `targetSdk 36` · `versionCode 119` — carnet de contacts 100 % local.</sub>
