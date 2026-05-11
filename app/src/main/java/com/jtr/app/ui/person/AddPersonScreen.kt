package com.jtr.app.ui.person

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jtr.app.R
import com.jtr.app.utils.getSocialIcon
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPersonScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMap: () -> Unit = {},
    cityFromMap: String? = null,
    latFromMap: Double? = null,
    lngFromMap: Double? = null,
    onMapResultConsumed: () -> Unit = {},
    viewModel: AddPersonViewModel = viewModel()
) {
    var firstName by remember { mutableStateOf(viewModel.firstName.value) }
    var lastName  by remember { mutableStateOf(viewModel.lastName.value) }
    var city      by remember { mutableStateOf(TextFieldValue(viewModel.city.value)) }
    var origin    by remember { mutableStateOf(viewModel.origin.value) }
    var likes     by remember { mutableStateOf(viewModel.likes.value) }
    var notes     by remember { mutableStateOf(viewModel.notes.value) }

    val gender          by viewModel.gender.collectAsStateWithLifecycle()
    val birthdate       by viewModel.birthdate.collectAsStateWithLifecycle()
    val birthdateNotify by viewModel.birthdateNotify.collectAsStateWithLifecycle()
    val cityLat         by viewModel.cityLat.collectAsStateWithLifecycle()
    val cityNotify      by viewModel.cityNotify.collectAsStateWithLifecycle()
    val photoUri        by viewModel.photoUri.collectAsStateWithLifecycle()
    val firstNameError  by viewModel.firstNameError.collectAsStateWithLifecycle()
    val pendingLinks    by viewModel.pendingLinks.collectAsStateWithLifecycle()

    val cityFocusRequester = remember { FocusRequester() }
    var showAddLinkDialog by remember { mutableStateOf(false) }

    LaunchedEffect(cityFromMap) {
        val c = cityFromMap ?: return@LaunchedEffect
        city = TextFieldValue(c, TextRange(c.length))
        viewModel.onCityFromMap(c, latFromMap, lngFromMap)
        onMapResultConsumed()
        cityFocusRequester.requestFocus()
    }

    var showDatePicker by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.onPhotoSelected(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.person_add_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val context = LocalContext.current

            // ── Photo ────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .then(
                        if (photoUri == null)
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
                    .clickable {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (photoUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(photoUri).crossfade(300).build(),
                        contentDescription = stringResource(R.string.person_photo_cd),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp))
                        Text(stringResource(R.string.common_photo),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White)
                    }
                }
            }

            HorizontalDivider()

            // ── Prénom ───────────────────────────────────────────────────────
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it; viewModel.onFirstNameChanged(it) },
                label = { Text(stringResource(R.string.person_first_name_label)) },
                isError = firstNameError,
                supportingText = {
                    if (firstNameError) Text(stringResource(R.string.person_first_name_error))
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // ── Nom ──────────────────────────────────────────────────────────
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it; viewModel.onLastNameChanged(it) },
                label = { Text(stringResource(R.string.person_last_name_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // ── Réseaux sociaux ──────────────────────────────────────────────
            HorizontalDivider()
            Text(
                stringResource(R.string.person_social_links_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Start)
            )
            if (pendingLinks.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pendingLinks.forEach { link ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(getSocialIcon(link.url)),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = Color.Unspecified
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    link.platform,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    link.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { viewModel.removePendingLink(link.url) }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = stringResource(R.string.person_social_delete_cd),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = { showAddLinkDialog = true },
                modifier = Modifier.align(Alignment.Start),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AddLink, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.person_add_social_link))
            }
            HorizontalDivider()

            // ── Genre ────────────────────────────────────────────────────────
            Text(stringResource(R.string.person_gender_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.Start)) {
                listOf(
                    "male"       to stringResource(R.string.person_gender_male),
                    "female"     to stringResource(R.string.person_gender_female),
                    "non-binary" to stringResource(R.string.person_gender_nonbinary)
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = gender == value,
                        onClick = { viewModel.onGenderChanged(if (gender == value) null else value) },
                        label = { Text(label) }
                    )
                }
            }

            // ── Anniversaire ─────────────────────────────────────────────────
            OutlinedTextField(
                value = birthdate?.let {
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
            TextButton(onClick = { showDatePicker = true },
                modifier = Modifier.align(Alignment.Start)) {
                Text(
                    if (birthdate == null) stringResource(R.string.person_birthday_select)
                    else stringResource(R.string.person_birthday_modify)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Switch(
                    checked = birthdateNotify,
                    onCheckedChange = { viewModel.onBirthdateNotifyChanged(it) }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.person_birthday_notify),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // ── Ville ────────────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it; viewModel.onCityChanged(it.text) },
                    label = { Text(stringResource(R.string.person_city_label)) },
                    modifier = Modifier.weight(1f).focusRequester(cityFocusRequester),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (cityLat != null) {
                            Icon(Icons.Default.MyLocation, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp))
                        }
                    }
                )
                IconButton(onClick = onNavigateToMap, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Default.Map,
                        contentDescription = stringResource(R.string.person_city_map_cd),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Switch(
                    checked = cityNotify,
                    onCheckedChange = { viewModel.onCityNotifyChanged(it) }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.person_city_notify),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // ── Infos personnelles ───────────────────────────────────────────
            HorizontalDivider()
            Text(stringResource(R.string.person_personal_info_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Start))

            OutlinedTextField(
                value = origin,
                onValueChange = { origin = it; viewModel.onOriginChanged(it) },
                label = { Text(stringResource(R.string.person_origin_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = likes,
                onValueChange = { likes = it; viewModel.onLikesChanged(it) },
                label = { Text(stringResource(R.string.person_likes_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2, maxLines = 4,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it; viewModel.onNotesChanged(it) },
                label = { Text(stringResource(R.string.person_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3, maxLines = 6,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.savePerson(onSuccess = onNavigateBack) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.person_save),
                    style = MaterialTheme.typography.titleMedium)
            }
        }

        if (showAddLinkDialog) {
            AddSocialLinkDialog(
                onConfirm = { url -> viewModel.addPendingLink(url) },
                onDismiss = { showAddLinkDialog = false }
            )
        }

        if (showDatePicker) {
            val initMillis = birthdate?.let { stored ->
                val localCal = Calendar.getInstance().apply { timeInMillis = stored }
                Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    set(localCal.get(Calendar.YEAR), localCal.get(Calendar.MONTH),
                        localCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = initMillis ?: System.currentTimeMillis()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val raw = datePickerState.selectedDateMillis
                        if (raw != null) {
                            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                                .apply { timeInMillis = raw }
                            val localNoon = Calendar.getInstance().apply {
                                set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH),
                                    utcCal.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                            viewModel.onBirthdateChanged(localNoon)
                        }
                        showDatePicker = false
                    }) { Text(stringResource(R.string.common_ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            ) { DatePicker(state = datePickerState) }
        }
    }
}
