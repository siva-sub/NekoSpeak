package com.nekospeak.tts.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.nekospeak.tts.data.PrefsManager
import com.nekospeak.tts.ui.components.VoiceCard
import com.nekospeak.tts.ui.viewmodel.VoicesViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import com.google.mlkit.nl.languageid.LanguageIdentification

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicesScreen(
    navController: NavController,
    viewModel: VoicesViewModel = viewModel(),
    pendingVoiceCloneData: Triple<String, String, String>? = null, // (path, name, transcript)
    onVoiceCloneHandled: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PrefsManager(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Test Speech State
    var testText by remember { mutableStateOf("Hello, I am NekoSpeak.") }
    var speechRate by remember { mutableFloatStateOf(prefs.speechSpeed) }
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var isSpeaking by remember { mutableStateOf(false) }
    var showLanguageModal by remember { mutableStateOf(false) }
    var showRegionModal by remember { mutableStateOf(false) }
    var showGenderModal by remember { mutableStateOf(false) }
    var showQualityModal by remember { mutableStateOf(false) }

    // Voice cloning state
    var showVoiceNameDialog by remember { mutableStateOf(false) }
    var voiceClonePath by remember { mutableStateOf<String?>(null) }
    var voiceCloneName by remember { mutableStateOf("") }
    var voiceCloneTranscript by remember { mutableStateOf("") } // Kept for API compat but not used
    var isCloning by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        // Explicitly use our own engine package to ensure we test NekoSpeak
        // regardless of the system-wide default setting.
        tts = TextToSpeech(context, { status ->
             if (status == TextToSpeech.SUCCESS) {
                 // Set up utterance progress listener to track speaking state
                 tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                     override fun onStart(utteranceId: String?) {
                         isSpeaking = true
                     }

                     override fun onDone(utteranceId: String?) {
                         isSpeaking = false
                     }

                     override fun onError(utteranceId: String?) {
                         isSpeaking = false
                     }
                 })
             }
        }, "com.nekospeak.tts")
        onDispose {
            tts?.shutdown()
        }
    }

    // Sync ViewModel selection with Prefs
    LaunchedEffect(uiState.selectedVoiceId) {
        uiState.selectedVoiceId?.let {
             prefs.currentVoice = it
             // Auto-update test text based on language
             testText = viewModel.getSampleTextForVoice(it)
        }
    }
    
    // Load initial selection and voice list from prefs
    LaunchedEffect(Unit) {
        viewModel.loadVoices() // Refresh list (in case model changed)
        viewModel.selectVoice(prefs.currentVoice)
    }
    
    // Handle pending voice clone from VoiceRecorderScreen
    LaunchedEffect(pendingVoiceCloneData) {
        pendingVoiceCloneData?.let { (path, name, transcript) ->
            if (name.isNotEmpty()) {
                // Clone directly with recorded audio and transcript
                viewModel.cloneVoice(path, name, transcript)
            } else {
                voiceClonePath = path
                voiceCloneTranscript = transcript
                showVoiceNameDialog = true
            }
            onVoiceCloneHandled()
        }
    }

    LaunchedEffect(uiState.cloneErrorMessage) {
        val message = uiState.cloneErrorMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearCloneError()
        }
    }
    
    // File picker for audio upload
    val audioPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            // Copy to cache and get path
            val inputStream = context.contentResolver.openInputStream(uri)
            val cacheFile = java.io.File(context.cacheDir, "voice_upload_${System.currentTimeMillis()}.wav")
            inputStream?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            voiceClonePath = cacheFile.absolutePath
            voiceCloneTranscript = "" // Transcript not used - cloning is audio-only
            showVoiceNameDialog = true // Go directly to name dialog (skip transcript)
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(0.45f)) {
                            Text("Voices", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "${uiState.filteredVoices.size} voices available",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Search Bar
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            modifier = Modifier
                                .weight(0.55f)
                                .height(48.dp),
                            placeholder = { Text("Search...") },
                            leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        },

        floatingActionButton = {
            if (prefs.currentModel == "pocket_v1") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(end = 60.dp) // Space for model buttons
                ) {
                    // Upload Audio File button
                    FloatingActionButton(
                        onClick = { audioPickerLauncher.launch("audio/*") },
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(Icons.Default.Add, "Upload Audio")
                    }

                    // Record Voice button
                    ExtendedFloatingActionButton(
                        onClick = { navController.navigate(com.nekospeak.tts.ui.navigation.Screen.VoiceRecorder.route) },
                        icon = { Icon(Icons.Default.PlayArrow, "Record") },
                        text = { Text("Clone Voice") },
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Filters Row
            Row(
                modifier = Modifier
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Region Filter
                FilterChip(
                    selected = uiState.selectedRegion != null,
                    onClick = { showRegionModal = true },
                    label = { Text(uiState.selectedRegion ?: "Region") },
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp)) }
                )

                // Language Filter
                FilterChip(
                    selected = uiState.selectedLanguage != null,
                    onClick = { showLanguageModal = true },
                    label = { Text(uiState.selectedLanguage ?: "Language") },
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp)) }
                )
                
                // Gender Filter
                FilterChip(
                    selected = uiState.selectedGender != null,
                    onClick = { showGenderModal = true },
                    label = { Text(uiState.selectedGender ?: "Gender") },
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp)) }
                )
                
                // Quality Filter (Piper only)
                if (uiState.availableQualities.isNotEmpty()) {
                    FilterChip(
                        selected = uiState.selectedQuality != null,
                        onClick = { showQualityModal = true },
                        label = { Text(uiState.selectedQuality ?: "Quality") },
                        trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp)) }
                    )
                }
                
                // Clear
                if (uiState.selectedRegion != null || uiState.selectedLanguage != null || uiState.selectedGender != null || uiState.selectedQuality != null) {
                    IconButton(onClick = { viewModel.clearFilters() }) {
                        Icon(Icons.Default.Clear, "Clear filters")
                    }
                }
            }
            
            // Processing Status Banner - shows when encoding voices
            uiState.processingStatus?.let { status ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            if (uiState.filteredVoices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No voices found", style = MaterialTheme.typography.titleMedium)
                        Text("Try adjusting your filters", style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = { viewModel.clearFilters() },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Clear Filters")
                        }
                    }
                }
            } else {
                // Voice List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start=16.dp, end=16.dp, top=8.dp, bottom=16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.filteredVoices) { voice ->
                        com.nekospeak.tts.ui.components.VoiceCard(
                            voice = voice,
                            isSelected = voice.id == uiState.selectedVoiceId,
                            onVoiceSelected = { viewModel.selectVoice(voice.id) },
                            onDownload = { viewModel.downloadVoice(voice) },
                            onDelete = if (voice.isCloned) {{ viewModel.deleteClonedVoice(voice.id) }} else null
                        )
                    }
                }
            }

            // Test Speech Area at bottom
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                    // Left: Text Input
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = testText,
                            onValueChange = { testText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(272.dp),
                            placeholder = { Text("Test voice...") },
                            singleLine = false,
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )

                        // Auto and Clear buttons at top-right
                        if (testText.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Auto language detect button
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            val languageIdentifier = LanguageIdentification.getClient()
                                            languageIdentifier.identifyLanguage(testText)
                                                .addOnSuccessListener { languageCode ->
                                                    // Map language to model
                                                    val newModel = when (languageCode) {
                                                        "en" -> "kokoro_v1" // English -> Kokoro
                                                        "zh", "ja", "ko", "es", "fr", "de" -> "piper_en_US-amy-low" // Multi-language -> Piper
                                                        else -> prefs.currentModel // Keep current if unknown
                                                    }
                                                    if (newModel != prefs.currentModel) {
                                                        prefs.currentModel = newModel
                                                        if (newModel.startsWith("piper")) {
                                                            prefs.currentVoice = "en_US-amy-low"
                                                        }
                                                        // Reload voices for new model
                                                        viewModel.loadVoices()
                                                        viewModel.selectVoice(prefs.currentVoice)
                                                    }
                                                }
                                                .addOnFailureListener {
                                                    // Silently fail, keep current model
                                                }
                                        }
                                    },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = "Auto",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                // Clear button
                                IconButton(
                                    onClick = { testText = "" },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Right: Play button + Model buttons
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Play/Stop Button
                        FloatingActionButton(
                            onClick = {
                                 if (isSpeaking) {
                                     tts?.stop()
                                     isSpeaking = false
                                 } else {
                                     val voiceId = uiState.selectedVoiceId ?: prefs.currentVoice
                                     val params = android.os.Bundle()
                                     params.putString("voiceName", voiceId)
                                     tts?.stop()
                                     tts?.setSpeechRate(prefs.speechSpeed)
                                     tts?.speak(testText, TextToSpeech.QUEUE_FLUSH, params, "test_id")
                                 }
                            },
                            containerColor = if (isSpeaking)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                if (isSpeaking) Icons.Default.Close else Icons.Default.PlayArrow,
                                contentDescription = if (isSpeaking) "Stop" else "Play"
                            )
                        }

                        // Model Toggle Buttons
                        val models = listOf(
                            "pocket_v1" to "PO",
                            "kokoro_v1" to "KO",
                            "kitten_nano" to "KI",
                            "piper" to "PI"
                        )

                        models.forEach { (modelId, label) ->
                            val isSelected = if (modelId == "piper") {
                                prefs.currentModel.startsWith("piper")
                            } else {
                                prefs.currentModel == modelId
                            }

                            Surface(
                                onClick = {
                                    if (modelId == "piper") {
                                        prefs.currentModel = "piper_en_US-amy-low"
                                        prefs.currentVoice = "en_US-amy-low"
                                    } else {
                                        prefs.currentModel = modelId
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                    // Speech Speed Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Slider(
                            value = speechRate,
                            onValueChange = { speechRate = it },
                            onValueChangeFinished = { prefs.speechSpeed = speechRate },
                            valueRange = 0.5f..2.0f,
                            steps = 14,
                            modifier = Modifier.weight(1f)
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(48.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1fx", speechRate),
                                style = MaterialTheme.typography.labelMedium
                            )
                            if (speechRate != 1.0f) {
                                TextButton(
                                    onClick = {
                                        speechRate = 1.0f
                                        prefs.speechSpeed = 1.0f
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(24.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Text(
                                        text = "Reset",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            } else {
                                Text(
                                    text = "Speed",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showLanguageModal) {
            ModalBottomSheet(
                onDismissRequest = { showLanguageModal = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        "Select Language",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                    HorizontalDivider()
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        viewModel.selectLanguage(null)
                                        showLanguageModal = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.selectedLanguage == null,
                                    onClick = { 
                                        viewModel.selectLanguage(null)
                                        showLanguageModal = false
                                    }
                                )
                                Text("All Languages", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        
                        items(uiState.availableLanguages) { lang ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        viewModel.selectLanguage(lang)
                                        showLanguageModal = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.selectedLanguage == lang,
                                    onClick = { 
                                        viewModel.selectLanguage(lang)
                                        showLanguageModal = false
                                    }
                                )
                                Text(lang, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
        
        if (showRegionModal) {
            ModalBottomSheet(onDismissRequest = { showRegionModal = false }) {
                Column(modifier = Modifier.padding(bottom=32.dp)) {
                    Text("Select Region", style=MaterialTheme.typography.titleLarge, modifier=Modifier.padding(16.dp))
                    HorizontalDivider()
                    uiState.availableRegions.forEach { region ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.selectRegion(region); showRegionModal = false }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(region)
                        }
                    }
                    Button(onClick={viewModel.selectRegion(null); showRegionModal=false}, modifier=Modifier.padding(16.dp).fillMaxWidth()) { Text("Clear Region") }
                }
            }
        }
        
        if (showGenderModal) {
             ModalBottomSheet(onDismissRequest = { showGenderModal = false }) {
                Column(modifier = Modifier.padding(bottom=32.dp)) {
                    Text("Select Gender", style=MaterialTheme.typography.titleLarge, modifier=Modifier.padding(16.dp))
                    HorizontalDivider()
                    listOf("Male", "Female").forEach { gender ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.selectGender(gender); showGenderModal = false }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(gender)
                        }
                    }
                    Button(onClick={viewModel.selectGender(null); showGenderModal=false}, modifier=Modifier.padding(16.dp).fillMaxWidth()) { Text("Clear Gender") }
                }
            }
        }
        
        if (showQualityModal) {
             ModalBottomSheet(onDismissRequest = { showQualityModal = false }) {
                Column(modifier = Modifier.padding(bottom=32.dp)) {
                    Text("Select Quality", style=MaterialTheme.typography.titleLarge, modifier=Modifier.padding(16.dp))
                    HorizontalDivider()
                    uiState.availableQualities.forEach { quality ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.selectQuality(quality); showQualityModal = false }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(quality.replaceFirstChar { it.uppercaseChar() })
                        }
                    }
                    Button(onClick={viewModel.selectQuality(null); showQualityModal=false}, modifier=Modifier.padding(16.dp).fillMaxWidth()) { Text("Clear Quality") }
                }
            }
        }

        // Voice Name Dialog (step 2 for file upload, or direct from recording)
        if (showVoiceNameDialog && voiceClonePath != null) {
            AlertDialog(
                onDismissRequest = { 
                    showVoiceNameDialog = false
                    voiceClonePath = null
                    voiceCloneName = ""
                    voiceCloneTranscript = ""
                },
                title = { Text("Name Your Voice") },
                text = {
                    Column {
                        Text(
                            "Give your cloned voice a name so you can find it later.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = voiceCloneName,
                            onValueChange = { voiceCloneName = it },
                            label = { Text("Voice Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.cloneVoice(voiceClonePath!!, voiceCloneName, voiceCloneTranscript)
                            showVoiceNameDialog = false
                            voiceClonePath = null
                            voiceCloneName = ""
                            voiceCloneTranscript = ""
                        },
                        enabled = voiceCloneName.isNotBlank()
                    ) {
                        Text("Clone Voice")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { 
                            showVoiceNameDialog = false 
                            voiceClonePath = null
                            voiceCloneName = ""
                            voiceCloneTranscript = ""
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
