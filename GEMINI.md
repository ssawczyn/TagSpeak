# TagSpeak — Project Overview & Rules

## 1. Product Vision

**TagSpeak** is an accessibility-first Android application that enables blind and low-vision users to create and read spoken labels using inexpensive, off-the-shelf NFC/RFID stickers (such as NTAG213/NTAG215).

### Core Workflows
1. **Instant Zero-UI Auto-Announce on Tap (Read Flow):**
   - When an unlocked Android phone with TagSpeak installed touches a TagSpeak-formatted NFC sticker from anywhere (including the home screen, without opening the app first), Android routes the NFC intent directly to TagSpeak.
   - TagSpeak **immediately speaks the label aloud** (respecting TalkBack / system TTS) without forcing the user to navigate UI chrome, press a "Scan" button, or listen to window title clutter before hearing the label.
2. **Self-Contained On-Tag Storage (Write Flow):**
   - Users enter or dictate a label in TagSpeak and touch an NFC sticker to write it.
   - The label text is stored **directly on the NFC tag** via NDEF using a standardized NFC Forum Text Record (`TNF_WELL_KNOWN` / `RTD_TEXT`, UTF-8) paired with an Android Application Record (`AAR` for `com.tagspeak.app`). This guarantees zero-prompt auto-launch on Android while preserving cross-platform readability for iOS `CoreNFC` and standard NFC readers, with zero cloud accounts, databases, or proprietary tag lock-in.

## 2. Accessibility & UI Principles

- **TalkBack-First UX:** Every screen, control, and state transition must be designed around TalkBack and keyboard/braille display navigation.
- **Native Android Controls & Semantics:** Prefer standard native Android / Material 3 controls with explicit accessible roles, labels, and state announcements.
- **No Unnecessary Visual Chrome:** Keep the UI minimal and task-focused (Write a tag, Read/Inspect a tag, Settings).
- **Automated Accessibility Verification:** Use unit tests (Compose Semantics / Robolectric / Espresso accessibility checks) and `adb` deployment so accessibility behavior can be verified programmatically alongside live TalkBack testing.
