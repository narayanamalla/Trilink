package com.trilink.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trilink.app.data.model.DeliveryMode
import com.trilink.app.data.model.Device
import com.trilink.app.data.model.ItemType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    devices: List<Device>,
    onSendText: (targetDevice: Device, text: String, itemType: ItemType) -> Unit,
    onSendFile: (targetDevice: Device, uri: Uri) -> Unit,
    onBack: () -> Unit
) {
    var selectedDevice by remember { mutableStateOf(devices.firstOrNull()) }
    var contentType by remember { mutableStateOf("text") } // "text" or "file"
    var textInput by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedFileUri = uri
    }

    val route = if (selectedDevice?.isOnlineLocally == true) DeliveryMode.LOCAL else DeliveryMode.REMOTE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send Content") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Select Recipient Device", style = MaterialTheme.typography.titleSmall)

            if (devices.isEmpty()) {
                Text(
                    "No paired devices. Please pair your tablet or Mac first.",
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedDevice?.let { "${it.deviceName} (${if (it.isOnlineLocally) "Local" else "Remote"})" } ?: "Select Device",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        devices.forEach { device ->
                            DropdownMenuItem(
                                text = {
                                    Text("${device.deviceName} (${device.platform}) - ${if (device.isOnlineLocally) "Local" else "Remote"}")
                                },
                                onClick = {
                                    selectedDevice = device
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Route Badge
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (route == DeliveryMode.LOCAL)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (route == DeliveryMode.LOCAL) Icons.Default.Wifi else Icons.Default.CloudQueue,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Delivery Route: ${route.name}", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (route == DeliveryMode.LOCAL)
                                "Direct P2P via local network (fast & encrypted)"
                            else
                                "End-to-end encrypted relay via Supabase",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Mode Selector
            Row(modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = contentType == "text",
                    onClick = { contentType = "text" },
                    label = { Text("Text / URL") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = contentType == "file",
                    onClick = { contentType = "file" },
                    label = { Text("File / Image") },
                    modifier = Modifier.weight(1f)
                )
            }

            if (contentType == "text") {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Type text or paste link...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
                Button(
                    onClick = {
                        val dev = selectedDevice ?: return@Button
                        val itemType = if (textInput.startsWith("http://") || textInput.startsWith("https://")) ItemType.URL else ItemType.TEXT
                        onSendText(dev, textInput, itemType)
                        textInput = ""
                        onBack()
                    },
                    enabled = selectedDevice != null && textInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send Text")
                }
            } else {
                OutlinedButton(
                    onClick = { filePicker.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (selectedFileUri != null) "File Selected" else "Choose File or Photo")
                }

                if (selectedFileUri != null) {
                    Text(
                        "Selected: ${selectedFileUri?.path ?: ""}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        val dev = selectedDevice ?: return@Button
                        val uri = selectedFileUri ?: return@Button
                        onSendFile(dev, uri)
                        onBack()
                    },
                    enabled = selectedDevice != null && selectedFileUri != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Transfer File")
                }
            }
        }
    }
}
