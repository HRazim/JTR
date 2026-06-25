package com.jtr.app.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jtr.app.BuildConfig
import com.jtr.app.R
import com.jtr.app.security.SecurityAuthDialog
import com.jtr.app.security.SecuritySetupDialog
import com.jtr.app.security.SecurityViewModel
import com.jtr.app.ui.backup.BackupDialog
import com.jtr.app.ui.navigation.nestedScreenContentInsets
import com.jtr.app.ui.theme.ThemePreset
import com.jtr.app.utils.LocaleManager
import com.jtr.app.utils.LocationUtils
import kotlin.math.abs

/** Tailles de police proposées (facteur → libellé) ; plafond strict = 1.30. */
private val FONT_SCALE_OPTIONS: List<Pair<Float, Int>> = listOf(
    0.9f to R.string.settings_font_small,
    1.0f to R.string.settings_font_normal,
    1.15f to R.string.settings_font_large,
    1.30f to R.string.settings_font_xlarge,
)

/**
 * Langues applicatives (tag BCP-47 ou null = langue du système) → endonyme.
 * Les 13 langues sont entièrement traduites (en, fr, es, ja, zh, de, it, pt, tr,
 * ar, id, ru, ko) ; l'arabe est en RTL (v6.2.1).
 */
private val LANGUAGE_OPTIONS: List<Pair<String?, Int>> = listOf(
    null to R.string.settings_language_system,
    "en" to R.string.lang_en,
    "fr" to R.string.lang_fr,
    "es" to R.string.lang_es,
    "ja" to R.string.lang_ja,
    "zh" to R.string.lang_zh,
    "de" to R.string.lang_de,
    "it" to R.string.lang_it,
    "pt" to R.string.lang_pt,
    "tr" to R.string.lang_tr,
    "ar" to R.string.lang_ar,
    "id" to R.string.lang_id,
    "ru" to R.string.lang_ru,
    "ko" to R.string.lang_ko,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    isDarkMode: Boolean = false,
    onDarkModeChange: (Boolean) -> Unit = {},
    selectedPreset: ThemePreset = ThemePreset.JTR_SIGNATURE,
    onPresetSelected: (ThemePreset) -> Unit = {},
    fontScale: Float = 1f,
    onFontScaleChange: (Float) -> Unit = {},
    onNavigateToTrash: () -> Unit = {},
    onNavigateToImportContacts: () -> Unit = {},
    settingsViewModel: SettingsViewModel = viewModel()
) {
    var showPrivacySheet by remember { mutableStateOf(false) }
    // Sauvegarde & restauration .jtr — relocalisée ici depuis le menu de
    // l'Accueil (v5.5) ; le BackupViewModel est résolu par BackupDialog.
    var showBackupDialog by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showFontPicker by remember { mutableStateOf(false) }
    // Sécurité locale (v6.2.0) : verrou par schéma + biométrie + code de secours.
    val securityViewModel: SecurityViewModel = viewModel()
    val lockEnabled by securityViewModel.lockEnabled.collectAsStateWithLifecycle()
    val biometricEnabled by securityViewModel.biometricEnabled.collectAsStateWithLifecycle()
    var showSecuritySetup by remember { mutableStateOf(false) }
    var showSecurityChange by remember { mutableStateOf(false) }
    var showSecurityDisableAuth by remember { mutableStateOf(false) }
    val notificationsEnabled by settingsViewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val proximityEnabled by settingsViewModel.proximityEnabled.collectAsStateWithLifecycle()
    val birthdayEnabled by settingsViewModel.birthdayEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    // SOURCE DE VÉRITÉ OS : true seulement si l'utilisateur n'a PAS bloqué les
    // notifications ET (API 33+) que POST_NOTIFICATIONS est accordée. Re-synchronisé
    // à chaque ON_RESUME (retour des Réglages système, auto-révocation « app inutilisée »…).
    var notificationsAllowed by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    // État RÉEL de la localisation système (interrupteur du téléphone) : le rappel de
    // proximité ne peut PAS fonctionner s'il est éteint, même avec la permission
    // accordée. Réévalué à chaque ON_RESUME (l'utilisateur a pu la basculer ailleurs).
    var locationOn by remember { mutableStateOf(LocationUtils.isLocationEnabled(context)) }

    // États des dialogues d'EXPLICATION / REDIRECTION. Les VRAIES boîtes de dialogue
    // de permission sont affichées par le SYSTÈME via les launchers ci-dessous ;
    // ces AlertDialog ne font qu'expliquer (avant re-demande) ou rediriger vers les
    // Réglages quand l'OS ne montrera plus sa boîte (refus définitif / blocage).
    var showNotifRationale by remember { mutableStateOf(false) }
    var showNotifSettings by remember { mutableStateOf(false) }
    var showLocationRationale by remember { mutableStateOf(false) }
    var showLocationSettings by remember { mutableStateOf(false) }
    var showBackgroundRationale by remember { mutableStateOf(false) }
    // Import contacts (v7.1.28) : divulgation (rationale) + redirection Réglages si bloqué.
    var showContactsRationale by remember { mutableStateOf(false) }
    var showContactsSettings by remember { mutableStateOf(false) }

    fun hasForegroundLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocation(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    // true si le système affichera ENCORE sa boîte (refus simple) ; false = jamais
    // demandé OU refus définitif (à lever avec le drapeau persisté).
    fun shouldShowRationale(perm: String): Boolean =
        activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)

    fun openSettings(intent: Intent) { runCatching { context.startActivity(intent) } }
    fun notificationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    fun appDetailsIntent(): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )

    // ── NOTIFICATIONS : boîte système POST_NOTIFICATIONS (API 33+) ──────────────
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (granted && notificationsAllowed) {
            settingsViewModel.setNotificationsEnabled(true)
        } else {
            settingsViewModel.setNotificationsEnabled(false)
            // Refus définitif → le système ne montrera plus sa boîte : Réglages.
            if (!shouldShowRationale(Manifest.permission.POST_NOTIFICATIONS)) showNotifSettings = true
        }
    }

    // Machine à états canonique : accordé / jamais demandé / refusé une fois / bloqué.
    fun enableNotificationsFlow() {
        val perm = Manifest.permission.POST_NOTIFICATIONS
        when {
            NotificationManagerCompat.from(context).areNotificationsEnabled() -> {
                notificationsAllowed = true
                settingsViewModel.setNotificationsEnabled(true)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED ->
                when {
                    !settingsViewModel.wasPermissionAsked(perm) -> {
                        settingsViewModel.markPermissionAsked(perm)
                        notifLauncher.launch(perm)
                    }
                    shouldShowRationale(perm) -> showNotifRationale = true
                    else -> showNotifSettings = true
                }
            // < 33, ou permission accordée mais notifications bloquées au niveau
            // app/canal : seuls les Réglages système peuvent réactiver.
            else -> showNotifSettings = true
        }
    }

    // ── CONTACTS : import depuis le téléphone (READ_CONTACTS) ───────────────────
    // Divulgation AVANT toute demande (bonne pratique Play pour une permission
    // sensible) : la VRAIE boîte système n'est lancée qu'après le dialogue
    // d'explication. Permission accordée → on ouvre l'écran de sélection.
    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onNavigateToImportContacts()
        // Refus définitif → le système ne montrera plus sa boîte : Réglages de l'app.
        else if (!shouldShowRationale(Manifest.permission.READ_CONTACTS)) showContactsSettings = true
    }

    fun importContactsFlow() {
        val perm = Manifest.permission.READ_CONTACTS
        when {
            hasContactsPermission() -> onNavigateToImportContacts()
            // Jamais demandé OU refus simple → on montre la divulgation, puis la boîte système.
            !settingsViewModel.wasPermissionAsked(perm) || shouldShowRationale(perm) ->
                showContactsRationale = true
            // Refus définitif (jamais re-proposé par l'OS) → redirection Réglages.
            else -> showContactsSettings = true
        }
    }

    // ── PROXIMITÉ : boîte système localisation (premier plan → arrière-plan) ────
    val fineLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            if (hasBackgroundLocation()) settingsViewModel.setProximityEnabled(true)
            else showBackgroundRationale = true
        } else {
            settingsViewModel.setProximityEnabled(false)
            if (!shouldShowRationale(Manifest.permission.ACCESS_FINE_LOCATION)) showLocationSettings = true
        }
    }

    // Arrière-plan : demandable au runtime UNIQUEMENT sur Android 10 (Q) ; sur 11+
    // (R), l'octroi passe obligatoirement par les Réglages (« Autoriser tout le temps »).
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> settingsViewModel.setProximityEnabled(granted) }

    fun enableProximityFlow() {
        if (hasForegroundLocation()) {
            if (hasBackgroundLocation()) settingsViewModel.setProximityEnabled(true)
            else showBackgroundRationale = true
            return
        }
        val perm = Manifest.permission.ACCESS_FINE_LOCATION
        when {
            !settingsViewModel.wasPermissionAsked(perm) -> {
                settingsViewModel.markPermissionAsked(perm)
                fineLocationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            shouldShowRationale(perm) -> showLocationRationale = true
            else -> showLocationSettings = true
        }
    }

    // Re-synchronisation ON_RESUME : si l'OS bloque/révoque (Réglages, « app
    // inutilisée »…), AUCUN toggle ne reste ON et les préférences sont remises à
    // false (le Worker cesse) — lecture + écriture synchrones, sans clignotement.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAllowed =
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
                locationOn = LocationUtils.isLocationEnabled(context)
                if (!notificationsAllowed && settingsViewModel.notificationsEnabled.value) {
                    settingsViewModel.setNotificationsEnabled(false)
                }
                if (settingsViewModel.proximityEnabled.value &&
                    !(hasForegroundLocation() && hasBackgroundLocation())
                ) {
                    settingsViewModel.setProximityEnabled(false)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Langue applicative courante (LocaleManager) → endonyme. Le changement
    // recrée l'Activity, donc cette valeur est relue à la recomposition.
    val currentLangTag = LocaleManager.currentTag(context)
    val currentLangLabel = stringResource(
        LANGUAGE_OPTIONS.firstOrNull { it.first == currentLangTag }?.second
            ?: R.string.settings_language_system
    )
    val currentFontLabel = stringResource(
        FONT_SCALE_OPTIONS.minByOrNull { abs(it.first - fontScale) }?.second
            ?: R.string.settings_font_normal
    )
    val shareText = stringResource(R.string.settings_share_text)

    // Persiste le choix puis recrée l'Activity → attachBaseContext applique la
    // locale et TOUT le texte change immédiatement (fiable sur toutes versions).
    fun applyLanguage(tag: String?) {
        if (tag == currentLangTag) return
        LocaleManager.setLanguage(context, tag)
        activity?.recreate()
    }

    fun shareApp() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, null)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        // Fond = `background` teinté du thème, IDENTIQUE à la barre de navigation
        // globale → aucune couture/bande grise entre le contenu et la barre.
        containerColor = MaterialTheme.colorScheme.background,
        // Scaffold IMBRIQUÉ dans JTRMainScaffold : haut/bas gérés globalement (sinon bande
        // grise « fantôme » coupant la dernière carte) ; on n'applique au CONTENU que l'inset
        // HORIZONTAL (barre de nav latérale en paysage 3 boutons) via la règle centralisée —
        // sinon le contenu passe sous la barre (v7.1.11).
        contentWindowInsets = nestedScreenContentInsets
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                // Padding bas généreux → la dernière carte (« Partager ») dégage
                // entièrement la barre de navigation, jamais coupée.
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Carte « Général » ─────────────────────────────────────────────
            SettingsCard(title = stringResource(R.string.settings_section_general)) {
                SettingsToggleRow(
                    icon = Icons.Default.DarkMode,
                    title = stringResource(R.string.settings_dark_mode_title),
                    subtitle = stringResource(R.string.settings_dark_mode_subtitle),
                    checked = isDarkMode,
                    onCheckedChange = onDarkModeChange
                )
                SettingsValueRow(
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.settings_color_palette),
                    value = stringResource(selectedPreset.labelRes),
                    onClick = { showThemePicker = true }
                )
                SettingsValueRow(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.settings_language_title),
                    value = currentLangLabel,
                    onClick = { showLanguagePicker = true }
                )
                SettingsValueRow(
                    icon = Icons.Default.FormatSize,
                    title = stringResource(R.string.settings_font_size_title),
                    value = currentFontLabel,
                    onClick = { showFontPicker = true }
                )
            }

            // ── Carte « Contacts » (v7.1.28) : import depuis le téléphone ──────
            SettingsCard(title = stringResource(R.string.settings_section_contacts)) {
                SettingsNavRow(
                    icon = Icons.Default.Contacts,
                    title = stringResource(R.string.settings_import_contacts_title),
                    subtitle = stringResource(R.string.settings_import_contacts_subtitle),
                    onClick = { importContactsFlow() }
                )
            }

            // ── Carte « Notifications » (logique système v6.0.2 inchangée) ─────
            SettingsCard(title = stringResource(R.string.settings_section_notifications)) {
                // checked = intention utilisateur ET réalité OS → jamais ON si l'OS bloque.
                SettingsToggleRow(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.settings_notifications_title),
                    subtitle = stringResource(R.string.settings_notifications_subtitle),
                    checked = notificationsEnabled && notificationsAllowed,
                    onCheckedChange = { enabled ->
                        if (enabled) enableNotificationsFlow()
                        else settingsViewModel.setNotificationsEnabled(false)
                    }
                )
                SettingsToggleRow(
                    icon = Icons.Default.LocationOn,
                    title = stringResource(R.string.settings_proximity_title),
                    subtitle = stringResource(R.string.settings_proximity_subtitle),
                    // L'état RÉEL de la localisation est inclus : jamais « activé » quand
                    // l'interrupteur système est éteint (la fonctionnalité ne peut pas marcher).
                    checked = proximityEnabled && notificationsEnabled && notificationsAllowed &&
                        hasForegroundLocation() && hasBackgroundLocation() && locationOn,
                    enabled = notificationsEnabled && notificationsAllowed,
                    onCheckedChange = { enabled ->
                        if (enabled) enableProximityFlow()
                        else settingsViewModel.setProximityEnabled(false)
                    }
                )
                // Cohérence : l'utilisateur veut la proximité mais la localisation système
                // est éteinte → on l'explique clairement et on propose de l'activer.
                if (proximityEnabled && notificationsEnabled && notificationsAllowed && !locationOn) {
                    SettingsWarningRow(
                        text = stringResource(R.string.settings_proximity_location_off),
                        onClick = { openSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                    )
                }
                SettingsToggleRow(
                    icon = Icons.Default.Event,
                    title = stringResource(R.string.settings_birthday_title),
                    subtitle = stringResource(R.string.settings_birthday_subtitle),
                    checked = birthdayEnabled && notificationsEnabled && notificationsAllowed,
                    enabled = notificationsEnabled && notificationsAllowed,
                    onCheckedChange = { enabled -> settingsViewModel.setBirthdayEnabled(enabled) }
                )
            }

            // ── Carte « Sécurité » (v6.2.0) : verrou local 100 % sur l'appareil ─
            SettingsCard(title = stringResource(R.string.settings_section_security)) {
                SettingsToggleRow(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.security_set_password),
                    subtitle = stringResource(R.string.security_set_password_subtitle),
                    checked = lockEnabled,
                    onCheckedChange = { enabled ->
                        // Activer = configurer le schéma ; désactiver = exiger une
                        // authentification d'abord (le toggle ne bascule qu'au succès).
                        if (enabled) showSecuritySetup = true
                        else showSecurityDisableAuth = true
                    }
                )
                if (lockEnabled) {
                    SettingsNavRow(
                        icon = Icons.Default.Password,
                        title = stringResource(R.string.security_change_password),
                        onClick = { showSecurityChange = true }
                    )
                    // Option biométrie masquée proprement si le matériel/identifiant
                    // d'appareil n'est pas disponible.
                    if (securityViewModel.biometricAvailable) {
                        SettingsToggleRow(
                            icon = Icons.Default.Fingerprint,
                            title = stringResource(R.string.security_biometric_title),
                            subtitle = stringResource(R.string.security_biometric_subtitle),
                            checked = biometricEnabled,
                            onCheckedChange = { securityViewModel.setBiometricEnabled(it) }
                        )
                    }
                }
            }

            // ── Carte « Autres » ──────────────────────────────────────────────
            SettingsCard(title = stringResource(R.string.settings_section_others)) {
                SettingsNavRow(
                    icon = Icons.Default.SettingsBackupRestore,
                    title = stringResource(R.string.backup_menu),
                    subtitle = stringResource(R.string.settings_backup_subtitle),
                    onClick = { showBackupDialog = true }
                )
                SettingsNavRow(
                    icon = Icons.Default.Delete,
                    title = stringResource(R.string.settings_trash_title),
                    subtitle = stringResource(R.string.settings_trash_subtitle),
                    onClick = onNavigateToTrash
                )
                SettingsNavRow(
                    icon = Icons.Default.Share,
                    title = stringResource(R.string.settings_share_title),
                    onClick = { shareApp() }
                )
                SettingsNavRow(
                    icon = Icons.Default.PrivacyTip,
                    title = stringResource(R.string.settings_privacy_title),
                    subtitle = stringResource(R.string.settings_privacy_subtitle),
                    onClick = { showPrivacySheet = true }
                )
            }

            // ── Pied de page : version dynamique (BuildConfig) ────────────────
            Text(
                text = stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    if (showThemePicker) {
        ThemePickerSheet(
            selected = selectedPreset,
            onSelect = { onPresetSelected(it); showThemePicker = false },
            onDismiss = { showThemePicker = false }
        )
    }
    if (showLanguagePicker) {
        LanguagePickerSheet(
            currentTag = currentLangTag,
            onSelect = { applyLanguage(it); showLanguagePicker = false },
            onDismiss = { showLanguagePicker = false }
        )
    }
    if (showFontPicker) {
        FontSizePickerSheet(
            currentScale = fontScale,
            onSelect = { onFontScaleChange(it); showFontPicker = false },
            onDismiss = { showFontPicker = false }
        )
    }

    if (showPrivacySheet) {
        PrivacyPolicySheet(isDarkMode = isDarkMode, onDismiss = { showPrivacySheet = false })
    }

    if (showBackupDialog) {
        BackupDialog(onDismiss = { showBackupDialog = false })
    }

    // ── Sécurité : configuration / changement / désactivation du verrou ────────
    if (showSecuritySetup) {
        SecuritySetupDialog(
            requireAuth = false,
            onDismiss = { showSecuritySetup = false },
            onComplete = { pattern, code, enableBiometric ->
                securityViewModel.applyNewPattern(pattern, code)
                securityViewModel.setBiometricEnabled(enableBiometric)
                showSecuritySetup = false
            }
        )
    }
    if (showSecurityChange) {
        SecuritySetupDialog(
            requireAuth = true,
            onDismiss = { showSecurityChange = false },
            onComplete = { pattern, code, enableBiometric ->
                securityViewModel.applyNewPattern(pattern, code)
                securityViewModel.setBiometricEnabled(enableBiometric)
                showSecurityChange = false
            }
        )
    }
    if (showSecurityDisableAuth) {
        SecurityAuthDialog(
            onDismiss = { showSecurityDisableAuth = false },
            onSuccess = {
                securityViewModel.disableLock()
                showSecurityDisableAuth = false
            }
        )
    }

    // ── Dialogues d'EXPLICATION (avant re-demande système) et de REDIRECTION
    //    (refus définitif / blocage). Aucune simulation de boîte système. ──

    // Notifications — refusées une fois : on explique, puis la VRAIE boîte système.
    if (showNotifRationale) {
        PermissionRationaleDialog(
            icon = Icons.Default.Notifications,
            title = stringResource(R.string.settings_notif_rationale_title),
            message = stringResource(R.string.settings_notif_rationale_message),
            onContinue = {
                showNotifRationale = false
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = { showNotifRationale = false }
        )
    }
    // Notifications — bloquées / refus définitif : redirection Réglages notifications.
    if (showNotifSettings) {
        PermissionSettingsDialog(
            icon = Icons.Default.Notifications,
            title = stringResource(R.string.settings_notif_blocked_title),
            message = stringResource(R.string.settings_notif_blocked_message),
            onOpen = { showNotifSettings = false; openSettings(notificationSettingsIntent()) },
            onDismiss = { showNotifSettings = false }
        )
    }
    // Contacts — divulgation AVANT la demande système (Play : permission sensible).
    if (showContactsRationale) {
        PermissionRationaleDialog(
            icon = Icons.Default.Contacts,
            title = stringResource(R.string.settings_contacts_rationale_title),
            message = stringResource(R.string.settings_contacts_rationale_message),
            onContinue = {
                showContactsRationale = false
                settingsViewModel.markPermissionAsked(Manifest.permission.READ_CONTACTS)
                contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
            },
            onDismiss = { showContactsRationale = false }
        )
    }
    // Contacts — refus définitif / bloqué : redirection Réglages de l'app.
    if (showContactsSettings) {
        PermissionSettingsDialog(
            icon = Icons.Default.Contacts,
            title = stringResource(R.string.settings_contacts_blocked_title),
            message = stringResource(R.string.settings_contacts_blocked_message),
            onOpen = { showContactsSettings = false; openSettings(appDetailsIntent()) },
            onDismiss = { showContactsSettings = false }
        )
    }
    // Localisation — refusée une fois : explication puis VRAIE boîte système.
    if (showLocationRationale) {
        PermissionRationaleDialog(
            icon = Icons.Default.LocationOn,
            title = stringResource(R.string.settings_location_rationale_title),
            message = stringResource(R.string.settings_location_rationale_message),
            onContinue = {
                showLocationRationale = false
                fineLocationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            },
            onDismiss = { showLocationRationale = false }
        )
    }
    // Localisation — refus définitif : redirection Réglages de l'app.
    if (showLocationSettings) {
        PermissionSettingsDialog(
            icon = Icons.Default.LocationOn,
            title = stringResource(R.string.settings_location_blocked_title),
            message = stringResource(R.string.settings_location_blocked_message),
            onOpen = { showLocationSettings = false; openSettings(appDetailsIntent()) },
            onDismiss = { showLocationSettings = false }
        )
    }
    // Localisation en arrière-plan : Android 10 (Q) = vraie boîte système ;
    // Android 11+ (R) = redirection Réglages (« Autoriser tout le temps »).
    if (showBackgroundRationale) {
        PermissionSettingsDialog(
            icon = Icons.Default.LocationOn,
            title = stringResource(R.string.settings_location_bg_title),
            message = stringResource(R.string.settings_location_bg_message),
            onOpen = {
                showBackgroundRationale = false
                // Logique Q-runtime vs R+-Réglages factorisée (LocationUtils) → identique au
                // flux par contact, plus de divergence possible.
                LocationUtils.requestBackgroundLocation(context) {
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            },
            onDismiss = { showBackgroundRationale = false }
        )
    }
}

// ── Cartes & lignes ──────────────────────────────────────────────────────────

/** Carte de section : surface arrondie, élévation douce, titre accent en haut. */
@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        // tonalElevation : en mode sombre, la surface est éclaircie (overlay M3) →
        // les cartes ressortent du fond ; shadowElevation gère l'ombre en clair.
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)
            )
            content()
        }
    }
}

