# TriLink macOS Setup & Build Guide

## Overview

The macOS companion provides:
- A menu-bar icon showing ecosystem status and pending items.
- Point-to-point local transfer listener (`LocalTransferServer`) over port 42425 with mutual Ed25519 authentication and SHA-256 streaming verification.
- Local discovery via Bonjour (`_trilink._tcp`).
- LocalTransferClient for streaming files, text notes, and URLs to Realme phone and Xiaomi Pad 8.
- Cryptographic security via Apple CryptoKit (`Curve25519`, `SHA256`, `AES.GCM`).

## Prerequisites

1. **macOS 13 (Ventura) or newer.**
2. **Xcode 15+ or matching Command Line Tools:**
   - Active developer directory set via:
     ```bash
     sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
     ```
   - *Note on Command Line Tools version skew:* If your host environment has a patch version difference between `swiftc` and `MacOSX.sdk` (e.g. `swift 6.4.0.34` with `MacOSX.sdk 6.4.0.31`), run `xcode-select` to select the matching full Xcode toolchain.

## Building with Swift Package Manager

From the `macos/` directory:

```bash
cd macos

# Run Unit and Integration Tests
swift test

# Build Executable
swift build -c release

# Run the TriLink Companion
.build/release/trilink-mac
```

## Running the Menu Bar Companion

When launched:
1. The companion binds to port 42425 (or configured `TRILINK_LOCAL_HTTP_PORT`).
2. Generates or loads its persistent hardware-backed Ed25519 identity key and X25519 key-agreement key from macOS Keychain.
3. Automatically advertises `_trilink._tcp` via Bonjour on the local network.
4. Listens for incoming transfers from paired Android devices. Files are automatically verified for SHA-256 integrity and placed in `~/Library/Application Support/TriLink/Downloads`.
