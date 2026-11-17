# Contributing to TagSpeak

Thank you for helping make **TagSpeak** better!

## 1. Accessibility First (Non-Negotiable Core Principle)

TagSpeak is built for blind and low-vision **Android and iOS** users. Every UI change, feature, and pull request must be designed around **TalkBack (Android)**, **VoiceOver (iOS)**, **braille displays**, and **keyboard / Switch Access / Switch Control navigation**:

1. **Prefer Native Controls:** Use standard semantic Material 3 / Android controls (`Button`, `OutlinedButton`, `OutlinedTextField`, `NavigationBarItem`) and native SwiftUI controls (`Button`, `Toggle`, `TextEditor`, `TabView`) rather than custom gesture-only widgets.
2. **Explicit Roles, States, and Headings:**
   - Mark section titles as headings (`Modifier.semantics { heading() }` on Android; `.accessibilityAddTraits(.isHeader)` on iOS).
   - Merge composite setting rows (such as switches and radio buttons) using `Modifier.toggleable(role = Role.Switch, ...)` / `Modifier.selectable(role = Role.RadioButton, ...)` on Android and `.accessibilityElement(children: .combine)` on iOS so screen readers announce the label, description, role, and state in a single swipe.
   - Expose dynamic state changes via polite/assertive live regions and accessibility notifications.
3. **Zero Window-Title Clutter on Tag Reads:**
   - On Android, do not add visual chrome or window titles to `TagReaderActivity`. Tapping a sticker from outside the app must speak the label immediately and close cleanly.
   - On iOS, coordinate speech output (`IOSSpeechAnnouncer`) with `CoreNFC` sheet dismissal so VoiceOver focus restoration never interrupts the spoken label.

## 2. Running Tests Before Opening a Pull Request

Please verify that both Android and iOS unit tests pass before submitting a PR:

```bash
# Android unit & accessibility tests
./gradlew testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin

# iOS core & ViewModel unit tests
./ios/run-tests.sh
```
