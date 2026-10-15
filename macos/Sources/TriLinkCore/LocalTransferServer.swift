import Foundation
import Network

public protocol LocalTransferServerDelegate: AnyObject, Sendable {
    func serverDidReceivePairingRequest(_ payload: PairingPayload) async -> Bool
    func serverDidReceiveTransferInitiate(_ request: TransferInitiateRequest) async -> Bool
    func serverDidCompleteTransfer(transferId: String, fileURL: URL, itemType: ItemType, displayName: String, textContent: String?) async
    func serverDidReceiveCancellation(transferId: String) async
}

public final class LocalTransferServer: @unchecked Sendable {
    public let port: UInt16
    public let downloadsDirectory: URL
    public weak var delegate: LocalTransferServerDelegate?
    public var trustedDevicesProvider: (@Sendable () -> [String: Device])?

    private var listener: NWListener?
    private let queue = DispatchQueue(label: "com.trilink.server", attributes: .concurrent)
    private var activeTransfers: [String: (request: TransferInitiateRequest, tempFile: URL, handle: FileHandle, receivedBytes: Int64)] = [:]
    private let lock = NSLock()

    public init(port: UInt16 = 42425, downloadsDirectory: URL? = nil) {
        self.port = port
        if let dir = downloadsDirectory {
            self.downloadsDirectory = dir
        } else {
            let fm = FileManager.default
            let appSupport = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            let trilinkDir = appSupport.appendingPathComponent("TriLink/Downloads")
            try? fm.createDirectory(at: trilinkDir, withIntermediateDirectories: true)
            self.downloadsDirectory = trilinkDir
        }
    }

    public func start() throws {
        let params = NWParameters.tcp
        params.allowLocalEndpointReuse = true
        let listener = try NWListener(using: params, on: NWEndpoint.Port(rawValue: port)!)
        self.listener = listener

        listener.stateUpdateHandler = { state in
            switch state {
            case .ready:
                break
            case .failed(let err):
                print("[LocalTransferServer] Listener failed: \(err)")
            default:
                break
            }
        }

        listener.newConnectionHandler = { [weak self] connection in
            self?.handleConnection(connection)
        }

        listener.start(queue: queue)
    }

    public func stop() {
        listener?.cancel()
        listener = nil
    }

    private func handleConnection(_ connection: NWConnection) {
        connection.start(queue: queue)
        readRequest(connection: connection, accumulatedData: Data())
    }

    private func readRequest(connection: NWConnection, accumulatedData: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] content, _, isComplete, error in
            guard let self = self else { return }
            if let error = error {
                connection.cancel()
                return
            }

            var current = accumulatedData
            if let data = content {
                current.append(data)
            }

