import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bson.Document
import java.util.*

// Модели данных
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

// State Management
class AppState {
    var servers = mutableStateListOf<ServerConnection>()
    var tabs = mutableStateListOf<TabData>()
    var activeTabIndex by mutableStateOf(0)
    var splitMode by mutableStateOf(SplitMode.SINGLE)
    var splitActiveTabIndex by mutableStateOf<Int?>(null)
    var showAddServerDialog by mutableStateOf(false)
    var editingServer by mutableStateOf<ServerConnection?>(null)
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
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

    // Добавить тестовый сервер при первом запуске
    LaunchedEffect(Unit) {
        if (appState.servers.isEmpty()) {
            appState.servers.add(
                ServerConnection(
                    name = "Local MongoDB",
                    host = "localhost",
                    port = 27017
                )
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Левая панель - серверы и коллекции
            LeftPanel(
                appState = appState,
                onAddServer = { appState.showAddServerDialog = true },
                onServerExpand = { server ->
                    scope.launch {
                        loadCollections(appState, server)
                    }
                },
                onCollectionClick = { server, collection ->
                    openCollection(appState, server, collection)
                    scope.launch {
                        loadDocuments(appState, server, collection)
                    }
                }
            )

            // Правая панель - вкладки
            RightPanel(appState = appState) { tab ->
                scope.launch {
                    val server = appState.servers.find { it.name == tab.serverName }
                    if (server != null) {
                        loadDocuments(appState, server, tab.collectionName, tab)
                    }
                }
            }
        }

        // Диалог добавления сервера
        if (appState.showAddServerDialog) {
            ServerDialog(
                server = null,
                onDismiss = { appState.showAddServerDialog = false },
                onConfirm = { server ->
                    appState.servers.add(server)
                    appState.showAddServerDialog = false
                }
            )
        }

        // Диалог редактирования сервера
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

        // Индикатор загрузки
        if (appState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colors.primary)
            }
        }

        // Сообщение об ошибке
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
            // Заголовок
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Серверы",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onAddServer) {
                    Icon(Icons.Default.Add, "Добавить сервер")
                }
            }

            Divider()

            // Список серверов
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(appState.servers) { server ->
                    ServerItem(
                        server = server,
                        onExpand = { onServerExpand(server) },
                        onEdit = { appState.editingServer = server },
                        onDelete = {
                            appState.servers.remove(server)
                            // Закрыть все вкладки этого сервера
                            appState.tabs.removeAll { it.serverName == server.name }
                        },
                        onCollectionClick = { collection ->
                            onCollectionClick(server, collection)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ServerItem(
    server: ServerConnection,
    onExpand: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCollectionClick: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onExpand() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (server.isExpanded) Icons.Default.KeyboardArrowDown
                    else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Settings, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(server.name, style = MaterialTheme.typography.body1)
                    Text(
                        text = "${server.host}:${server.port}",
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray
                    )
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Меню", modifier = Modifier.size(20.dp))
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(onClick = {
                        showMenu = false
                        onEdit()
                    }) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Редактировать")
                    }
                    DropdownMenuItem(onClick = {
                        showMenu = false
                        onDelete()
                    }) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Удалить")
                    }
                }
            }
        }

        if (server.isExpanded) {
            Column(modifier = Modifier.padding(start = 32.dp)) {
                server.collections.forEach { collection ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCollectionClick(collection) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colors.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(collection, style = MaterialTheme.typography.body2)
                    }
                }
            }
        }
    }
}

