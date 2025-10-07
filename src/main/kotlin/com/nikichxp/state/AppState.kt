package com.nikichxp.state

import androidx.compose.runtime.*
import com.nikichxp.model.ServerConnection
import com.nikichxp.model.TabData
import com.nikichxp.repository.ConnectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class SplitPane(
    val id: String = java.util.UUID.randomUUID().toString(),
    var activeTabIndex: Int = 0,
    val tabs: MutableList<TabData> = mutableStateListOf()
)

class AppState(
    private val connectionRepository: ConnectionRepository = ConnectionRepository(ConnectionRepository.getDefaultFile())
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    // Server connections management
    val servers = mutableStateListOf<ServerConnection>()
    
    var showAddServerDialog by mutableStateOf(false)
    var editingServer by mutableStateOf<ServerConnection?>(null)
    
    // Split panes management
    val splitPanes = mutableStateListOf(SplitPane())
    var activePaneIndex by mutableStateOf(0)
    
    // Application state
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    
    init {
        loadServers()
    }
    
    private fun loadServers() {
        coroutineScope.launch {
            val loadedServers = connectionRepository.loadConnections()
            servers.clear()
            servers.addAll(loadedServers)
        }
    }
    
    private fun saveServers() {
        coroutineScope.launch {
            connectionRepository.saveConnections(servers)
        }
    }
    
    fun addServer(server: ServerConnection) {
        servers.add(server)
        saveServers()
    }
    
    fun updateServer(oldServer: ServerConnection, newServer: ServerConnection) {
        val index = servers.indexOfFirst { it.id == oldServer.id }
        if (index != -1) {
            servers[index] = newServer
            saveServers()
        }
    }
    
    fun removeServer(server: ServerConnection) {
        servers.removeAll { it.id == server.id }
        saveServers()
    }
    
    // Current active tab in the active pane
    val activeTab: TabData?
        get() = splitPanes.getOrNull(activePaneIndex)?.tabs?.getOrNull(
            splitPanes[activePaneIndex].activeTabIndex
        )
    
    // Current active pane
    val activePane: SplitPane
        get() = splitPanes[activePaneIndex]
    
    // Add a new split pane
    fun addSplitPane() {
        splitPanes.add(SplitPane())
        activePaneIndex = splitPanes.lastIndex
    }
    
    // Remove a split pane by index
    fun removeSplitPane(index: Int) {
        if (splitPanes.size > 1) {
            splitPanes.removeAt(index)
            if (activePaneIndex >= splitPanes.size) {
                activePaneIndex = splitPanes.lastIndex
            }
        }
    }
    
    // Add a tab to the active pane
    fun addTab(tab: TabData) {
        val pane = splitPanes[activePaneIndex]
        pane.tabs.add(tab)
        pane.activeTabIndex = pane.tabs.lastIndex
    }
    
    // Close a tab in a specific pane
    fun closeTab(paneIndex: Int, tabIndex: Int) {
        if (paneIndex in splitPanes.indices) {
            val pane = splitPanes[paneIndex]
            if (tabIndex in pane.tabs.indices) {
                pane.tabs.removeAt(tabIndex)
                if (pane.activeTabIndex >= pane.tabs.size) {
                    pane.activeTabIndex = (pane.tabs.size - 1).coerceAtLeast(0)
                }
            }
        }
    }
    
    // Set active tab in a pane
    fun setActiveTab(paneIndex: Int, tabIndex: Int) {
        if (paneIndex in splitPanes.indices) {
            val pane = splitPanes[paneIndex]
            if (tabIndex in 0 until pane.tabs.size) {
                pane.activeTabIndex = tabIndex
                activePaneIndex = paneIndex
            }
        }
    }
}
