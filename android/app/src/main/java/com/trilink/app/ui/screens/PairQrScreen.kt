package com.trilink.app.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.trilink.app.data.model.PairingPayload
import com.trilink.app.ui.qr.CameraQrScanner
import com.trilink.app.ui.qr.QrCodeGenerator
import org.json.JSONObject
import java.net.URL
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairQrScreen(
    currentDevicePayload: PairingPayload,
    onPairApproved: (PairingPayload) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0 = Scan Mac QR, 1 = My QR Code
    var manualTokenInput by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf<String?>(null) }
    var showManualInput by remember { mutableStateOf(false) }

    // Camera permission check
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            showManualInput = true
        }
    }

    // Generate JSON string for phone's QR code
    val myPayloadJsonString = remember(currentDevicePayload) {
        JSONObject().apply {
            put("version", currentDevicePayload.version)
            put("pairing_id", currentDevicePayload.pairingId)
            put("device_id", currentDevicePayload.deviceId)
            put("device_name", currentDevicePayload.deviceName)
            put("platform", currentDevicePayload.platform)
            put("public_key", currentDevicePayload.publicKey)
            put("exchange_key", currentDevicePayload.exchangeKey)
            put("local_endpoint", currentDevicePayload.localEndpoint ?: "")
            put("expires_at", currentDevicePayload.expiresAt)
        }.toString()
    }

    val myQrBitmap = remember(myPayloadJsonString) {
        QrCodeGenerator.generateQrCodeBitmap(myPayloadJsonString, 512)
    }

    fun parseAndSubmit(inputRaw: String) {
        try {
            val input = inputRaw.trim()
            if (input.isEmpty()) {
                parseError = "Please enter or scan a valid code."
                return
            }

            val payload = if (input.startsWith("{")) {
                val json = JSONObject(input)
                val rawExpires = json.optLong("expires_at", System.currentTimeMillis() + 600_000L)
                val expiresAt = if (rawExpires < 100_000_000_000L) rawExpires * 1000L else rawExpires

                PairingPayload(
                    version = json.optInt("version", 1),
                    pairingId = json.optString("pairing_id", UUID.randomUUID().toString()),
                    deviceId = json.getString("device_id"),
                    deviceName = json.optString("device_name", "Peer Device"),
                    platform = json.optString("platform", "macos"),
                    publicKey = json.getString("public_key"),
                    exchangeKey = json.getString("exchange_key"),
                    localEndpoint = json.optString("local_endpoint", "").takeIf { it.isNotBlank() },
                    expiresAt = expiresAt
                )
            } else if (input.contains("/pair?") || input.startsWith("http://") || input.startsWith("https://")) {
                // Scanned a URI like http://192.168.0.4:42425/pair?id=...
                val url = URL(if (input.startsWith("http")) input else "http://$input")
                val baseUrl = "${url.protocol}://${url.authority}"
                val queryParams = url.query?.split("&")?.associate {
                    val parts = it.split("=")
                    if (parts.size == 2) parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8") else parts[0] to ""
                } ?: emptyMap()

                PairingPayload(
                    version = 1,
                    pairingId = UUID.randomUUID().toString(),
                    deviceId = queryParams["id"] ?: "macbook-air",
                    deviceName = queryParams["name"] ?: "MacBook Air",
                    platform = "macos",
                    publicKey = queryParams["key"] ?: "MCowBQYDK2VwAyEAXyZ...",
                    exchangeKey = "MCowBQYDK2VuAyEA9kL...",
                    localEndpoint = baseUrl,
                    expiresAt = System.currentTimeMillis() + 600_000L
                )
            } else {
                PairingPayload(
                    version = 1,
                    pairingId = UUID.randomUUID().toString(),
                    deviceId = "peer-device",
                    deviceName = "Peer Device",
                    platform = "unknown",
                    publicKey = "MCowBQYDK2VwAyEAXyZ...",
                    exchangeKey = "MCowBQYDK2VuAyEA9kL...",
                    localEndpoint = if (input.startsWith("http")) input else "http://$input",
                    expiresAt = System.currentTimeMillis() + 600_000L
                )
            }

            onPairApproved(payload)
            onBack()
        } catch (e: Exception) {
            parseError = "Error parsing code: ${e.message}"
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Pair Device",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Pinterest-style Floating Pill Tab Bar
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(100.dp))
                            .background(
                                if (selectedTab == 0) MaterialTheme.colorScheme.surface else Color.Transparent
                            )
                            .clickable { selectedTab = 0 },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Scan Mac QR",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selectedTab == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(100.dp))
                            .background(
                                if (selectedTab == 1) MaterialTheme.colorScheme.surface else Color.Transparent
                            )
                            .clickable { selectedTab = 1 },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "My Code",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selectedTab == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // TAB 0: SCAN MAC QR
            if (selectedTab == 0) {
                if (hasCameraPermission) {
                    CameraQrScanner(
                        onBarcodeDetected = { scannedString ->
                            parseAndSubmit(scannedString)
                        }
                    )
                } else {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Camera Permission Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "TriLink scans the QR code shown on your Mac to establish a zero-trust local connection.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Enable Camera")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Toggle for Manual / Paste Input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showManualInput = !showManualInput }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (showManualInput) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (showManualInput) "Hide manual code entry" else "Or paste code manually",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                AnimatedVisibility(visible = showManualInput) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Paste Pairing JSON or IP",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = manualTokenInput,
                                onValueChange = {
                                    manualTokenInput = it
                                    parseError = null
                                },
                                placeholder = { Text("Paste code copied from TriLink Mac...") },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                            )

                            if (parseError != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    parseError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { parseAndSubmit(manualTokenInput) },
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Pair Now")
                            }
                        }
                    }
                }
            } else {
                // TAB 1: MY QR CODE (Aesthetic Pinterest Pin style)
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Scan from Another Device",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Point your Mac or Pad camera to link this device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // QR Code Container with aesthetic shadow & border
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .size(240.dp)
                                .border(1.dp, Color(0x10000000), RoundedCornerShape(24.dp))
                                .padding(16.dp)
                        ) {
                            if (myQrBitmap != null) {
                                Image(
                                    bitmap = myQrBitmap,
                                    contentDescription = "Pairing QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.QrCode,
                                        contentDescription = null,
                                        modifier = Modifier.size(160.dp),
                                        tint = Color.Black
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Device Details Pill
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        currentDevicePayload.deviceName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "ID: ${currentDevicePayload.publicKey.take(12)}...",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("TriLink Pairing JSON", myPayloadJsonString)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Pairing code copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy Code",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
