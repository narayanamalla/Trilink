import XCTest
@testable import TriLinkCore

final class LocalTransferIntegrationTests: XCTestCase, @unchecked Sendable {

    func testEndToEndLocalFileAndTextTransfer() async throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("TriLinkServer_\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let testPort: UInt16 = 42431
        let server = LocalTransferServer(port: testPort, downloadsDirectory: tempDir)

        let senderSecurity = SecurityManager()
        let senderDeviceId = "device-sender-uuid"
        let receiverDeviceId = "device-receiver-uuid"

        let senderDevice = Device(
            id: senderDeviceId,
            deviceName: "Realme Phone",
            platform: "android",
            publicKey: senderSecurity.publicSigningKeyBase64,
            exchangeKey: senderSecurity.publicAgreementKeyBase64,
            isOnlineLocally: true
        )

        server.trustedDevicesProvider = {
            [senderDeviceId: senderDevice]
        }

        try server.start()
        defer { server.stop() }

        // Allow socket listener to bind
        try await Task.sleep(nanoseconds: 100_000_000)

        let client = LocalTransferClient(
            securityManager: senderSecurity,
            localDeviceId: senderDeviceId
        )
        let endpoint = "http://127.0.0.1:\(testPort)"

        // 1. Health check
        let isHealthy = await client.checkHealth(endpoint: endpoint)
        XCTAssertTrue(isHealthy, "Server health check must return 200 OK")

        // 2. Transfer file
        let filePayload = Data((0..<65536).map { UInt8($0 % 256) })
        let sourceFile = tempDir.appendingPathComponent("sample_image.png")
        try filePayload.write(to: sourceFile)

        let transferId = UUID().uuidString
        try await client.transferFile(
            endpoint: endpoint,
            transferId: transferId,
            targetDeviceId: receiverDeviceId,
            fileURL: sourceFile,
            displayName: "sample_image.png",
            itemType: .image
        )

        // Wait briefly for file write and verify
        try await Task.sleep(nanoseconds: 200_000_000)

        let destinationFile = tempDir.appendingPathComponent("sample_image.png")
        XCTAssertTrue(FileManager.default.fileExists(atPath: destinationFile.path), "Received file must exist in download folder")
        let receivedBytes = try Data(contentsOf: destinationFile)
        XCTAssertEqual(receivedBytes, filePayload, "Received file bytes must match sent file bytes exactly")

        // 3. Transfer text
        let textTransferId = UUID().uuidString
        try await client.sendText(
            endpoint: endpoint,
            transferId: textTransferId,
            targetDeviceId: receiverDeviceId,
            text: "https://github.com/google",
            isUrl: true
        )
    }
}
