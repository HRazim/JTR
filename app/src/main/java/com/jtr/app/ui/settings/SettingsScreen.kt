package com.jtr.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jtr.app.ui.theme.ThemePreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDarkMode: Boolean = false,
    onDarkModeChange: (Boolean) -> Unit = {},
    selectedPreset: ThemePreset = ThemePreset.AZURE,
    onPresetSelected: (ThemePreset) -> Unit = {},
    onNavigateToTrash: () -> Unit = {}
) {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var proximityEnabled by remember { mutableStateOf(true) }
    var birthdayEnabled by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection(title = "Notifications")

            SettingsSwitch(
                icon = Icons.Default.Notifications,
                title = "Activer les notifications",
                subtitle = "Active ou désactive toutes les notifications",
                checked = notificationsEnabled,
                onCheckedChange = { notificationsEnabled = it }
            )

            SettingsSwitch(
                icon = Icons.Default.LocationOn,
                title = "Rappels de proximité",
                subtitle = "Notifier quand un contact est dans la même ville",
                checked = proximityEnabled && notificationsEnabled,
                enabled = notificationsEnabled,
                onCheckedChange = { proximityEnabled = it }
            )

            SettingsSwitch(
                icon = Icons.Default.Cake,
                title = "Rappels d'anniversaire",
                subtitle = "Notifier le jour des anniversaires",
                checked = birthdayEnabled && notificationsEnabled,
                enabled = notificationsEnabled,
                onCheckedChange = { birthdayEnabled = it }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSection(title = "Apparence")

            SettingsSwitch(
                icon = Icons.Default.DarkMode,
                title = "Mode sombre",
                subtitle = "Utiliser le thème sombre",
                checked = isDarkMode,
                onCheckedChange = onDarkModeChange
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SettingsSection(title = "Personnalisation")

            Text(
                text = "Palette de couleurs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(ThemePreset.values().toList()) { preset ->
                    ThemePresetCard(
                        preset = preset,
                        isSelected = preset == selectedPreset,
                        onClick = { onPresetSelected(preset) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSection(title = "Données")

            ListItem(
                leadingContent = {
                    Icon(Icons.Default.Delete, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error)
                },
                headlineContent = { Text("Corbeille") },
                supportingContent = { Text("Contacts supprimés · purge auto après 30 jours",
                    style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    Icon(Icons.Default.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                modifier = Modifier.clickable { onNavigateToTrash() }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSection(title = "À propos")

            ListItem(
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                headlineContent = { Text("Version") },
                supportingContent = { Text("JTR 3.0-Final (PP3)") }
            )
        }
    }
}

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

    Card(
        modifier = Modifier
            .width(88.dp)
            .clickable { onClick() }
            .semantics { contentDescription = "Thème ${preset.displayName}" },
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
                text = preset.displayName,
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

@Composable
fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    )
}
