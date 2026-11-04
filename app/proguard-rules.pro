# Regles R8 propres a JTR. Le reste vient des regles embarquees par les
# bibliotheques (Room, WorkManager, lifecycle, Gson, Retrofit, kotlinx,
# Tink, MapLibre, Play services) et de proguard-android-optimize.txt.

# Traces de pile lisibles (le mapping est embarque dans l'AAB).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Assurance explicite pour la reflexion Gson du .jtr (union-safe avec la
# regle consumer de Gson ; on ne depend pas d'elle pour le chemin critique).
# Signature = types generiques des champs (List<Person>, List<NoteSection>...).
# *Annotation* = @SerializedName lu au runtime pour manifest.json.
-keepattributes Signature,*Annotation*

# --- Contrat du fichier .jtr (backup.json, Gson par reflexion) ---
# Les cles JSON SONT les noms de champs : renommer un champ rend les .jtr
# illisibles dans les deux sens. SANS allowoptimization : Gson ecrit ces
# champs par reflexion ; R8 ne doit ni supprimer un champ jamais relu
# (exportedAt), ni presumer sa valeur (formatVersion) ou sa non-nullite
# (noteSections absent des archives anterieures a v7.0.3).
# <init>() : Gson utilise le constructeur sans argument quand il existe.
# manifest.json (BackupManifest/BackupBrand) est couvert par la regle
# @SerializedName de Gson. Toute classe ajoutee au graphe de BackupPayload
# DOIT etre ajoutee ici.
-keepclassmembers class com.jtr.app.data.backup.BackupPayload { <fields>; <init>(); }
-keepclassmembers class com.jtr.app.domain.model.Person { <fields>; }
-keepclassmembers class com.jtr.app.domain.model.NoteSection { <fields>; <init>(); }
-keepclassmembers class com.jtr.app.domain.model.DynamicLine { <fields>; <init>(); }
-keepclassmembers class com.jtr.app.domain.model.Category { <fields>; }
-keepclassmembers class com.jtr.app.domain.model.CategoryGroup { <fields>; }
-keepclassmembers class com.jtr.app.domain.model.PersonCategoryJoin { <fields>; }
-keepclassmembers class com.jtr.app.domain.model.SocialLinkEntity { <fields>; }
-keepclassmembers class com.jtr.app.domain.model.GalleryImage { <fields>; }

# --- Tink (via security-crypto) : KeysDownloader reference des bibliotheques
# optionnelles absentes de l'app, jamais utilisees par EncryptedSharedPreferences.
-dontwarn com.google.api.client.http.GenericUrl
-dontwarn com.google.api.client.http.HttpHeaders
-dontwarn com.google.api.client.http.HttpRequest
-dontwarn com.google.api.client.http.HttpRequestFactory
-dontwarn com.google.api.client.http.HttpResponse
-dontwarn com.google.api.client.http.HttpTransport
-dontwarn com.google.api.client.http.javanet.NetHttpTransport
-dontwarn com.google.api.client.http.javanet.NetHttpTransport$Builder
-dontwarn org.joda.time.Instant
