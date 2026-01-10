package com.nekospeak.tts.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nekospeak.tts.engine.KokoroEngine
import com.nekospeak.tts.data.PrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Voice(
    val id: String,
    val name: String,
    val language: String,
    val gender: String, // "Male" or "Female"
    val region: String, // "US" or "UK"
    val downloadState: com.nekospeak.tts.data.DownloadState = com.nekospeak.tts.data.DownloadState.Downloaded,
    val downloadProgress: Float = 0f,
    val metadata: com.nekospeak.tts.data.VoiceInfo? = null
)

enum class ViewMode {
    LIST, GRID
}

enum class VoiceSortOption(val displayName: String) {
    NAME("Name"),
    LANGUAGE("Language")
}

class VoicesViewModel(application: Application) : AndroidViewModel(application) {
    
    data class UiState(
        val voices: List<Voice> = emptyList(),
        val filteredVoices: List<Voice> = emptyList(),
        val isLoading: Boolean = true,
        val searchQuery: String = "",
        val showFilters: Boolean = false,
        val viewMode: ViewMode = ViewMode.LIST,
        val selectedVoiceId: String? = "af_heart", // Default
        
        // Filters
        val selectedLanguage: String? = null,
        val selectedGender: String? = null,
        val selectedRegion: String? = null,
        val selectedQuality: String? = null, // For Piper: x_low, low, medium, high
        val sortBy: VoiceSortOption = VoiceSortOption.NAME,
        
        val availableLanguages: List<String> = emptyList(),
        val availableRegions: List<String> = emptyList(),
        val availableQualities: List<String> = emptyList()
    )
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    private val context: Context get() = getApplication<Application>().applicationContext
    private val voiceRepo by lazy { com.nekospeak.tts.data.VoiceRepository(context) }
    private val voiceDownloader by lazy { com.nekospeak.tts.data.VoiceDownloader(context) }
    
    private val kokoroVoices = listOf(
        Voice("af_heart", "Heart", "en-us", "Female", "US"),
        Voice("af_alloy", "Alloy", "en-us", "Female", "US"),
        Voice("af_aoede", "Aoede", "en-us", "Female", "US"),
        Voice("af_bella", "Bella", "en-us", "Female", "US"),
        Voice("af_jessica", "Jessica", "en-us", "Female", "US"),
        Voice("af_kore", "Kore", "en-us", "Female", "US"),
        Voice("af_nicole", "Nicole", "en-us", "Female", "US"),
        Voice("af_nova", "Nova", "en-us", "Female", "US"),
        Voice("af_river", "River", "en-us", "Female", "US"),
        Voice("af_sarah", "Sarah", "en-us", "Female", "US"),
        Voice("af_sky", "Sky", "en-us", "Female", "US"),
        
        Voice("am_adam", "Adam", "en-us", "Male", "US"),
        Voice("am_echo", "Echo", "en-us", "Male", "US"),
        Voice("am_eric", "Eric", "en-us", "Male", "US"),
        Voice("am_fenrir", "Fenrir", "en-us", "Male", "US"),
        Voice("am_liam", "Liam", "en-us", "Male", "US"),
        Voice("am_michael", "Michael", "en-us", "Male", "US"),
        Voice("am_onyx", "Onyx", "en-us", "Male", "US"),
        Voice("am_puck", "Puck", "en-us", "Male", "US"),
        Voice("am_santa", "Santa", "en-us", "Male", "US"),

        Voice("bf_alice", "Alice", "en-gb", "Female", "UK"),
        Voice("bf_emma", "Emma", "en-gb", "Female", "UK"),
        Voice("bf_isabella", "Isabella", "en-gb", "Female", "UK"),
        Voice("bf_lily", "Lily", "en-gb", "Female", "UK"),

        Voice("bm_daniel", "Daniel", "en-gb", "Male", "UK"),
        Voice("bm_fable", "Fable", "en-gb", "Male", "UK"),
        Voice("bm_george", "George", "en-gb", "Male", "UK"),
        Voice("bm_lewis", "Lewis", "en-gb", "Male", "UK")
    )
    
    private val kittenVoices = listOf(
        Voice("expr-voice-2-f", "Kitten F2", "en-us", "Female", "US"),
        Voice("expr-voice-2-m", "Kitten M2", "en-us", "Male", "US"),
        Voice("expr-voice-3-f", "Kitten F3", "en-us", "Female", "US"),
        Voice("expr-voice-3-m", "Kitten M3", "en-us", "Male", "US"),
        Voice("expr-voice-4-f", "Kitten F4", "en-us", "Female", "US"),
        Voice("expr-voice-4-m", "Kitten M4", "en-us", "Male", "US"),
        Voice("expr-voice-5-f", "Kitten F5", "en-us", "Female", "US"),
        Voice("expr-voice-5-m", "Kitten M5", "en-us", "Male", "US"),
    )

