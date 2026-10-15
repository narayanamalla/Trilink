# TriLink — Antigravity Build Specification

> **Purpose:** Build a private, personal cross-device ecosystem connecting a Realme Android phone, Xiaomi Pad 8 Android tablet, and MacBook Air. The app should support nearby local transfers and remote delivery when devices are on different networks.
>
> **Important instruction for the coding agent:** Treat this document as the product and engineering specification. First inspect the workspace and report what is already present. Then implement the project in small, testable phases. Do not attempt every feature at once, do not claim a feature works unless it has been implemented and tested, and do not replace real functionality with decorative mock UI.

---

## 1. Product vision

Build **TriLink**, a local-first, privacy-focused utility that makes the user's three devices work together like one workspace.

- **Realme phone:** capture and share content, send clipboard text, select and transfer photos/files, receive items.
- **Xiaomi Pad 8:** receive and read files, collect links and notes, send content back to the phone or Mac.
- **MacBook Air:** send and receive content from a lightweight macOS menu-bar companion.

The same Android app should run on both Android devices. The Mac should have its own native companion. This is a personal daily-use utility, not a public multi-tenant commercial product at this stage.

### Product principles

1. **Local-first:** Prefer direct device-to-device transfer when both devices are on a reachable local network.
2. **Remote-capable:** If direct transfer is unavailable, support encrypted remote delivery through a relay.
3. **Private by default:** Only explicitly paired devices are trusted. Files must not be publicly accessible.
4. **Honest state:** Clearly distinguish online, available, queued, transferring, delivered, failed, and expired states.
5. **Low friction:** Share content from Android's system Share menu, and send from the Mac through a menu-bar interface or file picker.
6. **Small, reliable MVP:** Make text, links, and files work end to end before adding advanced device control.
7. **No platform lock-in:** Do not require the phone and tablet to be from the same manufacturer or use Apple-only device handoff features.

---

## 2. MVP scope

### Must build in the first usable release

- [ ] Android app installable on both the Realme phone and Xiaomi Pad 8.
- [ ] macOS companion for the MacBook Air.
- [ ] QR-based device pairing with explicit approval.
- [ ] Trusted-device list with device name, platform, last-seen time, and revocation.
- [ ] Send text and URLs between all three devices.
- [ ] Send and receive ordinary files and images.
- [ ] Direct local-network transfer when peers are reachable locally.
- [ ] Remote encrypted delivery when peers are on different networks.
- [ ] Persistent inbox and transfer history on each device.
- [ ] Transfer progress and meaningful error states.
- [ ] Cancel, retry, resend, open, save, forward, and delete actions where appropriate.
- [ ] Queue remote delivery while a recipient is offline, then deliver when it becomes available.
- [ ] Android system Share-menu integration for text, URLs, and files.
- [ ] Basic documentation, environment setup, tests, and troubleshooting instructions.

### Explicitly out of scope for the MVP

Do **not** implement these until the core transfer flow is reliable:

- Full phone-screen mirroring or remote-control access to every app.
- Phone-call or SMS relay.
- Automatic background clipboard monitoring.
- Silent notification scraping or notification mirroring.
- Remote camera or microphone access.
- Broad storage access or accessibility-service use as a shortcut.
- AI features, accounts for unrelated users, monetization, or a public social layer.
- A custom VPN or home-grown network tunneling protocol.

Advanced features can be added later as opt-in modules with appropriate permissions and platform-specific limitations documented.

---

## 3. Preferred technology stack

Use the following stack unless the repository already has a good, working foundation that makes a different choice clearly better. Explain any significant deviation in `docs/architecture.md`.

### Android

- Kotlin
- Jetpack Compose for UI
- AndroidX Navigation if multiple screens need navigation
- Room for local transfer history and inbox metadata
- DataStore for simple app settings
- Android Keystore for device-held key material
- WorkManager for persistent retries and deferred work
- Android Network Service Discovery (`NsdManager`) for local service discovery
- Android Sharesheet integration via the appropriate `ACTION_SEND` and `ACTION_SEND_MULTIPLE` intents
- System Photo Picker / Storage Access Framework instead of broad storage permissions

### macOS

