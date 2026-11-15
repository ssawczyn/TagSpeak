# Contributing to TagSpeak

Thank you for helping make **TagSpeak** better!

## 1. Accessibility First (Non-Negotiable Core Principle)

TagSpeak is built for blind and low-vision Android users. Every UI change, feature, and pull request must be designed around **TalkBack**, **braille displays**, and **keyboard / Switch Access navigation**:

1. **Prefer Native Controls:** Use standard semantic Material 3 / Android controls (`Button`, `OutlinedButton`, `OutlinedTextField`, `NavigationBarItem`) rather than custom gesture-only widgets.
2. **Explicit Roles, States, and Headings:**
   - Mark section titles with `Modifier.semantics { heading() }`.
   - Merge composite setting rows (such as switches and radio buttons) using `Modifier.toggleable(role = Role.Switch, ...)` or `Modifier.selectable(role = Role.RadioButton, ...)` so TalkBack reads the label, description, role, and state in a single swipe.
   - Expose dynamic state changes via `LiveRegionMode.Polite` or `LiveRegionMode.Assertive` and `stateDescription`.
3. **Zero Window-Title Clutter on Home-Screen Tag Taps:**
   - Do not add visual chrome or window titles to `TagReaderActivity`. Tapping a sticker from outside the app must speak the label immediately and close cleanly.

## 2. Running Tests Before Opening a Pull Request

Please verify that unit tests and Compose Semantics accessibility tests pass before submitting a PR:

```bash
./gradlew testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin
```
