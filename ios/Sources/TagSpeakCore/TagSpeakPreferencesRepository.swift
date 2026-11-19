import Foundation

/// Supported NFC sticker capacity profiles for default character limit warnings.
public enum StickerTypeProfile: String, Sendable, Equatable, CaseIterable {
  case ntag213 = "NTAG213"
  case ntag215 = "NTAG215"
  case ntag216 = "NTAG216"

  public var displayName: String {
    switch self {
    case .ntag213:
      return "NTAG213 (standard, ~96 characters)"
    case .ntag215:
      return "NTAG215 (medium, ~448 characters)"
    case .ntag216:
      return "NTAG216 (large, ~832 characters)"
    }
  }

  public var shortLabel: String {
    switch self {
    case .ntag213:
      return "standard NTAG213 sticker"
    case .ntag215:
      return "medium NTAG215 sticker"
    case .ntag216:
      return "large NTAG216 sticker"
    }
  }

  public var ndefCapacityBytes: Int {
    switch self {
    case .ntag213:
      return TagSpeakNdefCodec.ntag213CapacityBytes
    case .ntag215:
      return TagSpeakNdefCodec.ntag215CapacityBytes
    case .ntag216:
      return TagSpeakNdefCodec.ntag216CapacityBytes
    }
  }

  public var maxRecommendedTextBytes: Int {
    switch self {
    case .ntag213:
      return TagSpeakNdefCodec.ntag213MaxTextBytes
    case .ntag215:
      return TagSpeakNdefCodec.ntag215MaxTextBytes
    case .ntag216:
      return TagSpeakNdefCodec.ntag216MaxTextBytes
    }
  }

  public static func fromId(_ id: String?) -> StickerTypeProfile {
    guard let id else { return .ntag213 }
    return allCases.first { $0.rawValue.caseInsensitiveCompare(id) == .orderedSame } ?? .ntag213
  }
}

/// User-configurable accessibility and NFC preferences for TagSpeak.
public struct TagSpeakSettings: Sendable, Equatable {
  public var confirmBeforeOverwrite: Bool
  public var speakAfterWrite: Bool
  public var speechRate: Float
  /// iOS convenience setting: when enabled, opening TagSpeak immediately starts an NFC read scan
  /// so users launching the app via the Action Button, Dock, or Home Screen can immediately tap a sticker.
  public var autoScanOnLaunch: Bool
  public var defaultStickerType: StickerTypeProfile

  public init(
    confirmBeforeOverwrite: Bool = true,
    speakAfterWrite: Bool = true,
    speechRate: Float = 1.0,
    autoScanOnLaunch: Bool = false,
    defaultStickerType: StickerTypeProfile = .ntag213
  ) {
    self.confirmBeforeOverwrite = confirmBeforeOverwrite
    self.speakAfterWrite = speakAfterWrite
    self.speechRate = min(max(speechRate, 0.5), 2.0)
    self.autoScanOnLaunch = autoScanOnLaunch
    self.defaultStickerType = defaultStickerType
  }
}

public protocol TagSpeakPreferencesRepository: AnyObject {
  var currentSettings: TagSpeakSettings { get }
  func setConfirmBeforeOverwrite(_ enabled: Bool)
  func setSpeakAfterWrite(_ enabled: Bool)
  func setSpeechRate(_ rate: Float)
  func setAutoScanOnLaunch(_ enabled: Bool)
  func setDefaultStickerType(_ stickerType: StickerTypeProfile)
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

  public func setDefaultStickerType(_ stickerType: StickerTypeProfile) {
    currentSettings.defaultStickerType = stickerType
  }
}

public final class UserDefaultsTagSpeakRepository: TagSpeakPreferencesRepository {
  private enum Keys {
    static let confirmBeforeOverwrite = "tagspeak.confirm_before_overwrite"
    static let speakAfterWrite = "tagspeak.speak_after_write"
    static let speechRate = "tagspeak.speech_rate"
    static let autoScanOnLaunch = "tagspeak.auto_scan_on_launch"
    static let defaultStickerType = "tagspeak.default_sticker_type"
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
    let defaultStickerType =
      StickerTypeProfile.fromId(defaults.string(forKey: Keys.defaultStickerType))

    self.currentSettings = TagSpeakSettings(
      confirmBeforeOverwrite: confirmOverwrite,
      speakAfterWrite: speakAfterWrite,
      speechRate: rawRate,
      autoScanOnLaunch: autoScanOnLaunch,
      defaultStickerType: defaultStickerType
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

  public func setDefaultStickerType(_ stickerType: StickerTypeProfile) {
    defaults.set(stickerType.rawValue, forKey: Keys.defaultStickerType)
    currentSettings.defaultStickerType = stickerType
  }
}
