package com.trilink.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trilink.app.data.model.Device
import com.trilink.app.data.model.Transfer
import com.trilink.app.data.model.TransferStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    devices: List<Device>,
    recentTransfers: List<Transfer>,
    onSendClick: () -> Unit,
    onPairClick: () -> Unit,
    onDeviceClick: (Device) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TriLink Ecosystem") },
                actions = {
                    IconButton(onClick = onPairClick) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Pair Device")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onSendClick,
                icon = { Icon(Icons.Default.Send, contentDescription = null) },
                text = { Text("Send Item") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Trusted Devices",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (devices.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("No paired devices found.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(onClick = onPairClick) {
                                Text("Pair Realme, Xiaomi, or Mac")
                            }
                        }
                    }
                }
            } else {
                items(devices) { device ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onDeviceClick(device) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (device.platform == "macos") Icons.Default.Laptop else Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.deviceName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (device.isOnlineLocally) "Online locally (${device.localEndpoint ?: "LAN"})" else "Offline / remote",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (device.isOnlineLocally) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                                )
                            }
                            AssistChip(
                                onClick = {},
                                label = { Text(if (device.isOnlineLocally) "Local" else "Remote") }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Recent Transfers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (recentTransfers.isEmpty()) {
                item {
                    Text(
                        "No recent transfers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                items(recentTransfers.take(5)) { transfer ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    transfer.displayName,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    transfer.status.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (transfer.status) {
                                        TransferStatus.DELIVERED -> MaterialTheme.colorScheme.secondary
                                        TransferStatus.FAILED -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${transfer.sizeBytes / 1024} KB • Route: ${transfer.deliveryMode.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
