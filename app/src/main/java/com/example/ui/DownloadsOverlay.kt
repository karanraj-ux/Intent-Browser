package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.util.ActiveDownload
import com.example.util.CustomDownloadManager
import com.example.util.DownloadState

@Composable
fun DownloadsOverlay(
    downloadManager: CustomDownloadManager,
    modifier: Modifier = Modifier
) {
    val downloads by downloadManager.downloads.collectAsStateWithLifecycle()
    var isExpanded by remember { mutableStateOf(false) }
    
    val activeCount = downloads.count { it.state == DownloadState.DOWNLOADING || it.state == DownloadState.PAUSED }
    var previousActiveCount by remember { mutableIntStateOf(activeCount) }
    
    LaunchedEffect(activeCount, downloads.isEmpty()) {
        if (activeCount > previousActiveCount) {
            isExpanded = true
        } else if (downloads.isEmpty()) {
            isExpanded = false
        }
        previousActiveCount = activeCount
    }

    AnimatedVisibility(
        visible = isExpanded,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
            .padding(16.dp)
            .widthIn(max = 400.dp)
            .fillMaxWidth()
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Active Downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { isExpanded = false }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close overlay")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(downloads, key = { it.id }) { dl ->
                        DownloadOverlayItem(
                            download = dl,
                            onPause = { downloadManager.pause(dl.id) },
                            onResume = { downloadManager.resume(dl.id) },
                            onCancel = { downloadManager.cancel(dl.id) },
                            onDismiss = { downloadManager.removeCompleted(dl.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadOverlayItem(
    download: ActiveDownload,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = download.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            Row(horizontalArrangement = Arrangement.End) {
                if (download.state == DownloadState.DOWNLOADING) {
                    IconButton(onClick = onPause, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(20.dp))
                    }
                } else if (download.state == DownloadState.PAUSED) {
                    IconButton(onClick = onResume, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(20.dp))
                    }
                }
                
                if (download.state != DownloadState.COMPLETED && download.state != DownloadState.FAILED && download.state != DownloadState.CANCELED) {
                    IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(20.dp))
                    }
                } else {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinearProgressIndicator(
                progress = { download.progress / 100f },
                modifier = Modifier.weight(1f).height(4.dp),
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            val text = when (download.state) {
                DownloadState.COMPLETED -> "Done"
                DownloadState.FAILED -> "Failed"
                DownloadState.CANCELED -> "Canceled"
                DownloadState.PAUSED -> "Paused"
                DownloadState.DOWNLOADING -> "${download.progress}%"
            }
            Text(text = text, style = MaterialTheme.typography.labelSmall)
        }
    }
}
