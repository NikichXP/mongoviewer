package com.nikichxp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nikichxp.model.TabData
import com.nikichxp.model.ViewMode
import com.nikichxp.state.SplitPane
import org.bson.Document

@Composable
fun RightPanel(
    pane: SplitPane,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (Int) -> Unit,
    onRefresh: (TabData) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Tabs row with scrollable tabs
        ScrollableTabRow(
            selectedTabIndex = if (pane.tabs.isNotEmpty()) pane.activeTabIndex.coerceIn(pane.tabs.indices) else 0,
            edgePadding = 0.dp,
            backgroundColor = MaterialTheme.colors.surface,
            contentColor = MaterialTheme.colors.onSurface,
            indicator = { tabPositions ->
                if (pane.tabs.isNotEmpty() && pane.activeTabIndex in pane.tabs.indices) {
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[pane.activeTabIndex]),
                        color = MaterialTheme.colors.primary
                    )
                } else {
                    Box {}
                }
            },
            divider = {}
        ) {
            pane.tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = index == pane.activeTabIndex,
                    onClick = { onTabSelected(index) },
                    text = { 
                        Text(
                            "${tab.serverName} - ${tab.collectionName}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    },
                    icon = {
                        IconButton(
                            onClick = { onTabClosed(index) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close tab",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                )
            }
        }

        // Tab content
        if (pane.tabs.isNotEmpty() && pane.activeTabIndex in pane.tabs.indices) {
            val activeTab = pane.tabs[pane.activeTabIndex]
            TabContent(
                tab = activeTab,
                onRefresh = { onRefresh(activeTab) },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CollectionsBookmark,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).padding(bottom = 8.dp),
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        "No Collection Selected",
                        style = MaterialTheme.typography.h6,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
                    )
                    Text(
                        "Select a collection from the left panel to get started",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TabContent(
    tab: TabData,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        // Toolbar with view mode toggle and refresh button
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // View mode toggle - only show if we have a valid tab
            if (tab.collectionName.isNotBlank()) {
                ButtonGroup(
                    options = ViewMode.values().toList(),
                    selectedOption = tab.viewMode,
                    onOptionSelected = { tab.viewMode = it },
                    optionLabel = { it.name },
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                // Empty spacer to push the refresh button to the right
                Spacer(modifier = Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Refresh button - only enable if we have a valid collection
            val isRefreshEnabled = tab.collectionName.isNotBlank()
            IconButton(
                onClick = onRefresh,
                enabled = isRefreshEnabled
            ) {
                Icon(
                    Icons.Default.Refresh, 
                    "Refresh",
                    tint = if (isRefreshEnabled) 
                        MaterialTheme.colors.onSurface 
                    else 
                        MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        // Documents list
        if (tab.documents.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(tab.documents) { document ->
                    DocumentCard(
                        document = document,
                        viewMode = tab.viewMode
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No documents found")
            }
        }
    }
}

@Composable
private fun <T> ButtonGroup(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selectedOption
            Button(
                onClick = { onOptionSelected(option) },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (isSelected) {
                        MaterialTheme.colors.primary
                    } else {
                        MaterialTheme.colors.surface
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colors.onPrimary
                    } else {
                        MaterialTheme.colors.onSurface
                    }
                ),
                elevation = ButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    disabledElevation = 0.dp,
                    hoveredElevation = 0.dp,
                    focusedElevation = 0.dp
                )
            ) {
                Text(optionLabel(option))
            }
        }
    }
}

@Composable
private fun DocumentCard(
    document: Document,
    viewMode: ViewMode
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        backgroundColor = MaterialTheme.colors.surface,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            when (viewMode) {
                ViewMode.LIST -> {
                    document.entries.joinToString(", ") { "${it.key}: ${it.value}" }.let {
                        Text(it, style = MaterialTheme.typography.body2)
                    }
                }
                ViewMode.TREE -> {
                    // Tree view implementation would go here
                    Text("Tree view not implemented yet")
                }
            }
        }
    }
}
