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
    val region: String // "US" or "UK"
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
        val sortBy: VoiceSortOption = VoiceSortOption.NAME,
        
        val availableLanguages: List<String> = emptyList()
    )
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    private val context: Context get() = getApplication<Application>().applicationContext
    
    // In a real app this would come from the engine
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
    private var allVoices = kokoroVoices
    
    init {
        loadVoices()
    }
    
    fun loadVoices() {
        val prefs = PrefsManager(context)
        allVoices = if (prefs.currentModel == "kitten_nano") kittenVoices else kokoroVoices
        
        // Auto-fix filters if they are invalid for the current model
        val currentRegion = _uiState.value.selectedRegion
        if (currentRegion == "UK" && allVoices.none { it.region == "UK" }) {
             _uiState.update { it.copy(selectedRegion = null) }
        }
        
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isLoading = false, 
                    voices = allVoices,
                    // filteredVoices will be updated by applyFilters
                    availableLanguages = allVoices.map { v -> v.language }.distinct().sorted(),
                    selectedVoiceId = prefs.currentVoice
                ) 
            }
            applyFilters()
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
        _uiState.update { it.copy(selectedVoiceId = voiceId) }
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
                selectedRegion = null
            )
        }
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
            
            matchesSearch && matchesLang && matchesGender && matchesRegion
        }
        
        val sorted = when(currentState.sortBy) {
            VoiceSortOption.NAME -> filtered.sortedBy { it.name }
            VoiceSortOption.LANGUAGE -> filtered.sortedBy { it.language }
        }
        
        _uiState.update { it.copy(filteredVoices = sorted) }
    }
}