- Swift
- SwiftUI
- A menu-bar app or menu-bar-first companion with a small main window
- Bonjour / Network framework for service discovery and local networking
- Keychain for device-held key material
- macOS notifications only if enabled by the user

### Backend / remote delivery

- Supabase Auth for a private account or device-pairing identity model
- Supabase Postgres for device registrations, transfer metadata, and delivery state
- Supabase Realtime for metadata updates and transfer notifications—not for streaming file bytes
- Supabase private Storage for encrypted file payloads
- Row Level Security (RLS) on all user data tables and explicit Storage policies

### Shared protocol

Define a documented, versioned JSON protocol in `shared/protocol/`. If the language-specific apps cannot practically share generated models, maintain equivalent typed models and contract tests in both codebases.

---

## 4. Architecture

### Client components

Each device has these logical components:

1. **Pairing and identity:** create a device identity, scan/approve a short-lived pairing QR code, exchange public identity information, save trusted peers, and support revocation.
2. **Device discovery:** find paired peers on the same network. Treat discovery as a hint only; a discovered device is not trusted until authentication succeeds.
3. **Transfer manager:** create transfers, select local or remote delivery, report progress, retry safely, cancel where possible, and verify content after receipt.
4. **Local receiver:** accept incoming content only from an authenticated, paired sender and only within configured size/type limits.
5. **Remote relay client:** upload/download encrypted payloads and synchronize status through the backend.
6. **Inbox/history:** store metadata locally and allow users to inspect and manage received items.
7. **Platform integration:** Android Share menu and file pickers; macOS menu-bar and file-picker actions.

### Network path selection

Use this order:

1. If a paired recipient is discovered and reachable on the local network, attempt authenticated local transfer.
2. If local transfer is unavailable or fails for a recoverable reason, offer or use the configured remote-delivery path.
3. If the recipient is offline, keep the encrypted payload and delivery record queued until it reconnects or the transfer expires.
4. Do not silently send large files to cloud storage if the user has disabled remote delivery. Clearly display the chosen route: **Local** or **Remote**.

Remote and local modes must share the same transfer metadata and UI state model.

### Local networking requirements

- Use Android NSD and Bonjour for discovery where supported.
- Use a clearly documented local transport, preferably HTTPS/TLS with peer identity verification.
- Do not trust a peer only because it is on the same Wi-Fi network.
- Authenticate the sender and recipient against the stored pairing relationship.
- Bind receiver services only as broadly as required, and stop listening when not needed.
- Make the listening state visible to the user when relevant.
- Support Wi-Fi and phone hotspot scenarios when the operating systems allow peer-to-peer reachability.
- Treat multicast discovery as best effort. Include manual retry/refresh and an alternative QR/manual connection path for networks that block multicast.

### Remote delivery requirements

- The client encrypts payloads **before** uploading them. Supabase should store ciphertext, not plaintext files.
- Supabase Realtime carries status and small metadata only, not file contents.
- Use private Storage buckets and restrictive RLS/Storage policies.
- Use short-lived authorized operations where supported; never expose a service-role key in either app.
- Do not place long-lived bearer secrets in QR codes, URLs, logs, or crash reports.
- Support retry and idempotency so a retry cannot create duplicate inbox entries or corrupt a transfer.
- Queue recipient-offline delivery and expire abandoned items under a documented retention policy.

---

## 5. Security and privacy requirements

Security is a product requirement, not a future polish task.

### Pairing and identity

- Pairing must require user approval on the receiving/trusting device.
- QR codes should contain a short-lived, single-use pairing token or challenge—not a permanent password, private key, or reusable access token.
- Show the device name and platform to the user before approval.
- Give every paired device a revocable identity.
- Revoking a device must prevent future transfers and remote access for that identity.
- Do not treat device discovery, the same Wi-Fi network, or possession of a transfer ID as authentication.

### Encryption

- Use maintained cryptographic libraries and established protocols. Do not invent a cipher or custom cryptographic construction.
- Encrypt network traffic in transit.
- For remote storage, encrypt file contents on the sender before upload and decrypt only on an authorized recipient.
- Establish a documented key-management model that works across Kotlin and Swift clients. Prefer a well-reviewed cross-platform cryptographic library and standard authenticated encryption/key agreement.
- Keep private keys in Android Keystore and macOS Keychain wherever technically supported.
- If robust cross-platform end-to-end encryption cannot be completed safely in a phase, do not claim end-to-end encryption. Keep remote uploads disabled behind an explicit development flag until the security design and tests are complete.
- Never log file contents, private keys, pairing tokens, access tokens, or decrypted sensitive metadata.

