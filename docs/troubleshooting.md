# TriLink Troubleshooting Guide

## 1. Local Network Discovery (mDNS / Bonjour / NSD)

### Symptom: Devices on the same Wi-Fi network do not see each other.
**Causes & Solutions:**
1. **AP / Client Isolation on Public or Enterprise Wi-Fi:**
   - Some routers prevent devices on the same Wi-Fi from communicating directly (common in cafes, universities, and hotels).
   - *Workaround:* Use a phone mobile hotspot (e.g. enable hotspot on Realme phone and connect Xiaomi Pad and Mac), or use the **Manual Connect / QR Code** fallback by scanning the QR code directly, which embeds the target's IP:Port.
2. **macOS Local Network Privacy Prompt:**
   - On macOS 14+, apps must be granted permission to access the local network.
   - Go to **System Settings > Privacy & Security > Local Network** and ensure TriLink is allowed.
3. **Android Multicast Lock:**
   - Some aggressive battery savers put the Wi-Fi chip into power-saving mode, filtering multicast packets (`NsdManager`).
   - TriLink acquires a transient `WifiManager.MulticastLock` during discovery sessions. If discovery stalls, tap **Refresh** in the Devices screen.

---

## 2. Android Battery Management & Background Execution

### Symptom: Incoming transfer fails or gets interrupted when the phone screen turns off.
**Explanation:**
- Android OEMs (specifically Realme UI / ColorOS and Xiaomi HyperOS / MIUI) impose aggressive background process restrictions.
- TriLink strictly respects user battery by avoiding permanent background wake-locks. For long-running user-initiated transfers, TriLink promotes the transfer to an active **Foreground Service** with an ongoing notification (`dataSync` type).

**Recommended Device Settings:**
1. **Realme (ColorOS / Realme UI):**
   - Settings > Battery > More battery settings > App battery management > TriLink > Select **Allow background activity** and disable **Auto-freeze**.
2. **Xiaomi Pad 8 (HyperOS / MIUI):**
   - Settings > Apps > Manage Apps > TriLink > Battery Saver > Select **No restrictions**.
   - Enable **Autostart** if you want the app to resume pending queued transfers upon boot.

---

## 3. Storage & Filename Sanitization

### Symptom: Transferred file is named `photo(1).jpg` instead of `photo.jpg`.
- **Cause:** TriLink has built-in collision prevention. If a file with the same name already exists in your destination folder, TriLink safely appends an incremental counter `(1)`, `(2)` to protect your existing files from being overwritten.

### Symptom: Error `INTEGRITY_MISMATCH`.
- **Cause:** The computed SHA-256 hash of the received bytes did not match the declared hash provided by the sender.
- **Security Action:** TriLink immediately deletes the temporary `.part` file to prevent corrupted or tampered data from reaching your inbox. Check for network interference or retry the transfer.

---

## 4. Remote Delivery (Supabase Relay)

### Symptom: Transfer remains in `QUEUED` state.
- **Cause:** The recipient device is currently offline or disconnected from the internet.
- **Expected Behavior:** Remote delivery queues the encrypted payload securely. As soon as the recipient reconnects and pings the relay, the download will begin automatically.
- **Retention:** Abandoned transfers expire automatically after 48 hours and are cleaned up by the retention policy.
