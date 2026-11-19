import Combine
import Foundation

public enum TagSpeakTab: String, Sendable, Equatable, CaseIterable {
  case write
  case inspect
  case settings
}

/// Identifies which button launched the iOS CoreNFC scanning sheet so VoiceOver focus
/// can be deterministically restored to that exact button when the sheet dismisses.
public enum NfcTriggerButton: String, Sendable, Equatable, Hashable {
  case writeTabReadButton
  case writeTabWriteButton
  case writeTabScanToEditButton
  case inspectTabScanButton
}

public enum NfcOperationMode: Sendable, Equatable {
  /// Default mode: scanning a sticker reads, announces, and inspects it.
  case readAndInspect
  /// Armed to write `draftLabelText` to the next sticker scanned.
  case armedToWrite
  /// Armed to read an existing sticker's label into the text editor for editing.
  case armedToLoadForEdit
  /// Waiting for user confirmation before overwriting an existing label on a sticker.
  case awaitingOverwriteConfirmation
}

public struct PendingOverwritePrompt: Sendable, Equatable {
  public let existingLabel: String
  public let newLabel: String

  public init(existingLabel: String, newLabel: String) {
    self.existingLabel = existingLabel
    self.newLabel = newLabel
  }
}

/// Protocol allowing the ViewModel to start or cancel the iOS system `NFCNDEFReaderSession`
/// when the user activates "Read Sticker Aloud", "Write Label to Sticker", or "Scan Existing Sticker to Edit".
@MainActor
public protocol NfcSessionDriver: AnyObject {
  func beginReadSession(alertMessage: String)
  func beginWriteSession(labelText: String, alertMessage: String)
  func cancelActiveSession()
}

public struct TagSpeakUiState: Sendable, Equatable {
  public var selectedTab: TagSpeakTab = .write
  public var draftLabelText: String = ""
  public var nfcMode: NfcOperationMode = .readAndInspect
  public var overwriteConfirmedForNextTap: Bool = false
  public var pendingOverwritePrompt: PendingOverwritePrompt? = nil
  public var lastScannedLabel: ParsedTagSpeakLabel? = nil
  public var statusAnnouncement: String =
    "Ready. Enter a label to write, or activate Read Sticker Aloud to scan a TagSpeak sticker."
  public var settings: TagSpeakSettings = TagSpeakSettings()
  public var lastTriggerButton: NfcTriggerButton? = nil
  public var focusRestoreGeneration: Int = 0
  public var focusRestoreDelaySeconds: Double = 1.9

  public init(
    selectedTab: TagSpeakTab = .write,
    draftLabelText: String = "",
    nfcMode: NfcOperationMode = .readAndInspect,
    overwriteConfirmedForNextTap: Bool = false,
    pendingOverwritePrompt: PendingOverwritePrompt? = nil,
    lastScannedLabel: ParsedTagSpeakLabel? = nil,
    statusAnnouncement: String =
      "Ready. Enter a label to write, or activate Read Sticker Aloud to scan a TagSpeak sticker.",
    settings: TagSpeakSettings = TagSpeakSettings(),
    lastTriggerButton: NfcTriggerButton? = nil,
    focusRestoreGeneration: Int = 0,
    focusRestoreDelaySeconds: Double = 1.9
  ) {
    self.selectedTab = selectedTab
    self.draftLabelText = draftLabelText
    self.nfcMode = nfcMode
    self.overwriteConfirmedForNextTap = overwriteConfirmedForNextTap
    self.pendingOverwritePrompt = pendingOverwritePrompt
    self.lastScannedLabel = lastScannedLabel
    self.statusAnnouncement = statusAnnouncement
    self.settings = settings
    self.lastTriggerButton = lastTriggerButton
    self.focusRestoreGeneration = focusRestoreGeneration
    self.focusRestoreDelaySeconds = focusRestoreDelaySeconds
  }

  public var draftCharacterCount: Int {
    draftLabelText.trimmingCharacters(in: .whitespacesAndNewlines).count
  }

  public var draftTextUtf8Bytes: Int {
    draftLabelText.trimmingCharacters(in: .whitespacesAndNewlines).utf8.count
  }

  public var maxRecommendedTextBytes: Int {
    settings.defaultStickerType.maxRecommendedTextBytes
  }

