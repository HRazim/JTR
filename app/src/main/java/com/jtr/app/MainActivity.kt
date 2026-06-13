package com.jtr.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.ui.navigation.JTRMainScaffold
import com.jtr.app.utils.JtrNotificationManager
import com.jtr.app.ui.theme.JTRTheme
import com.jtr.app.ui.theme.ThemeViewModel

class MainActivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()

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

            JTRTheme(darkTheme = isDarkMode, preset = selectedPreset) {
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
                        notificationPersonId = notificationPersonId
                    )
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
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
