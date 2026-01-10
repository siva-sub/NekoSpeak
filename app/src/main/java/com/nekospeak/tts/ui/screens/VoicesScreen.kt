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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicesScreen(
    navController: NavController,
    viewModel: VoicesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PrefsManager(context) }
    
    // Test Speech State
    var testText by remember { mutableStateOf("Hello, I am NekoSpeak.") }
    var speechRate by remember { mutableFloatStateOf(1.0f) }
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var showLanguageModal by remember { mutableStateOf(false) }
    var showRegionModal by remember { mutableStateOf(false) }
    var showGenderModal by remember { mutableStateOf(false) }
    var showQualityModal by remember { mutableStateOf(false) }
    
    DisposableEffect(Unit) {
        // Explicitly use our own engine package to ensure we test NekoSpeak
        // regardless of the system-wide default setting.
        tts = TextToSpeech(context, { status ->
             if (status == TextToSpeech.SUCCESS) {
                 // Init
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Voices", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${uiState.filteredVoices.size} voices available",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Test Speech Bar
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = testText,
                        onValueChange = { testText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type to speak...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                             val voiceId = uiState.selectedVoiceId ?: prefs.currentVoice
                             val params = android.os.Bundle()
                             params.putString("voiceName", voiceId)
                             
                             // Graceful recovery: stop, and if isSpeaking was true after stop, recreate TTS
                             val wasSpeaking = tts?.isSpeaking == true
                             tts?.stop()
                             
                             // If TTS was stuck or in an error state, recreate it
                             if (wasSpeaking) {
                                 // Give a brief moment for stop to take effect
                                 tts?.shutdown()
                                 tts = TextToSpeech(context, { _ -> }, "com.nekospeak.tts")
                             }
                             
                             // Set the speech rate from preferences
                             tts?.setSpeechRate(prefs.speechSpeed)
                             
                             tts?.speak(testText, TextToSpeech.QUEUE_FLUSH, params, "test_id")
                        },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.PlayArrow, "Speak")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search voices...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            
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
                    contentPadding = PaddingValues(start=16.dp, end=16.dp, bottom=100.dp), // Extra bottom padding for TestBar
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.filteredVoices) { voice ->
                        com.nekospeak.tts.ui.components.VoiceCard(
                            voice = voice,
                            isSelected = voice.id == uiState.selectedVoiceId,
                            onVoiceSelected = { viewModel.selectVoice(voice.id) },
                            onDownload = { viewModel.downloadVoice(voice) }
                        )
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
    }
}