@Composable
fun RightPanel(appState: AppState, onRefresh: (TabData) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Панель вкладок
        if (appState.tabs.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colors.surface,
                elevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())
                    ) {
                        appState.tabs.forEachIndexed { index, tab ->
                            TabItem(
                                tab = tab,
                                isActive = index == appState.activeTabIndex,
                                onClick = { appState.activeTabIndex = index },
                                onClose = {
                                    appState.tabs.removeAt(index)
                                    if (appState.activeTabIndex >= appState.tabs.size) {
                                        appState.activeTabIndex = (appState.tabs.size - 1).coerceAtLeast(0)
                                    }
                                }
                            )
                        }
                    }

                    // Кнопка split screen
                    IconButton(
                        onClick = {
                            appState.splitMode = when (appState.splitMode) {
                                SplitMode.SINGLE -> {
                                    appState.splitActiveTabIndex = appState.activeTabIndex
                                    SplitMode.HORIZONTAL
                                }
                                SplitMode.HORIZONTAL -> {
                                    appState.splitActiveTabIndex = null
                                    SplitMode.SINGLE
                                }
                            }
                        }
                    ) {
                        Icon(
                            if (appState.splitMode == SplitMode.HORIZONTAL)
                                Icons.Default.CheckCircle
                            else Icons.Default.Build,
                            "Split Screen"
                        )
                    }
                }
            }

            Divider()

            // Содержимое вкладок
            when (appState.splitMode) {
                SplitMode.SINGLE -> {
                    if (appState.tabs.isNotEmpty() && appState.activeTabIndex < appState.tabs.size) {
                        TabContent(
                            tab = appState.tabs[appState.activeTabIndex],
                            modifier = Modifier.fillMaxSize(),
                            onRefresh = onRefresh
                        )
                    }
                }
                SplitMode.HORIZONTAL -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (appState.tabs.isNotEmpty() && appState.activeTabIndex < appState.tabs.size) {
                            TabContent(
                                tab = appState.tabs[appState.activeTabIndex],
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onRefresh = onRefresh
                            )
                        }

                        Divider(modifier = Modifier.width(2.dp).fillMaxHeight())

                        val splitIndex = appState.splitActiveTabIndex ?: 0
                        if (appState.tabs.size > splitIndex) {
                            TabContent(
                                tab = appState.tabs[splitIndex],
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onRefresh = onRefresh
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Выберите коллекцию для просмотра", color = Color.Gray)
            }
        }
    }
}

@Composable
fun TabItem(
    tab: TabData,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = if (isActive) MaterialTheme.colors.background else MaterialTheme.colors.surface,
        modifier = Modifier.padding(4.dp)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${tab.serverName}/${tab.collectionName}",
                style = MaterialTheme.typography.body2,
                maxLines = 1
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    "Закрыть",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun TabContent(tab: TabData, modifier: Modifier, onRefresh: (TabData) -> Unit) {
    var filterText by remember(tab.id) { mutableStateOf(tab.filter) }
    var projectionText by remember(tab.id) { mutableStateOf(tab.projection) }

    Column(modifier = modifier) {
        // Панель управления
        Surface(
            color = MaterialTheme.colors.surface,
            elevation = 1.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Фильтр и проекция",
                        style = MaterialTheme.typography.body2,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        IconButton(
                            onClick = {
                                tab.viewMode = if (tab.viewMode == ViewMode.LIST)
                                    ViewMode.TREE else ViewMode.LIST
                            }
                        ) {
                            Icon(
                                if (tab.viewMode == ViewMode.LIST) Icons.Default.List
                                else Icons.Default.AccountTree,
                                "Переключить вид"
                            )
                        }
                        IconButton(onClick = { onRefresh(tab) }) {
                            Icon(Icons.Default.Refresh, "Обновить")
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        label = { Text("Фильтр") },
                        placeholder = { Text("{}") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = projectionText,
                        onValueChange = { projectionText = it },
                        label = { Text("Проекция") },
                        placeholder = { Text("{}") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = Color.White
                        )
                    )
                    Button(
                        onClick = {
                            tab.filter = filterText
                            tab.projection = projectionText
                            onRefresh(tab)
                        }
                    ) {
                        Text("Применить")
                    }
                }
            }
        }

        Divider()

        // Список документов
        if (tab.documents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Нет документов", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tab.documents) { doc ->
                    DocumentCard(doc, tab.viewMode)
                }
            }
        }
    }
}

@Composable
fun DocumentCard(document: Document, viewMode: ViewMode) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colors.surface,
        elevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            when (viewMode) {
                ViewMode.LIST -> {
                    Text(
                        text = document.toJson(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFCE9178)
                    )
                }
                ViewMode.TREE -> {
                    DocumentTreeView(document, 0)
                }
            }
        }
    }
}

@Composable
fun DocumentTreeView(doc: Any?, level: Int) {
    val indent = 16.dp * level

    when (doc) {
        is Document -> {
            doc.forEach { (key, value) ->
                Row(modifier = Modifier.padding(start = indent)) {
                    Text(
                        text = "$key: ",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF9CDCFE),
                        fontWeight = FontWeight.Bold
                    )
                    if (value is Document || value is List<*>) {
                        Text("{...}", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        Text(
                            text = value.toString(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFFCE9178)
                        )
                    }
                }
                if (value is Document || value is List<*>) {
                    DocumentTreeView(value, level + 1)
                }
            }
        }
        is List<*> -> {
            doc.forEachIndexed { index, item ->
                Row(modifier = Modifier.padding(start = indent)) {
                    Text(
                        text = "[$index]: ",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF9CDCFE)
                    )
                }
                DocumentTreeView(item, level + 1)
            }
        }
    }
}

