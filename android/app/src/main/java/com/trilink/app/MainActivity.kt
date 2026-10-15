package com.trilink.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.trilink.app.data.model.*
import com.trilink.app.ui.navigation.MainAppNavigation
import com.trilink.app.ui.theme.TriLinkTheme
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TriLinkApplication
        val deviceRepo = app.deviceRepository
        val transferRepo = app.transferRepository

        val currentDevicePayload = app.currentDevicePayload

        setContent {
            TriLinkTheme {
                val devices by deviceRepo.getTrustedDevices().collectAsState(initial = emptyList())
                val transfers by transferRepo.getAllTransfers().collectAsState(initial = emptyList())
                val inboxItems by transferRepo.getInboxItems().collectAsState(initial = emptyList())
                var remoteDeliveryEnabled by remember { mutableStateOf(false) }

                MainAppNavigation(
                    devices = devices,
                    transfers = transfers,
                    inboxItems = inboxItems,
                    currentDevicePayload = currentDevicePayload,
                    remoteDeliveryEnabled = remoteDeliveryEnabled,
                    onSendText = { target, text, itemType ->
                        lifecycleScope.launch {
                            val transfer = Transfer(
                                senderDeviceId = currentDevicePayload.deviceId,
                                recipientDeviceId = target.id,
                                itemType = itemType,
                                displayName = if (itemType == ItemType.URL) "Link" else "Text note",
                                sizeBytes = text.toByteArray().size.toLong(),
                                sha256 = "dummy-sha-text",
                                deliveryMode = if (target.isOnlineLocally) DeliveryMode.LOCAL else DeliveryMode.REMOTE,
                                status = TransferStatus.UPLOADING,
                                textContent = text
                            )
                            transferRepo.createTransfer(transfer)
                            Toast.makeText(this@MainActivity, "Transfer initiated to ${target.deviceName}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSendFile = { target, uri ->
                        lifecycleScope.launch {
                            val transfer = Transfer(
                                senderDeviceId = currentDevicePayload.deviceId,
                                recipientDeviceId = target.id,
                                itemType = ItemType.FILE,
                                displayName = uri.lastPathSegment ?: "file",
                                sizeBytes = 1024,
                                sha256 = "dummy-sha-file",
                                deliveryMode = if (target.isOnlineLocally) DeliveryMode.LOCAL else DeliveryMode.REMOTE,
                                status = TransferStatus.UPLOADING,
                                localPath = uri.toString()
                            )
                            transferRepo.createTransfer(transfer)
                            Toast.makeText(this@MainActivity, "File transfer started", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onPairApproved = { payload ->
                        lifecycleScope.launch {
                            val res = deviceRepo.pairDevice(payload)
                            if (res.isSuccess) {
                                Toast.makeText(this@MainActivity, "Paired with ${payload.deviceName}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@MainActivity, "Pairing failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onRevokeDevice = { id ->
                        lifecycleScope.launch {
                            deviceRepo.revokeDevice(id)
                            Toast.makeText(this@MainActivity, "Device trust revoked", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCancelTransfer = { id ->
                        lifecycleScope.launch {
                            transferRepo.cancelTransfer(id)
                        }
                    },
                    onRetryTransfer = { transfer ->
                        lifecycleScope.launch {
                            transferRepo.updateTransferProgress(transfer.id, TransferStatus.UPLOADING, 0)
                        }
                    },
                    onDeleteInboxItem = { id ->
                        lifecycleScope.launch {
                            transferRepo.deleteInboxItem(id)
                        }
                    },
                    onOpenFile = { path ->
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(path), "*/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onToggleRemoteDelivery = { remoteDeliveryEnabled = it }
                )
            }
        }
    }
}
