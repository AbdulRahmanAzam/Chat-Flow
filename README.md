# ChatFlow — Offline Bluetooth LE Mesh Chat for Android

> Computer Networks course project. Inspired by [BitChat](https://github.com/permissionlesstech/bitchat) (iOS) — rebuilt natively for Android with a simplified academic feature set.

ChatFlow lets up to **20+ nearby Android devices talk to each other with no Wi-Fi, no SIM, no internet, and no account** — purely over Bluetooth Low Energy. Every device is both a **BLE peripheral** and a **BLE central** at the same time. Messages hop through the mesh (up to 7 hops) so devices that are *not* in direct radio range still receive each other's traffic as long as the swarm is connected.

<p align="center"><em>Bluetooth Low Energy mesh · AES-GCM encryption · Jetpack Compose UI · Room persistence</em></p>

---

## 1. Features

| Feature | How it is implemented |
| --- | --- |
| Automatic peer discovery (no pairing) | Every device advertises a fixed 128-bit service UUID and scans for it continuously (`BleAdvertiser` + `BleScanner`). |
| Simultaneous connections to many devices | GATT server accepts incoming centrals; a client pool maintains outbound connections. Combined with multi-hop relay this supports **20+ devices** even though one Android radio only holds ~7 direct GATT links. |
| Mesh relay (multi-hop) | Binary packet carries a TTL (start = 7); every receiver decrements and re-broadcasts. Duplicate `messageId` UUIDs are dropped via an LRU "seen" cache. |
| Private 1-on-1 chat (end-to-end encrypted) | ECDH on the stored per-device keypair → HKDF-SHA256 → AES-GCM-128. Only the addressed recipient can decrypt; relays just forward opaque ciphertext. |
| Group / channel chat | AES-GCM with a symmetric key derived from the channel name via HKDF. Anyone who knows the channel name can read and write. |
| Delivery receipts (✓ → ✓✓) | Every direct message receives an `ACK` packet from the final recipient; sender's UI updates the check icon. |
| Persistent history | Room database (`messages`, `peers`, `channels`). |
| Online / last-seen status | Peers table updated on every `ANNOUNCE`. Status dot shown if seen in the last 60 s. |
| Dark / Light / System theme | Material 3 color scheme, switchable in Settings. |
| Keeps running in background | `BleMeshService` is a foreground service (type `connectedDevice`) with a persistent notification showing the live peer count. |
| No accounts / no phone numbers | Device identity is a locally-generated ECDH keypair. The first 8 bytes of SHA-256(publicKey) are the peer id. |

---

## 2. Architecture

```
┌────────────────────────────── UI (Jetpack Compose) ──────────────────────────────┐
│  Onboarding · Home · Chat · Peers · Settings · NewChannel                        │
└─────────────────────────────────────────────────────────────────────────────────┘
                             │ observes Room flows + calls mesh
┌────────────────────────────────── Domain ────────────────────────────────────────┐
│  ChatRepository   │  CryptoManager (ECDH + AES-GCM + HKDF)  │  PrefsStore        │
└─────────────────────────────────────────────────────────────────────────────────┘
                             │
┌──────────────────────────────── MeshEngine ──────────────────────────────────────┐
│  ┌─ Packet (binary codec, versioned header) ─┐  ┌─ MeshRouter (TTL + dedup) ─┐   │
│  │   ANNOUNCE · MESSAGE · ACK · KEY_REQUEST  │  │   shouldRelay / seen cache │   │
│  └───────────────────────────────────────────┘  └────────────────────────────┘   │
│                                                                                  │
│  BleAdvertiser   BleScanner   BleGattServer   BleClientPool                      │
│      │               │              │                │                           │
└──────┴───────────────┴──────────────┴────────────────┴──────────────────────────┘
                             │ all running inside
                     BleMeshService (foreground)
```

### Wire format (36 B fixed header + channel + length-prefixed payload)

```
version(1) type(1) ttl(1) flags(1)
senderId(8) recipientId(8) messageId(16) timestamp(8)
channelLen(1) channelName(N)
payloadLen(2) payload(M)            <-- opaque; may be AES-GCM ciphertext
```

### Crypto

* Each device generates an ECDH keypair (secp256r1 — picked over X25519 for API 26 compatibility) on first launch and stores it in internal storage.
* Private DMs: shared secret = `ECDH(selfPriv, peerPub)`, key = `HKDF-SHA256(secret, info="chatflow-dm", 32 bytes)`, ciphertext = `AES/GCM/NoPadding(iv ‖ ct)` with a fresh 12-byte IV.
* Group channels: symmetric key = `HKDF-SHA256("channel:<name>", info="chatflow-ch", 32 bytes)`.
* Packets are authenticated by GCM's built-in MAC; tampering by a relay fails decryption.

---

## 3. Requirements

* **Android 8.0 (API 26) or newer** on at least two real devices (BLE peripheral mode is required — most emulators cannot advertise). Android **12 (API 31+)** gives the best runtime because of the new Bluetooth permission model.
* **Android Studio Hedgehog (2023.1.1) or newer** (or Iguana / Koala). JDK 17 is bundled.

> ⚠️ Emulators do not support BLE advertising. You **must** test on real phones.

---

## 4. Build and run

### Option A — Android Studio (recommended)

1. Open Android Studio → **File ▸ Open…** → select the project folder `Chat Flow`.
2. Android Studio will prompt *"Gradle wrapper not found"* the first time — click **OK, use Gradle wrapper** or run **File ▸ Sync Project with Gradle Files**. Studio will download Gradle 8.9 and generate `gradlew` for you.
3. Plug in your Android phone with **USB debugging** enabled (Settings ▸ Developer options).
4. Select your device in the toolbar and press **Run ▶**.
5. Install the APK on every additional phone you want in the mesh (drag & drop `app-debug.apk` onto a device, or run `Run ▶` again with a different phone connected).

### Option B — Command line

```powershell
# One-time: put an SDK path in local.properties
"sdk.dir=C:\\Users\\$env:USERNAME\\AppData\\Local\\Android\\Sdk" | Out-File -Encoding ASCII .\local.properties

# Let Gradle bootstrap the wrapper
gradle wrapper --gradle-version 8.9

# Install on every connected device
.\gradlew :app:installDebug
```

The APK is emitted to `app/build/outputs/apk/debug/app-debug.apk`.

---

## 5. Testing checklist — how to demo to your professor

Install the app on **N ≥ 2** phones (5–20 for a striking demo) and keep them in the same room.

| # | What to do | What should happen |
| - | ---------- | ------------------- |
| 1 | Launch the app on every phone, pick different nicknames. | Each phone ends onboarding on the home screen. |
| 2 | Accept the Bluetooth / Nearby-devices permission prompts. | The foreground notification "ChatFlow" appears with "Connected peers: N". |
| 3 | Tap the **People** icon. | Every other running phone appears in the list within ~15 s, with a green online dot. |
| 4 | Tap a peer, send a message. | Message appears instantly on the recipient with the sender's nickname; sender sees a double-tick (✓✓). |
| 5 | Tap **New channel**, enter `cn-project`, send a message. | Every phone that joined `cn-project` receives the message in real time. |
| 6 | **Mesh test:** put phone **A** and phone **C** out of direct BLE range but keep phone **B** between them. | Messages between A and C still go through, relayed by B (TTL visibly decrements in logs — see §6). |
| 7 | Turn a phone to **Dark** in Settings. | UI palette switches; preference is persisted across restarts. |
| 8 | Kill the app with the screen off. | Foreground notification keeps the service alive; messages continue to be relayed. |
| 9 | Uninstall ChatFlow. | Identity keypair and all message history is wiped (app storage only). |

### Automated unit checks

The included code is small enough to exercise by eye, but you can add JVM unit tests for the crypto and packet codec under `app/src/test/java/…`. The packet round-trip is trivially verifiable:

```kotlin
val p = Packet(PacketType.MESSAGE, 7, true, ByteArray(8) { 1 }, ByteArray(8) { 2 },
               UUID.randomUUID(), System.currentTimeMillis(), "cn-project", byteArrayOf(1,2,3))
assertContentEquals(p.payload, Packet.decode(p.encode())!!.payload)
```

---

## 6. Debugging tips

* **`adb logcat -s ChatFlow/Mesh ChatFlow/GattSrv ChatFlow/GattCli ChatFlow/Scan ChatFlow/Adv`** shows every packet the mesh sees. TTL decrements per hop — useful to prove multi-hop is real.
* **Bluetooth HCI snoop log** (Developer options ▸ Enable Bluetooth HCI snoop log) captures the on-air bytes. Pull `/sdcard/Android/data/…/btsnoop_hci.log` and open in Wireshark for the full radio transcript.
* If scanning never returns peers: make sure **Location services** are on (Android 8–11 require it even with the `BLUETOOTH_SCAN` permission). The app asks for location only on those OS versions.
* If advertising silently fails on some old devices, the likely cause is that the chipset reports `isMultipleAdvertisementSupported == false`. Use a different phone as peripheral or reduce simultaneous advertisements.
* If GATT writes time out after many peers, restart Bluetooth (the Android BLE stack limits ~7 concurrent GATT links on most SoCs; the mesh relay is exactly what breaks this ceiling to reach 20+ devices).

---

## 7. Project layout

```
app/src/main/java/com/chatflow/app
├── MainActivity.kt           # runtime-permission gate + launches foreground service
├── ChatFlowApp.kt            # Application subclass — builds the service-locator AppContainer
├── ble/
│   ├── BleConstants.kt       # service/characteristic UUIDs and tunables
│   ├── BleAdvertiser.kt      # BLE peripheral advertiser
│   ├── BleScanner.kt         # BLE central scanner (filters on our service UUID)
│   ├── BleGattServer.kt      # GATT server — accepts writes, notifies subscribers
│   ├── BleClientPool.kt      # Outbound GATT client connections
│   ├── MeshEngine.kt         # Orchestrates scan/advertise/server/clients + relay rules
│   └── BleMeshService.kt     # Foreground service that owns the MeshEngine
├── crypto/
│   ├── CryptoManager.kt      # ECDH + AES-GCM + per-channel key derivation
│   └── HkdfSha256.kt         # RFC 5869 HKDF implementation
├── protocol/
│   ├── Packet.kt             # Binary wire-format codec
│   └── MeshRouter.kt         # Dedup (LRU of seen UUIDs) + TTL rules
├── data/
│   ├── AppContainer.kt       # manual DI
│   ├── PrefsStore.kt         # DataStore-backed preferences (nickname, theme)
│   ├── db/AppDatabase.kt     # Room DB + DAOs for messages/peers/channels
│   └── repo/ChatRepository.kt
└── ui/                        # Jetpack Compose screens & Material 3 theme
    ├── theme/Theme.kt
    ├── nav/ChatFlowNav.kt
    └── screens/
        ├── OnboardingScreen.kt
        ├── HomeScreen.kt
        ├── ChatScreen.kt
        ├── PeersScreen.kt
        ├── SettingsScreen.kt
        └── NewChannelScreen.kt
```

---

## 8. Known limitations (worth mentioning in your report)

* Secp256r1 is used instead of X25519 so the app still runs on API 26–32. X25519 `XDH` keys landed in JCA on API 33 — dropping older devices would let you swap that in.
* The group-channel key is derived only from the channel name, which means channel names must be considered secret. A class-upgrade would be to use the Noise Protocol pattern BitChat uses (group key established via a pairwise key ratchet).
* BLE ATT MTU caps payloads at ~500 B after headers. Messages larger than that are currently rejected by the encoder. Adding fragmentation would let you send arbitrary text + attachments.
* Android's BLE stack on some OEMs (older Samsung, MediaTek) is flaky under load — a production app would use Nordic's Android-BLE-Library instead of raw `BluetoothGatt`.

---

## 9. Credits

* Architecture inspired by [permissionlesstech/bitchat](https://github.com/permissionlesstech/bitchat) (Swift, iOS). ChatFlow re-implements the BLE-mesh subset in Kotlin for an Android-only course project.
* BLE mesh routing pattern: TTL + LRU-dedup is a classic flooded-mesh design.

## 10. License

Released for academic use. Include the repository URL in your submission.
