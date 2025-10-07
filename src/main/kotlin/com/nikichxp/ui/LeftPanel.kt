package com.nikichxp.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nikichxp.model.ServerConnection
import com.nikichxp.state.AppState

@Composable
fun LeftPanel(
    appState: AppState,
    onAddServer: () -> Unit,
    onServerExpand: (ServerConnection) -> Unit,
    onCollectionClick: (ServerConnection, String) -> Unit
) {
    Surface(
        modifier = Modifier.width(280.dp).fillMaxHeight(),
        color = MaterialTheme.colors.surface,
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SERVERS",
                    style = MaterialTheme.typography.overline,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                
                Row {
                    // Add server button
                    IconButton(
                        onClick = onAddServer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add Server",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Divider()

            // Servers list
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(appState.servers) { server ->
                    ServerItem(
                        server = server,
                        onExpand = { onServerExpand(server) },
                        onEdit = { appState.editingServer = server },
                        onDelete = { appState.servers.remove(server) },
                        onCollectionClick = { collection -> onCollectionClick(server, collection) }
                    )
                    Divider()
                }
                
                // Add extra space at the bottom
                item {
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    }
}

@Composable
private fun ServerItem(
    server: ServerConnection,
    onExpand: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCollectionClick: (String) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.animateContentSize()
    ) {
        // Server row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpand() }
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expand/collapse icon with rotation animation
            val rotation by animateFloatAsState(if (server.isExpanded) 0f else -90f)
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = if (server.isExpanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(24.dp)
                    .rotate(rotation)
            )
            
            // Server name
            Text(
                text = server.name,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                style = MaterialTheme.typography.subtitle1
            )
            
            // Connection status indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (server.collections.isNotEmpty()) 
                            MaterialTheme.colors.primary 
                        else 
                            MaterialTheme.colors.onSurface.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small
                    )
            )
            
            // Edit button
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit Server",
                    modifier = Modifier.size(16.dp)
                )
            }
            
            // Delete button with confirmation
            if (showDeleteConfirm) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        "Delete?",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.error
                    )
                    TextButton(
                        onClick = { onDelete() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colors.error
                        ),
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Text("YES")
                    }
                    TextButton(
                        onClick = { showDeleteConfirm = false },
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Text("NO")
                    }
                }
            } else {
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Server",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Collections list
        if (server.isExpanded) {
            Column(
                modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
            ) {
                if (server.collections.isEmpty()) {
                    Text(
                        "No collections",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                    )
                } else {
                    server.collections.forEach { collection ->
                        Text(
                            text = collection,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCollectionClick(collection) }
                                .padding(8.dp),
                            style = MaterialTheme.typography.body2
                        )
                    }
                }
            }
        }
    }
}
