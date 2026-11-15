# TagSpeak

**TagSpeak** is an accessibility-first Android application that enables blind and low-vision users to create, read, and edit spoken labels on inexpensive, off-the-shelf NFC/RFID stickers.

labels are written directly onto the NFC sticker in a standardized, self-contained NDEF format—requiring **zero cloud accounts, zero internet connectivity, and zero proprietary tag lock-in**.

---

## Key Features

### 1. Instant Zero-UI Auto-Announce from the Home Screen
- Touch your unlocked Android phone to any TagSpeak-labeled NFC sticker from anywhere (including the home screen, without opening the app first).
- Android routes the tap directly to TagSpeak's silent background reader (`TagReaderActivity`), which **immediately speaks the label aloud** on the accessibility audio stream and closes automatically—with **zero window-title clutter** or extra buttons to navigate.

### 2. Full In-App Write, Inspect, and Edit Workflows
- **Write Label:** Type or dictate a spoken label, monitor live UTF-8 byte capacity, and touch an NFC sticker to write it.
- **Edit or Rewrite Existing Labels:**
  - Use **Scan Existing Sticker to Edit** to load a sticker's current text directly into the editor, modify it, and write it back.
  - Or touch any sticker while TagSpeak is open in the foreground to inspect its label, byte usage, and writability, then activate **Edit or Rewrite This Label**.
- **Configurable Overwrite Protection:** By default, TagSpeak warns you and asks for confirmation before overwriting a sticker that already contains a label. You can toggle **Confirm before overwriting an existing label** off in Settings for rapid batch relabeling.

### 3. Built for TalkBack, Switch Access, and Braille Displays
- Explicit semantic headings (`heading()`), polite and assertive live regions (`LiveRegionMode`), and merged single-focus switch and radio controls (`Role.Switch`, `Role.RadioButton`).

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

### Prerequisites
- JDK 17 or 21
- Android SDK Platform 36

### Commands
```bash
# Run unit tests
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug

# Install onto a connected Android device
./gradlew installDebug
```

---

## Privacy

TagSpeak does not request internet permissions, does not collect analytics or telemetry, and stores all label text exclusively on your physical NFC stickers and local device preferences. See [PRIVACY.md](PRIVACY.md) for full details.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
