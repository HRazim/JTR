package com.jtr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.ui.navigation.JTRMainScaffold
import com.jtr.app.ui.theme.JTRTheme
import com.jtr.app.ui.theme.ThemeViewModel

class MainActivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = PersonRepository(applicationContext)

        setContent {
            val isDarkMode by themeViewModel.isDarkMode.collectAsState()
            val selectedPreset by themeViewModel.selectedPreset.collectAsState()

            JTRTheme(darkTheme = isDarkMode, preset = selectedPreset) {
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
                        onPresetSelected = { themeViewModel.setPreset(it) }
                    )
                }
            }
        }
    }
}
