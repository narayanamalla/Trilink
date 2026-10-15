import Foundation

public enum ItemType: String, Codable, Sendable {
    case text
    case url
    case image
    case file
}

public enum DeliveryMode: String, Codable, Sendable {
    case local
    case remote
}

public enum TransferStatus: String, Codable, Sendable {
    case created
    case queued
    case uploading
    case available
    case downloading
    case delivered
    case failed
    case cancelled
    case expired
}

public struct Device: Identifiable, Codable, Sendable {
    public let id: String
    public var ownerId: String?
    public var deviceName: String
    public var platform: String // "android" or "macos"
    public var publicKey: String // Base64 Ed25519 pubkey
    public var exchangeKey: String // Base64 X25519 pubkey
    public var localEndpoint: String?
    public var isOnlineLocally: Bool = false
    public var lastSeenAt: Int64
    public var isRevoked: Bool

    public init(
        id: String = UUID().uuidString,
        ownerId: String? = nil,
        deviceName: String,
        platform: String,
        publicKey: String,
        exchangeKey: String,
        localEndpoint: String? = nil,
        isOnlineLocally: Bool = false,
        lastSeenAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        isRevoked: Bool = false
    ) {
        self.id = id
        self.ownerId = ownerId
        self.deviceName = deviceName
        self.platform = platform
        self.publicKey = publicKey
        self.exchangeKey = exchangeKey
        self.localEndpoint = localEndpoint
        self.isOnlineLocally = isOnlineLocally
        self.lastSeenAt = lastSeenAt
        self.isRevoked = isRevoked
    }
}

public struct Transfer: Identifiable, Codable, Sendable {
    public let id: String
    public let senderDeviceId: String
    public let recipientDeviceId: String
    public let itemType: ItemType
    public let displayName: String
    public var mimeType: String?
    public let sizeBytes: Int64
    public let sha256: String
    public let deliveryMode: DeliveryMode
    public var status: TransferStatus
    public var bytesTransferred: Int64
    public var localPath: String?
    public var textContent: String?
    public var failureCode: String?
    public var failureReason: String?
    public let createdAt: Int64
    public var updatedAt: Int64

    public init(
        id: String = UUID().uuidString,
        senderDeviceId: String,
        recipientDeviceId: String,
        itemType: ItemType,
        displayName: String,
        mimeType: String? = nil,
        sizeBytes: Int64,
        sha256: String,
        deliveryMode: DeliveryMode,
        status: TransferStatus = .created,
        bytesTransferred: Int64 = 0,
        localPath: String? = nil,
        textContent: String? = nil,
        failureCode: String? = nil,
        failureReason: String? = nil,
        createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        updatedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.id = id
        self.senderDeviceId = senderDeviceId
        self.recipientDeviceId = recipientDeviceId
        self.itemType = itemType
        self.displayName = displayName
        self.mimeType = mimeType
        self.sizeBytes = sizeBytes
        self.sha256 = sha256
        self.deliveryMode = deliveryMode
        self.status = status
        self.bytesTransferred = bytesTransferred
        self.localPath = localPath
        self.textContent = textContent
        self.failureCode = failureCode
        self.failureReason = failureReason
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
}

public struct PairingPayload: Codable, Sendable {
    public let version: Int
    public let pairingId: String
    public let deviceId: String
    public let deviceName: String
    public let platform: String
    public let publicKey: String
    public let exchangeKey: String
    public let localEndpoint: String?
    public let expiresAt: Int64

    public init(
        version: Int = 1,
        pairingId: String = UUID().uuidString,
        deviceId: String,
        deviceName: String,
        platform: String,
        publicKey: String,
        exchangeKey: String,
        localEndpoint: String? = nil,
        expiresAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000) + 180_000
    ) {
        self.version = version
        self.pairingId = pairingId
        self.deviceId = deviceId
        self.deviceName = deviceName
        self.platform = platform
        self.publicKey = publicKey
        self.exchangeKey = exchangeKey
        self.localEndpoint = localEndpoint
        self.expiresAt = expiresAt
    }
}

public struct TransferInitiateRequest: Codable, Sendable {
    public let version: Int
    public let transferId: String
    public let senderDeviceId: String
    public let recipientDeviceId: String
    public let itemType: ItemType
    public let displayName: String
    public let mimeType: String?
    public let sizeBytes: Int64
    public let sha256: String
    public let deliveryMode: DeliveryMode
    public let textContent: String?
    public let createdAt: Int64

    public init(
        version: Int = 1,
        transferId: String,
        senderDeviceId: String,
        recipientDeviceId: String,
        itemType: ItemType,
        displayName: String,
        mimeType: String? = nil,
        sizeBytes: Int64,
        sha256: String,
        deliveryMode: DeliveryMode,
        textContent: String? = nil,
        createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.version = version
        self.transferId = transferId
        self.senderDeviceId = senderDeviceId
        self.recipientDeviceId = recipientDeviceId
        self.itemType = itemType
        self.displayName = displayName
        self.mimeType = mimeType
        self.sizeBytes = sizeBytes
        self.sha256 = sha256
        self.deliveryMode = deliveryMode
        self.textContent = textContent
        self.createdAt = createdAt
    }
}

public struct TransferStatusResponse: Codable, Sendable {
    public let version: Int
    public let transferId: String
    public let status: TransferStatus
    public let bytesTransferred: Int64
    public let totalBytes: Int64
    public let failureCode: String?
    public let failureReason: String?
    public let updatedAt: Int64

    public init(
        version: Int = 1,
        transferId: String,
        status: TransferStatus,
        bytesTransferred: Int64,
        totalBytes: Int64,
        failureCode: String? = nil,
        failureReason: String? = nil,
        updatedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.version = version
        self.transferId = transferId
        self.status = status
        self.bytesTransferred = bytesTransferred
        self.totalBytes = totalBytes
        self.failureCode = failureCode
        self.failureReason = failureReason
        self.updatedAt = updatedAt
    }
}
