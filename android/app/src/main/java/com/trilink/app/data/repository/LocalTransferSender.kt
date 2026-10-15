package com.trilink.app.data.repository

import android.content.Context
import android.net.Uri
import com.trilink.app.data.model.ItemType
import com.trilink.app.data.model.Transfer
import com.trilink.app.data.model.TransferStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

object LocalTransferSender {

    suspend fun executeTransfer(
        context: Context,
        transfer: Transfer,
        targetEndpoint: String,
        transferRepo: TransferRepository,
        onProgress: suspend (Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var normalized = targetEndpoint.trim().removeSuffix("/")
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }

        try {
            // 1. Resolve payload bytes
            val dataBytes: ByteArray = when (transfer.itemType) {
                ItemType.TEXT, ItemType.URL -> {
                    transfer.textContent?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
                }
                ItemType.FILE -> {
                    val uriStr = transfer.localPath
                    if (uriStr != null) {
                        val uri = Uri.parse(uriStr)
                        val inputStream: InputStream? = if (uri.scheme == "content" || uri.scheme == "file") {
                            context.contentResolver.openInputStream(uri)
                        } else {
                            java.io.File(uriStr).inputStream()
                        }
                        inputStream?.use { it.readBytes() } ?: ByteArray(0)
                    } else {
                        ByteArray(0)
                    }
                }
            }

            val sha256Hex = MessageDigest.getInstance("SHA-256")
                .digest(dataBytes)
                .joinToString("") { "%02x".format(it) }

            val timestamp = System.currentTimeMillis()
            val nonce = UUID.randomUUID().toString()

            // 2. Initiate Transfer
            val initiateUrl = URL("$normalized/api/v1/transfers/initiate")
            val initConn = (initiateUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-TriLink-Sender-ID", transfer.senderDeviceId)
                setRequestProperty("X-TriLink-Timestamp", "$timestamp")
                setRequestProperty("X-TriLink-Nonce", nonce)
                setRequestProperty("X-TriLink-Signature", "trilink-local-auth")
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
            }

            val initJson = JSONObject().apply {
                put("version", 1)
                put("transfer_id", transfer.id)
                put("transferId", transfer.id)
                put("sender_device_id", transfer.senderDeviceId)
                put("senderDeviceId", transfer.senderDeviceId)
                put("recipient_device_id", transfer.recipientDeviceId)
                put("recipientDeviceId", transfer.recipientDeviceId)
                put("item_type", transfer.itemType.name.lowercase())
                put("itemType", transfer.itemType.name.lowercase())
                put("display_name", transfer.displayName)
                put("displayName", transfer.displayName)
                put("size_bytes", dataBytes.size.toLong())
                put("sizeBytes", dataBytes.size.toLong())
                put("sha256", sha256Hex)
                put("delivery_mode", "local")
                put("deliveryMode", "local")
                if (transfer.textContent != null) {
                    put("text_content", transfer.textContent)
                    put("textContent", transfer.textContent)
                }
            }

            initConn.outputStream.use { os ->
                os.write(initJson.toString().toByteArray(Charsets.UTF_8))
            }

            val initCode = initConn.responseCode
            if (initCode !in 200..299) {
                val err = initConn.errorStream?.bufferedReader()?.readText() ?: "HTTP $initCode"
                transferRepo.markFailed(transfer.id, "INITIATE_FAILED", err)
                return@withContext Result.failure(Exception("Peer rejected initiate: $err"))
            }

            // If text transfer was already completed by text_content in initiate
            if (dataBytes.isEmpty()) {
                transferRepo.updateTransferProgress(transfer.id, TransferStatus.DELIVERED, 0)
                onProgress(0)
                return@withContext Result.success(Unit)
            }

            // 3. Upload Payload
            val payloadUrl = URL("$normalized/api/v1/transfers/${transfer.id}/payload")
            val payloadConn = (payloadUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("Content-Length", "${dataBytes.size}")
                setRequestProperty("X-TriLink-Sender-ID", transfer.senderDeviceId)
                setRequestProperty("X-TriLink-Timestamp", "${System.currentTimeMillis()}")
                setRequestProperty("X-TriLink-Nonce", UUID.randomUUID().toString())
                setRequestProperty("X-TriLink-Signature", "trilink-local-auth")
                setFixedLengthStreamingMode(dataBytes.size)
                connectTimeout = 10000
                readTimeout = 30000
                doOutput = true
            }

            transferRepo.updateTransferProgress(transfer.id, TransferStatus.UPLOADING, 0)

            val chunkSize = 32768
            var sent = 0
            payloadConn.outputStream.use { os ->
                while (sent < dataBytes.size) {
                    val end = minOf(sent + chunkSize, dataBytes.size)
                    os.write(dataBytes, sent, end - sent)
                    sent = end
                    transferRepo.updateTransferProgress(transfer.id, TransferStatus.UPLOADING, sent.toLong())
                    onProgress(sent.toLong())
                }
                os.flush()
            }

            val payloadCode = payloadConn.responseCode
            if (payloadCode in 200..299) {
                transferRepo.updateTransferProgress(transfer.id, TransferStatus.DELIVERED, dataBytes.size.toLong())
                Result.success(Unit)
            } else {
                val err = payloadConn.errorStream?.bufferedReader()?.readText() ?: "HTTP $payloadCode"
                transferRepo.markFailed(transfer.id, "PAYLOAD_FAILED", err)
                Result.failure(Exception("Upload payload rejected: $err"))
            }
        } catch (e: Exception) {
            transferRepo.markFailed(transfer.id, "NETWORK_ERROR", e.message)
            Result.failure(e)
        }
    }
}
