package com.nekospeak.tts.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.nekospeak.tts.ui.components.VoiceCard
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
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    
    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
             if (status == TextToSpeech.SUCCESS) {
                 // Init
             }
        }
        onDispose {
            tts?.shutdown()
        }
    }
    
    // Sync ViewModel selection with Prefs
    LaunchedEffect(uiState.selectedVoiceId) {
        uiState.selectedVoiceId?.let { 
             prefs.currentVoice = it 
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
                        Text("Voices")
                        Text(
                            text = "${uiState.filteredVoices.size} voices available",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFilters() }) {
                        Icon(
                            imageVector = if (uiState.showFilters) Icons.Default.Close else Icons.Default.Menu,
                            contentDescription = "Filters"
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
                        .imePadding(), // Handle keyboard
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
                             // Hack: Setting voice name in Bundle for API < 21 compatibility if needed, 
                             // but mostly relying on service checking Prefs now.
                             // However, for immediate feedback in THIS session, we ask TTS to reload?
                             // Standard Android TTS API creates a new request.
                             // Service onSynthesizeText will be called.
                             // It will check PrefsManager (which we just updated).
                             // So saving to prefs is the key!
                             
                             val params = android.os.Bundle()
                             params.putString("voiceName", voiceId)
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
                    .padding(16.dp),
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
            
            // Filter Panel
            if (uiState.showFilters) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Gender", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.selectedGender == null,
                            onClick = { viewModel.selectGender(null) },
                            label = { Text("All") }
                        )
                        FilterChip(
                            selected = uiState.selectedGender == "Male",
                            onClick = { viewModel.selectGender("Male") },
                            label = { Text("Male") }
                        )
                        FilterChip(
                            selected = uiState.selectedGender == "Female",
                            onClick = { viewModel.selectGender("Female") },
                            label = { Text("Female") }
                        )
                    }
                    
                    Text("Region", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.selectedRegion == null,
                            onClick = { viewModel.selectRegion(null) },
                            label = { Text("All") }
                        )
                        FilterChip(
                            selected = uiState.selectedRegion == "US",
                            onClick = { viewModel.selectRegion("US") },
                            label = { Text("US") }
                        )
                         FilterChip(
                            selected = uiState.selectedRegion == "UK",
                            onClick = { viewModel.selectRegion("UK") },
                            label = { Text("UK") }
                        )
                    }
                }
            }
            
            // Voice List
            LazyColumn(
                contentPadding = PaddingValues(start=16.dp, end=16.dp, bottom=100.dp), // Extra bottom padding for TestBar
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.filteredVoices) { voice ->
                    VoiceCard(
                        voice = voice,
                        isSelected = voice.id == uiState.selectedVoiceId,
                        onVoiceSelected = { viewModel.selectVoice(voice.id) }
                    )
                }
            }
        }
    }
}
