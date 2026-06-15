# 📱 JTR — Just To Remember

> Un carnet de contacts Android **100 % local** qui se souvient du contexte humain de vos relations. · Version **7.0.0**

JTR (*Just To Remember*) va au-delà du répertoire téléphonique : il garde une **mémoire sociale** de chaque personne (goûts, anniversaires, ville, notes, réseaux sociaux, relations) et vous rappelle proactivement les dates importantes ainsi que les contacts dont vous êtes physiquement proche.

---

## Sommaire

1. [Présentation](#présentation)
2. [Fonctionnalités](#fonctionnalités)
3. [Pile technique & architecture](#pile-technique--architecture)
4. [Installation & build](#installation--build)
5. [Structure du projet](#structure-du-projet)
6. [Confidentialité](#confidentialité)
7. [Licence](#licence)

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
| Langage / SDK | Kotlin 2.1.0 · JVM 17 · `minSdk 26` · `compile/targetSdk 35` |
| UI | Jetpack Compose (BOM 2024.12.01) · Material 3 · Navigation Compose |
| Persistance | Room 2.6.1 (via **KSP**) — base **v19** · DataStore · EncryptedSharedPreferences |
| Tâches de fond | WorkManager · `AlarmManager` (alarmes exactes) · `ProcessLifecycleOwner` |
| Carte & réseau | MapLibre 11.5 · Retrofit / OkHttp · kotlinx.serialization (Nominatim) |
| Localisation | Play Services Location (FusedLocation + Geofencing) |
| Sécurité | `androidx.security:security-crypto` · `androidx.biometric` |
| Images | Coil |
| Tests | JUnit · MockK · Turbine · Truth · `room-testing` (test de migration) |

> ⚠️ La base Room est en **v19** avec `fallbackToDestructiveMigration()` comme filet de sécurité : toute évolution de schéma **doit** fournir une `Migration` explicite (sinon perte de données utilisateur).

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
│   ├── local/         # AppDatabase (v19) + DAOs
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

## Confidentialité

JTR est **local-first** : vos données restent sur votre appareil et sont **définitivement supprimées** à la désinstallation. Les seules requêtes réseau concernent le géocodage de ville (Nominatim) et les fonds de carte (OpenFreeMap), **sans transmettre de donnée personnelle**. Les permissions (notifications, localisation, photos) sont demandées **à l'usage** et révocables à tout moment. La politique complète est consultable dans l'app : **Paramètres → Politique de confidentialité**.

---

## Licence

Projet **personnel** à vocation d'apprentissage, non publié sous licence open source (tous droits réservés).

Données cartographiques © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright) (ODbL) ; tuiles servies par [OpenFreeMap](https://openfreemap.org).