@Composable
fun ServerDialog(
    server: ServerConnection?,
    onDismiss: () -> Unit,
    onConfirm: (ServerConnection) -> Unit
) {
    var name by remember { mutableStateOf(server?.name ?: "") }
    var host by remember { mutableStateOf(server?.host ?: "localhost") }
    var port by remember { mutableStateOf(server?.port?.toString() ?: "27017") }
    var username by remember { mutableStateOf(server?.username ?: "") }
    var password by remember { mutableStateOf(server?.password ?: "") }
    var authDatabase by remember { mutableStateOf(server?.authDatabase ?: "admin") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (server == null) "Добавить сервер" else "Редактировать сервер") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя сервера") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Хост") },
                        modifier = Modifier.weight(2f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text("Порт") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    "Аутентификация (опционально)",
                    style = MaterialTheme.typography.subtitle2,
                    color = Color.Gray
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Имя пользователя") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Person, "Username")
                    }
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showPassword)
                        androidx.compose.ui.text.input.VisualTransformation.None
                    else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(Icons.Default.Lock, "Password")
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.Check else Icons.Default.Close,
                                if (showPassword) "Скрыть" else "Показать"
                            )
                        }
                    }
                )

                OutlinedTextField(
                    value = authDatabase,
                    onValueChange = { authDatabase = it },
                    label = { Text("Auth Database") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = username.isNotEmpty()
                )

                if (username.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                "Connection String:",
                                style = MaterialTheme.typography.caption,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "mongodb://$username:***@$host:$port/?authSource=$authDatabase",
                                style = MaterialTheme.typography.caption,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && host.isNotBlank() && port.isNotBlank()) {
                        val newServer = ServerConnection(
                            id = server?.id ?: UUID.randomUUID().toString(),
                            name = name,
                            host = host,
                            port = port.toIntOrNull() ?: 27017,
                            username = username,
                            password = password,
                            authDatabase = authDatabase
                        )
                        onConfirm(newServer)
                    }
                },
                enabled = name.isNotBlank() && host.isNotBlank() && port.isNotBlank()
            ) {
                Text(if (server == null) "Добавить" else "Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

// MongoDB операции
suspend fun loadCollections(appState: AppState, server: ServerConnection) {
    withContext(Dispatchers.IO) {
        try {
            appState.isLoading = true
            val client = MongoClients.create(server.connectionString)
            val collections = client.listDatabaseNames()
                .flatMap { dbName ->
                    client.getDatabase(dbName).listCollectionNames().map { "$dbName.$it" }
                }
                .toList()

            withContext(Dispatchers.Main) {
                val index = appState.servers.indexOf(server)
                appState.servers[index] = server.copy(
                    collections = collections,
                    isExpanded = true
                )
            }
            client.close()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                appState.errorMessage = "Ошибка подключения: ${e.message}"
            }
        } finally {
            withContext(Dispatchers.Main) {
                appState.isLoading = false
            }
        }
    }
}

fun openCollection(appState: AppState, server: ServerConnection, collectionName: String) {
    val existing = appState.tabs.find {
        it.serverName == server.name && it.collectionName == collectionName
    }

    if (existing != null) {
        appState.activeTabIndex = appState.tabs.indexOf(existing)
    } else {
        val newTab = TabData(serverName = server.name, collectionName = collectionName)
        appState.tabs.add(newTab)
        appState.activeTabIndex = appState.tabs.size - 1
    }
}

suspend fun loadDocuments(
    appState: AppState,
    server: ServerConnection,
    collectionName: String,
    tab: TabData? = null
) {
    withContext(Dispatchers.IO) {
        try {
            appState.isLoading = true
            val client = MongoClients.create(server.connectionString)
            val parts = collectionName.split(".")
            val dbName = parts[0]
            val colName = parts.drop(1).joinToString(".")

            val collection = client.getDatabase(dbName).getCollection(colName)

            val currentTab = tab ?: appState.tabs.find {
                it.serverName == server.name && it.collectionName == collectionName
            }

            val filterDoc = try {
                Document.parse(currentTab?.filter ?: "{}")
            } catch (e: Exception) {
                Document()
            }

            val projectionDoc = try {
                Document.parse(currentTab?.projection ?: "{}")
            } catch (e: Exception) {
                null
            }

            val query = if (projectionDoc != null && projectionDoc.isNotEmpty()) {
                collection.find(filterDoc).projection(projectionDoc)
            } else {
                collection.find(filterDoc)
            }

            val documents = query.limit(100).into(mutableListOf())

            withContext(Dispatchers.Main) {
                currentTab?.documents = documents
            }

            client.close()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                appState.errorMessage = "Ошибка загрузки: ${e.message}"
            }
        } finally {
            withContext(Dispatchers.Main) {
                appState.isLoading = false
            }
        }
    }
}