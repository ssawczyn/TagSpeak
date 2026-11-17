# TagSpeak — Project Overview & Rules

## 1. Product Vision

**TagSpeak** is an accessibility-first **Android and iOS** application that enables blind and low-vision users to create and read spoken labels using inexpensive, off-the-shelf NFC/RFID stickers (such as NTAG213/NTAG215).

### Core Workflows
1. **Fast Spoken Read Flow (Android & iOS):**
   - **Android (Zero-UI Auto-Announce on Tap):** When an unlocked Android phone with TagSpeak installed touches a TagSpeak-formatted NFC sticker from anywhere (including the home screen, without opening the app first), Android routes the NFC intent directly to TagSpeak (`TagReaderActivity`), which **immediately speaks the label aloud** without window title clutter.
   - **iOS (Launch & Scan / Action Button / Shortcuts):** Users can trigger *"Read TagSpeak Sticker"* via the iPhone Action Button, Back Tap, Siri, or the in-app **Read Sticker Aloud** button (or enable **Automatically start scanning on app launch**). TagSpeak scans the sticker via `CoreNFC` and immediately speaks the label aloud via `AVSpeechSynthesizer` / VoiceOver without focus-restoration clipping.
2. **Self-Contained Cross-Platform On-Tag Storage (Write Flow):**
   - Users enter or dictate a label in TagSpeak and touch an NFC sticker to write it.
   - Both the Android and iOS apps store the label **directly on the NFC tag** via NDEF using a standardized NFC Forum Text Record (`TNF_WELL_KNOWN` / `RTD_TEXT`, UTF-8) paired with an Android Application Record (`AAR` for `com.tagspeak.app`). This guarantees zero-prompt auto-launch on Android while preserving 100% read/write compatibility on iOS `CoreNFC`, with zero cloud accounts, databases, or proprietary tag lock-in.

## 2. Accessibility & UI Principles

- **TalkBack & VoiceOver First UX:** Every screen, control, and state transition must be designed around TalkBack (Android), VoiceOver (iOS), and keyboard/braille display navigation.
- **Native Platform Controls & Semantics:** Prefer standard native Material 3 (Android) and SwiftUI (iOS) controls with explicit accessible roles, headings, labels, and state announcements.
- **No Unnecessary Visual Chrome:** Keep the UI minimal and task-focused (Write a tag, Read/Inspect a tag, Settings).
- **Automated Accessibility Verification:** Use unit and semantics tests (`./gradlew testDebugUnitTest` on Android and `./ios/run-tests.sh` / `swift test` on iOS) so NDEF compatibility, ViewModel state transitions, and accessibility behavior are verified programmatically alongside live screen-reader testing.
