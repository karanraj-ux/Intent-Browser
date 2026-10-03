package com.intentbrowser.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel, onHistoryClick: (String) -> Unit) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    
    val groupedHistory = remember(history) {
        val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        history.reversed().groupBy { format.format(Date(it.accessedAt)) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("History", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { viewModel.clearHistory() }) {
                Icon(Icons.Default.Delete, contentDescription = "Clear History")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            groupedHistory.forEach { (date, items) ->
                item {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(items) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.title, maxLines = 1) },
                        supportingContent = { Text(entry.url, maxLines = 1) },
                        modifier = Modifier.clickable {
                            onHistoryClick(entry.url)
                        }
                    )
                }
            }
        }
    }
}