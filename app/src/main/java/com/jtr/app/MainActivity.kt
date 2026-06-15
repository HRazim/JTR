package com.jtr.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.security.LockScreen
import com.jtr.app.security.SecurityManager
import com.jtr.app.ui.navigation.JTRMainScaffold
import com.jtr.app.utils.JtrNotificationManager
import com.jtr.app.utils.LocaleManager
import com.jtr.app.ui.theme.JTRTheme
import com.jtr.app.ui.theme.ThemeViewModel

// FragmentActivity (et non ComponentActivity) : requis par BiometricPrompt
// (déverrouillage biométrique v6.2.0). FragmentActivity EST une ComponentActivity,
// donc viewModels()/setContent/enableEdgeToEdge restent valides.
class MainActivity : FragmentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()

    // Applique la langue applicative choisie (LocaleManager) au contexte de cette
    // Activity — indispensable pour une ComponentActivity 100 % Compose, où
    // AppCompatDelegate ne suffit pas. Relu à chaque (re)création → persistance.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = PersonRepository(applicationContext)

        // Deep link du Moteur de Proximité (v5.4) : le tap sur la notification
        // transporte l'id du contact → ouverture directe de sa fiche.
        val notificationPersonId = intent.getStringExtra(JtrNotificationManager.EXTRA_PERSON_ID)

        setContent {
            val isDarkMode by themeViewModel.isDarkMode.collectAsState()
            val selectedPreset by themeViewModel.selectedPreset.collectAsState()
            val fontScale by themeViewModel.fontScale.collectAsState()

            JTRTheme(darkTheme = isDarkMode, preset = selectedPreset) {
                // Échelle de police choisie dans l'app appliquée à TOUT le texte
                // (accessibilité, plafond strict 1.30 garanti par ThemeViewModel).
                // On remplace l'échelle système par la nôtre → mise en page stable
                // et bornée, indépendamment du réglage système.
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, fontScale)
                ) {
                    // ── Garde de verrou local (v6.2.0, re-verrouillage v6.2.3) ──
                    // isUnlocked = vrai d'emblée si le verrou n'est PAS actif.
                    // Le re-verrouillage est piloté par le PROCESS (ProcessLifecycleOwner)
                    // et non par l'Activity : un recreate() (changement de langue/config)
                    // ne fait PAS passer le process en arrière-plan, donc son ON_STOP ne
                    // se déclenche QUE lors d'un vrai retour d'arrière-plan. Résultat : le
                    // changement de langue ne re-verrouille plus et n'auto-invoque plus la
                    // biométrie ; le verrou reste affiché au démarrage à froid et au vrai
                    // retour d'arrière-plan.
                    val context = LocalContext.current
                    var isUnlocked by rememberSaveable {
                        mutableStateOf(!SecurityManager.isLockEnabled(context))
                    }
                    DisposableEffect(Unit) {
                        val processLifecycle = ProcessLifecycleOwner.get().lifecycle
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_STOP) isUnlocked = false
                        }
                        processLifecycle.addObserver(observer)
                        onDispose { processLifecycle.removeObserver(observer) }
                    }
                    val locked = SecurityManager.isLockEnabled(context) && !isUnlocked

                    if (locked) {
                        LockScreen(onUnlocked = { isUnlocked = true })
                    } else {
                        // Permission POST_NOTIFICATIONS (Android 13+) : sans elle, AUCUNE
                        // notification locale (anniversaires, proximité) ne s'affiche, même
                        // si le canal est créé. Demandée une seule fois au premier lancement.
                        RequestNotificationPermissionOnce()
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            val navController = rememberNavController()
                            JTRMainScaffold(
                                navController = navController,
                                repository = repository,
                                isDarkMode = isDarkMode,
                                onDarkModeChange = { themeViewModel.setDarkMode(it) },
                                selectedPreset = selectedPreset,
                                onPresetSelected = { themeViewModel.setPreset(it) },
                                fontScale = fontScale,
                                onFontScaleChange = { themeViewModel.setFontScale(it) },
                                notificationPersonId = notificationPersonId
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Demande la permission POST_NOTIFICATIONS au premier lancement sur Android 13+.
 * Sans cette permission accordée à l'exécution, le système ne diffuse aucune
 * notification locale (le canal seul ne suffit pas) : c'était la cause des
 * notifications d'anniversaire absentes le jour J. No-op sur API < 33 ou si la
 * permission est déjà accordée.
 */
@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* L'état est relu à la prochaine notification ; rien à faire ici. */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            // Drapeau partagé avec le flux des Paramètres (SettingsViewModel) : marque
            // la permission comme « déjà demandée » pour distinguer plus tard
            // « jamais demandée » de « refusée définitivement ».
            context.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("asked_${Manifest.permission.POST_NOTIFICATIONS}", true)
                .apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
