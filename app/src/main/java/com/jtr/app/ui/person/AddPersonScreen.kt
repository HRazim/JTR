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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
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
    // Champs texte : état LOCAL pour ne pas briser la composition IME des accents (é, à, ç…).
    // Le round-trip StateFlow→recomposition interrompt la séquence IME et avale le caractère.
    // Le ViewModel reste notifié à chaque frappe pour persister l'état lors de la navigation.
    var firstName by remember { mutableStateOf(viewModel.firstName.value) }
    var lastName  by remember { mutableStateOf(viewModel.lastName.value) }
    var city      by remember { mutableStateOf(TextFieldValue(viewModel.city.value)) }
    var origin    by remember { mutableStateOf(viewModel.origin.value) }
    var likes     by remember { mutableStateOf(viewModel.likes.value) }
    var notes     by remember { mutableStateOf(viewModel.notes.value) }

    // Champs non-texte : pas de composition IME, le StateFlow est utilisé normalement.
    val gender         by viewModel.gender.collectAsStateWithLifecycle()
    val birthdate      by viewModel.birthdate.collectAsStateWithLifecycle()
    val cityLat        by viewModel.cityLat.collectAsStateWithLifecycle()
    val photoUri       by viewModel.photoUri.collectAsStateWithLifecycle()
    val firstNameError by viewModel.firstNameError.collectAsStateWithLifecycle()

    val cityFocusRequester = remember { FocusRequester() }

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
                title = { Text("Ajouter une personne") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
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
            // Photo de profil
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (photoUri != null) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "Photo de profil",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp))
                        Text("Photo", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            HorizontalDivider()

            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it; viewModel.onFirstNameChanged(it) },
                label = { Text("Prénom *") },
                isError = firstNameError,
                supportingText = { if (firstNameError) Text("Le prénom est obligatoire") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it; viewModel.onLastNameChanged(it) },
                label = { Text("Nom de famille") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Text("Genre", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.Start)) {
                listOf("male" to "Homme", "female" to "Femme", "non-binary" to "Non-binaire")
                    .forEach { (value, label) ->
                        FilterChip(
                            selected = gender == value,
                            onClick = { viewModel.onGenderChanged(if (gender == value) null else value) },
                            label = { Text(label) }
                        )
                    }
            }

            // Fix datepicker : affichage en heure locale
            OutlinedTextField(
                value = birthdate?.let {
                    SimpleDateFormat("d MMMM yyyy", Locale.FRENCH).format(Date(it))
                } ?: "",
                onValueChange = {},
                label = { Text("Anniversaire") },
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
                Text(if (birthdate == null) "Sélectionner une date" else "Modifier la date")
            }

            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it; viewModel.onCityChanged(it.text) },
                    label = { Text("Ville") },
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
                    Icon(Icons.Default.Map, contentDescription = "Choisir sur la carte",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }

            HorizontalDivider()
            Text("Informations personnelles",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Start))

            OutlinedTextField(
                value = origin,
                onValueChange = { origin = it; viewModel.onOriginChanged(it) },
                label = { Text("Origine") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = likes,
                onValueChange = { likes = it; viewModel.onLikesChanged(it) },
                label = { Text("Ce qu'il/elle aime") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2, maxLines = 4,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it; viewModel.onNotesChanged(it) },
                label = { Text("Notes diverses") },
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
                Text("Sauvegarder", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (showDatePicker) {
            // Fix datepicker : convertir le millis stocké (local) en UTC midnight pour l'affichage
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
                            // selectedDateMillis = minuit UTC → convertir en midi heure locale
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
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Annuler") }
                }
            ) { DatePicker(state = datePickerState) }
        }
    }
}
