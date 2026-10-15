# TriLink Threat Model & Security Specification

## 1. Assets to Protect

1. **User Payload Data:** Private files, photos, PDFs, clipboard text, sensitive URLs.
2. **Device Cryptographic Keys:** Private signing keys and private key-agreement keys stored in hardware keystores.
3. **Identity & Pairing Tokens:** QR pairing challenges and mutual authentication secrets.
4. **Local Host Integrity:** Protection against malicious file overwrites, path traversal (`../../`), arbitrary code execution, and disk exhaustion.

---

## 2. Threat Actors & Scenarios

| Threat Actor | Capabilities | Target / Attack Vector | Mitigation in TriLink |
| :--- | :--- | :--- | :--- |
| **Local Wi-Fi Eavesdropper** | Packet sniffing on open or shared Wi-Fi networks | Intercepting plain text or file bytes in transit | All local transfers are authenticated and encrypted over TLS with pinned/verified keys. |
| **Malicious Peer on Local LAN** | Spoofing mDNS/Bonjour service advertisements; sending unsolicited payloads | Injecting malware, corrupting inbox, tricking receiver into accepting files | Discovery is treated as unauthenticated. Endpoints enforce signed HTTP headers using stored pairing keys. Unpaired devices are rejected with 403. |
| **Untrusted Cloud Operator / Relay** | Inspection of Postgres records, S3 bucket storage, and Realtime websocket traffic | Reading stored files or leaking transfer history | Payloads are client-side encrypted (AES-256-GCM) before upload; only authorized recipient holds the decryption key. Storage and DB enforce strict RLS. |
| **Stolen/Compromised Device** | Physical possession of an old or lost device | Accessing ongoing and future user transfers | Fast device revocation. Revoked device IDs are rejected locally and denied access by Postgres RLS. |
| **Malicious Payload / Path Injection** | Transmitting filenames containing `../`, null bytes, or dangerous shell characters | Overwriting system files or app code | Filenames are strictly sanitized: paths are stripped to basenames, forbidden characters removed, collisions resolved with unique suffixes, and saved only in sandboxed app folders. |

---

## 3. Cryptographic Primitives & Design

### 3.1 Pairing Protocol
- **Ephemeral Pairing Challenge:** A single-use, 256-bit high-entropy secret with a 3-minute expiration timestamp.
- **QR Code Content:** Contains `{ "challenge": "...", "sender_id": "...", "name": "...", "pub_key": "...", "endpoint": "..." }`.
- **User Approval:** Recipient device scans QR, displays sender name and fingerprint, prompts user with explicit "Accept / Reject".
- **Mutual Key Exchange:** Upon approval, both peers record each other's verified public keys in local persistent storage (Room / Keychain).

### 3.2 Request Authentication (Local Transport)
Every HTTP request to a local TriLink receiver includes:
- `X-TriLink-Sender-ID`: Sender device UUID.
- `X-TriLink-Timestamp`: ISO 8601 or epoch timestamp (checked against clock drift: must be within ±120 seconds).
- `X-TriLink-Nonce`: Random 16-byte hex to defend against replay.
- `X-TriLink-Signature`: Ed25519 signature over canonical string:
  ```
  METHOD\nPATH\nTIMESTAMP\nNONCE\nSHA256(PAYLOAD)
  ```
The receiver looks up the sender's public key in its trusted-devices table and validates the signature.

### 3.3 End-to-End Encryption for Remote Transfers
1. **Key Agreement:** Sender generates an ephemeral X25519 key pair. Uses recipient's public X25519 key to derive a shared secret via ECDH + HKDF-SHA256.
2. **Symmetric Encryption:** File payload is encrypted using AES-256-GCM with a random 96-bit IV.
3. **Envelope:** The ciphertext payload is uploaded to Supabase Storage. The encrypted envelope containing the ephemeral public key and IV is stored in the transfers table.
4. **Decryption:** Recipient retrieves ciphertext and envelope, derives the shared secret using its private key, and verifies GCM authentication tag before writing plaintext to disk.

---

## 4. Input Sanitization & Storage Rules

1. **Path Traversal Protection:**
   - Any path delimiters (`/`, `\`, `..`) are stripped from `display_name`.
   - File extensions are validated against allowed/safe patterns. Executable scripts (`.sh`, `.apk`, `.app`, `.exe`) are preserved as raw non-executable data or tagged with warning flags; files are never auto-executed upon receipt.
2. **Size Quotas:**
   - Local transfers default max limit: 500 MB (user-configurable).
   - Remote transfers default max limit: 50 MB (configurable).
   - Declared `size_bytes` is checked before accepting; incoming stream length is counted and immediately aborted if it exceeds declared size.
3. **Atomic Writes:**
   - File data streams into a `.part` temporary file.
   - Once transfer completes, SHA-256 hash is computed and compared against declared hash.
   - Only on match is the file atomically moved to the designated incoming directory.
