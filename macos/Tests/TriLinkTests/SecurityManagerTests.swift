import XCTest
@testable import TriLinkCore

final class SecurityManagerTests: XCTestCase {

    func testSignatureVerificationSuccess() throws {
        let senderSecurity = SecurityManager()
        let timestamp = Int64(Date().timeIntervalSince1970)
        let nonce = UUID().uuidString
        let method = "POST"
        let path = "/api/v1/transfers/initiate"
        let bodySha = SecurityManager.sha256Hex(data: Data("test-body".utf8))

        let signature = try senderSecurity.sign(
            method: method,
            path: path,
            timestamp: timestamp,
            nonce: nonce,
            bodySha256Hex: bodySha
        )

        let isValid = SecurityManager.verifySignature(
            publicKeyBase64: senderSecurity.publicSigningKeyBase64,
            signatureBase64: signature,
            method: method,
            path: path,
            timestamp: timestamp,
            nonce: nonce,
            bodySha256Hex: bodySha
        )

        XCTAssertTrue(isValid)
    }

    func testTamperedSignatureFails() throws {
        let senderSecurity = SecurityManager()
        let timestamp = Int64(Date().timeIntervalSince1970)
        let nonce = UUID().uuidString

        let signature = try senderSecurity.sign(
            method: "POST",
            path: "/api/v1/transfers/initiate",
            timestamp: timestamp,
            nonce: nonce,
            bodySha256Hex: SecurityManager.sha256Hex(data: Data("original".utf8))
        )

        // Verify with tampered body hash
        let isValid = SecurityManager.verifySignature(
            publicKeyBase64: senderSecurity.publicSigningKeyBase64,
            signatureBase64: signature,
            method: "POST",
            path: "/api/v1/transfers/initiate",
            timestamp: timestamp,
            nonce: nonce,
            bodySha256Hex: SecurityManager.sha256Hex(data: Data("tampered".utf8))
        )

        XCTAssertFalse(isValid)
    }

    func testClockDriftRejection() throws {
        let senderSecurity = SecurityManager()
        let expiredTimestamp = Int64(Date().timeIntervalSince1970) - 300 // 5 minutes old
        let nonce = UUID().uuidString

        let signature = try senderSecurity.sign(
            method: "GET",
            path: "/api/v1/health",
            timestamp: expiredTimestamp,
            nonce: nonce,
            bodySha256Hex: SecurityManager.sha256Hex(data: Data())
        )

        let isValid = SecurityManager.verifySignature(
            publicKeyBase64: senderSecurity.publicSigningKeyBase64,
            signatureBase64: signature,
            method: "GET",
            path: "/api/v1/health",
            timestamp: expiredTimestamp,
            nonce: nonce,
            bodySha256Hex: SecurityManager.sha256Hex(data: Data()),
            maxClockDriftSeconds: 120
        )

        XCTAssertFalse(isValid, "Timestamp older than 120s must be rejected")
    }

    func testAESGCMEncryptionAndDecryptionRoundtrip() throws {
        let alice = SecurityManager()
        let bob = SecurityManager()

        let plaintext = Data("Confidential user document bytes for TriLink".utf8)

        // Alice encrypts for Bob
        let encrypted = try alice.encryptAESGCM(
            plaintext: plaintext,
            recipientExchangeKeyBase64: bob.publicAgreementKeyBase64
        )

        XCTAssertNotEqual(encrypted.ciphertext, plaintext)

        // Bob decrypts Alice's ciphertext
        let decrypted = try bob.decryptAESGCM(
            combinedCiphertext: encrypted.ciphertext,
            ephemeralPublicKeyBase64: encrypted.ephemeralPublicKeyBase64
        )

        XCTAssertEqual(decrypted, plaintext)
    }
}
