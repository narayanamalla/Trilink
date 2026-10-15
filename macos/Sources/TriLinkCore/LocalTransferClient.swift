import Foundation

public final class LocalTransferClient: @unchecked Sendable {
    public let securityManager: SecurityManager
    public let localDeviceId: String

    public init(securityManager: SecurityManager, localDeviceId: String) {
        self.securityManager = securityManager
        self.localDeviceId = localDeviceId
    }

    public func checkHealth(endpoint: String) async -> Bool {
        guard let url = URL(string: "\(endpoint)/api/v1/health") else { return false }
        do {
            var request = URLRequest(url: url)
            request.timeoutInterval = 3.0
            let (_, response) = try await URLSession.shared.data(for: request)
            return (response as? HTTPURLResponse)?.statusCode == 200
        } catch {
            return false
        }
    }

    public func sendPairingRequest(endpoint: String, payload: PairingPayload) async throws -> Bool {
        guard let url = URL(string: "\(endpoint)/api/v1/pair") else {
            throw URLError(.badURL)
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(payload)
        request.timeoutInterval = 10.0

        let (_, response) = try await URLSession.shared.data(for: request)
        return (response as? HTTPURLResponse)?.statusCode == 200
    }

    public func transferFile(
        endpoint: String,
        transferId: String,
        targetDeviceId: String,
        fileURL: URL,
        displayName: String,
        itemType: ItemType = .file,
        onProgress: (@Sendable (Int64, Int64) -> Void)? = nil
    ) async throws {
        let fileData = try Data(contentsOf: fileURL)
        let totalSize = Int64(fileData.count)
        let sha256 = SecurityManager.sha256Hex(data: fileData)

        // 1. Initiate transfer
        guard let initiateURL = URL(string: "\(endpoint)/api/v1/transfers/initiate") else {
            throw URLError(.badURL)
        }

        let initiateReq = TransferInitiateRequest(
            version: 1,
            transferId: transferId,
            senderDeviceId: localDeviceId,
            recipientDeviceId: targetDeviceId,
            itemType: itemType,
            displayName: displayName,
            sizeBytes: totalSize,
            sha256: sha256,
            deliveryMode: .local
        )
        let initiateBody = try JSONEncoder().encode(initiateReq)
        let timestamp = Int64(Date().timeIntervalSince1970)
        let nonce = UUID().uuidString
        let signature = try securityManager.sign(
            method: "POST",
            path: "/api/v1/transfers/initiate",
            timestamp: timestamp,
            nonce: nonce,
            bodySha256Hex: SecurityManager.sha256Hex(data: initiateBody)
        )

        var request = URLRequest(url: initiateURL)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(localDeviceId, forHTTPHeaderField: "X-TriLink-Sender-ID")
        request.setValue("\(timestamp)", forHTTPHeaderField: "X-TriLink-Timestamp")
        request.setValue(nonce, forHTTPHeaderField: "X-TriLink-Nonce")
        request.setValue(signature, forHTTPHeaderField: "X-TriLink-Signature")
        request.httpBody = initiateBody

        let (_, initResponse) = try await URLSession.shared.data(for: request)
        guard let httpInit = initResponse as? HTTPURLResponse, httpInit.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }

        // 2. Stream Payload
        guard let payloadURL = URL(string: "\(endpoint)/api/v1/transfers/\(transferId)/payload") else {
            throw URLError(.badURL)
        }

        let payloadSig = try securityManager.sign(
            method: "POST",
            path: "/api/v1/transfers/\(transferId)/payload",
            timestamp: timestamp,
            nonce: nonce,
            bodySha256Hex: sha256
        )

        var payloadRequest = URLRequest(url: payloadURL)
        payloadRequest.httpMethod = "POST"
        payloadRequest.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")
        payloadRequest.setValue("\(totalSize)", forHTTPHeaderField: "Content-Length")
        payloadRequest.setValue(localDeviceId, forHTTPHeaderField: "X-TriLink-Sender-ID")
        payloadRequest.setValue("\(timestamp)", forHTTPHeaderField: "X-TriLink-Timestamp")
        payloadRequest.setValue(nonce, forHTTPHeaderField: "X-TriLink-Nonce")
        payloadRequest.setValue(payloadSig, forHTTPHeaderField: "X-TriLink-Signature")
        payloadRequest.httpBody = fileData

        let (respData, streamResponse) = try await URLSession.shared.data(for: payloadRequest)
        guard let httpStream = streamResponse as? HTTPURLResponse, httpStream.statusCode == 200 else {
            let errorText = String(data: respData, encoding: .utf8) ?? "Unknown transfer error"
            throw URLError(.cannotCreateFile, userInfo: [NSLocalizedDescriptionKey: errorText])
        }

        onProgress?(totalSize, totalSize)
    }