### Data handling

- Use random, non-guessable transfer identifiers.
- Verify a checksum or authenticated payload integrity after receipt.
- Enforce configurable file-size and item-count limits.
- Reject malformed messages and unsafe file paths; never blindly overwrite existing files.
- Sanitize filenames and use app-controlled destination locations by default.
- Do not automatically execute or open received files.
- Provide delete and expiry controls for remote payloads.
- Use minimal analytics. Do not introduce third-party tracking in this personal utility.

### Supabase security

- Enable RLS on all tables containing private user/device/transfer data.
- Add explicit policies for each operation; do not use permissive policies just to make the demo work.
- Scope queries and Storage access to the authenticated account and authorized paired-device relationship.
- Do not commit secrets into Git. Provide `.env.example` with variable names and fake placeholders only.
- Never place `service_role` credentials in Android or macOS client configuration.
- Include a security note explaining what the backend can see, including metadata versus encrypted payload contents.

---

## 6. Permissions and OS integration

Request permissions only when the user invokes a feature that requires them. Explain the reason in plain language.

### Android permissions and capabilities

Use only those required by the chosen implementation and target SDK. Potential requirements include:

- `INTERNET`: remote API and network operations.
- `ACCESS_NETWORK_STATE`: inspect connectivity state.
- Camera: only for QR scanning. Prefer a system or library-based scanner that does not require a persistent camera session.
- System Photo Picker / Storage Access Framework: user-selected photos, PDFs, and files; prefer these to broad storage permissions.
- `POST_NOTIFICATIONS`: only when transfer and incoming-item notifications are used and the OS requires runtime permission.
- Foreground service with the correct service type and permissions for active, user-initiated, long-running transfers, following the chosen target Android SDK.
- `NsdManager` / local network behavior required by Android's current SDK and OS version.

Rules:

- Do not request contacts, location, microphone, phone-call, SMS, accessibility, or broad all-files access for the MVP.
- Do not use a notification-listener service in the MVP.
- Do not promise indefinite background execution. Respect Android battery-management and background-execution limits.
- Use WorkManager for retryable background work; use an appropriate visible foreground transfer only when necessary for a user-initiated long-running transfer.
- Explain any OEM-specific battery settings only in troubleshooting documentation, not as a required hack to make basic foreground transfers work.

### macOS permissions and capabilities

- Request local network privacy access for peer discovery/transfers as needed.
- Configure Bonjour service declarations and local-network usage text if Bonjour is used.
- Use Keychain to store device keys and credentials.
- Request notification permission only if notifications are implemented.
- Use standard file-open/save panels for user-selected files.
- Do not request Full Disk Access unless a separately approved future feature truly requires it; the MVP must not need it.

Document every capability in the app's setup/readme and explain when the OS should show a permission prompt.

---

## 7. Core user flows

### Flow A: First-time setup

1. Launch TriLink on the Android phone.
2. Create or sign into the private account/identity flow, depending on the implementation decision documented by the agent.
3. Name the device (default can be editable, e.g. `Realme Phone`).
4. Open the Pair device screen on the tablet or Mac.
5. Scan the QR code and approve the displayed peer.
6. Confirm the device appears in the paired-device list.
7. Repeat until all three devices are paired.

Do not require a public account or social profile. Keep account/identity setup as minimal as possible while maintaining secure remote delivery.

### Flow B: Send text or a URL

1. User selects text/link or uses Android Share.
2. User picks a trusted target device.
3. UI displays the intended target and transfer route (Local or Remote).
4. Sender creates a transfer ID and transmits the typed payload.
5. Recipient validates sender, payload type, and size; persists the item to the inbox.
6. Both sides update the status without creating duplicate items if events arrive twice.

### Flow C: Send a file locally

