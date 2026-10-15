# TriLink Architecture Specification

## 1. Executive Summary

TriLink is a local-first, privacy-respecting cross-device synchronization and transfer utility designed to seamlessly connect three primary personal devices:
1. **Realme phone (Android)**
2. **Xiaomi Pad 8 (Android)**
3. **MacBook Air (macOS)**

The system prioritizes point-to-point local network transfers (via Wi-Fi or hotspot) with mutual cryptographic authentication. When devices are located on separate networks or a peer is temporarily offline, TriLink can fall back to client-side encrypted remote delivery mediated by a Supabase backend.

---

## 2. System Topology

```
   ┌─────────────────────────────────────────────────────────┐
   │                    Local Network                        │
   │  (mDNS/NSD Discovery + Direct Mutual-Auth HTTP/TLS API) │
   │                                                         │
   │   ┌──────────────┐                  ┌──────────────┐    │
   │   │ Realme Phone │◄────────────────►│ Xiaomi Pad 8 │    │
   │   │  (Android)   │                  │  (Android)   │    │
   │   └──────┬───────┘                  └──────┬───────┘    │
   │          ▲                                 ▲            │
   │          │          ┌──────────────┐       │            │
   │          └─────────►│  MacBook Air │◄──────┘            │
   │                     │   (macOS)    │                    │
   │                     └──────┬───────┘                    │
   └────────────────────────────┼────────────────────────────┘
                                │
                    [When peers not on same LAN]
                                │
                                ▼
   ┌─────────────────────────────────────────────────────────┐
   │                    Supabase Backend                     │
   │  - Auth: Authenticated Device & Account Sessions        │
   │  - Postgres DB (RLS): Device registry, Transfer Queue   │
   │  - Realtime: Metadata signal & availability ping        │
   │  - Private Storage: Client-Encrypted Blobs (Ciphertext) │
   └─────────────────────────────────────────────────────────┘
```

---

## 3. Core Subsystems and Layering

Each device client (Android and macOS) mirrors an identical logical architecture:

### 3.1 Device Identity & Cryptographic Keystore
- **Android:** Hardware-backed `AndroidKeyStore` storing an Ed25519 or ECDSA P-256 signing key pair and an X25519 key-agreement pair.
- **macOS:** Secure Enclave / macOS `Keychain` holding private key material.
- **Identity Model:** Every device has a permanent UUID `deviceId`, an Ed25519/ECDSA public identity key `identityKey`, and an X25519 public key `exchangeKey`. No private keys are ever exported or transmitted.

### 3.2 Discovery Engine
- **Local Multicast:**
  - Android: `NsdManager` advertising `_trilink._tcp` and resolving peers.
  - macOS: `NWListener` / `NWBrowser` (Network framework) advertising and resolving `_trilink._tcp`.
- **Manual/Direct Fallback:**
  - Direct IP:Port configuration and QR-based connect code for restricted networks where multicast/mDNS is blocked or filtered by AP isolation.
- **Security Rule:** Discovered addresses are treated purely as network hints. No traffic or data is sent to a peer until mutual pairing/token verification succeeds.

### 3.3 Transfer Manager & State Machine
The Transfer Manager handles lifecycle transitions, stream slicing, progress events, and cancellation.

#### State Machine:
```
[created] ──► [uploading / transferring] ──► [available] ──► [downloading] ──► [delivered]
    │                     │                                      │
    ├──► [queued] (offline)                                     │
    │                                                            │
    └───► [failed] ◄─────────────────────────────────────────────┘
          (with safe retry)
```
- **Integrity:** SHA-256 hash computed during transmission and verified before moving temporary files (`.part`) to final storage.
- **Streaming:** Memory-bounded streams; files are read and written in 64 KB chunks, preventing Out-Of-Memory errors even on large payloads.

### 3.4 Local HTTP Transport
- Each device runs an authenticated lightweight HTTP server (port 42425 default, or dynamic).
- Endpoints enforce signed request headers:
  - `X-TriLink-Sender-ID`: UUID of the sending device.
  - `X-TriLink-Timestamp`: Unix timestamp (within ±120 seconds replay window).
  - `X-TriLink-Signature`: Ed25519/ECDSA signature over `HTTP-METHOD + PATH + TIMESTAMP + SHA256(BODY)`.
- Requests from unknown or revoked device IDs are rejected immediately with `403 Forbidden`.

### 3.5 Remote Relay Engine (Supabase)
- **Role:** Fallback store-and-forward relay.
- **Zero-Knowledge Payloads:** Sender generates a one-time symmetric AES-256-GCM key, encrypts file payload into ciphertext locally, encrypts the AES key using recipient's public key (X25519 ECDH), and uploads ciphertext to private Supabase Storage.
- **Metadata:** Minimal metadata (item type, cipher length, encrypted key envelope, transfer status) is stored in Supabase Postgres.
- **Row-Level Security (RLS):** Strictly enforces that only the authenticated device owner and authorized paired recipient can view or fetch transfer records and download storage objects.

---

## 4. Network Path Selection Algorithm

When a transfer is initiated:
1. Transfer Manager checks local discovery cache for the recipient `deviceId`.
2. If found, it probes the local endpoint `GET /api/v1/health` with a signed challenge.
3. If reachable and identity matches, the route is selected as **Local**.
4. If local endpoint is unreachable or discovery is missing:
   - If `TRILINK_ENABLE_REMOTE_DELIVERY` is enabled, route is selected as **Remote**.
   - If recipient is not currently online in Supabase Realtime, state is marked **queued**.
   - If remote delivery is disabled, the transfer errors with `PEER_UNREACHABLE_LOCALLY` and prompts user.
5. The UI always explicitly displays whether the route is **Local** or **Remote**.

---

## 5. Development Environment & Build Limitations

- **Host Machine:** macOS (Darwin arm64).
- **Installed Native Tools:**
  - `swift 6.4` & Swift Package Manager (`swift build`, `swift test`): Fully available for macOS core networking, protocol, and CLI/companion tests.
  - Command Line Tools active (Full Xcode GUI IDE is not active at `/Applications/Xcode.app`; Swift Package Manager targets compile directly).
  - Amazon Corretto JDK 11 is installed in JVMs; modern Android Gradle Plugin 8.x requires JDK 17+.
  - Android SDK and `adb` are not currently in system PATH on this host.
- **Strategy:**
  - Build and unit test the shared protocol and cross-platform verification suite.
  - Build and unit test the native Swift/macOS networking, protocol, state machine, and companion target using SPM.
  - Provide a clean, robust, modern Android Gradle project structure with Compose UI, Room DB, WorkManager, and unit tests, accompanied by complete build instructions and scripts in `docs/setup-android.md`.
