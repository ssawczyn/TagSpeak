import Foundation

/// User-configurable accessibility and NFC preferences for TagSpeak.
public struct TagSpeakSettings: Sendable, Equatable {
  public var confirmBeforeOverwrite: Bool
  public var speakAfterWrite: Bool
  public var speechRate: Float
  /// iOS convenience setting: when enabled, opening TagSpeak immediately starts an NFC read scan
  /// so users launching the app via the Action Button, Dock, or Home Screen can immediately tap a sticker.
  public var autoScanOnLaunch: Bool

  public init(
    confirmBeforeOverwrite: Bool = true,
    speakAfterWrite: Bool = true,
    speechRate: Float = 1.0,
    autoScanOnLaunch: Bool = false
  ) {
    self.confirmBeforeOverwrite = confirmBeforeOverwrite
    self.speakAfterWrite = speakAfterWrite
    self.speechRate = min(max(speechRate, 0.5), 2.0)
    self.autoScanOnLaunch = autoScanOnLaunch
  }
}

public protocol TagSpeakPreferencesRepository: AnyObject {
  var currentSettings: TagSpeakSettings { get }
  func setConfirmBeforeOverwrite(_ enabled: Bool)
  func setSpeakAfterWrite(_ enabled: Bool)
  func setSpeechRate(_ rate: Float)
  func setAutoScanOnLaunch(_ enabled: Bool)
}

public final class InMemoryPreferencesRepository: TagSpeakPreferencesRepository {
  public private(set) var currentSettings: TagSpeakSettings

  public init(initialSettings: TagSpeakSettings = TagSpeakSettings()) {
    self.currentSettings = initialSettings
  }

  public func setConfirmBeforeOverwrite(_ enabled: Bool) {
    currentSettings.confirmBeforeOverwrite = enabled
  }

  public func setSpeakAfterWrite(_ enabled: Bool) {
    currentSettings.speakAfterWrite = enabled
  }

  public func setSpeechRate(_ rate: Float) {
    currentSettings.speechRate = min(max(rate, 0.5), 2.0)
  }

  public func setAutoScanOnLaunch(_ enabled: Bool) {
    currentSettings.autoScanOnLaunch = enabled
  }
}

public final class UserDefaultsTagSpeakRepository: TagSpeakPreferencesRepository {
  private enum Keys {
    static let confirmBeforeOverwrite = "tagspeak.confirm_before_overwrite"
    static let speakAfterWrite = "tagspeak.speak_after_write"
    static let speechRate = "tagspeak.speech_rate"
    static let autoScanOnLaunch = "tagspeak.auto_scan_on_launch"
  }

  private let defaults: UserDefaults
  public private(set) var currentSettings: TagSpeakSettings

  public init(defaults: UserDefaults = .standard) {
    self.defaults = defaults
    let confirmOverwrite =
      defaults.object(forKey: Keys.confirmBeforeOverwrite) as? Bool ?? true
    let speakAfterWrite =
      defaults.object(forKey: Keys.speakAfterWrite) as? Bool ?? true
    let rawRate =
      defaults.object(forKey: Keys.speechRate) as? Float ?? 1.0
    let autoScanOnLaunch =
      defaults.object(forKey: Keys.autoScanOnLaunch) as? Bool ?? false

    self.currentSettings = TagSpeakSettings(
      confirmBeforeOverwrite: confirmOverwrite,
      speakAfterWrite: speakAfterWrite,
      speechRate: rawRate,
      autoScanOnLaunch: autoScanOnLaunch
    )
  }

  public func setConfirmBeforeOverwrite(_ enabled: Bool) {
    defaults.set(enabled, forKey: Keys.confirmBeforeOverwrite)
    currentSettings.confirmBeforeOverwrite = enabled
  }

  public func setSpeakAfterWrite(_ enabled: Bool) {
    defaults.set(enabled, forKey: Keys.speakAfterWrite)
    currentSettings.speakAfterWrite = enabled
  }

  public func setSpeechRate(_ rate: Float) {
    let clamped = min(max(rate, 0.5), 2.0)
    defaults.set(clamped, forKey: Keys.speechRate)
    currentSettings.speechRate = clamped
  }

  public func setAutoScanOnLaunch(_ enabled: Bool) {
    defaults.set(enabled, forKey: Keys.autoScanOnLaunch)
    currentSettings.autoScanOnLaunch = enabled
  }
}
