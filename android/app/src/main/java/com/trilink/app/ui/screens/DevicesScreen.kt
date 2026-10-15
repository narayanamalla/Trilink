package com.trilink.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trilink.app.data.model.Device
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    devices: List<Device>,
    onPairClick: () -> Unit,
    onRevokeDevice: (id: String) -> Unit
) {
    var deviceToRevoke by remember { mutableStateOf<Device?>(null) }
    val dateFormatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trusted Devices") },
                actions = {
                    IconButton(onClick = onPairClick) {
                        Icon(Icons.Default.Add, contentDescription = "Pair New Device")
                    }
                }
            )
        }
    ) { padding ->
        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No trusted devices yet.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onPairClick) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pair Realme, Xiaomi, or Mac")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(devices) { device ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (device.platform == "macos") Icons.Default.Laptop else Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(device.deviceName, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Platform: ${device.platform.replaceFirstChar { it.uppercase() }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                AssistChip(
                                    onClick = {},
                                    label = { Text(if (device.isOnlineLocally) "Local Reachable" else "Offline / Remote") }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Last Seen: ${dateFormatter.format(Date(device.lastSeenAt))}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Identity Fingerprint: ${device.publicKey.take(16)}...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = { deviceToRevoke = device },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Block, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Revoke Trust")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Revocation Confirmation Dialog
        deviceToRevoke?.let { dev ->
            AlertDialog(
                onDismissRequest = { deviceToRevoke = null },
                title = { Text("Revoke ${dev.deviceName}?") },
                text = {
                    Text("Revoking this device will permanently prevent it from sending or receiving content across TriLink until re-paired.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onRevokeDevice(dev.id)
                            deviceToRevoke = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Revoke")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deviceToRevoke = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
