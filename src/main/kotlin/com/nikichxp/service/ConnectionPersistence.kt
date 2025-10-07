package com.nikichxp.service

import com.nikichxp.model.ServerConnection
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.File

object ConnectionPersistence {
    private const val CONNECTIONS_FILE = "connections.dat"
    private val json = Json { prettyPrint = true }

    fun loadConnections(): List<ServerConnection> {
        val file = File(CONNECTIONS_FILE)
        return if (file.exists()) {
            try {
                val jsonString = file.readText()
                if (jsonString.isNotBlank()) {
                    json.decodeFromString(jsonString)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                // If there's any error reading the file, return empty list
                emptyList()
            }
        } else {
            // Create empty file if it doesn't exist
            file.createNewFile()
            emptyList()
        }
    }

    fun saveConnections(connections: List<ServerConnection>) {
        try {
            val file = File(CONNECTIONS_FILE)
            val jsonString = json.encodeToString(connections)
            file.writeText(jsonString)
        } catch (e: Exception) {
            println("Error saving connections: ${e.message}")
        }
    }

    fun saveConnection(connection: ServerConnection, connections: List<ServerConnection>): List<ServerConnection> {
        val updated = connections.toMutableList()
        val existingIndex = updated.indexOfFirst { it.id == connection.id }
        
        return if (existingIndex != -1) {
            updated[existingIndex] = connection
            updated
        } else {
            updated + connection
        }.also { saveConnections(it) }
    }

    fun deleteConnection(connectionId: String, connections: List<ServerConnection>): List<ServerConnection> {
        return connections.filter { it.id != connectionId }.also { saveConnections(it) }
    }
}

// Extension functions for Kotlin serialization
private inline fun <reified T> Json.decodeFromString(jsonString: String): T {
    return decodeFromString(serializer(), jsonString)
}

private inline fun <reified T> Json.encodeToString(value: T): String {
    return encodeToString(serializer(), value)
}