  public var isDraftOverRecommendedSize: Bool {
    draftTextUtf8Bytes > maxRecommendedTextBytes
  }

  public var capacitySummaryText: String {
    let profileLabel = settings.defaultStickerType.shortLabel
    if draftTextUtf8Bytes != draftCharacterCount {
      return
        "\(draftCharacterCount) of \(maxRecommendedTextBytes) characters (\(draftTextUtf8Bytes) bytes) used for \(profileLabel)"
    } else {
      return
        "\(draftTextUtf8Bytes) of \(maxRecommendedTextBytes) characters used for \(profileLabel)"
    }
  }

  public var canArmWrite: Bool {
    !draftLabelText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
  }
}

@MainActor
public final class MainScreenViewModel: ObservableObject {
  @Published public private(set) var uiState: TagSpeakUiState

  private let preferencesRepository: TagSpeakPreferencesRepository
  private let speechSpeaker: SpeechSpeaker?
  public weak var nfcSessionDriver: NfcSessionDriver?

  public init(
    preferencesRepository: TagSpeakPreferencesRepository = InMemoryPreferencesRepository(),
    speechSpeaker: SpeechSpeaker? = nil,
    nfcSessionDriver: NfcSessionDriver? = nil
  ) {
    self.preferencesRepository = preferencesRepository
    self.speechSpeaker = speechSpeaker
    self.nfcSessionDriver = nfcSessionDriver
    self.uiState = TagSpeakUiState(settings: preferencesRepository.currentSettings)
  }

  public func selectTab(_ tab: TagSpeakTab) {
    uiState.selectedTab = tab
  }

  public func updateDraftLabelText(_ newText: String) {
    let trimmed = newText.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.isEmpty && uiState.nfcMode == .armedToWrite {
      uiState.nfcMode = .readAndInspect
      nfcSessionDriver?.cancelActiveSession()
    }
    uiState.draftLabelText = newText
    uiState.overwriteConfirmedForNextTap = false
    uiState.pendingOverwritePrompt = nil
  }

  public func clearDraftLabel() {
    uiState.draftLabelText = ""
    uiState.nfcMode = .readAndInspect
    uiState.overwriteConfirmedForNextTap = false
    uiState.pendingOverwritePrompt = nil
    uiState.statusAnnouncement = "Label text cleared."
    nfcSessionDriver?.cancelActiveSession()
  }

  /// Starts an immediate iOS CoreNFC read session (triggered from the UI button, Action Button AppIntent, or auto-scan on launch).
  public func startReadStickerScan(triggeredFrom button: NfcTriggerButton = .writeTabReadButton) {
    uiState.nfcMode = .readAndInspect
    uiState.pendingOverwritePrompt = nil
    uiState.lastTriggerButton = button
    let alertMsg = "Hold the top of your iPhone near a TagSpeak NFC sticker to read its label."
    uiState.statusAnnouncement = alertMsg
    nfcSessionDriver?.beginReadSession(alertMessage: alertMsg)
  }

  /// Invoked when the iOS system CoreNFC sheet is cancelled/invalidated without completing a tag read/write.
  public func onNfcSessionDismissed() {
    if uiState.nfcMode == .armedToWrite || uiState.nfcMode == .armedToLoadForEdit {
      uiState.nfcMode = .readAndInspect
    }
    uiState.focusRestoreDelaySeconds = 0.65
    uiState.focusRestoreGeneration += 1
  }

  /// Computes a safe delay (in seconds) before restoring VoiceOver focus to the launching button:
  /// - Always waits at least 1.95s so Apple's CoreNFC checkmark modal sheet has completely dismissed.
  /// - Scales up for longer spoken labels so restoring focus to the button never cuts off the spoken label.
  private func scheduleFocusRestore(afterSpeaking spokenText: String) {
    let estimatedSpeechSeconds = 0.7 + (Double(spokenText.count) * 0.065)
    uiState.focusRestoreDelaySeconds = min(max(1.95, estimatedSpeechSeconds), 6.5)
    uiState.focusRestoreGeneration += 1
  }

  /// Invoked when the app enters the foreground if `autoScanOnLaunch` is enabled.
  public func handleAppDidBecomeActive(isTriggeredByShortcut: Bool = false) {
    if isTriggeredByShortcut || uiState.settings.autoScanOnLaunch {
      let targetButton: NfcTriggerButton =
        (uiState.selectedTab == .inspect) ? .inspectTabScanButton : .writeTabReadButton
      startReadStickerScan(triggeredFrom: targetButton)
    }
  }