/** Ligne avec interrupteur (Switch Material 3). */
@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = subtitle?.let {
            { Text(it, style = MaterialTheme.typography.bodySmall) }
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    )
}

/** Ligne « sélecteur » : valeur courante (couleur accent) + chevron ▼. */
@Composable
private fun SettingsValueRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}

/** Ligne de navigation : chevron ▶ à droite. */
@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = subtitle?.let {
            { Text(it, style = MaterialTheme.typography.bodySmall) }
        },
        trailingContent = {
            // AutoMirrored : la flèche se reflète automatiquement en RTL (arabe).
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

/** Ligne d'avertissement (ton « erreur » doux), cliquable → ouvre des réglages. */
@Composable
private fun SettingsWarningRow(text: String, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                Icons.Default.LocationOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        headlineContent = {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        }
    )
}

// ── Sélecteurs (bottom sheets) ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ThemePickerSheet(
    selected: ThemePreset,
    onSelect: (ThemePreset) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_color_palette),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 3
            ) {
                ThemePreset.values().forEach { preset ->
                    ThemePresetCard(
                        preset = preset,
                        isSelected = preset == selected,
                        onClick = { onSelect(preset) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerSheet(
    currentTag: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.settings_language_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            LANGUAGE_OPTIONS.forEach { (tag, labelRes) ->
                PickerOptionRow(
                    label = stringResource(labelRes),
                    selected = tag == currentTag,
                    onClick = { onSelect(tag) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontSizePickerSheet(
    currentScale: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.settings_font_size_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            val baseDensity = LocalDensity.current
            FONT_SCALE_OPTIONS.forEach { (scale, labelRes) ->
                val isSelected = abs(scale - currentScale) < 0.01f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(scale) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        // Aperçu EN DIRECT à l'échelle de l'option (densité surchargée
                        // localement, indépendamment du réglage global courant).
                        CompositionLocalProvider(
                            LocalDensity provides Density(baseDensity.density, scale)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_font_preview_text),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/** Ligne d'option simple (libellé + coche si sélectionnée) pour les sélecteurs. */
@Composable
private fun PickerOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Explication courte AVANT de relancer la vraie boîte de dialogue système. */
@Composable
private fun PermissionRationaleDialog(
    icon: ImageVector,
    title: String,
    message: String,
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onContinue) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/** Redirection vers les Réglages système quand l'OS ne montrera plus sa boîte. */
@Composable
private fun PermissionSettingsDialog(
    icon: ImageVector,
    title: String,
    message: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onOpen) {
                Text(stringResource(R.string.gallery_permission_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

// ── Theme Preset Card ──────────────────────────────────────────────────────────

@Composable
private fun ThemePresetCard(
    preset: ThemePreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val themeName = stringResource(preset.labelRes)
    val themeLabel = stringResource(R.string.settings_theme_cd, themeName)

    Card(
        modifier = Modifier
            .width(88.dp)
            .clickable { onClick() }
            .semantics { contentDescription = themeLabel },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ColorDot(color = preset.previewPrimary, size = 22)
                ColorDot(color = preset.previewSecondary, size = 18)
                ColorDot(color = preset.previewTertiary, size = 14)
            }

            Text(
                text = themeName,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ColorDot(color: Color, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
    )
}

// ── Politique de confidentialité ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacyPolicySheet(isDarkMode: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(screenHeight * 0.82f)
                .navigationBarsPadding(),
            factory = { ctx ->
                WebView(ctx).apply {
                    isNestedScrollingEnabled = true
                    settings.apply {
                        javaScriptEnabled = false
                        domStorageEnabled = false
                        builtInZoomControls = false
                        displayZoomControls = false
                        loadWithOverviewMode = false
                        useWideViewPort = false
                    }
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN ->
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            android.view.MotionEvent.ACTION_UP -> {
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                                // a11y : annonce un « clic » pour TalkBack ; le défilement de
                                // la WebView n'est pas consommé (on renvoie false).
                                v.performClick()
                            }
                            android.view.MotionEvent.ACTION_CANCEL ->
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        false
                    }
                    val raw = ctx.resources.openRawResource(R.raw.privacy_policy)
                        .bufferedReader().use { it.readText() }
                    val themed = if (isDarkMode)
                        raw.replace("<html ", "<html class=\"dark\" ")
                    else raw
                    loadDataWithBaseURL(null, themed, "text/html", "UTF-8", null)
                }
            }
        )
    }
}