    public func sendText(
        endpoint: String,
        transferId: String,
        targetDeviceId: String,
        text: String,
        isUrl: Bool
    ) async throws {
        let textData = Data(text.utf8)
        let totalSize = Int64(textData.count)
        let sha256 = SecurityManager.sha256Hex(data: textData)

        guard let initiateURL = URL(string: "\(endpoint)/api/v1/transfers/initiate") else {
            throw URLError(.badURL)
        }

        let initiateReq = TransferInitiateRequest(
            version: 1,
            transferId: transferId,
            senderDeviceId: localDeviceId,
            recipientDeviceId: targetDeviceId,
            itemType: isUrl ? .url : .text,
            displayName: isUrl ? "Shared URL" : "Shared Text",
            sizeBytes: totalSize,
            sha256: sha256,
            deliveryMode: .local,
            textContent: text
        )
        let initiateBody = try JSONEncoder().encode(initiateReq)
        let timestamp = Int64(Date().timeIntervalSince1970)
        let nonce = UUID().uuidString
        let signature = try securityManager.sign(
            method: "POST",
            path: "/api/v1/transfers/initiate",
            timestamp: timestamp,
            nonce: nonce,
            bodySha256Hex: SecurityManager.sha256Hex(data: initiateBody)
        )

        var request = URLRequest(url: initiateURL)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(localDeviceId, forHTTPHeaderField: "X-TriLink-Sender-ID")
        request.setValue("\(timestamp)", forHTTPHeaderField: "X-TriLink-Timestamp")
        request.setValue(nonce, forHTTPHeaderField: "X-TriLink-Nonce")
        request.setValue(signature, forHTTPHeaderField: "X-TriLink-Signature")
        request.httpBody = initiateBody

        let (_, initResponse) = try await URLSession.shared.data(for: request)
        guard let httpInit = initResponse as? HTTPURLResponse, httpInit.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }

        // Stream payload bytes
        guard let payloadURL = URL(string: "\(endpoint)/api/v1/transfers/\(transferId)/payload") else {
            throw URLError(.badURL)
        }

        let payloadSig = try securityManager.sign(
            method: "POST",
            path: "/api/v1/transfers/\(transferId)/payload",
            timestamp: timestamp,
            nonce: nonce,
            bodySha256Hex: sha256
        )

        var payloadRequest = URLRequest(url: payloadURL)
        payloadRequest.httpMethod = "POST"
        payloadRequest.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")
        payloadRequest.setValue("\(totalSize)", forHTTPHeaderField: "Content-Length")
        payloadRequest.setValue(localDeviceId, forHTTPHeaderField: "X-TriLink-Sender-ID")
        payloadRequest.setValue("\(timestamp)", forHTTPHeaderField: "X-TriLink-Timestamp")
        payloadRequest.setValue(nonce, forHTTPHeaderField: "X-TriLink-Nonce")
        payloadRequest.setValue(payloadSig, forHTTPHeaderField: "X-TriLink-Signature")
        payloadRequest.httpBody = textData

        let (_, streamResponse) = try await URLSession.shared.data(for: payloadRequest)
        guard let httpStream = streamResponse as? HTTPURLResponse, httpStream.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }
    }
}
