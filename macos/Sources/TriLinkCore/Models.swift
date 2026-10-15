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

    enum CodingKeys: String, CodingKey {
        case version
        case pairingId, pairing_id
        case deviceId, device_id
        case deviceName, device_name
        case platform
        case publicKey, public_key
        case exchangeKey, exchange_key
        case localEndpoint, local_endpoint
        case expiresAt, expires_at
    }

    public init(
        version: Int = 1,
        pairingId: String = UUID().uuidString,
        deviceId: String,
        deviceName: String,
        platform: String,
        publicKey: String,
        exchangeKey: String,
        localEndpoint: String? = nil,
        expiresAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000) + 600_000
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

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.version = try container.decodeIfPresent(Int.self, forKey: .version) ?? 1
        self.pairingId = (try? container.decodeIfPresent(String.self, forKey: .pairingId))
            ?? (try? container.decodeIfPresent(String.self, forKey: .pairing_id))
            ?? UUID().uuidString
        self.deviceId = (try? container.decodeIfPresent(String.self, forKey: .deviceId))
            ?? (try? container.decodeIfPresent(String.self, forKey: .device_id))
            ?? UUID().uuidString
        self.deviceName = (try? container.decodeIfPresent(String.self, forKey: .deviceName))
            ?? (try? container.decodeIfPresent(String.self, forKey: .device_name))
            ?? "Unknown Device"
        self.platform = (try? container.decodeIfPresent(String.self, forKey: .platform)) ?? "android"
        self.publicKey = (try? container.decodeIfPresent(String.self, forKey: .publicKey))
            ?? (try? container.decodeIfPresent(String.self, forKey: .public_key))
            ?? ""
        self.exchangeKey = (try? container.decodeIfPresent(String.self, forKey: .exchangeKey))
            ?? (try? container.decodeIfPresent(String.self, forKey: .exchange_key))
            ?? ""
        self.localEndpoint = (try? container.decodeIfPresent(String.self, forKey: .localEndpoint))
            ?? (try? container.decodeIfPresent(String.self, forKey: .local_endpoint))
        self.expiresAt = (try? container.decodeIfPresent(Int64.self, forKey: .expiresAt))
            ?? (try? container.decodeIfPresent(Int64.self, forKey: .expires_at))
            ?? (Int64(Date().timeIntervalSince1970 * 1000) + 600_000)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(version, forKey: .version)
        try container.encode(pairingId, forKey: .pairingId)
        try container.encode(deviceId, forKey: .deviceId)
        try container.encode(deviceName, forKey: .deviceName)
        try container.encode(platform, forKey: .platform)
        try container.encode(publicKey, forKey: .publicKey)
        try container.encode(exchangeKey, forKey: .exchangeKey)
        try container.encodeIfPresent(localEndpoint, forKey: .localEndpoint)
        try container.encode(expiresAt, forKey: .expiresAt)
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

    enum CodingKeys: String, CodingKey {
        case version
        case transferId, transfer_id
        case senderDeviceId, sender_device_id
        case recipientDeviceId, recipient_device_id
        case itemType, item_type
        case displayName, display_name
        case mimeType, mime_type
        case sizeBytes, size_bytes
        case sha256
        case deliveryMode, delivery_mode
        case textContent, text_content
        case createdAt, created_at
    }

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

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.version = try container.decodeIfPresent(Int.self, forKey: .version) ?? 1
        self.transferId = (try? container.decodeIfPresent(String.self, forKey: .transferId))
            ?? (try? container.decodeIfPresent(String.self, forKey: .transfer_id))
            ?? UUID().uuidString
        self.senderDeviceId = (try? container.decodeIfPresent(String.self, forKey: .senderDeviceId))
            ?? (try? container.decodeIfPresent(String.self, forKey: .sender_device_id))
            ?? ""
        self.recipientDeviceId = (try? container.decodeIfPresent(String.self, forKey: .recipientDeviceId))
            ?? (try? container.decodeIfPresent(String.self, forKey: .recipient_device_id))
            ?? ""
        let itemTypeRaw = (try? container.decodeIfPresent(String.self, forKey: .itemType))
            ?? (try? container.decodeIfPresent(String.self, forKey: .item_type))
            ?? "file"
        self.itemType = ItemType(rawValue: itemTypeRaw.lowercased()) ?? .file
        self.displayName = (try? container.decodeIfPresent(String.self, forKey: .displayName))
            ?? (try? container.decodeIfPresent(String.self, forKey: .display_name))
            ?? "file"
        self.mimeType = (try? container.decodeIfPresent(String.self, forKey: .mimeType))
            ?? (try? container.decodeIfPresent(String.self, forKey: .mime_type))
        self.sizeBytes = (try? container.decodeIfPresent(Int64.self, forKey: .sizeBytes))
            ?? (try? container.decodeIfPresent(Int64.self, forKey: .size_bytes))
            ?? 0
        self.sha256 = (try? container.decodeIfPresent(String.self, forKey: .sha256)) ?? ""
        let modeRaw = (try? container.decodeIfPresent(String.self, forKey: .deliveryMode))
            ?? (try? container.decodeIfPresent(String.self, forKey: .delivery_mode))
            ?? "local"
        self.deliveryMode = DeliveryMode(rawValue: modeRaw.lowercased()) ?? .local
        self.textContent = (try? container.decodeIfPresent(String.self, forKey: .textContent))
            ?? (try? container.decodeIfPresent(String.self, forKey: .text_content))
        self.createdAt = (try? container.decodeIfPresent(Int64.self, forKey: .createdAt))
            ?? (try? container.decodeIfPresent(Int64.self, forKey: .created_at))
            ?? Int64(Date().timeIntervalSince1970 * 1000)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(version, forKey: .version)
        try container.encode(transferId, forKey: .transferId)
        try container.encode(senderDeviceId, forKey: .senderDeviceId)
        try container.encode(recipientDeviceId, forKey: .recipientDeviceId)
        try container.encode(itemType.rawValue, forKey: .itemType)
        try container.encode(displayName, forKey: .displayName)
        try container.encodeIfPresent(mimeType, forKey: .mimeType)
        try container.encode(sizeBytes, forKey: .sizeBytes)
        try container.encode(sha256, forKey: .sha256)
        try container.encode(deliveryMode.rawValue, forKey: .deliveryMode)
        try container.encodeIfPresent(textContent, forKey: .textContent)
        try container.encode(createdAt, forKey: .createdAt)
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
