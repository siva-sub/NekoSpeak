package com.nekospeak.tts.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nekospeak.tts.data.ModelInfo
import com.nekospeak.tts.data.ModelRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshTrigger by remember { mutableIntStateOf(0) } // To force recomposition on state changes
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage AI Models") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Iterate over all models in repository
            items(ModelRepository.models, key = { it.id }) { modelInfo ->
                ModelCardWithState(
                    modelInfo = modelInfo,
                    refreshTrigger = refreshTrigger,
                    onRefresh = { refreshTrigger++ }
                )
            }
            
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            
            // Piper Section (managed separately for now)
            item {
                Text(
                    "Offline Voices (Piper)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                 Text(
                    "Standard Piper voices are managed automatically or bundled.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ModelCardWithState(
    modelInfo: ModelInfo,
    refreshTrigger: Int,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Track download state locally to trigger recomposition
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    
    val isInstalled = remember(refreshTrigger) { 
        ModelRepository.isInstalled(context, modelInfo.id) 
    }
    
    // Observe the progress flow when downloading
    LaunchedEffect(isDownloading) {
        if (isDownloading) {
            // Poll for progress updates
            while (isDownloading) {
                val flow = ModelRepository.getDownloadProgress(modelInfo.id)
                if (flow != null) {
                    flow.collect { progress ->
                        downloadProgress = progress
                    }
                }
                kotlinx.coroutines.delay(100)
            }
        }
    }
    
    ModelCard(
        title = modelInfo.name,
        description = modelInfo.description,
        isInstalled = isInstalled,
        isDownloading = isDownloading,
        progress = downloadProgress,
        status = when {
            downloadError != null -> "Error: $downloadError"
            isDownloading -> "Downloading ${(downloadProgress * 100).toInt()}%..."
            else -> ""
        },
        onDownload = {
            downloadError = null
            isDownloading = true
            downloadProgress = 0f
            
            scope.launch {
                ModelRepository.downloadModel(context, modelInfo.id) { success ->
                    isDownloading = false
                    if (success) {
                        onRefresh() // Trigger UI update
                    } else {
                        downloadError = "Download failed. Check connection."
                    }
                }
            }
        },
        onDelete = {
            ModelRepository.deleteModel(context, modelInfo.id)
            onRefresh()
        }
    )
}

@Composable
fun ModelCard(
    title: String,
    description: String,
    isInstalled: Boolean,
    isDownloading: Boolean,
    progress: Float,
    status: String,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                if (isInstalled) {
                    Icon(Icons.Default.CheckCircle, "Installed", tint = MaterialTheme.colorScheme.primary)
                } else if (!isDownloading) { // Only show warning if not installed and not downloading
                     Icon(Icons.Default.Warning, "Not Installed", tint = MaterialTheme.colorScheme.error)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isDownloading) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(status, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (status.isNotEmpty()) {
                        Text(
                            status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    if (isInstalled) {
                        OutlinedButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Delete")
                        }
                    } else {
                        Button(onClick = onDownload) {
                            Text("Download")
                        }
                    }
                }
            }
        }
    }
}
