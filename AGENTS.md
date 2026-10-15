# AGENTS.md — Developer and AI Agent Guidelines for TriLink

Welcome to the **TriLink** codebase. When modifying, extending, or maintaining this repository, you must adhere to the product and engineering principles established below.

---

## 1. Core Principles

1. **Local-First & Privacy by Default:** Always prefer direct local point-to-point transfer when devices are on the same reachable subnet. Never route local files through a cloud relay unless the peer is unreachable locally and remote delivery is explicitly enabled by the user.
2. **Zero-Trust Network:** Never assume a device on the same local subnet is trusted. Every HTTP request must be signed with the device's hardware-backed Ed25519 key and checked against trusted devices.
3. **End-to-End Encryption for Remote Transfers:** Cloud storage (Supabase) must only ever store ciphertext encrypted by the sender using AES-256-GCM with recipient X25519 ECDH key agreement. Never upload unencrypted files to cloud storage.
4. **Honest State Reporting:** Do not mark a transfer as "delivered" when it is queued, uploading, or unverified. Do not use decorative mock UI to simulate transfer states.
5. **Robust Input Sanitization:** Never trust incoming filenames. Always sanitize paths, strip directory traversal (`../`), remove dangerous characters, and resolve collisions without overwriting existing files.

---

## 2. Code Organization

```text
├── android/            # Android Kotlin Compose app with Room DB, WorkManager, Sharesheet
├── macos/              # macOS Swift Package Manager companion and Core networking library
├── shared/             # Shared JSON protocol schemas and example payloads
├── supabase/           # Postgres migrations, RLS policies, and private Storage buckets
├── docs/               # Architecture, Threat Model, Protocol, Setup, and Troubleshooting
└── scripts/            # Automated test suite and verification runners
```

---

## 3. Rules for Making Changes

- **No Secrets in Git:** Never commit `.env` files, production tokens, API keys, private keys, or keystores.
- **Run Tests After Every Modification:** Execute `scripts/run_all_tests.sh` to ensure all state machines, protocol schemas, security verifications, and transfer simulations pass.
- **Maintain Documentation:** If a protocol endpoint or database column is added or modified, update `docs/protocol.md` and `docs/architecture.md` simultaneously.
