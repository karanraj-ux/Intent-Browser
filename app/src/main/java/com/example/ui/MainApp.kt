package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "browser"

    Scaffold(
        topBar = {
            if (currentRoute != "browser") {
                TopAppBar(
                    title = { 
                        Text(when(currentRoute) {
                            "history" -> "History"
                            "bookmarks" -> "Bookmarks"
                            else -> "Browser"
                        }) 
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "browser",
            modifier = Modifier.padding(paddingValues),
            enterTransition = { androidx.compose.animation.EnterTransition.None },
            exitTransition = { androidx.compose.animation.ExitTransition.None },
            popEnterTransition = { androidx.compose.animation.EnterTransition.None },
            popExitTransition = { androidx.compose.animation.ExitTransition.None }
        ) {
            composable("browser") {
                BrowserScreen(viewModel, onNavigate = { route -> 
                    navController.navigate(route)
                })
            }
            composable("bookmarks") {
                BookmarksScreen(viewModel) { url ->
                    viewModel.loadUrl(viewModel.activeTabId.value, url)
                    navController.navigate("browser") { popUpTo("browser") { inclusive = true } }
                }
            }
            composable("history") {
                HistoryScreen(viewModel) { url ->
                    viewModel.loadUrl(viewModel.activeTabId.value, url)
                    navController.navigate("browser") { popUpTo("browser") { inclusive = true } }
                }
            }
            composable("notes") {
                NotesScreen(viewModel, onBack = { navController.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(navController, viewModel)
            }
            composable("offline") {
                OfflinePagesScreen(viewModel) { url ->
                    viewModel.loadUrl(viewModel.activeTabId.value, url)
                    navController.navigate("browser") { popUpTo("browser") { inclusive = true } }
                }
            }
        }
    }
}

@Composable
fun OfflinePagesScreen(viewModel: MainViewModel, onPageClick: (String) -> Unit) {
    val allPages by viewModel.allPages.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (allPages.isEmpty()) {
            Text("No offline pages saved.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn {
                items(allPages) { page ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onPageClick("app://offline/${page.id}") },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(page.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(page.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { viewModel.deleteOfflinePage(page.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Page", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookmarksScreen(viewModel: MainViewModel, onBookmarkClick: (String) -> Unit) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (bookmarks.isEmpty()) {
            Text("No bookmarks saved.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn {
                items(bookmarks) { bookmark ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onBookmarkClick(bookmark.url) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                if (bookmark.url.startsWith("http")) {
                                    AsyncImage(
                                        model = "https://www.google.com/s2/favicons?sz=64&domain_url=${bookmark.url}",
                                        contentDescription = "Favicon",
                                        modifier = Modifier.size(32.dp).padding(end = 16.dp)
                                    )
                                }
                                Column {
                                    Text(bookmark.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text(bookmark.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }

                            IconButton(onClick = { viewModel.toggleBookmark(bookmark.url, bookmark.title) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Bookmark", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
