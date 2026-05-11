package com.jtr.app.ui.settings

import android.webkit.WebView
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jtr.app.R
import com.jtr.app.ui.theme.ThemePreset
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDarkMode: Boolean = false,
    onDarkModeChange: (Boolean) -> Unit = {},
    selectedPreset: ThemePreset = ThemePreset.JTR_SIGNATURE,
    onPresetSelected: (ThemePreset) -> Unit = {},
    onNavigateToTrash: () -> Unit = {},
    settingsViewModel: SettingsViewModel = viewModel()
) {
    var showPrivacySheet by remember { mutableStateOf(false) }
    val notificationsEnabled by settingsViewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val proximityEnabled by settingsViewModel.proximityEnabled.collectAsStateWithLifecycle()
    val birthdayEnabled by settingsViewModel.birthdayEnabled.collectAsStateWithLifecycle()
    val proximityRadiusKm by settingsViewModel.proximityRadiusKm.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            SettingsSection(title = stringResource(R.string.settings_section_notifications))

            SettingsSwitch(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.settings_notifications_title),
                subtitle = stringResource(R.string.settings_notifications_subtitle),
                checked = notificationsEnabled,
                onCheckedChange = { settingsViewModel.setNotificationsEnabled(it) }
            )

            SettingsSwitch(
                icon = Icons.Default.LocationOn,
                title = stringResource(R.string.settings_proximity_title),
                subtitle = stringResource(R.string.settings_proximity_subtitle),
                checked = proximityEnabled && notificationsEnabled,
                enabled = notificationsEnabled,
                onCheckedChange = { settingsViewModel.setProximityEnabled(it) }
            )

            if (proximityEnabled && notificationsEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_proximity_radius_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_proximity_radius_km, proximityRadiusKm.roundToInt()),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = proximityRadiusKm,
                        onValueChange = { settingsViewModel.setProximityRadiusKm(it) },
                        valueRange = 1f..50f,
                        steps = 48,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.settings_proximity_radius_min),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.settings_proximity_radius_max),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SettingsSwitch(
                icon = Icons.Default.Cake,
                title = stringResource(R.string.settings_birthday_title),
                subtitle = stringResource(R.string.settings_birthday_subtitle),
                checked = birthdayEnabled && notificationsEnabled,
                enabled = notificationsEnabled,
                onCheckedChange = { settingsViewModel.setBirthdayEnabled(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSection(title = stringResource(R.string.settings_section_appearance))

            SettingsSwitch(
                icon = Icons.Default.DarkMode,
                title = stringResource(R.string.settings_dark_mode_title),
                subtitle = stringResource(R.string.settings_dark_mode_subtitle),
                checked = isDarkMode,
                onCheckedChange = onDarkModeChange
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SettingsSection(title = stringResource(R.string.settings_section_customization))

            Text(
                text = stringResource(R.string.settings_color_palette),
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
            SettingsSection(title = stringResource(R.string.settings_section_data))

            ListItem(
                leadingContent = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                headlineContent = { Text(stringResource(R.string.settings_trash_title)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.settings_trash_subtitle),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clickable { onNavigateToTrash() }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSection(title = stringResource(R.string.settings_section_legal))

            ListItem(
                leadingContent = {
                    Icon(
                        Icons.Default.PrivacyTip,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                headlineContent = { Text(stringResource(R.string.settings_privacy_title)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.settings_privacy_subtitle),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clickable { showPrivacySheet = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSection(title = stringResource(R.string.settings_section_about))

            ListItem(
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_version_title)) },
                supportingContent = { Text(stringResource(R.string.settings_version_value)) }
            )
        }
    }

    if (showPrivacySheet) {
        PrivacyPolicySheet(isDarkMode = isDarkMode, onDismiss = { showPrivacySheet = false })
    }
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

// ── Reusable Settings composables ─────────────────────────────────────────────

@Composable
fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

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
                            android.view.MotionEvent.ACTION_UP,
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
