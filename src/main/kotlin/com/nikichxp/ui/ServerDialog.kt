package com.nikichxp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nikichxp.model.ServerConnection

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ServerDialog(
    server: ServerConnection?,
    onDismiss: () -> Unit,
    onConfirm: (ServerConnection) -> Unit
) {
    val isEditMode = server != null
    var name by remember { mutableStateOf(server?.name ?: "") }
    var host by remember { mutableStateOf(server?.host ?: "") }
    var port by remember { mutableStateOf(server?.port?.toString() ?: "27017") }
    var username by remember { mutableStateOf(server?.username ?: "") }
    var password by remember { mutableStateOf(server?.password ?: "") }
    var authDatabase by remember { mutableStateOf(server?.authDatabase ?: "admin") }
    
    // Form validation
    val isFormValid = name.isNotBlank() && host.isNotBlank() && port.toIntOrNull() != null

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = if (isEditMode) "Edit Server" else "Add Server",
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    isError = name.isBlank(),
                )
                if (name.isBlank()) {
                    Text(
                        text = "Name is required",
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                    )
                }
                // Host and port row
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("Host") },
                            isError = host.isBlank(),
                            modifier = Modifier.weight(3f)
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) port = it },
                            label = { Text("Port") },
                            isError = port.toIntOrNull() == null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (host.isBlank() || port.toIntOrNull() == null) {
                        val errorMessage = when {
                            host.isBlank() && port.toIntOrNull() == null -> "Host and port are required"
                            host.isBlank() -> "Host is required"
                            port.toIntOrNull() == null -> "Port must be a number"
                            else -> ""
                        }
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.caption,
                            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username (optional)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = authDatabase,
                    onValueChange = { authDatabase = it },
                    label = { Text("Auth Database") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(
                                ServerConnection(
                                    id = server?.id ?: "",
                                    name = name,
                                    host = host,
                                    port = port.toIntOrNull() ?: 27017,
                                    username = username,
                                    password = password,
                                    authDatabase = authDatabase
                                )
                            )
                        },
                        enabled = name.isNotBlank() && host.isNotBlank() && port.isNotBlank()
                    ) {
                        Text(if (isEditMode) "SAVE" else "ADD")
                    }
                }
            }
        }
    }
}
