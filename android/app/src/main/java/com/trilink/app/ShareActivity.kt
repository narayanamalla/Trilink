package com.trilink.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.trilink.app.data.model.*
import com.trilink.app.ui.theme.TriLinkTheme
import kotlinx.coroutines.launch

class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TriLinkApplication
        val deviceRepo = app.deviceRepository
        val transferRepo = app.transferRepository

        val intent = intent
        val action = intent.action
        val type = intent.type

        val sharedText = if (Intent.ACTION_SEND == action && type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        val sharedUri: Uri? = if (Intent.ACTION_SEND == action && type != null && type != "text/plain") {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        } else null

        setContent {
            TriLinkTheme {
                val devices by deviceRepo.getTrustedDevices().collectAsState(initial = emptyList())

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Share via TriLink", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (sharedText != null) "Sharing: \"$sharedText\"" else "Sharing file: ${sharedUri?.lastPathSegment ?: "Content"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (devices.isEmpty()) {
                            Text("No paired devices available. Please open TriLink and pair a device first.")
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { finish() }) {
                                Text("Close")
                            }
                        } else {
                            Text("Send to:", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            devices.forEach { device ->
                                OutlinedButton(
                                    onClick = {
                                        lifecycleScope.launch {
                                            if (sharedText != null) {
                                                val isUrl = sharedText.startsWith("http://") || sharedText.startsWith("https://")
                                                transferRepo.createTransfer(
                                                    Transfer(
                                                        senderDeviceId = "android-local",
                                                        recipientDeviceId = device.id,
                                                        itemType = if (isUrl) ItemType.URL else ItemType.TEXT,
                                                        displayName = if (isUrl) "Shared Link" else "Shared Note",
                                                        sizeBytes = sharedText.toByteArray().size.toLong(),
                                                        sha256 = "dummy-sha",
                                                        deliveryMode = if (device.isOnlineLocally) DeliveryMode.LOCAL else DeliveryMode.REMOTE,
                                                        status = TransferStatus.UPLOADING,
                                                        textContent = sharedText
                                                    )
                                                )
                                            } else if (sharedUri != null) {
                                                transferRepo.createTransfer(
                                                    Transfer(
                                                        senderDeviceId = "android-local",
                                                        recipientDeviceId = device.id,
                                                        itemType = ItemType.FILE,
                                                        displayName = sharedUri.lastPathSegment ?: "shared_file",
                                                        sizeBytes = 1024,
                                                        sha256 = "dummy-sha",
                                                        deliveryMode = if (device.isOnlineLocally) DeliveryMode.LOCAL else DeliveryMode.REMOTE,
                                                        status = TransferStatus.UPLOADING,
                                                        localPath = sharedUri.toString()
                                                    )
                                                )
                                            }
                                            Toast.makeText(this@ShareActivity, "Sending to ${device.deviceName}...", Toast.LENGTH_SHORT).show()
                                            finish()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Text("${device.deviceName} (${if (device.isOnlineLocally) "Local" else "Remote"})")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
