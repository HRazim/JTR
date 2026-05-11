package com.jtr.app.ui.settings

import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jtr.app.R
import com.jtr.app.ui.theme.ThemePreset
import kotlin.math.roundToInt

private val colorPickerSwatches = listOf(
    0xFF1A1A1AL, 0xFF37474FL, 0xFF455A64L, 0xFF546E7AL,
    0xFF1565C0L, 0xFF1976D2L, 0xFF0277BDL, 0xFF0288D1L,
    0xFF00695CL, 0xFF00796BL, 0xFF2E7D32L, 0xFF388E3CL,
    0xFFAD1457L, 0xFFE91E63L, 0xFF6A1B9AL, 0xFF7B1FA2L,
    0xFFBF360CL, 0xFFE64A19L, 0xFFF57F17L, 0xFFF9A825L,
    0xFF4527A0L, 0xFF283593L, 0xFF006064L, 0xFF004D40L,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDarkMode: Boolean = false,
    onDarkModeChange: (Boolean) -> Unit = {},
    selectedPreset: ThemePreset = ThemePreset.JTR_SIGNATURE,
    onPresetSelected: (ThemePreset) -> Unit = {},
    customColor: Long = 0xFF1565C0L,
    onCustomColorSelected: (Long) -> Unit = {},
    onNavigateToTrash: () -> Unit = {},
    settingsViewModel: SettingsViewModel = viewModel()
) {
    var showPrivacySheet by remember { mutableStateOf(false) }
    var showColorPickerDialog by remember { mutableStateOf(false) }
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
                        Text(stringResource(R.string.settings_proximity_radius_min),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.settings_proximity_radius_max),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        customColor = if (preset == ThemePreset.CUSTOM) Color(customColor) else null,
                        onClick = {
                            onPresetSelected(preset)
                            if (preset == ThemePreset.CUSTOM) showColorPickerDialog = true
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSection(title = stringResource(R.string.settings_section_data))

            ListItem(
                leadingContent = {
                    Icon(Icons.Default.Delete, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error)
                },
                headlineContent = { Text(stringResource(R.string.settings_trash_title)) },
                supportingContent = { Text(stringResource(R.string.settings_trash_subtitle),
                    style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    Icon(Icons.Default.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                modifier = Modifier.clickable { onNavigateToTrash() }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSection(title = stringResource(R.string.settings_section_legal))

            ListItem(
                leadingContent = {
                    Icon(Icons.Default.PrivacyTip, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                },
                headlineContent = { Text(stringResource(R.string.settings_privacy_title)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_privacy_subtitle),
                        style = MaterialTheme.typography.bodySmall)
                },
                trailingContent = {
                    Icon(Icons.Default.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
        PrivacyPolicySheet(
            isDarkMode = isDarkMode,
            onDismiss = { showPrivacySheet = false }
        )
    }

    if (showColorPickerDialog) {
        ColorPickerDialog(
            currentColor = Color(customColor),
            onColorSelected = { color ->
                onCustomColorSelected(color.value.toLong())
                showColorPickerDialog = false
            },
            onDismiss = { showColorPickerDialog = false }
        )
    }
}

@Composable
private fun ColorPickerDialog(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedColor by remember { mutableStateOf(currentColor) }
    var hexInput by remember { mutableStateOf(
        "%06X".format(currentColor.value.toLong() and 0xFFFFFFL)
    ) }
    var hexError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(selectedColor)
                )
                Text(stringResource(R.string.settings_color_palette),
                    style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(180.dp)
                ) {
                    items(colorPickerSwatches) { argb ->
                        val c = Color(argb)
                        val isChosen = selectedColor == c
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(
                                    if (isChosen) Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.onSurface,
                                        CircleShape
                                    ) else Modifier
                                )
                                .clickable {
                                    selectedColor = c
                                    hexInput = "%06X".format(argb and 0xFFFFFFL)
                                    hexError = false
                                }
                        )
                    }
                }
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { v ->
                        hexInput = v.uppercase().take(6)
                        val parsed = v.trim().trimStart('#').takeIf { it.length == 6 }
                            ?.toLongOrNull(16)
                        if (parsed != null) {
                            selectedColor = Color(0xFF000000L or parsed)
                            hexError = false
                        } else {
                            hexError = v.isNotEmpty()
                        }
                    },
                    label = { Text("Hex (#RRGGBB)") },
                    isError = hexError,
                    singleLine = true,
                    leadingIcon = {
                        Text("#", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingIcon = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(selectedColor)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(selectedColor) }) {
                Text(stringResource(R.string.common_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun ThemePresetCard(
    preset: ThemePreset,
    isSelected: Boolean,
    customColor: Color? = null,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val themeLabel = stringResource(R.string.settings_theme_cd, preset.displayName)

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
                ColorDot(color = customColor ?: preset.previewPrimary, size = 22)
                ColorDot(color = customColor?.copy(alpha = 0.7f) ?: preset.previewSecondary, size = 18)
                ColorDot(color = customColor?.copy(alpha = 0.3f) ?: preset.previewTertiary, size = 14)
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
