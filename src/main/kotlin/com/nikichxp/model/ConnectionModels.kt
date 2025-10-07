package com.nikichxp.model

import kotlinx.serialization.Serializable
import org.bson.Document
import java.util.*

@Serializable
data class ServerConnection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 27017,
    val username: String = "",
    val password: String = "",
    val authDatabase: String = "admin",
    var collections: List<String> = emptyList(),
    var isExpanded: Boolean = false
) {
    val connectionString: String
        get() = if (username.isNotEmpty()) {
            "mongodb://$username:$password@$host:$port/?authSource=$authDatabase"
        } else {
            "mongodb://$host:$port"
        }
}

data class TabData(
    val id: String = UUID.randomUUID().toString(),
    val serverName: String,
    val collectionName: String,
    var documents: List<Document> = emptyList(),
    var viewMode: ViewMode = ViewMode.LIST,
    var filter: String = "{}",
    var projection: String = "{}"
)

enum class ViewMode {
    LIST, TREE
}

enum class SplitMode {
    SINGLE, HORIZONTAL
}