            // Check if HTTP header is complete (\r\n\r\n)
            if let headerEndRange = current.range(of: Data("\r\n\r\n".utf8)) {
                let headerData = current.subdata(in: 0..<headerEndRange.lowerBound)
                let bodyData = current.subdata(in: headerEndRange.upperBound..<current.count)

                guard let headerString = String(data: headerData, encoding: .utf8) else {
                    self.sendResponse(connection: connection, statusCode: 400, body: "Bad Request")
                    return
                }

                let lines = headerString.components(separatedBy: "\r\n")
                let requestLine = lines.first ?? ""
                let parts = requestLine.split(separator: " ")
                let path = parts.count >= 2 ? String(parts[1]) : ""

                var contentLength: Int? = nil
                for line in lines.dropFirst() {
                    if let colon = line.firstIndex(of: ":") {
                        let key = line[..<colon].trimmingCharacters(in: .whitespaces).lowercased()
                        let value = line[line.index(after: colon)...].trimmingCharacters(in: .whitespaces)
                        if key == "content-length" {
                            contentLength = Int(value)
                        }
                    }
                }

                if !path.contains("/payload"), let expectedLength = contentLength, bodyData.count < expectedLength {
                    if !isComplete {
                        self.readRequest(connection: connection, accumulatedData: current)
                        return
                    }
                }

                self.processHttpRequest(
                    connection: connection,
                    headerString: headerString,
                    initialBody: bodyData,
                    isComplete: isComplete
                )
            } else if !isComplete {
                self.readRequest(connection: connection, accumulatedData: current)
            } else {
                connection.cancel()
            }
        }
    }

    private func processHttpRequest(
        connection: NWConnection,
        headerString: String,
        initialBody: Data,
        isComplete: Bool
    ) {
        let lines = headerString.components(separatedBy: "\r\n")
        guard let requestLine = lines.first else {
            sendResponse(connection: connection, statusCode: 400, body: "Bad Request")
            return
        }

        let parts = requestLine.split(separator: " ")
        guard parts.count >= 2 else {
            sendResponse(connection: connection, statusCode: 400, body: "Bad Request Line")
            return
        }

        let method = String(parts[0])
        let path = String(parts[1])

        var headers: [String: String] = [:]
        for line in lines.dropFirst() {
            if let colon = line.firstIndex(of: ":") {
                let key = line[..<colon].trimmingCharacters(in: .whitespaces).lowercased()
                let value = line[line.index(after: colon)...].trimmingCharacters(in: .whitespaces)
                headers[key] = value
            }
        }

        // Health check endpoint
        if method == "GET" && path == "/api/v1/health" {
            sendResponse(connection: connection, statusCode: 200, contentType: "application/json", body: "{\"status\":\"ok\"}")
            return
        }

        // Pair endpoint
        if method == "POST" && path == "/api/v1/pair" {
            guard let payload = try? JSONDecoder().decode(PairingPayload.self, from: initialBody) else {
                sendResponse(connection: connection, statusCode: 400, body: "Invalid Pairing Payload")
                return
            }

            Task {
                let approved = await self.delegate?.serverDidReceivePairingRequest(payload) ?? true
                if approved {
                    let resp = "{\"status\":\"approved\",\"confirmed_at\":\(Int64(Date().timeIntervalSince1970))}"
                    self.sendResponse(connection: connection, statusCode: 200, contentType: "application/json", body: resp)
                } else {
                    self.sendResponse(connection: connection, statusCode: 403, body: "Pairing Rejected")
                }
            }
            return
        }

        // Authenticate sender for protected transfer endpoints
        guard let senderId = headers["x-trilink-sender-id"],
              let timestampStr = headers["x-trilink-timestamp"],
              let timestamp = Int64(timestampStr),
              let nonce = headers["x-trilink-nonce"],
              let signature = headers["x-trilink-signature"] else {
            sendResponse(connection: connection, statusCode: 401, body: "Missing Authentication Headers")
            return
        }

        let trustedDevices = trustedDevicesProvider?() ?? [:]
        guard let peerDevice = trustedDevices[senderId], !peerDevice.isRevoked else {
            sendResponse(connection: connection, statusCode: 403, body: "Untrusted or Revoked Peer")
            return
        }

        // Verify signature
        let bodySha: String
        if path.contains("/payload") {
            let comps = path.split(separator: "/")
            if comps.count >= 4 {
                let transferId = String(comps[3])
                self.lock.lock()
                let reqSha = self.activeTransfers[transferId]?.request.sha256
                self.lock.unlock()
                bodySha = reqSha ?? SecurityManager.sha256Hex(data: initialBody)
            } else {
                bodySha = SecurityManager.sha256Hex(data: initialBody)
            }
        } else {
            bodySha = SecurityManager.sha256Hex(data: initialBody)
        }

        let isValid = SecurityManager.verifySignature(
            publicKeyBase64: peerDevice.publicKey,
            signatureBase64: signature,
            method: method,
            path: path,
            timestamp: timestamp,
            nonce: nonce,
            bodySha256Hex: bodySha
        )

        guard isValid else {
            sendResponse(connection: connection, statusCode: 401, body: "Invalid Signature")
            return
        }

        if method == "POST" && path == "/api/v1/transfers/initiate" {
            guard let req = try? JSONDecoder().decode(TransferInitiateRequest.self, from: initialBody) else {
                sendResponse(connection: connection, statusCode: 400, body: "Malformed TransferInitiateRequest")
                return
            }

            Task {
                let accepted = await self.delegate?.serverDidReceiveTransferInitiate(req) ?? true
                if accepted {
                    let tempFile = self.downloadsDirectory.appendingPathComponent("\(req.transferId).part")
                    FileManager.default.createFile(atPath: tempFile.path, contents: nil)
                    if let handle = try? FileHandle(forWritingTo: tempFile) {
                        self.lock.lock()
                        self.activeTransfers[req.transferId] = (req, tempFile, handle, 0)
                        self.lock.unlock()

                        let resp = "{\"status\":\"accepted\",\"upload_token\":\"\(req.transferId)\"}"
                        self.sendResponse(connection: connection, statusCode: 200, contentType: "application/json", body: resp)
                    } else {
                        self.sendResponse(connection: connection, statusCode: 500, body: "Failed to create temporary file")
                    }
                } else {
                    self.sendResponse(connection: connection, statusCode: 403, body: "Transfer Rejected")
                }
            }
            return
        }

        // Payload Streaming: /api/v1/transfers/{id}/payload
        if method == "POST" && path.contains("/payload") {
            let comps = path.split(separator: "/")
            if comps.count >= 4 {
                let transferId = String(comps[3])
                self.lock.lock()
                guard let active = self.activeTransfers[transferId] else {
                    self.lock.unlock()
                    sendResponse(connection: connection, statusCode: 404, body: "Transfer Not Found")
                    return
                }
                self.lock.unlock()

                // Append initial body chunk
                try? active.handle.write(contentsOf: initialBody)
                let currentBytes = active.receivedBytes + Int64(initialBody.count)
                self.lock.lock()
                self.activeTransfers[transferId] = (active.request, active.tempFile, active.handle, currentBytes)
                self.lock.unlock()

                if currentBytes >= active.request.sizeBytes {
                    self.finalizeTransfer(connection: connection, transferId: transferId)
                } else {
                    self.readStreamBody(
                        connection: connection,
                        transferId: transferId,
                        expectedBytes: active.request.sizeBytes
                    )
                }
                return
            }
        }

        sendResponse(connection: connection, statusCode: 404, body: "Not Found")
    }

    private func readStreamBody(connection: NWConnection, transferId: String, expectedBytes: Int64) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] content, _, isComplete, error in
            guard let self = self else { return }
            if let error = error {
                connection.cancel()
                return
            }

            self.lock.lock()
            guard let active = self.activeTransfers[transferId] else {
                self.lock.unlock()
                connection.cancel()
                return
            }
            self.lock.unlock()

            if let chunk = content {
                try? active.handle.write(contentsOf: chunk)
                let newTotal = active.receivedBytes + Int64(chunk.count)
                self.lock.lock()
                self.activeTransfers[transferId] = (active.request, active.tempFile, active.handle, newTotal)
                self.lock.unlock()
            }

            if isComplete || (self.activeTransfers[transferId]?.receivedBytes ?? 0) >= expectedBytes {
                self.finalizeTransfer(connection: connection, transferId: transferId)
            } else {
                self.readStreamBody(connection: connection, transferId: transferId, expectedBytes: expectedBytes)
            }
        }
    }

    private func finalizeTransfer(connection: NWConnection, transferId: String) {
        self.lock.lock()
        guard let active = self.activeTransfers[transferId] else {
            self.lock.unlock()
            connection.cancel()
            return
        }
        self.lock.unlock()

        try? active.handle.close()

        // Verify SHA-256 integrity
        if let fileData = try? Data(contentsOf: active.tempFile) {
            let computedSha = SecurityManager.sha256Hex(data: fileData)
            if computedSha.lowercased() == active.request.sha256.lowercased() {
                let finalDestination = PathSanitizer.resolveDestinationFile(
                    parentDir: self.downloadsDirectory,
                    filename: active.request.displayName
                )
                try? FileManager.default.moveItem(at: active.tempFile, to: finalDestination)

                self.lock.lock()
                self.activeTransfers.removeValue(forKey: transferId)
                self.lock.unlock()

                Task {
                    await self.delegate?.serverDidCompleteTransfer(
                        transferId: transferId,
                        fileURL: finalDestination,
                        itemType: active.request.itemType,
                        displayName: active.request.displayName,
                        textContent: active.request.textContent
                    )
                }

                let resp = "{\"status\":\"delivered\",\"transfer_id\":\"\(transferId)\",\"verified_sha256\":\"\(computedSha)\"}"
                self.sendResponse(connection: connection, statusCode: 200, contentType: "application/json", body: resp)
            } else {
                try? FileManager.default.removeItem(at: active.tempFile)
                self.sendResponse(connection: connection, statusCode: 422, body: "INTEGRITY_MISMATCH")
            }
        } else {
            self.sendResponse(connection: connection, statusCode: 500, body: "Failed to read transferred file")
        }
    }

    private func sendResponse(
        connection: NWConnection,
        statusCode: Int,
        contentType: String = "text/plain",
        body: String
    ) {
        let statusText = statusCode == 200 ? "OK" : (statusCode == 401 ? "Unauthorized" : (statusCode == 403 ? "Forbidden" : (statusCode == 404 ? "Not Found" : "Error")))
        let bodyData = Data(body.utf8)
        let response = "HTTP/1.1 \(statusCode) \(statusText)\r\nContent-Type: \(contentType)\r\nContent-Length: \(bodyData.count)\r\nConnection: close\r\n\r\n\(body)"
        connection.send(content: Data(response.utf8), completion: .contentProcessed({ _ in
            connection.cancel()
        }))
    }
}