    // Dynamic source
    private var allVoices: List<Voice> = kokoroVoices
    private var lastLoadedModel: String? = null
    
    init {
        loadVoices()
        startPollLoop()
        observePrefsChanges()
    }
    
    private fun observePrefsChanges() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(500)
                val prefs = PrefsManager(context)
                val currentModel = prefs.currentModel
                if (lastLoadedModel != null && lastLoadedModel != currentModel) {
                    // Model changed externally (e.g., from Settings), reload voices
                    loadVoices()
                }
            }
        }
    }
    
    private fun startPollLoop() {
        viewModelScope.launch {
            while(true) {
                kotlinx.coroutines.delay(1000)
                // Refresh status only if Piper mode
                val prefs = PrefsManager(context)
                if (prefs.currentModel.startsWith("piper")) {
                    refreshPiperStatuses()
                }
            }
        }
    }
    
    private fun refreshPiperStatuses() {
        // Check only downloading or not-downloaded items? Or all?
        // Checking 500+ items is slow.
        // But the user wants to see "Download started", "Level", "Failed".
        // VoiceDownloader only tracks Active/Recent downloads via prefs.
        // Repo checks file existence.
        
        val updatedVoices = allVoices.map { voice ->
            if (voice.id.startsWith("piper") || voice.metadata != null) {
                // It's a piper voice
                val status = voiceDownloader.getDownloadStatus(voice.id)
                if (status.state != com.nekospeak.tts.data.DownloadState.NotDownloaded) {
                    // Active or stored download info
                    voice.copy(downloadState = status.state, downloadProgress = status.progress)
                } else {
                    // Fallback to Repo check (Disk existence)
                    val diskState = voiceRepo.getDownloadState(voice.id)
                    voice.copy(downloadState = diskState, downloadProgress = if (diskState == com.nekospeak.tts.data.DownloadState.Downloaded) 1f else 0f)
                }
            } else {
                voice
            }
        }
        
        if (updatedVoices != allVoices) {
            allVoices = updatedVoices
            applyFilters()
        }
    }
    
    fun downloadVoice(voice: Voice) {
        val meta = voice.metadata ?: return
        viewModelScope.launch {
            voiceDownloader.downloadVoice(voice.id, meta.onnxUrl, meta.jsonUrl)
            refreshPiperStatuses() // Optimistic update
        }
    }
    
    fun loadVoices() {
        val prefs = PrefsManager(context)
        val currentModel = prefs.currentModel
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            allVoices = when {
                currentModel == "kitten_nano" -> kittenVoices
                currentModel.startsWith("piper") -> {
                    // Load ALL piper voices from repo
                    voiceRepo.availableVoices.map { info ->
                        // Parse language name to separate language from country
                        // Format: "English (United States)" -> language="English", region="United States"
                        val languageName = info.languageName
                        val pureLang = if (languageName.contains("(")) {
                            languageName.substringBefore("(").trim()
                        } else {
                            languageName
                        }
                        
                        val countryPart = if (languageName.contains("(")) {
                            languageName.substringAfter("(").substringBefore(")").trim()
                        } else {
                            info.region // Fallback to region code
                        }
                        
                        Voice(
                            id = info.id, // e.g. en_US-amy-low
                            name = "${info.name} (${info.quality})", // e.g. Amy (low)
                            language = pureLang, // e.g. "English"
                            gender = "Unknown", // Metadata doesn't strictly have gender.
                            region = countryPart, // e.g. "United States"
                            metadata = info,
                            downloadState = com.nekospeak.tts.data.DownloadState.NotDownloaded // Initial, will refresh
                        )
                    }
                }
                else -> kokoroVoices
            }
            
            // Initial status refresh
            if (currentModel.startsWith("piper")) {
                refreshPiperStatuses()
            }
            
            // Auto-fix filters if they are invalid for the current model
            val currentRegion = _uiState.value.selectedRegion
            if (currentRegion == "UK" && allVoices.none { it.region == "UK" }) {
                 _uiState.update { it.copy(selectedRegion = null) }
            }
            
            // Validate selected voice
            val currentVoiceId = prefs.currentVoice
            val isValid = allVoices.any { it.id == currentVoiceId }
            
            val voiceToSelect = if (isValid) {
                currentVoiceId
            } else {
                // Determine sensible default
                val defaultId = when {
                    currentModel == "kitten_nano" -> "expr-voice-2-f" 
                    currentModel.startsWith("piper") -> {
                         // Default to amy-low if available, else first bundled
                         "en_US-amy-low"
                    }
                    else -> "af_heart"
                }
                // Update persistent storage
                prefs.currentVoice = defaultId
                defaultId
            }
            
            _uiState.update { 
                it.copy(
                    isLoading = false, 
                    voices = emptyList(), // Use filtered list primarily
                    // filteredVoices will be updated by applyFilters
                    availableLanguages = allVoices.map { v -> v.language }.distinct().sorted(),
                    availableRegions = allVoices.map { v -> v.region }.distinct().sorted(),
                    // Extract quality from Piper voice names (e.g., "Amy (low)" -> "low")
                    availableQualities = allVoices
                        .mapNotNull { v -> 
                            val match = Regex("\\(([^)]+)\\)").find(v.name)
                            match?.groupValues?.getOrNull(1)
                        }
                        .distinct()
                        .sortedBy { 
                            when(it) { "x_low" -> 0; "low" -> 1; "medium" -> 2; "high" -> 3; else -> 4 }
                        },
                    selectedVoiceId = voiceToSelect
                ) 
            }
            applyFilters()
            lastLoadedModel = currentModel
        }
    }
    
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }
    
    fun toggleFilters() {
        _uiState.update { it.copy(showFilters = !it.showFilters) }
    }
    
    fun setViewMode(mode: ViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }
    
    fun selectVoice(voiceId: String) {
        val voice = allVoices.find { it.id == voiceId }
        // Only select if downloaded
        if (voice != null && voice.downloadState == com.nekospeak.tts.data.DownloadState.Downloaded) {
            _uiState.update { it.copy(selectedVoiceId = voiceId) }
            val prefs = PrefsManager(context)
            prefs.currentVoice = voiceId
            
            // Explicitly update model for ALL voice types to prevent getting stuck in wrong engine mode
            val newModel = when {
                voiceId.startsWith("expr-voice-") -> "kitten_nano"
                voiceId.startsWith("af_") || voiceId.startsWith("am_") || voiceId.startsWith("bf_") || voiceId.startsWith("bm_") -> "kokoro_v1.0"
                else -> "piper_$voiceId" // Piper voices
            }
            prefs.currentModel = newModel
        }
    }
    
    fun selectLanguage(language: String?) {
        _uiState.update { it.copy(selectedLanguage = language) }
        applyFilters()
    }
    
    fun selectGender(gender: String?) {
        _uiState.update { it.copy(selectedGender = gender) }
        applyFilters()
    }
    
    fun selectRegion(region: String?) {
        _uiState.update { it.copy(selectedRegion = region) }
        applyFilters()
    }
    
    fun clearFilters() {
        _uiState.update { 
            it.copy(
                searchQuery = "",
                selectedLanguage = null,
                selectedGender = null,
                selectedRegion = null,
                selectedQuality = null
            )
        }
        applyFilters()
    }
    
    fun selectQuality(quality: String?) {
        _uiState.update { it.copy(selectedQuality = quality) }
        applyFilters()
    }
    
    private fun applyFilters() {
        val currentState = _uiState.value
        val query = currentState.searchQuery.lowercase()
        
        val filtered = allVoices.filter { voice ->
            val matchesSearch = voice.name.lowercase().contains(query) || 
                              voice.language.contains(query)
            
            val matchesLang = currentState.selectedLanguage?.let { it == voice.language } ?: true
            val matchesGender = currentState.selectedGender?.let { it == voice.gender } ?: true
            val matchesRegion = currentState.selectedRegion?.let { it == voice.region } ?: true
            
            // Quality filter for Piper voices
            val matchesQuality = currentState.selectedQuality?.let { quality ->
                voice.name.contains("($quality)", ignoreCase = true)
            } ?: true
            
            matchesSearch && matchesLang && matchesGender && matchesRegion && matchesQuality
        }
        
        val sorted = when(currentState.sortBy) {
            VoiceSortOption.NAME -> filtered.sortedBy { it.name }
            VoiceSortOption.LANGUAGE -> filtered.sortedBy { it.language }
        }
        
        _uiState.update { it.copy(filteredVoices = sorted) }
    }
    fun getSampleTextForVoice(voiceId: String?): String {
        val voice = allVoices.find { it.id == voiceId } ?: return "Hello, I am NekoSpeak."
        val lang = voice.language.lowercase() // e.g. "English (United States)" or "en_US"
        
        return when {
            lang.contains("tamil") || lang.contains("ta_in") -> "வணக்கம், நான் NekoSpeak."
            lang.contains("spanish") || lang.contains("es_") -> "Hola, soy NekoSpeak."
            lang.contains("french") || lang.contains("fr_") -> "Bonjour, je suis NekoSpeak."
            lang.contains("german") || lang.contains("de_") -> "Hallo, ich bin NekoSpeak."
            lang.contains("japanese") || lang.contains("ja_") -> "こんにちは、NekoSpeakです。"
            lang.contains("chinese") || lang.contains("zh_") -> "你好，我是NekoSpeak。"
            else -> "Hello, I am NekoSpeak."
        }
    }
}
