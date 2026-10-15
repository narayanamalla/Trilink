# TriLink Android Setup & Build Guide

## Prerequisites

1. **Java Development Kit (JDK):** JDK 17 or higher (required by Android Gradle Plugin 8.x).
   ```bash
   brew install openjdk@17
   export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
   ```
2. **Android SDK:**
   - Android SDK Command-line Tools / Android Studio
   - Platform SDK: Android 14 (API 34)
   - Build-Tools: 34.0.0
   - Set environment variables:
     ```bash
     export ANDROID_HOME="$HOME/Library/Android/sdk"
     export PATH="$ANDROID_HOME/platform-tools:$PATH"
     ```

## Project Structure

- `android/app/src/main/java/com/trilink/app/`:
  - `data/database/`: Room Entities (`DeviceEntity`, `TransferEntity`, `InboxEntity`), DAOs, `TriLinkDatabase`.
  - `data/model/`: Domain models (`Device`, `Transfer`, `ItemType`, `TransferStatus`, `PairingPayload`).
  - `data/repository/`: `TransferRepository`, `RealTransferRepository`, `FakeTransferRepository` (isolated for UI tests/previews), `DeviceRepository`.
  - `domain/`: `TransferStateMachine`, `PathSanitizer`.
  - `ui/`: Compose Theme, Navigation, Screens (`HomeScreen`, `SendScreen`, `InboxScreen`, `TransfersScreen`, `DevicesScreen`, `SettingsScreen`, `PairQrScreen`).
  - `MainActivity.kt`: Primary Compose host.
  - `ShareActivity.kt`: Android Sharesheet (`ACTION_SEND`) receiver.

## Building the Android App

From the `android/` directory:

```bash
cd android

# Run Unit Tests
./gradlew test

# Assemble Debug APK
./gradlew assembleDebug
```

The compiled APK will be located at:
`android/app/build/outputs/apk/debug/app-debug.apk`

## Installing on Real Devices

Connect the Realme phone or Xiaomi Pad 8 via USB with USB Debugging enabled:

```bash
# Verify device connection
adb devices

# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
