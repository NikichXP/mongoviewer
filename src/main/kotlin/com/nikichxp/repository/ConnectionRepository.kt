package com.nikichxp.repository

import com.nikichxp.model.ServerConnection
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class StoredConnections(
    val version: Int = 1,
    val connections: List<ServerConnection> = emptyList()
)

class ConnectionRepository(private val storageFile: File) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        // Create parent directories if they don't exist
        storageFile.parentFile?.mkdirs()
    }

    fun saveConnections(connections: List<ServerConnection>) {
        try {
            val storedConnections = StoredConnections(connections = connections)
            val jsonString = json.encodeToString(storedConnections)
            storageFile.writeText(jsonString)
        } catch (e: Exception) {
            println("Failed to save connections: ${e.message}")
        }
    }

    fun loadConnections(): List<ServerConnection> {
        return if (storageFile.exists()) {
            try {
                val jsonString = storageFile.readText()
                val storedConnections = json.decodeFromString<StoredConnections>(jsonString)
                storedConnections.connections
            } catch (e: Exception) {
                println("Failed to load connections: ${e.message}")
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    companion object {
        private const val DEFAULT_FILENAME = "known-dbs.dat"
        
        fun getDefaultFile(): File {
            val userHome = System.getProperty("user.home")
            val appDir = File(userHome, ".mongoviewer")
            return File(appDir, DEFAULT_FILENAME)
        }
    }
}
