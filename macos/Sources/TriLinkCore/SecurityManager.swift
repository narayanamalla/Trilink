import Foundation
import CryptoKit

public enum SecurityError: Error, CustomStringConvertible {
    case invalidSignature
    case keyDerivationFailed
    case encryptionFailed
    case decryptionFailed
    case timestampOutOfWindow
    case untrustedPeer

    public var description: String {
        switch self {
        case .invalidSignature: return "Invalid cryptographic signature"
        case .keyDerivationFailed: return "Failed to derive shared encryption key"
        case .encryptionFailed: return "Encryption operation failed"
        case .decryptionFailed: return "Decryption operation failed or authentication tag mismatch"
        case .timestampOutOfWindow: return "Request timestamp drift exceeds allowed window (±120 seconds)"
        case .untrustedPeer: return "Peer device is untrusted or revoked"
        }
    }
}

public final class SecurityManager: Sendable {
    public let signingPrivateKey: Curve25519.Signing.PrivateKey
    public let agreementPrivateKey: Curve25519.KeyAgreement.PrivateKey

    public var publicSigningKeyBase64: String {
        signingPrivateKey.publicKey.rawRepresentation.base64EncodedString()
    }

    public var publicAgreementKeyBase64: String {
        agreementPrivateKey.publicKey.rawRepresentation.base64EncodedString()
    }

    public init(
        signingKey: Curve25519.Signing.PrivateKey? = nil,
        agreementKey: Curve25519.KeyAgreement.PrivateKey? = nil
    ) {
        self.signingPrivateKey = signingKey ?? Curve25519.Signing.PrivateKey()
        self.agreementPrivateKey = agreementKey ?? Curve25519.KeyAgreement.PrivateKey()
    }

    // Canonical request string: METHOD\nPATH\nTIMESTAMP\nNONCE\nSHA256_HEX
    public static func canonicalString(
        method: String,
        path: String,
        timestamp: Int64,
        nonce: String,
        bodySha256Hex: String
    ) -> String {
        "\(method.uppercased())\n\(path)\n\(timestamp)\n\(nonce)\n\(bodySha256Hex.lowercased())"
    }

    public func sign(
        method: String,
        path: String,
        timestamp: Int64,
        nonce: String,
        bodySha256Hex: String
    ) throws -> String {
        let canonical = Self.canonicalString(
            method: method,
            path: path,
            timestamp: timestamp,
            nonce: nonce,
            bodySha256Hex: bodySha256Hex
        )
        let signature = try signingPrivateKey.signature(for: Data(canonical.utf8))
        return signature.base64EncodedString()
    }

    public static func verifySignature(
        publicKeyBase64: String,
        signatureBase64: String,
        method: String,
        path: String,
        timestamp: Int64,
        nonce: String,
        bodySha256Hex: String,
        maxClockDriftSeconds: Int64 = 120
    ) -> Bool {
        let now = Int64(Date().timeIntervalSince1970)
        let delta = abs(now - (timestamp > 1_000_000_000_000 ? timestamp / 1000 : timestamp))
        if delta > maxClockDriftSeconds {
            return false
        }

        guard let pubKeyData = Data(base64Encoded: publicKeyBase64),
              let sigData = Data(base64Encoded: signatureBase64),
              let pubKey = try? Curve25519.Signing.PublicKey(rawRepresentation: pubKeyData) else {
            return false
        }

        let canonical = Self.canonicalString(
            method: method,
            path: path,
            timestamp: timestamp,
            nonce: nonce,
            bodySha256Hex: bodySha256Hex
        )
        return pubKey.isValidSignature(sigData, for: Data(canonical.utf8))
    }

    public static func sha256Hex(data: Data) -> String {
        let digest = SHA256.hash(data: data)
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    public func encryptAESGCM(plaintext: Data, recipientExchangeKeyBase64: String) throws -> (ciphertext: Data, ephemeralPublicKeyBase64: String) {
        guard let recipientKeyData = Data(base64Encoded: recipientExchangeKeyBase64),
              let recipientPubKey = try? Curve25519.KeyAgreement.PublicKey(rawRepresentation: recipientKeyData) else {
            throw SecurityError.keyDerivationFailed
        }

        let ephemeralPriv = Curve25519.KeyAgreement.PrivateKey()
        let sharedSecret = try ephemeralPriv.sharedSecretFromKeyAgreement(with: recipientPubKey)
        let symmetricKey = sharedSecret.hkdfDerivedSymmetricKey(
            using: SHA256.self,
            salt: Data("TriLink-E2E-v1".utf8),
            sharedInfo: Data(),
            outputByteCount: 32
        )

        let sealed = try AES.GCM.seal(plaintext, using: symmetricKey)
        guard let combined = sealed.combined else {
            throw SecurityError.encryptionFailed
        }

        return (
            ciphertext: combined,
            ephemeralPublicKeyBase64: ephemeralPriv.publicKey.rawRepresentation.base64EncodedString()
        )
    }

    public func decryptAESGCM(combinedCiphertext: Data, ephemeralPublicKeyBase64: String) throws -> Data {
        guard let senderEphemeralKeyData = Data(base64Encoded: ephemeralPublicKeyBase64),
              let senderEphemeralPubKey = try? Curve25519.KeyAgreement.PublicKey(rawRepresentation: senderEphemeralKeyData) else {
            throw SecurityError.keyDerivationFailed
        }

        let sharedSecret = try agreementPrivateKey.sharedSecretFromKeyAgreement(with: senderEphemeralPubKey)
        let symmetricKey = sharedSecret.hkdfDerivedSymmetricKey(
            using: SHA256.self,
            salt: Data("TriLink-E2E-v1".utf8),
            sharedInfo: Data(),
            outputByteCount: 32
        )

        let box = try AES.GCM.SealedBox(combined: combinedCiphertext)
        return try AES.GCM.open(box, using: symmetricKey)
    }
}