  public func toggleArmWrite() {
    if uiState.nfcMode == .armedToWrite {
      uiState.nfcMode = .readAndInspect
      uiState.overwriteConfirmedForNextTap = false
      uiState.pendingOverwritePrompt = nil
      uiState.statusAnnouncement = "Canceled waiting for sticker."
      nfcSessionDriver?.cancelActiveSession()
      return
    }

    let cleanLabel = uiState.draftLabelText.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !cleanLabel.isEmpty else {
      uiState.statusAnnouncement = "Please enter label text before writing to a sticker."
      return
    }

    let alertMsg =
      "Ready to write label: \"\(cleanLabel)\". Hold the top of your iPhone near an NFC sticker."
    uiState.selectedTab = .write
    uiState.nfcMode = .armedToWrite
    uiState.lastTriggerButton = .writeTabWriteButton
    uiState.overwriteConfirmedForNextTap = false
    uiState.pendingOverwritePrompt = nil
    uiState.statusAnnouncement = alertMsg
    nfcSessionDriver?.beginWriteSession(labelText: cleanLabel, alertMessage: alertMsg)
  }

  public func toggleScanToEditMode() {
    if uiState.nfcMode == .armedToLoadForEdit {
      uiState.nfcMode = .readAndInspect
      uiState.statusAnnouncement = "Canceled scan to edit."
      nfcSessionDriver?.cancelActiveSession()
    } else {
      let alertMsg =
        "Scan to edit ready. Hold the top of your iPhone near an existing TagSpeak sticker to load its label into the editor."
      uiState.selectedTab = .write
      uiState.nfcMode = .armedToLoadForEdit
      uiState.lastTriggerButton = .writeTabScanToEditButton
      uiState.overwriteConfirmedForNextTap = false
      uiState.pendingOverwritePrompt = nil
      uiState.statusAnnouncement = alertMsg
      nfcSessionDriver?.beginReadSession(alertMessage: alertMsg)
    }
  }

  public func confirmOverwriteAndArmWrite() {
    guard let prompt = uiState.pendingOverwritePrompt else { return }
    let message =
      "Overwrite confirmed. Hold your iPhone near the sticker again to replace \"\(prompt.existingLabel)\" with \"\(prompt.newLabel)\"."
    uiState.nfcMode = .armedToWrite
    uiState.lastTriggerButton = .writeTabWriteButton
    uiState.overwriteConfirmedForNextTap = true
    uiState.pendingOverwritePrompt = nil
    uiState.statusAnnouncement = message
    speakAloud(message)
    nfcSessionDriver?.beginWriteSession(labelText: prompt.newLabel, alertMessage: message)
  }

  public func cancelOverwritePrompt() {
    uiState.nfcMode = .readAndInspect
    uiState.overwriteConfirmedForNextTap = false
    uiState.pendingOverwritePrompt = nil
    uiState.statusAnnouncement = "Write canceled. Existing sticker label was not changed."
    nfcSessionDriver?.cancelActiveSession()
  }

  public func editLastScannedLabel() {
    guard let scanned = uiState.lastScannedLabel else { return }
    let text = scanned.text.trimmingCharacters(in: .whitespacesAndNewlines)
    uiState.selectedTab = .write
    uiState.draftLabelText = text
    uiState.nfcMode = .readAndInspect
    uiState.overwriteConfirmedForNextTap = true
    uiState.pendingOverwritePrompt = nil
    uiState.statusAnnouncement =
      "Loaded \"\(text)\" into editor. Modify the text and activate Write Label to Sticker."
  }

  public func speakLastScannedLabelAgain() {
    guard
      let text = uiState.lastScannedLabel?.text.trimmingCharacters(in: .whitespacesAndNewlines),
      !text.isEmpty
    else { return }
    speakAloud(text)
    uiState.statusAnnouncement = "Speaking label: \"\(text)\""
  }

  public func testSpeechOutput() {
    let sample = "TagSpeak speech output is active and ready."
    speakAloud(sample)
    uiState.statusAnnouncement = sample
  }

