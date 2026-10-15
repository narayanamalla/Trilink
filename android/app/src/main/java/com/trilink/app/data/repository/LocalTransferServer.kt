package com.trilink.app.data.repository

import android.content.Context
import android.os.Environment
import com.trilink.app.data.model.DeliveryMode
import com.trilink.app.data.model.ItemType
import com.trilink.app.data.model.PairingPayload
import com.trilink.app.data.model.Transfer
import com.trilink.app.data.model.TransferStatus
import com.trilink.app.domain.FileUtils
import com.trilink.app.domain.PathSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LocalTransferServer(
    private val context: Context,
    private val port: Int = 42426,
    private val transferRepo: TransferRepository,
    private val deviceRepo: DeviceRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    private data class ActiveTransfer(
        val transferId: String,
        val senderDeviceId: String,
        val displayName: String,
        val itemType: ItemType,
        val sizeBytes: Long,
        val sha256: String,
        val mimeType: String?,
        val textContent: String?,
        val tempFile: File
    )

    private val activeTransfers = ConcurrentHashMap<String, ActiveTransfer>()

    fun start() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            try {
                val server = ServerSocket(port)
                server.reuseAddress = true
                serverSocket = server

                while (isRunning && !server.isClosed) {
                    try {
                        val client = server.accept()
                        scope.launch {
                            handleClient(client)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            } catch (e: Exception) {
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
        scope.cancel()
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 15000
            val input = s.getInputStream()
            val output = s.getOutputStream()

            // 1. Read HTTP Request Line and Headers
            val headerBytes = ByteArrayOutputStream()
            var prev1 = -1
            var prev2 = -1
            var prev3 = -1
            var b: Int

            while (input.read().also { b = it } != -1) {
                headerBytes.write(b)
                if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) {
                    break
                }
                prev3 = prev2
                prev2 = prev1
                prev1 = b
            }

            val headerStr = headerBytes.toString("UTF-8")
            if (headerStr.isBlank()) return

            val lines = headerStr.split("\r\n")
            val requestLine = lines.firstOrNull() ?: return
            val requestParts = requestLine.split(" ")
            if (requestParts.size < 2) return

            val method = requestParts[0].uppercase()
            val path = requestParts[1]

            val headers = mutableMapOf<String, String>()
            for (line in lines.drop(1)) {
                val colonIdx = line.indexOf(':')
                if (colonIdx != -1) {
                    val k = line.substring(0, colonIdx).trim().lowercase()
                    val v = line.substring(colonIdx + 1).trim()
                    headers[k] = v
                }
            }

            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0

            // 2. Route Handling
            when {
                // Health Check
                method == "GET" && path == "/api/v1/health" -> {
                    sendJsonResponse(output, 200, "{\"status\":\"ok\"}")
                }

                // Pairing Endpoint
                method == "POST" && path == "/api/v1/pair" -> {
                    val bodyData = readExactBytes(input, contentLength)
                    handlePair(output, bodyData, socket.inetAddress.hostAddress)
                }

                // Transfer Initiate Endpoint
                method == "POST" && path == "/api/v1/transfers/initiate" -> {
                    val bodyData = readExactBytes(input, contentLength)
                    handleTransferInitiate(output, bodyData)
                }

                // Payload Upload Endpoint
                method == "POST" && path.contains("/payload") -> {
                    val transferId = path.substringAfter("/api/v1/transfers/").substringBefore("/payload")
                    handlePayloadUpload(output, input, transferId, contentLength)
                }

                else -> {
                    sendResponse(output, 404, "Not Found")
                }
            }
        }
    }

    private suspend fun handlePair(output: OutputStream, body: ByteArray, remoteIp: String?) {
        try {
            val jsonStr = String(body, Charsets.UTF_8)
            val json = JSONObject(jsonStr)

            val pId = json.optString("pairing_id").ifEmpty { json.optString("pairingId", UUID.randomUUID().toString()) }
            val dId = json.optString("device_id").ifEmpty { json.optString("deviceId", UUID.randomUUID().toString()) }
            val dName = json.optString("device_name").ifEmpty { json.optString("deviceName", "Mac Companion") }
            val plat = json.optString("platform", "macos")
            val pubK = json.optString("public_key").ifEmpty { json.optString("publicKey", "") }
            val exK = json.optString("exchange_key").ifEmpty { json.optString("exchangeKey", "") }
            var ep = json.optString("local_endpoint").ifEmpty { json.optString("localEndpoint", "") }

            if (ep.isBlank() && !remoteIp.isNullOrBlank()) {
                ep = "http://$remoteIp:42425"
            }

            val payload = PairingPayload(
                version = json.optInt("version", 1),
                pairingId = pId,
                deviceId = dId,
                deviceName = dName,
                platform = plat,
                publicKey = pubK,
                exchangeKey = exK,
                localEndpoint = ep,
                expiresAt = json.optLong("expires_at", System.currentTimeMillis() + 600_000)
            )

            deviceRepo.pairDevice(payload)
            val resp = "{\"status\":\"approved\",\"confirmed_at\":${System.currentTimeMillis()}}"
            sendJsonResponse(output, 200, resp)
        } catch (e: Exception) {
            sendResponse(output, 400, "Invalid Pairing Payload: ${e.message}")
        }
    }

    private suspend fun handleTransferInitiate(output: OutputStream, body: ByteArray) {
        try {
            val jsonStr = String(body, Charsets.UTF_8)
            val json = JSONObject(jsonStr)

            val transferId = json.optString("transfer_id").ifEmpty { json.optString("transferId", UUID.randomUUID().toString()) }
            val senderId = json.optString("sender_device_id").ifEmpty { json.optString("senderDeviceId", "mac-sender") }
            val displayName = json.optString("display_name").ifEmpty { json.optString("displayName", "incoming_file") }
            val itemTypeStr = json.optString("item_type").ifEmpty { json.optString("itemType", "file") }.lowercase()
            val sizeBytes = if (json.has("size_bytes")) json.optLong("size_bytes") else json.optLong("sizeBytes", 0L)
            val sha256 = json.optString("sha256", "")
            val mimeType = json.optString("mime_type").ifEmpty { json.optString("mimeType", null) }
            val textContent = json.optString("text_content").ifEmpty { json.optString("textContent", null) }

            val itemType = when (itemTypeStr) {
                "text" -> ItemType.TEXT
                "url" -> ItemType.URL
                "image" -> ItemType.IMAGE
                else -> ItemType.FILE
            }

            // Direct Text / URL Note
            if ((itemType == ItemType.TEXT || itemType == ItemType.URL) && !textContent.isNullOrBlank()) {
                val transfer = Transfer(
                    id = transferId,
                    senderDeviceId = senderId,
                    recipientDeviceId = "android-local",
                    itemType = itemType,
                    displayName = if (itemType == ItemType.URL) "Shared Link" else "Shared Text",
                    sizeBytes = textContent.toByteArray(Charsets.UTF_8).size.toLong(),
                    sha256 = sha256,
                    deliveryMode = DeliveryMode.LOCAL,
                    status = TransferStatus.DELIVERED,
                    textContent = textContent
                )
                transferRepo.createTransfer(transfer)

                val resp = "{\"status\":\"delivered\",\"transfer_id\":\"$transferId\"}"
                sendJsonResponse(output, 200, resp)
                return
            }

            // Binary File / Image Transfer
            val tempFile = File(context.cacheDir, "trilink_incoming_$transferId.part")
            if (tempFile.exists()) tempFile.delete()
            tempFile.createNewFile()

            activeTransfers[transferId] = ActiveTransfer(
                transferId = transferId,
                senderDeviceId = senderId,
                displayName = displayName,
                itemType = itemType,
                sizeBytes = sizeBytes,
                sha256 = sha256,
                mimeType = mimeType,
                textContent = textContent,
                tempFile = tempFile
            )

            val resp = "{\"status\":\"accepted\",\"upload_token\":\"$transferId\"}"
            sendJsonResponse(output, 200, resp)
        } catch (e: Exception) {
            sendResponse(output, 400, "Malformed Transfer Initiate: ${e.message}")
        }
    }

    private suspend fun handlePayloadUpload(output: OutputStream, input: InputStream, transferId: String, totalBytes: Int) {
        val active = activeTransfers.remove(transferId)
        if (active == null) {
            sendResponse(output, 404, "Transfer Not Found")
            return
        }

        try {
            FileOutputStream(active.tempFile).use { fos ->
                val buffer = ByteArray(65536)
                var remaining = totalBytes
                while (remaining > 0) {
                    val toRead = remaining.coerceAtMost(buffer.size)
                    val read = input.read(buffer, 0, toRead)
                    if (read == -1) break
                    fos.write(buffer, 0, read)
                    remaining -= read
                }
            }

            // Verify SHA-256
            val digest = MessageDigest.getInstance("SHA-256")
            val fileBytes = active.tempFile.readBytes()
            val computedSha = digest.digest(fileBytes).joinToString("") { "%02x".format(it) }

            if (active.sha256.isNotBlank() && !computedSha.equals(active.sha256, ignoreCase = true)) {
                active.tempFile.delete()
                sendResponse(output, 422, "INTEGRITY_MISMATCH")
                return
            }

            // Destination directory
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads").apply { mkdirs() }

            var finalFilename = active.displayName
            val hasExt = finalFilename.contains(".") && finalFilename.substringAfterLast(".").length in 1..8
            if (!hasExt) {
                val detected = detectExtension(fileBytes, active.mimeType)
                if (detected.isNotBlank()) {
                    finalFilename = "$finalFilename.$detected"
                }
            }

            val sanitizedFilename = PathSanitizer.sanitizeFilename(finalFilename, fallback = "received_file")
            val destinationFile = PathSanitizer.resolveDestinationFile(downloadsDir, sanitizedFilename)

            active.tempFile.renameTo(destinationFile)

            // Record Transfer & Inbox Item
            val transfer = Transfer(
                id = transferId,
                senderDeviceId = active.senderDeviceId,
                recipientDeviceId = "android-local",
                itemType = active.itemType,
                displayName = destinationFile.name,
                mimeType = active.mimeType,
                sizeBytes = destinationFile.length(),
                sha256 = computedSha,
                deliveryMode = DeliveryMode.LOCAL,
                status = TransferStatus.DELIVERED,
                localPath = destinationFile.absolutePath
            )
            transferRepo.createTransfer(transfer)

            val inboxItem = InboxItem(
                transferId = transferId,
                senderDeviceId = active.senderDeviceId,
                senderDeviceName = "Mac",
                itemType = active.itemType,
                displayName = destinationFile.name,
                sizeBytes = destinationFile.length(),
                localPath = destinationFile.absolutePath,
                textContent = null
            )
            transferRepo.recordInboxItem(inboxItem)

            val resp = "{\"status\":\"delivered\",\"transfer_id\":\"$transferId\",\"verified_sha256\":\"$computedSha\"}"
            sendJsonResponse(output, 200, resp)
        } catch (e: Exception) {
            active.tempFile.delete()
            sendResponse(output, 500, "Payload Storage Failed: ${e.message}")
        }
    }

    private fun detectExtension(data: ByteArray, mimeType: String?): String {
        mimeType?.lowercase()?.let { m ->
            if (m.contains("jpeg") || m.contains("jpg")) return "jpg"
            if (m.contains("png")) return "png"
            if (m.contains("webp")) return "webp"
            if (m.contains("gif")) return "gif"
            if (m.contains("pdf")) return "pdf"
            if (m.contains("mp4")) return "mp4"
            if (m.contains("zip")) return "zip"
        }

        if (data.size >= 3 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() && data[2] == 0xFF.toByte()) return "jpg"
        if (data.size >= 4 && data[0] == 0x89.toByte() && data[1] == 0x50.toByte() && data[2] == 0x4E.toByte() && data[3] == 0x47.toByte()) return "png"
        if (data.size >= 3 && data[0] == 0x47.toByte() && data[1] == 0x49.toByte() && data[2] == 0x46.toByte()) return "gif"
        if (data.size >= 4 && data[0] == 0x25.toByte() && data[1] == 0x50.toByte() && data[2] == 0x44.toByte() && data[3] == 0x46.toByte()) return "pdf"
        if (data.size >= 4 && data[0] == 0x50.toByte() && data[1] == 0x4B.toByte() && data[2] == 0x03.toByte() && data[3] == 0x04.toByte()) return "zip"

        return ""
    }

    private fun readExactBytes(input: InputStream, length: Int): ByteArray {
        val out = ByteArrayOutputStream(length)
        val buf = ByteArray(4096)
        var remaining = length
        while (remaining > 0) {
            val toRead = remaining.coerceAtMost(buf.size)
            val read = input.read(buf, 0, toRead)
            if (read == -1) break
            out.write(buf, 0, read)
            remaining -= read
        }
        return out.toByteArray()
    }

    private fun sendJsonResponse(output: OutputStream, code: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val status = if (code == 200) "OK" else "Error"
        val header = "HTTP/1.1 $code $status\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendResponse(output: OutputStream, code: Int, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val status = if (code == 200) "OK" else if (code == 404) "Not Found" else "Error"
        val header = "HTTP/1.1 $code $status\r\nContent-Type: text/plain\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }
}
