# TagSpeak

**TagSpeak** is an accessibility-first **Android and iOS** application that enables blind and low-vision users to create, read, and edit spoken labels on inexpensive, off-the-shelf NFC/RFID stickers.

Labels are written directly onto the NFC sticker in a standardized, self-contained NDEF format—requiring **zero cloud accounts, zero internet connectivity, and zero proprietary tag lock-in**. Any sticker written on Android can be read and edited on iOS, and any sticker written on iOS automatically launches and speaks on Android.

---

## Key Features

### 1. Fast, Spoken NFC Label Reading (Android & iOS)
- **Android (Zero-UI Home Screen Auto-Announce):** Touch your unlocked Android phone to any TagSpeak-labeled NFC sticker from anywhere (including the home screen, without opening the app first). Android routes the tap directly to TagSpeak's silent background reader (`TagReaderActivity`), which **immediately speaks the label aloud** on the accessibility audio stream and closes automatically—with **zero window-title clutter** or extra buttons to navigate.
- **iOS (Action Button, Shortcuts, Auto-Scan on Launch, or One-Tap Read):** Launch TagSpeak via the iPhone **Action Button**, **Back Tap**, **Siri** (*"Read sticker in TagSpeak"*), or the Home Screen icon and activate **Read Sticker Aloud** (or enable **Automatically start scanning on app launch** in Settings) to immediately scan and speak any TagSpeak sticker over VoiceOver / `AVSpeechSynthesizer` (even when the physical Silent switch is on).

### 2. Full In-App Write, Inspect, and Edit Workflows
- **Write Label:** Type or dictate a spoken label, monitor live UTF-8 byte capacity, and touch an NFC sticker to write it.
- **Edit or Rewrite Existing Labels:**
  - Use **Scan Existing Sticker to Edit** to load a sticker's current text directly into the editor, modify it, and write it back.
  - Or scan any sticker while TagSpeak is open to inspect its label, byte usage, and writability, then activate **Edit or Rewrite This Label**.
- **Configurable Overwrite Protection:** By default, TagSpeak warns you and asks for confirmation before overwriting a sticker that already contains a label. You can toggle **Confirm before overwriting an existing label** off in Settings for rapid batch relabeling.

### 3. Built for TalkBack (Android), VoiceOver (iOS), Switch Access, and Braille Displays
- Explicit semantic headings (`heading()` on Android, `.isHeader` on iOS), polite and assertive live announcements, and merged single-focus switch and radio controls so every setting is read in a single swipe.

---

## Recommended NFC Stickers

TagSpeak works with any **NFC Forum Type 2 / NDEF-compatible** sticker operating at **13.56 MHz**:
- **NTAG213:** ~144 bytes of user memory (~96 characters of UTF-8 label text after NDEF + auto-launch headers). Great for short labels like spice jars, medications, and pantry items.
- **NTAG215:** ~504 bytes of user memory (~460 characters of UTF-8 text). Ideal for longer cooking instructions or multi-line notes.
- **NTAG216:** ~888 bytes of user memory (~840 characters of UTF-8 text).

> **Note:** Avoid **MIFARE Classic 1K** tags (which use a proprietary NXP sector format not supported by all phones or iOS devices) and **125 kHz RFID** keyfobs (phones only read 13.56 MHz NFC). If sticking tags onto metal cans or appliances, choose **on-metal (anti-metal) NTAG213/215** stickers.

---

## Standardized On-Tag NDEF Format

Every TagSpeak sticker stores an `NdefMessage` with two records:
1. **Record 1 — NFC Forum Well-Known Text Record (`TNF_WELL_KNOWN` / `RTD_TEXT`):**
   - Contains the status byte (UTF-8 encoding flag + language code length), ISO language code (e.g., `en`), and the UTF-8 spoken label text.
   - Readable out of the box by iOS `CoreNFC` (`NFCNDEFPayload.wellKnownTypeTextPayload()`) and standard cross-platform NFC utilities.
2. **Record 2 — Android Application Record (`AAR` for `com.tagspeak.app`):**
   - Instructs Android to launch TagSpeak directly when the sticker is tapped from the home screen without showing an application disambiguation prompt.

---

## Building and Testing

### Android (`app/`)

#### Prerequisites
- JDK 17 or 21
- Android SDK Platform 36

#### Commands
```bash
# Run Android unit tests
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug

# Install onto a connected Android device
./gradlew installDebug
```

### iOS (`ios/`)

#### Prerequisites
- macOS with Swift 5.10+ / Xcode 16+
- `xcodegen` (`brew install xcodegen`) to generate `TagSpeak.xcodeproj` from `ios/project.yml`
- Apple Developer Program account (required by Apple for the `CoreNFC` tag reading/writing entitlement on physical iPhones)

#### Commands
```bash
# Run iOS Core & ViewModel unit tests (works with Xcode or Command Line Tools)
./ios/run-tests.sh

# Generate Xcode project from ios/project.yml
cd ios && xcodegen generate
```

---

## Privacy

TagSpeak does not request internet permissions, does not collect analytics or telemetry, and stores all label text exclusively on your physical NFC stickers and local device preferences. See [PRIVACY.md](PRIVACY.md) for full details.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
