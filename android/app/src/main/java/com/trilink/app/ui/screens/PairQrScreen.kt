package com.trilink.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trilink.app.data.model.PairingPayload

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairQrScreen(
    currentDevicePayload: PairingPayload,
    onPairApproved: (PairingPayload) -> Unit,
    onBack: () -> Unit
) {
    var isShowingQr by remember { mutableStateOf(true) }
    var manualTokenInput by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair TriLink Device") },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = isShowingQr,
                    onClick = { isShowingQr = true },
                    label = { Text("My Pairing Code") },
                    leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = !isShowingQr,
                    onClick = { isShowingQr = false },
                    label = { Text("Scan / Enter Code") },
                    leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (isShowingQr) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Scan with Xiaomi Pad or MacBook Air",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "This code is single-use and expires in 3 minutes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // QR Code Placeholder visual container
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .background(Color.White, shape = MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = "QR Code",
                                modifier = Modifier.size(160.dp),
                                tint = Color.Black
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Device: ${currentDevicePayload.deviceName} (${currentDevicePayload.platform})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Fingerprint: ${currentDevicePayload.publicKey.take(16)}...",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Enter Peer Pairing Payload",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Paste the JSON or scan QR code displayed on your other device.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = manualTokenInput,
                            onValueChange = {
                                manualTokenInput = it
                                parseError = null
                            },
                            label = { Text("Pairing JSON or Token") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        )

                        if (parseError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(parseError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                try {
                                    val input = manualTokenInput.trim()
                                    if (input.isEmpty()) {
                                        parseError = "Please enter a valid pairing code."
                                        return@Button
                                    }

                                    val payload = if (input.startsWith("{")) {
                                        val json = org.json.JSONObject(input)
                                        PairingPayload(
                                            version = json.optInt("version", 1),
                                            pairingId = json.optString("pairing_id", java.util.UUID.randomUUID().toString()),
                                            deviceId = json.getString("device_id"),
                                            deviceName = json.optString("device_name", "MacBook Air"),
                                            platform = json.optString("platform", "macos"),
                                            publicKey = json.getString("public_key"),
                                            exchangeKey = json.getString("exchange_key"),
                                            localEndpoint = json.optString("local_endpoint", null),
                                            expiresAt = json.optLong("expires_at", System.currentTimeMillis() + 600_000)
                                        )
                                    } else {
                                        val endpoint = if (input.startsWith("http")) input else "http://$input"
                                        PairingPayload(
                                            version = 1,
                                            pairingId = java.util.UUID.randomUUID().toString(),
                                            deviceId = "macbook-peer",
                                            deviceName = "MacBook Air",
                                            platform = "macos",
                                            publicKey = "MCowBQYDK2VwAyEAXyZ...",
                                            exchangeKey = "MCowBQYDK2VuAyEA9kL...",
                                            localEndpoint = endpoint,
                                            expiresAt = System.currentTimeMillis() + 600_000
                                        )
                                    }

                                    onPairApproved(payload)
                                    onBack()
                                } catch (e: Exception) {
                                    parseError = "Failed to parse pairing code: ${e.message}"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Confirm & Pair Device")
                        }
                    }
                }
            }
        }
    }
}