  public func setConfirmBeforeOverwrite(_ enabled: Bool) {
    preferencesRepository.setConfirmBeforeOverwrite(enabled)
    uiState.settings = preferencesRepository.currentSettings
    let stateText = enabled ? "enabled" : "disabled"
    uiState.statusAnnouncement = "Confirm before overwriting existing labels \(stateText)."
  }

  public func setSpeakAfterWrite(_ enabled: Bool) {
    preferencesRepository.setSpeakAfterWrite(enabled)
    uiState.settings = preferencesRepository.currentSettings
    let stateText = enabled ? "enabled" : "disabled"
    uiState.statusAnnouncement = "Speak confirmation after writing \(stateText)."
  }

  public func setSpeechRate(_ rate: Float) {
    preferencesRepository.setSpeechRate(rate)
    uiState.settings = preferencesRepository.currentSettings
    let formatted = String(format: "%.2fx", uiState.settings.speechRate)
    uiState.statusAnnouncement = "Speech speed set to \(formatted)."
  }

  public func setAutoScanOnLaunch(_ enabled: Bool) {
    preferencesRepository.setAutoScanOnLaunch(enabled)
    uiState.settings = preferencesRepository.currentSettings
    let stateText = enabled ? "enabled" : "disabled"
    uiState.statusAnnouncement = "Automatically start scanning on app launch \(stateText)."
  }

  public func setDefaultStickerType(_ stickerType: StickerTypeProfile) {
    preferencesRepository.setDefaultStickerType(stickerType)
    uiState.settings = preferencesRepository.currentSettings
    uiState.statusAnnouncement = "Default sticker type set to \(stickerType.displayName)."
  }

  /// Handles a scanned sticker in the current operation mode (or simulated tag tap in tests).
  public func onTagDiscovered(parsedLabel: ParsedTagSpeakLabel?) {
    switch uiState.nfcMode {
    case .armedToWrite, .awaitingOverwriteConfirmation:
      let outcome = simulateOrEvaluateWriteOutcome(
        existingLabel: parsedLabel?.text,
        newLabel: uiState.draftLabelText,
        maxCapacityBytes: parsedLabel?.maxCapacityBytes
          ?? uiState.settings.defaultStickerType.ndefCapacityBytes,
        isWritable: parsedLabel?.isWritable ?? true
      )
      handleWriteOutcome(outcome)

    case .armedToLoadForEdit:
      let existingText =
        parsedLabel?.text.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
      if existingText.isEmpty {
        let message = "This sticker does not contain an existing text label to edit."
        uiState.nfcMode = .readAndInspect
        uiState.statusAnnouncement = message
        scheduleFocusRestore(afterSpeaking: message)
        speakAloud(message)
      } else {
        let message =
          "Loaded existing label for editing: \"\(existingText)\". Modify text and activate Write Label to Sticker."
        uiState.selectedTab = .write
        uiState.draftLabelText = existingText
        uiState.lastScannedLabel = parsedLabel
        uiState.nfcMode = .readAndInspect
        uiState.overwriteConfirmedForNextTap = true
        uiState.pendingOverwritePrompt = nil
        uiState.statusAnnouncement = message
        scheduleFocusRestore(afterSpeaking: message)
        speakAloud(message)
      }

    case .readAndInspect:
      if let parsedLabel, parsedLabel.hasExistingLabel {
        let cleanText = parsedLabel.text.trimmingCharacters(in: .whitespacesAndNewlines)
        // Only switch to the Inspect tab if the scan was not explicitly launched from the Write tab's Read button
        if uiState.lastTriggerButton != .writeTabReadButton {
          uiState.selectedTab = .inspect
        }
        uiState.lastScannedLabel = parsedLabel
        uiState.statusAnnouncement = "Sticker label: \"\(cleanText)\""
        scheduleFocusRestore(afterSpeaking: cleanText)
        speakAloud(cleanText)
      } else {
        let msg =
          "Blank or unformatted NFC sticker detected. Switch to Write Label to record a label."
        uiState.statusAnnouncement = msg
        scheduleFocusRestore(afterSpeaking: msg)
        speakAloud(msg)
      }
    }
  }