1. User selects file(s) using the system file picker/share menu.
2. Sender selects a paired device.
3. Transfer manager checks local reachability.
4. Sender and receiver authenticate each other using the pairing identity.
5. Transfer streams file contents without loading the whole file into RAM.
6. Receiver writes to a temporary file, verifies integrity, then commits it to the destination.
7. UI reports success/failure and provides Open/Save/Forward/Delete actions.

### Flow D: Send a file remotely

1. User selects a paired target and Remote delivery is enabled.
2. Sender encrypts the file locally using the selected protocol.
3. Sender uploads ciphertext to private Storage and writes minimal transfer metadata.
4. Recipient receives a metadata event or discovers pending items on the next sync.
5. Recipient authorizes the transfer, downloads ciphertext, verifies integrity and decrypts locally.
6. Recipient records successful receipt and updates status; duplicate delivery events are idempotent.
7. Expiry and deletion behavior is applied according to the documented retention policy.

### Flow E: Offline receiver

1. Sender sends an item while recipient is offline.
2. Sender sees **Queued for delivery**, not **Delivered**.
3. When recipient returns online, it fetches authorized pending transfer metadata.
4. Recipient retrieves, validates, decrypts and saves the payload.
5. Both sides converge on a final transfer state.

---

## 8. Data model and protocol

Design a minimal model first. Adapt names if needed, but document the final schema and migrations.

### Device

Suggested fields:

- `id`: stable UUID
- `owner_id`: authenticated owner/account ID
- `device_name`: user-editable display name
- `platform`: `android` or `macos`
- `public_key`: public identity/key-agreement material only
- `created_at`, `last_seen_at`
- `revoked_at`: nullable timestamp
- `protocol_version`

Never store private keys in Postgres.

### Transfer

Suggested fields:

- `id`: random UUID
- `owner_id`
- `sender_device_id`
- `recipient_device_id`
- `item_type`: `text`, `url`, `image`, or `file`
- `display_name`: sanitized presentation name
- `mime_type`: nullable, treated as untrusted input
- `size_bytes`
- `sha256` or the documented integrity identifier
- `delivery_mode`: `local` or `remote`
- `status`: `created`, `queued`, `uploading`, `available`, `downloading`, `delivered`, `failed`, `cancelled`, `expired`
- `storage_object_id`: nullable; never a public URL
- `created_at`, `updated_at`, `expires_at`
- `failure_code`: nullable, machine-readable and non-sensitive

Store local-only fields such as a final local file URI in the local database, not in remote metadata unless there is a clear reason.

### Transfer protocol requirements

- Version every message/protocol schema.
- Include a unique transfer ID, sender identity, recipient identity, item type, declared length and integrity information.
- Validate actual bytes against declared length and limits.
- Treat all remote input and filenames as untrusted.
- Define idempotency for create, upload, receive, retry, cancel and delete operations.
- Model state transitions explicitly; reject invalid transitions.
- Use streaming I/O for large files and provide progress updates.
- Preserve backward compatibility within a documented protocol-version window.

### State machine

Implement and test an explicit state machine. At minimum:

`created -> uploading -> available -> downloading -> delivered`

A remote offline transfer can remain `queued` before it becomes `available`. Any active state may transition to `failed` where appropriate, with retry paths that preserve the same logical transfer. Cancellation and expiry must have clearly defined transition rules.

---

## 9. Suggested repository structure

Use a monorepo unless the existing workspace has a compelling reason not to.

```text
trilink/
├── README.md
├── AGENTS.md
├── .gitignore
├── .env.example
├── android/
│   ├── app/
│   ├── data/
│   │   ├── database/
│   │   ├── transfer/
│   │   └── security/
│   ├── discovery/
│   ├── networking/
│   ├── domain/
│   └── ui/
├── macos/
│   └── TriLink/
│       ├── App/
│       ├── Discovery/
│       ├── Networking/
│       ├── Security/
│       └── UI/
├── shared/
│   └── protocol/
│       ├── schemas/
│       └── examples/
├── supabase/
│   ├── migrations/
│   ├── seed.sql
│   └── config.toml
├── docs/
│   ├── architecture.md
│   ├── threat-model.md
│   ├── protocol.md
│   ├── setup-android.md
│   ├── setup-macos.md
│   └── troubleshooting.md
└── scripts/
```

