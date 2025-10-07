package com.nikichxp.repository

import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.nikichxp.model.ServerConnection
import com.nikichxp.model.TabData
import com.nikichxp.model.ViewMode
import com.nikichxp.state.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document

class MongoRepository {

    suspend fun loadCollections(appState: AppState, server: ServerConnection) {
        try {
            appState.isLoading = true
            withContext(Dispatchers.IO) {
                MongoClients.create(server.connectionString).use { client ->
                    val database = client.getDatabase(server.authDatabase)
                    val collections = database.listCollectionNames().toList()
                    
                    withContext(Dispatchers.Main) {
                        val serverIndex = appState.servers.indexOfFirst { it.id == server.id }
                        if (serverIndex != -1) {
                            appState.servers[serverIndex] = server.copy(
                                collections = collections,
                                isExpanded = true
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                appState.errorMessage = "Failed to load collections: ${e.message}"
            }
        } finally {
            withContext(Dispatchers.Main) {
                appState.isLoading = false
            }
        }
    }

    suspend fun loadDocuments(
        appState: AppState,
        server: ServerConnection,
        collectionName: String,
        tab: TabData
    ) {
        try {
            appState.isLoading = true
            withContext(Dispatchers.IO) {
                MongoClients.create(server.connectionString).use { client ->
                    val database = client.getDatabase(server.authDatabase)
                    val collection: MongoCollection<Document> = database.getCollection(collectionName)
                    val documents = collection.find().limit(100).toList()
                    
                    withContext(Dispatchers.Main) {
                        // Update the tab with the loaded documents
                        tab.documents = documents
                        
                        // Find and update the tab in the active pane
                        val activePane = appState.activePane
                        val tabIndex = activePane.tabs.indexOfFirst { 
                            it.serverName == tab.serverName && 
                            it.collectionName == tab.collectionName 
                        }
                        
                        if (tabIndex != -1) {
                            activePane.tabs[tabIndex] = tab.copy(documents = documents)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                appState.errorMessage = "Failed to load documents: ${e.message}"
            }
        } finally {
            withContext(Dispatchers.Main) {
                appState.isLoading = false
            }
        }
    }
    
    suspend fun testConnection(server: ServerConnection): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                MongoClients.create(server.connectionString).use { client ->
                    client.listDatabaseNames().firstOrNull() != null
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}
