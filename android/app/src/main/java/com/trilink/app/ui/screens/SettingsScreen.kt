package com.trilink.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    remoteDeliveryEnabled: Boolean,
    onToggleRemoteDelivery: (Boolean) -> Unit
) {
    var remoteEnabled by remember { mutableStateOf(remoteDeliveryEnabled) }
    var maxFileSizeMb by remember { mutableStateOf(500) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings & Privacy") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Delivery Preferences", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Allow Remote Delivery", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "When peers are on separate Wi-Fi networks, send end-to-end encrypted payloads via cloud relay.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = remoteEnabled,
                            onCheckedChange = {
                                remoteEnabled = it
                                onToggleRemoteDelivery(it)
                            }
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Maximum Transfer Size", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Current limit: $maxFileSizeMb MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Slider(
                        value = maxFileSizeMb.toFloat(),
                        onValueChange = { maxFileSizeMb = it.toInt() },
                        valueRange = 50f..2000f,
                        steps = 39
                    )
                }
            }

            Text("Security & Privacy Architecture", style = MaterialTheme.typography.titleMedium)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hardware-Backed Keystore", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        "Private cryptographic keys never leave this device. All local transfers require cryptographic signature verification.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("End-to-End Encrypted Relay", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        "Cloud storage only ever holds ciphertext encrypted on this device. Supabase relay cannot read user files or content.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