The agent can refine the structure based on actual frameworks and build tools, but keep networking, storage, domain logic, security, and UI separated.

---

## 10. UI requirements

### Android primary screens

1. **Home:** paired devices, online/last-seen state, quick Send button, recent transfers.
2. **Send:** select target, choose file/text/link, see Local/Remote route.
3. **Inbox:** received items, sender, received time, type and status.
4. **Transfers:** active/completed/failed/queued tabs and progress.
5. **Devices:** pair device, rename, inspect last-seen state, revoke.
6. **Settings:** remote delivery on/off, auto-accept policy, storage destination, file-size limit, retention, notifications, privacy information.

### macOS primary UI

- Menu-bar icon showing status and pending transfers.
- Menu actions: Send Text/Link, Send File, Open Inbox, Devices, Settings, Quit.
- Small main window for paired devices, inbox, and transfer history.
- Native file-open/save dialogs.
- Clear route/status labels and actionable errors.

### Visual style

- Simple, calm, minimal interface.
- Neutral dark/light themes using platform conventions.
- Readable status text and accessible touch targets.
- No excessive gradients, animations, decorative charts, or marketing pages.
- Show real device/transfer state. Do not use hardcoded fake devices in production screens.

---

## 11. Testing and acceptance criteria

Do not mark a phase complete until its tests have been run and results summarized.

### Unit tests

- Transfer state-machine transitions.
- Message schema validation and protocol-version handling.
- Filename sanitization and size-limit checks.
- Idempotency for duplicate remote events.
- Retry and expiry decisions.
- Encryption/decryption round trips and tamper detection using a vetted library.
- RLS/storage policy tests for authorized and unauthorized device access.

### Integration tests

- Android phone to Android tablet on the same Wi-Fi.
- Android tablet to Mac on the same Wi-Fi.
- Mac to Android phone on the same Wi-Fi.
- Remote delivery between two different networks.
- Offline recipient: queue, reconnect, deliver.
- Large-file transfer without reading the entire file into RAM.
- Interrupted transfer and retry.
- Expired or revoked device cannot access new content.
- No duplicate inbox items when the same event is delivered twice.
- Local network discovery blocked: manual QR/manual connection fallback.

### Manual QA checklist

- [ ] Works when Android and Mac are on the same Wi-Fi.
- [ ] Works when one Android device uses a phone hotspot, provided peer traffic is allowed.
- [ ] Behaves clearly when local multicast discovery is blocked.
- [ ] Behaves clearly when the receiver is asleep/offline.
- [ ] Shows the actual delivery route and status.
- [ ] Does not label a queued transfer as delivered.
- [ ] Does not expose an unencrypted file in remote Storage.
- [ ] Revocation stops future transfers for that device.
- [ ] App remains usable if Supabase is unavailable for local transfers.
- [ ] App remains usable if local discovery fails and remote delivery is available.

Do not make unsupported claims such as "works in the background forever" or "instant remote delivery in every situation".

---

## 12. Development phases and coding-agent workflow

### Phase 0 — Workspace reconnaissance and technical design

1. Inspect the current repository and available SDKs/toolchains.
2. Detect whether Android Studio/Gradle and Xcode/macOS build tools are available.
3. Do not overwrite useful existing files.
4. Create `docs/architecture.md`, `docs/threat-model.md`, and `docs/protocol.md` with the key decisions.
5. Identify any build limitation in the current environment, especially that a macOS app generally needs macOS/Xcode to build and test natively.
6. Report the proposed file structure and exact Phase 1 scope before making wide-ranging changes.

### Phase 1 — Android shell and local data

1. Create the Android app with Compose navigation and a basic but polished Home, Devices, Inbox, Transfers, and Settings UI.
2. Add Room entities/DAO/repository for inbox and transfer metadata.
3. Add a repository abstraction for device discovery and transfer operations.
4. Use a clear `FakeTransferRepository` only for preview/design development and tests. Do not ship fake transfer status as real functionality.
5. Add unit tests and a README with build/run instructions.
6. Build the Android app and fix all compile errors before moving on.

### Phase 2 — Pairing and local transfer

