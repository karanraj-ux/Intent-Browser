package com.intentbrowser.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.intentbrowser.app.util.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val isAdBlockerEnabled by viewModel.isAdBlockerEnabled.collectAsState()
    val blockThirdPartyCookies by viewModel.blockThirdPartyCookies.collectAsState()
    val syncServerIp by viewModel.syncServerIp.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Browser Features", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ad & Tracker Blocker", style = MaterialTheme.typography.bodyLarge)
                    Text("Blocks known ad servers and trackers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = isAdBlockerEnabled,
                    onCheckedChange = { viewModel.toggleAdBlocker(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Block third-party cookies", style = MaterialTheme.typography.bodyLarge)
                    Text("Stronger privacy; some logins may need it off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = blockThirdPartyCookies,
                    onCheckedChange = { viewModel.toggleThirdPartyCookies(it) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text("PC-to-Mobile Sync", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (syncServerIp != null && syncServerIp?.startsWith("http") == true) {
                        Text("Scan this QR code from your PC or tablet:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                        LaunchedEffect(syncServerIp) {
                            syncServerIp?.let { ip ->
                                withContext(Dispatchers.Default) {
                                    qrBitmap = QrCodeGenerator.generateQrCode(ip, 400)
                                }
                            }
                        }
                        
                        qrBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "QR Code for Sync Server",
                                modifier = Modifier.size(200.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(syncServerIp ?: "", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = {
                            viewModel.stopSyncServer()
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Stop Sync Server")
                        }
                    } else {
                        Text("Start the sync server to manage tabs, push notifications, and sync clipboard from your PC browser.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.startSyncServer(context.applicationContext) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Start Sync Server")
                        }
                        if (syncServerIp != null && syncServerIp?.startsWith("http") != true) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(syncServerIp ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
