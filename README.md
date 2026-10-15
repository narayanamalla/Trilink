# TriLink — Cross-Device Personal Ecosystem

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Protocol: trilink/v1](https://img.shields.io/badge/Protocol-trilink%2Fv1-green.svg)](docs/protocol.md)
[![Status: MVP Implemented](https://img.shields.io/badge/Status-MVP%20Implemented-brightgreen.svg)]()

**TriLink** is a local-first, privacy-respecting cross-device synchronization and transfer utility connecting your **Realme phone**, **Xiaomi Pad 8**, and **MacBook Air** into a unified, secure workspace.

---

## Key Features

- ⚡ **Local-First P2P Transfer:** Ultra-fast direct Wi-Fi and mobile hotspot transfers with streaming I/O.
- 🔒 **Zero-Trust Mutual Cryptography:** Requests are signed using hardware-backed Ed25519 keys (Android Keystore / macOS Keychain) and validated against a stored pairing table.
- ☁️ **End-to-End Encrypted Remote Relay:** Fallback remote delivery via Supabase private Storage using client-side AES-256-GCM encryption and X25519 ECDH key agreement. Cloud relay only ever sees ciphertext.
- 📱 **Android Sharesheet Integration:** Send text, links, photos, and files directly from Android's native system Share menu.
- 💻 **Native macOS Companion:** Lightweight menu-bar companion with Bonjour discovery (`_trilink._tcp`) and automatic inbox management.
- 🛡️ **Defensive Path Sanitization:** Eliminates directory traversal attacks (`../`), strips unsafe characters, and safely resolves filename collisions with incremental counters (`file(1).pdf`).
- 🔄 **Deterministic State Machine & Idempotency:** Explicit state transitions (`created -> uploading -> available -> downloading -> delivered`) with offline queueing and safe retries.

---

## System Architecture

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

For full architectural details, see [docs/architecture.md](docs/architecture.md).  
For the threat model and cryptographic specifications, see [docs/threat-model.md](docs/threat-model.md).  
For wire protocols and schemas, see [docs/protocol.md](docs/protocol.md).

---

## Repository Structure

```text
Ecosystem/
├── README.md                      # Master project documentation
├── AGENTS.md                      # Guidelines for developers and AI agents
├── .env.example                   # Environment variable template
├── .gitignore                     # Repository git ignore rules
├── android/                       # Android application (Kotlin, Compose, Room, WorkManager)
│   ├── app/src/main/java/com/trilink/app/
│   │   ├── data/                  # Room DB, DAOs, Entities, Models, Repositories
│   │   ├── domain/                # TransferStateMachine, PathSanitizer
│   │   ├── ui/                    # Compose Screens (Home, Send, Inbox, Transfers, Devices, Settings, Pair)
│   │   ├── MainActivity.kt        # Primary app Activity
│   │   └── ShareActivity.kt       # System Sharesheet receiver (ACTION_SEND)
│   └── app/src/test/              # Android Unit Tests
├── macos/                         # macOS Companion (Swift, SwiftUI, Network.framework, CryptoKit)
│   ├── Package.swift              # Swift Package Manager manifest
│   ├── Sources/TriLinkCore/       # Core protocol, HTTP server/client, Bonjour, Crypto
│   ├── Sources/TriLinkApp/        # Menu-bar companion executable entry point
│   └── Tests/TriLinkTests/        # macOS Swift Test Suite
├── shared/protocol/               # Shared JSON schemas and example fixtures
│   ├── schemas/                   # pairing_payload.json, transfer_request.json, transfer_status.json
│   └── examples/                  # pairing_payload_example.json, transfer_request_example.json, ...
├── supabase/                      # Backend infrastructure
│   ├── migrations/                # Schema DDL, Row-Level Security, and Storage policies
│   ├── seed.sql                   # Seed data for local Supabase testing
│   └── config.toml                # Supabase local environment config
├── docs/                          # In-depth technical documentation
│   ├── architecture.md            # Topology and component interaction
│   ├── threat-model.md            # Security analysis, attacks, and mitigations
│   ├── protocol.md                # REST API and wire specifications
│   ├── setup-android.md           # Android build, run, and adb instructions
│   ├── setup-macos.md             # macOS build, run, and SPM instructions
│   └── troubleshooting.md         # Wi-Fi AP isolation, battery saving, and mDNS fixes
└── scripts/                       # Verification test runners and automation
    ├── validate_protocol.py       # JSON schema and example contract validation
    ├── test_state_machine.py      # State machine lifecycle verification
    ├── test_security_and_path.py  # Path traversal, canonical strings, and clock drift
    ├── test_supabase_schema.py    # DDL, RLS, and private storage policy tests
    ├── simulate_local_transfer.py # End-to-end local streaming transfer simulation
    └── run_all_tests.sh           # Master test runner
```

---

## Running Automated Tests

Run the master test runner from the root directory:

```bash
./scripts/run_all_tests.sh
```

This executes:
1. `validate_protocol.py`: Validates protocol JSON schemas and message examples.
2. `test_state_machine.py`: Tests deterministic state transitions, offline queuing, retries, and cancellation.
3. `test_security_and_path.py`: Tests path traversal defense (`../../`), filename collision resolution, canonical strings, and clock drift windows.
4. `test_supabase_schema.py`: Verifies SQL schema, Row Level Security (RLS) policies, and private bucket isolation.
5. `simulate_local_transfer.py`: Simulates end-to-end local network transfers, chunked streaming, SHA-256 verification, and untrusted peer rejection.

---

## Getting Started

### Android App (Realme & Xiaomi Pad)
See [docs/setup-android.md](docs/setup-android.md) for full instructions:
```bash
cd android
./gradlew test
./gradlew assembleDebug
```

### macOS Companion (MacBook Air)
See [docs/setup-macos.md](docs/setup-macos.md) for full instructions:
```bash
cd macos
swift test
swift build -c release
.build/release/trilink-mac
```

---

## Security Disclosure & Integrity Note

- **Zero Plaintext Cloud Storage:** Payloads sent via remote relay are encrypted client-side using `AES-256-GCM` before transmission. Supabase never receives decryption keys.
- **Hardware-Backed Keys:** Cryptographic signing and key-agreement keys are stored in `AndroidKeyStore` on Android and `macOS Keychain` on Mac. Private keys are never exported.
- **Sanitized Filenames:** All incoming file paths are stripped of directory structures to prevent path traversal attacks.
- **Cryptographic Verification:** Transfers stream to a temporary `.part` file. Only when the computed SHA-256 matches the declared sender hash is the file committed to the inbox.
