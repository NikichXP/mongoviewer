import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Snackbar
import androidx.compose.material.Tab
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.darkColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.nikichxp.model.ServerConnection
import com.nikichxp.model.TabData
import com.nikichxp.model.ViewMode
import com.nikichxp.repository.MongoRepository
import com.nikichxp.state.AppState
import com.nikichxp.ui.LeftPanel
import com.nikichxp.ui.RightPanel
import com.nikichxp.ui.ServerDialog
import kotlinx.coroutines.launch

private val mongoRepository = MongoRepository()

@Composable
private fun SplitView(
    appState: AppState,
    onAddServer: () -> Unit,
    onServerExpand: (ServerConnection) -> Unit,
    onCollectionClick: (ServerConnection, String) -> Unit,
    onRefresh: (TabData) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Split view controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.surface)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Panes: ", style = MaterialTheme.typography.caption)
            
            // List of panes
            LazyRow(
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(appState.splitPanes) { index, pane ->
                    val isActive = index == appState.activePaneIndex
                    Tab(
                        selected = isActive,
                        onClick = { appState.activePaneIndex = index },
                        text = { 
                            Text(
                                "Pane ${index + 1}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            ) 
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
            
            // Add new pane button
            IconButton(
                onClick = { appState.addSplitPane() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Add, "Add Pane")
            }
            
            // Remove current pane button (if more than one)
            if (appState.splitPanes.size > 1) {
                IconButton(
                    onClick = { appState.removeSplitPane(appState.activePaneIndex) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, "Remove Pane")
                }
            }
        }
        
        // Main content area
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left panel (servers)
                LeftPanel(
                    appState = appState,
                    onAddServer = onAddServer,
                    onServerExpand = onServerExpand,
                    onCollectionClick = onCollectionClick
                )
                
                // Right panel (content)
                Box(modifier = Modifier.weight(1f)) {
                    val activePane = appState.activePane
                    RightPanel(
                        pane = activePane,
                        onTabSelected = { tabIndex ->
                            appState.setActiveTab(appState.activePaneIndex, tabIndex)
                        },
                        onTabClosed = { tabIndex ->
                            appState.closeTab(appState.activePaneIndex, tabIndex)
                        },
                        onRefresh = { tab ->
                            onRefresh(tab)
                        }
                    )
                }
            }
        }
    }
}

fun main() = application {
    val windowState = rememberWindowState(width = 1400.dp, height = 900.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "MongoDB Client",
        state = windowState
    ) {
        MaterialTheme(
            colors = darkColors(
                primary = Color(0xFF00ED64),
                primaryVariant = Color(0xFF00684A),
                secondary = Color(0xFF589636),
                background = Color(0xFF1E1E1E),
                surface = Color(0xFF2D2D2D),
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = Color.White,
                onSurface = Color.White
            )
        ) {
            MongoDBClientApp()
        }
    }
}

@Composable
fun MongoDBClientApp() {
    val appState = remember { AppState() }
    val scope = rememberCoroutineScope()

    // Add test server on first launch if no servers are loaded
    LaunchedEffect(Unit) {
        if (appState.servers.isEmpty()) {
            appState.addServer(
                ServerConnection(
                    name = "Local MongoDB",
                    host = "localhost",
                    port = 27017
                )
            )
        }
    }

    // Handle loading documents for a tab
    val onRefresh: (TabData) -> Unit = { tab: TabData ->
        scope.launch {
            val server = appState.servers.find { it.name == tab.serverName }
            if (server != null) {
                // Use the active pane from appState
                mongoRepository.loadDocuments(appState, server, tab.collectionName, tab)
            }
        }
    }

    // Handle server expansion (loading collections)
    val onServerExpand: (ServerConnection) -> Unit = { server: ServerConnection ->
        scope.launch {
            mongoRepository.loadCollections(appState, server)
        }
    }

    // Handle collection click (open in current tab)
    val onCollectionClick: (ServerConnection, String) -> Unit = { server: ServerConnection, collectionName: String ->
        val tab = TabData(
            serverName = server.name,
            collectionName = collectionName,
            documents = emptyList(),
            viewMode = ViewMode.LIST
        )
        
        // Add tab to the active pane
        appState.activePane.tabs.add(tab)
        appState.activePane.activeTabIndex = appState.activePane.tabs.size - 1
        
        // Load documents for the new tab
        scope.launch {
            mongoRepository.loadDocuments(appState, server, collectionName, tab)
        }
    }
    
    // Handle server updates (add or edit)
    val onServerUpdated = { oldServer: ServerConnection?, newServer: ServerConnection ->
        if (oldServer == null) {
            appState.addServer(newServer)
        } else {
            appState.updateServer(oldServer, newServer)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        SplitView(
            appState = appState,
            onAddServer = { appState.showAddServerDialog = true },
            onServerExpand = onServerExpand,
            onCollectionClick = onCollectionClick,
            onRefresh = onRefresh
        )

        // Add/Edit server dialog
        if (appState.showAddServerDialog) {
            ServerDialog(
                server = appState.editingServer,
                onDismiss = { 
                    appState.showAddServerDialog = false
                    appState.editingServer = null
                },
                onConfirm = { server ->
                    onServerUpdated(appState.editingServer, server)
                    appState.showAddServerDialog = false
                    appState.editingServer = null
                }
            )
        }

        // Edit server dialog
        appState.editingServer?.let { server ->
            ServerDialog(
                server = server,
                onDismiss = { appState.editingServer = null },
                onConfirm = { updatedServer ->
                    val index = appState.servers.indexOfFirst { it.id == server.id }
                    if (index != -1) {
                        appState.servers[index] = updatedServer
                    }
                    appState.editingServer = null
                }
            )
        }

        // Loading indicator
        if (appState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colors.primary)
            }
        }

        // Error message
        appState.errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = {
                    TextButton(onClick = { appState.errorMessage = null }) {
                        Text("OK")
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}

// Helper function to open a collection in the active pane
private fun openCollection(appState: AppState, server: ServerConnection, collectionName: String) {
    val activePane = appState.activePane
    val existingTabIndex = activePane.tabs.indexOfFirst {
        it.serverName == server.name && it.collectionName == collectionName
    }
    
    if (existingTabIndex == -1) {
        val newTab = TabData(
            serverName = server.name,
            collectionName = collectionName,
            documents = emptyList(),
            viewMode = ViewMode.LIST
        )
        activePane.tabs.add(newTab)
        activePane.activeTabIndex = activePane.tabs.size - 1
    } else {
        activePane.activeTabIndex = existingTabIndex
    }
}
