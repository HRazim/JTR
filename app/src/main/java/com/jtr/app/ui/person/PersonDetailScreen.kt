package com.jtr.app.ui.person

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.os.Build
import android.view.MotionEvent
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import coil.request.ImageRequest
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jtr.app.R
import com.jtr.app.utils.getSocialIcon
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.SocialLinkEntity
import com.jtr.app.utils.SocialPlatform
import com.jtr.app.utils.extractSocialLinks
import com.jtr.app.utils.icon
import com.jtr.app.utils.openSocialLink
import com.jtr.app.utils.SocialLink
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    person: Person?,
    categoryNames: List<String> = emptyList(),
    onNavigateBack: () -> Unit,
    onDeleteClick: () -> Unit,
    onNavigateToMap: () -> Unit = {},
    cityFromMap: String? = null,
    latFromMap: Double? = null,
    lngFromMap: Double? = null,
    onMapResultConsumed: () -> Unit = {}
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddLinkDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val editVm: EditPersonViewModel = viewModel()
    val isEditing by editVm.isEditing.collectAsStateWithLifecycle()
    val isLoading by editVm.isLoading.collectAsStateWithLifecycle()
    val vmFirstName by editVm.firstName.collectAsStateWithLifecycle()
    val vmLastName by editVm.lastName.collectAsStateWithLifecycle()
    val vmGender by editVm.gender.collectAsStateWithLifecycle()
    val vmBirthdate by editVm.birthdate.collectAsStateWithLifecycle()
    val vmBirthdateNotify by editVm.birthdateNotify.collectAsStateWithLifecycle()
    val vmCity by editVm.city.collectAsStateWithLifecycle()
    val vmCityLat by editVm.cityLat.collectAsStateWithLifecycle()
    val vmCityNotify by editVm.cityNotify.collectAsStateWithLifecycle()
    val vmOrigin by editVm.origin.collectAsStateWithLifecycle()
    val vmLikes by editVm.likes.collectAsStateWithLifecycle()
    val vmNotes by editVm.notes.collectAsStateWithLifecycle()
    val vmPhotoUri by editVm.photoUri.collectAsStateWithLifecycle()
    val firstNameError by editVm.firstNameError.collectAsStateWithLifecycle()
    val socialLinks by editVm.socialLinks.collectAsStateWithLifecycle()

    LaunchedEffect(person?.id) { person?.id?.let { editVm.loadPerson(it) } }

    LaunchedEffect(cityFromMap) {
        val c = cityFromMap ?: return@LaunchedEffect
        editVm.onCityFromMap(c, latFromMap, lngFromMap)
        onMapResultConsumed()
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) editVm.onPhotoSelected(uri)
    }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = vmBirthdate)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditing) stringResource(R.string.person_edit_title)
                        else person?.fullName ?: stringResource(R.string.person_not_found)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isEditing) editVm.cancelEdit() else onNavigateBack()
                    }) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Close
                                          else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEditing) stringResource(R.string.common_cancel)
                                                 else stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    if (!isEditing) {
                        IconButton(onClick = { editVm.enterEditMode() }) {
                            Icon(Icons.Default.Edit,
                                contentDescription = stringResource(R.string.common_edit),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isEditing) MaterialTheme.colorScheme.secondaryContainer
                                     else MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = if (isEditing) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = isEditing,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.person_save)) },
                    icon = { Icon(Icons.Default.Check, contentDescription = null) },
                    onClick = { editVm.commitAllEdits() },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { paddingValues ->

        if (isLoading || person == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center) {
                if (isLoading) CircularProgressIndicator()
                else Text(stringResource(R.string.person_not_found))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .pointerInput(isEditing) {
                    if (!isEditing) detectTapGestures(onDoubleTap = { editVm.enterEditMode() })
                }
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            if (!isEditing) {
                Text(
                    stringResource(R.string.person_double_tap_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(8.dp))
            }

            Box(modifier = Modifier.size(96.dp)) {
                val photoSrc = if (isEditing) vmPhotoUri else person.photoUri

                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .then(
                            if (photoSrc == null)
                                Modifier.background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                )
                            else Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                        )
                        .then(if (isEditing) Modifier.blur(8.dp) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (photoSrc != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(photoSrc).crossfade(300).build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = person.initials,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                EditPhotoOverlay(isEditing = isEditing) {
                    photoPicker.launch(PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    ))
                }
            }

            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState = isEditing,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "name_section"
            ) { editing ->
                if (editing) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var localFirst by remember(vmFirstName) { mutableStateOf(vmFirstName) }
                        var localLast by remember(vmLastName) { mutableStateOf(vmLastName) }
                        OutlinedTextField(
                            value = localFirst,
                            onValueChange = { localFirst = it; editVm.onFirstNameChanged(it) },
                            label = { Text(stringResource(R.string.person_first_name_label)) },
                            isError = firstNameError,
                            supportingText = if (firstNameError) ({
                                Text(stringResource(R.string.person_first_name_required))
                            }) else null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = localLast,
                            onValueChange = { localLast = it; editVm.onLastNameChanged(it) },
                            label = { Text(stringResource(R.string.person_last_name_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = person.fullName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold)
                        if (person.isFavorite) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Favorite, null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.person_favorite_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            if (categoryNames.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categoryNames.forEach { name ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(name) },
                            icon = { Icon(Icons.Default.Folder, null, Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SocialLinksSection(
                links = socialLinks,
                isEditing = isEditing,
                onAddClick = { showAddLinkDialog = true },
                onRemoveClick = { editVm.removeSocialLink(it) }
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            AnimatedContent(
                targetState = isEditing,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "gender_section"
            ) { editing ->
                if (editing) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(stringResource(R.string.person_gender_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf(
                                "male"       to stringResource(R.string.person_gender_male),
                                "female"     to stringResource(R.string.person_gender_female),
                                "non-binary" to stringResource(R.string.person_gender_nonbinary_short)
                            ).forEachIndexed { i, (value, label) ->
                                SegmentedButton(
                                    selected = vmGender == value,
                                    onClick = {
                                        editVm.onGenderChanged(if (vmGender == value) null else value)
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = i, count = 3)
                                ) { Text(label) }
                            }
                        }
                    }
                } else {
                    if (person.gender != null) {
                        DetailRow(
                            icon = Icons.Default.Person,
                            label = stringResource(R.string.person_gender_label),
                            value = when (person.gender) {
                                "male"       -> stringResource(R.string.person_gender_male)
                                "female"     -> stringResource(R.string.person_gender_female)
                                "non-binary" -> stringResource(R.string.person_gender_nonbinary)
                                else         -> person.gender
                            }
                        )
                    } else {
                        Spacer(Modifier.height(0.dp))
                    }
                }
            }

            if (isEditing) {
                Spacer(Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = vmBirthdate?.let {
                            SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(it))
                        } ?: "",
                        onValueChange = {},
                        label = { Text(stringResource(R.string.person_birthday_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true, enabled = false,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showDatePicker = true }) {
                            Text(
                                if (vmBirthdate == null) stringResource(R.string.person_birthday_select)
                                else stringResource(R.string.person_birthday_modify)
                            )
                        }
                        if (vmBirthdate != null) {
                            TextButton(onClick = { editVm.onBirthdateChanged(null) }) {
                                Text(stringResource(R.string.person_birthday_clear),
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = vmBirthdateNotify,
                            onCheckedChange = { editVm.onBirthdateNotifyChanged(it) })
                        Text(stringResource(R.string.person_birthday_notify),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else if (person.birthdate != null) {
                DetailRow(
                    icon = Icons.Default.Cake,
                    label = stringResource(R.string.person_birthday_label),
                    value = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
                        .format(Date(person.birthdate))
                )
            }

            if (isEditing) {
                Spacer(Modifier.height(8.dp))
                var localCity by remember(vmCity) { mutableStateOf(vmCity) }
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = localCity,
                        onValueChange = { localCity = it; editVm.onCityChanged(it) },
                        label = { Text(stringResource(R.string.person_city_label)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (vmCityLat != null) {
                                Icon(Icons.Default.MyLocation, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                    IconButton(onClick = onNavigateToMap, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Default.Map,
                            contentDescription = stringResource(R.string.person_city_map_cd),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = vmCityNotify,
                        onCheckedChange = { editVm.onCityNotifyChanged(it) })
                    Text(stringResource(R.string.person_city_notify),
                        style = MaterialTheme.typography.bodySmall)
                }
            } else if (person.city != null) {
                CityDetailRow(city = person.city,
                    cityLat = person.cityLat, cityLng = person.cityLng)
            }

            if (isEditing) {
                Spacer(Modifier.height(8.dp))
                var localOrigin by remember(vmOrigin) { mutableStateOf(vmOrigin) }
                OutlinedTextField(
                    value = localOrigin,
                    onValueChange = { localOrigin = it; editVm.onOriginChanged(it) },
                    label = { Text(stringResource(R.string.person_origin_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            } else if (person.origin != null) {
                DetailRow(icon = Icons.Default.Public,
                    label = stringResource(R.string.person_origin_label),
                    value = person.origin)
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            if (isEditing) {
                var localLikes by remember(vmLikes) { mutableStateOf(vmLikes) }
                OutlinedTextField(
                    value = localLikes,
                    onValueChange = { localLikes = it; editVm.onLikesChanged(it) },
                    label = { Text(stringResource(R.string.person_likes_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2, maxLines = 4,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
            } else if (person.likes != null) {
                DetailTextBlock(icon = Icons.Default.Favorite,
                    label = stringResource(R.string.person_likes_label),
                    value = person.likes)
            }

            if (isEditing) {
                var localNotes by remember(vmNotes) { mutableStateOf(vmNotes) }
                OutlinedTextField(
                    value = localNotes,
                    onValueChange = { localNotes = it; editVm.onNotesChanged(it) },
                    label = { Text(stringResource(R.string.person_notes_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3, maxLines = 8,
                    shape = RoundedCornerShape(12.dp)
                )
            } else if (person.notes != null) {
                DetailTextBlock(icon = Icons.Filled.Notes,
                    label = stringResource(R.string.person_notes_label),
                    value = person.notes)
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    val raw = datePickerState.selectedDateMillis
                    if (raw != null) {
                        val tz = TimeZone.getDefault()
                        val adjusted = raw + tz.getOffset(raw)
                        editVm.onBirthdateChanged(adjusted)
                    }
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showAddLinkDialog) {
        AddSocialLinkDialog(
            onConfirm = { url -> editVm.addSocialLink(url) },
            onDismiss = { showAddLinkDialog = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.person_delete_dialog_title)) },
            text = { Text(stringResource(R.string.person_delete_dialog_text)) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDeleteClick() }) {
                    Text(stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun EditPhotoOverlay(isEditing: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(visible = isEditing, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CameraAlt, contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.person_edit_photo_label),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun AddSocialLinkDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    val detected by remember { derivedStateOf { extractSocialLinks(url).firstOrNull() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.person_add_link_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.person_add_link_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp)
                )
                AnimatedVisibility(visible = detected != null) {
                    val platform = detected?.platform ?: return@AnimatedVisibility
                    val bgColor = Color(platform.argbColor)
                    val contentColor = if (platform == SocialPlatform.Snapchat) Color.Black else Color.White
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledIconButton(
                            onClick = {},
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = bgColor, contentColor = contentColor),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(platform.icon(), null, modifier = Modifier.size(18.dp))
                        }
                        Text(platform.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url); onDismiss() },
                enabled = url.isNotBlank()
            ) { Text(stringResource(R.string.person_add_link_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun SocialLinksSection(
    links: List<SocialLinkEntity>,
    isEditing: Boolean,
    onAddClick: () -> Unit,
    onRemoveClick: (String) -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (links.isEmpty() && !isEditing) return@Column

        AnimatedContent(
            targetState = isEditing,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "social_links_content"
        ) { editing ->
            if (editing) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    links.forEach { link ->
                        val platform = SocialPlatform.all.firstOrNull {
                            it.displayName == link.platform
                        }
                        val bgColor = Color(platform?.argbColor ?: 0xFF607D8BL)
                        val contentColor = if (platform == SocialPlatform.Snapchat) Color.Black else Color.White
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledIconButton(
                                onClick = {},
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = bgColor, contentColor = contentColor),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(platform?.icon() ?: Icons.Default.Link, null,
                                    modifier = Modifier.size(18.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(link.platform,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text(link.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1)
                            }
                            IconButton(onClick = { onRemoveClick(link.id) }) {
                                Icon(Icons.Default.DeleteOutline,
                                    contentDescription = stringResource(R.string.person_social_delete_cd),
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = onAddClick,
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(Icons.Default.AddLink, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.person_add_social_link))
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    links.forEach { link ->
                        val platform = SocialPlatform.all.firstOrNull { it.displayName == link.platform }
                        val socialLink = SocialLink(
                            platform = platform ?: SocialPlatform.Instagram,
                            url = link.url
                        )
                        Icon(
                            painter = painterResource(getSocialIcon(link.url)),
                            contentDescription = link.platform,
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { openSocialLink(context, socialLink) },
                            tint = Color.Unspecified
                        )
                    }
                    FilledTonalIconButton(onClick = onAddClick, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Add,
                            contentDescription = stringResource(R.string.common_add),
                            modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CityDetailRow(city: String, cityLat: Double?, cityLng: Double?) {
    val hasCoords = cityLat != null && cityLng != null
    var showMap by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.LocationOn, null, Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.person_city_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(city, style = MaterialTheme.typography.bodyLarge)
        }
        if (hasCoords) {
            IconButton(onClick = { showMap = !showMap }) {
                Icon(
                    if (showMap) Icons.Default.Map else Icons.Outlined.Map,
                    contentDescription = if (showMap) stringResource(R.string.person_map_hide_cd)
                                         else stringResource(R.string.person_map_cd),
                    tint = if (showMap) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (hasCoords) {
        AnimatedVisibility(visible = showMap, enter = expandVertically(), exit = shrinkVertically()) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                MapLibreMiniMap(lat = cityLat!!, lng = cityLng!!, cityName = city,
                    modifier = Modifier.fillMaxWidth().height(180.dp))
                Text("© OpenStreetMap contributors | MapLibre",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun MapLibreMiniMap(lat: Double, lng: Double, cityName: String, modifier: Modifier) {
    val context = LocalContext.current
    val mapView = remember { MapView(context).also { it.onCreate(null) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(context) {
        val cb = object : android.content.ComponentCallbacks2 {
            override fun onTrimMemory(l: Int) = mapView.onLowMemory()
            override fun onConfigurationChanged(c: android.content.res.Configuration) = Unit
            override fun onLowMemory() = mapView.onLowMemory()
        }
        context.registerComponentCallbacks(cb)
        onDispose { context.unregisterComponentCallbacks(cb) }
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            if (lifecycle.currentState != Lifecycle.State.DESTROYED) { mapView.onStop(); mapView.onDestroy() }
        }
    }
    AndroidView(factory = { _ ->
        mapView.apply {
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_POINTER_DOWN ->
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL ->
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
            getMapAsync { map ->
                map.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                    map.uiSettings.isScrollGesturesEnabled = true
                    map.uiSettings.isZoomGesturesEnabled = true
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 12.0))
                    @Suppress("DEPRECATION")
                    map.addMarker(MarkerOptions().position(LatLng(lat, lng)).title(cityName))
                }
            }
        }
    }, modifier = modifier.clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)))
}

@Composable
fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun DetailTextBlock(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(24.dp).padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
