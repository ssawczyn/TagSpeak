# Privacy Policy for TagSpeak

**Effective Date:** November 15, 2026

**TagSpeak** (`com.tagspeak.app`) is an open-source accessibility utility designed to read and write spoken text labels on physical NFC stickers.

## 1. Zero Data Collection
TagSpeak **does not collect, store, transmit, or share any personal information, usage analytics, telemetry, or device identifiers**.

## 2. No Internet Permission
TagSpeak does not request the `android.permission.INTERNET` permission in its Android Manifest. The application operates 100% offline on your device.

## 3. Where Your Data Lives
- **NFC Stickers:** Any label text you create is written directly to the physical NFC sticker you touch with your phone using standard NDEF text records.
- **Local App Preferences:** Your accessibility settings (such as speech speed and whether to confirm before overwriting an existing sticker) are stored exclusively on your device using standard Android `SharedPreferences`.

## 4. Permissions Used
- **NFC (`android.permission.NFC`):** Used solely to read NDEF text labels from and write NDEF text labels to physical NFC tags when you touch your device to a tag.

## 5. Contact
If you have questions or accessibility feedback regarding TagSpeak, please open an issue on the project's GitHub repository.