1. Implement QR pairing with a short-lived token and explicit approval.
2. Implement trusted-device identity storage and revocation.
3. Implement discovery plus a manual fallback.
4. Implement authenticated local text/URL transfer first, then file streaming.
5. Implement Android Share-menu integration.
6. Add integrity verification, transfer progress, cancellation, retry, and useful errors.
7. Test transfers between two real devices if hardware is available. If not, document exactly what remains untested.

### Phase 3 — macOS companion

1. Create the SwiftUI menu-bar app.
2. Implement pairing, discovery, and the same protocol/state machine.
3. Support send/receive for text, URL, and files.
4. Use Keychain and native file panels.
5. Add the Mac build instructions and tests.
6. Validate Android-to-Mac and Mac-to-Android local transfer. Do not claim successful cross-device operation based only on compilation.

### Phase 4 — Secure remote delivery

1. Add Supabase migrations and document the data model.
2. Implement minimal identity/authentication and device authorization.
3. Enable strict RLS and Storage policies; test allowed and denied cases.
4. Finalize and test the cross-platform encryption/key-management approach.
5. Add encrypted upload/download, status sync, offline queueing, expiry, and retry.
6. Add environment-variable configuration and never commit real credentials.
7. Keep remote delivery disabled by default until encryption and authorization tests pass.

### Phase 5 — Hardening and daily-use polish

1. Test network changes, app process death, retries, and offline cases.
2. Add sensible file-size limits and configurable remote-delivery settings.
3. Improve accessibility, loading, empty, failure, and success states.
4. Write troubleshooting guides for macOS local network permission, Android battery behavior, and multicast-blocked networks.
5. Review logs and ensure no secrets or content are logged.
6. Run all available checks and summarize pass/fail/unverified items.

### Coding-agent rules

- Work in small increments. Build and test after each phase.
- Before a significant change, briefly state the files/components to be changed and why.
- Keep business logic out of UI components.
- Use typed models, structured errors, cancellation support, and dependency injection where appropriate.
- Avoid giant single-file implementations.
- Never hardcode keys, tokens, local IP addresses, fake peers, or production credentials.
- Do not silently swallow errors. Show actionable errors to the user and log only non-sensitive diagnostic information.
- Do not claim hardware tests were performed if they were not.
- If a required SDK, hardware device, or operating system is unavailable, implement what can be tested, document the limitation, and provide the exact manual test steps.
- At the end of each phase, report: files changed, commands run, tests/build results, limitations, and the next smallest milestone.

---

## 13. Environment configuration

Create `.env.example` with placeholders, not real secrets. Possible variables:

```dotenv
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_ANON_KEY=YOUR_PUBLIC_ANON_KEY
```

These variables must not include a service-role key. Ensure the client is secure even though the anonymous/public key is bundled into client applications; RLS and authorization policies are mandatory.

Document where each platform reads configuration and how to use a local development Supabase project. Never commit `.env`, signing keys, private keys, provisioning profiles, keystores, or real credentials.

---

## 14. Required deliverables

By the end of the initial implementation effort, produce:

1. Android application source code.
2. macOS companion source code or a clearly documented scaffold if native build tools are unavailable.
3. Database migrations and RLS/Storage policies.
4. `README.md` covering architecture, prerequisites, setup, builds, pairing, security, and troubleshooting.
5. `docs/architecture.md`, `docs/protocol.md`, and `docs/threat-model.md`.
6. Automated tests for the protocol, state machine, authorization, and transfer integrity.
7. `.env.example` with placeholders only.
8. A final implementation report that distinguishes **implemented**, **tested**, **not tested**, and **not implemented** features.

---

## 15. Definition of done for the first MVP

The first MVP is complete only when:

- All three of the user's devices can be paired with explicit approval.
- Text and URLs can be sent between all device pairs.
- Files can be sent and received locally between Android and macOS.
- The inbox and transfer history persist across app restarts.
- Remote delivery is implemented with encryption before cloud upload and strict authorization, or is explicitly labeled incomplete and disabled rather than falsely advertised.
- Failed/interrupted transfers present useful status and can be safely retried.
- The project has repeatable build instructions and an honest test report.

**Start now with Phase 0. Inspect the workspace and toolchain, write the architecture/threat-model/protocol documents, and implement only the smallest verified next milestone. Do not jump straight to Phase 4.**