  /// Evaluates overwrite rules and capacity before writing to a physical or simulated tag.
  public func simulateOrEvaluateWriteOutcome(
    existingLabel: String?,
    newLabel: String,
    maxCapacityBytes: Int = TagSpeakNdefCodec.ntag213CapacityBytes,
    isWritable: Bool = true
  ) -> TagWriteOutcome {
    let cleanNew = newLabel.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !cleanNew.isEmpty else {
      return .failure(reason: .emptyLabel)
    }

    guard isWritable else {
      return .failure(reason: .tagReadOnly)
    }

    if let confirmationNeeded = TagSpeakNdefCodec.evaluateOverwriteProtection(
      existingLabel: existingLabel,
      pendingNewLabel: cleanNew,
      confirmBeforeOverwrite: uiState.settings.confirmBeforeOverwrite,
      overwriteAlreadyConfirmed: uiState.overwriteConfirmedForNextTap
    ) {
      return confirmationNeeded
    }

    let estimatedBytes = TagSpeakNdefCodec.estimateNdefMessageBytes(text: cleanNew)
    if estimatedBytes > maxCapacityBytes {
      let maxTextChars = TagSpeakNdefCodec.maxTextBytesForTagCapacity(maxCapacityBytes)
      return .failure(
        reason: .labelTooLarge,
        details:
          "Label is \(cleanNew.count) characters (\(estimatedBytes) total NDEF bytes), exceeding this sticker's \(maxTextChars)-character (\(maxCapacityBytes)-byte) capacity."
      )
    }

    let cleanExisting = existingLabel?.trimmingCharacters(in: .whitespacesAndNewlines)
    return .success(
      writtenLabel: cleanNew,
      previousLabel: (cleanExisting?.isEmpty == false) ? cleanExisting : nil,
      bytesWritten: estimatedBytes,
      maxCapacityBytes: maxCapacityBytes
    )
  }

  public func handleWriteOutcome(_ outcome: TagWriteOutcome) {
    switch outcome {
    case .success(let writtenLabel, _, let bytesWritten, let maxCapacityBytes):
      let updatedScanned = ParsedTagSpeakLabel(
        text: writtenLabel,
        hasTagSpeakAar: true,
        usedBytes: bytesWritten,
        maxCapacityBytes: maxCapacityBytes,
        isWritable: true
      )
      let announcement = "Label written to sticker: \"\(writtenLabel)\"."
      uiState.nfcMode = .readAndInspect
      uiState.overwriteConfirmedForNextTap = false
      uiState.pendingOverwritePrompt = nil
      uiState.lastScannedLabel = updatedScanned
      uiState.statusAnnouncement = announcement
      scheduleFocusRestore(afterSpeaking: uiState.settings.speakAfterWrite ? announcement : "")
      if uiState.settings.speakAfterWrite {
        speakAloud(announcement)
      }

    case .overwriteConfirmationRequired(let existingLabel, let pendingNewLabel):
      let warningMessage =
        "Sticker already has label: \"\(existingLabel)\". Activate Confirm Overwrite to replace it with \"\(pendingNewLabel)\"."
      uiState.nfcMode = .awaitingOverwriteConfirmation
      uiState.pendingOverwritePrompt = PendingOverwritePrompt(
        existingLabel: existingLabel,
        newLabel: pendingNewLabel
      )
      uiState.statusAnnouncement = warningMessage
      scheduleFocusRestore(afterSpeaking: warningMessage)
      speakAloud(warningMessage)

    case .failure(let reason, let details):
      let errorMessage: String
      switch reason {
      case .emptyLabel:
        errorMessage = "Cannot write an empty label. Please enter label text first."
      case .tagReadOnly:
        errorMessage = "Write failed: This NFC sticker is locked as read-only."
      case .labelTooLarge:
        errorMessage =
          "Write failed: \(details ?? "The label text is too long for this sticker's memory capacity.")"
      case .unsupportedTagFormat:
        errorMessage = "Write failed: This sticker does not support NDEF formatting."
      case .ioError:
        errorMessage =
          "Write failed: Sticker moved away too quickly. Hold your iPhone steady against the sticker and try again."
      }
      uiState.statusAnnouncement = errorMessage
      scheduleFocusRestore(afterSpeaking: errorMessage)
      speakAloud(errorMessage)
    }
  }

  private func speakAloud(_ text: String) {
    let rate = uiState.settings.speechRate
    speechSpeaker?.speak(text: text, speechRate: rate, onComplete: nil)
  }
}
