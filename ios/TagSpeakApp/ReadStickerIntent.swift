#if canImport(AppIntents)
import AppIntents
import Foundation

/// Notification posted when the user triggers the "Read TagSpeak Sticker" AppIntent
/// (via the iPhone Action Button, Back Tap, Siri, or Control Center).
public extension Notification.Name {
  static let tagSpeakTriggerReadStickerIntent = Notification.Name(
    "com.tagspeak.app.triggerReadStickerIntent"
  )
}

@available(iOS 16.0, *)
public struct ReadStickerIntent: AppIntent {
  public static var title: LocalizedStringResource = "Read TagSpeak Sticker"
  public static var description = IntentDescription(
    "Opens TagSpeak and immediately scans an NFC sticker to speak its label aloud."
  )
  public static var openAppWhenRun: Bool = true

  public init() {}

  @MainActor
  public func perform() async throws -> some IntentResult {
    NotificationCenter.default.post(name: .tagSpeakTriggerReadStickerIntent, object: nil)
    return .result()
  }
}

@available(iOS 16.0, *)
public struct TagSpeakShortcutsProvider: AppShortcutsProvider {
  public static var appShortcuts: [AppShortcut] {
    AppShortcut(
      intent: ReadStickerIntent(),
      phrases: [
        "Read sticker in \(.applicationName)",
        "Scan sticker with \(.applicationName)",
        "Read label in \(.applicationName)",
      ],
      shortTitle: "Read Sticker",
      systemImageName: "wave.3.right.circle.fill"
    )
  }
}
#endif
