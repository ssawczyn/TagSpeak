import Foundation
#if canImport(XCTest)
import XCTest
@testable import TagSpeakCore
#endif

@MainActor
final class MainScreenViewModelTests: XCTestCase {
  private struct TestHarness {
    let fakeSpeaker: RecordingSpeechSpeaker
    let fakeNfcDriver: FakeNfcSessionDriver
    let preferencesRepository: InMemoryPreferencesRepository
    let viewModel: MainScreenViewModel
  }

  private func makeHarness() -> TestHarness {
    let speaker = RecordingSpeechSpeaker()
    let driver = FakeNfcSessionDriver()
    let prefs = InMemoryPreferencesRepository()
    let vm = MainScreenViewModel(
      preferencesRepository: prefs,
      speechSpeaker: speaker,
      nfcSessionDriver: driver
    )
    return TestHarness(
      fakeSpeaker: speaker,
      fakeNfcDriver: driver,
      preferencesRepository: prefs,
      viewModel: vm
    )
  }

  func testTapLabeledStickerInDefaultMode_speaksLabelAndOpensInspectTab() {
    let h = makeHarness()
    let scanned = ParsedTagSpeakLabel(
      text: "All-Purpose Flour",
      hasTagSpeakAar: true,
      usedBytes: 58,
      maxCapacityBytes: 137,
      isWritable: true
    )

    h.viewModel.onTagDiscovered(parsedLabel: scanned)

    let state = h.viewModel.uiState
    XCTAssertEqual(state.selectedTab, .inspect)
    XCTAssertEqual(state.lastScannedLabel?.text, "All-Purpose Flour")
    XCTAssertEqual(h.fakeSpeaker.spokenUtterances, ["All-Purpose Flour"])
  }

  func testWriteToBlankSticker_writesAndAnnouncesConfirmation() {
    let h = makeHarness()
    h.viewModel.updateDraftLabelText("Black Pepper")
    h.viewModel.toggleArmWrite()
    XCTAssertEqual(h.viewModel.uiState.nfcMode, .armedToWrite)
    XCTAssertEqual(h.fakeNfcDriver.writeSessionStartedCount, 1)

    // Simulate touching a blank sticker
    h.viewModel.onTagDiscovered(parsedLabel: nil)

    let state = h.viewModel.uiState
    XCTAssertEqual(state.nfcMode, .readAndInspect)
    XCTAssertEqual(state.lastScannedLabel?.text, "Black Pepper")
    XCTAssertTrue(state.statusAnnouncement.contains("Black Pepper"))
    XCTAssertEqual(
      h.fakeSpeaker.spokenUtterances,
      ["Label written to sticker: \"Black Pepper\"."]
    )
  }

  func testWriteToExistingLabeledSticker_promptsForOverwriteConfirmationThenWritesAfterConfirm() {
    let h = makeHarness()
    h.viewModel.updateDraftLabelText("Garlic Powder")
    h.viewModel.toggleArmWrite()

    let existingSticker = ParsedTagSpeakLabel(text: "Onion Powder", hasTagSpeakAar: true)
    h.viewModel.onTagDiscovered(parsedLabel: existingSticker)

    // Should pause and ask for confirmation
    let pausedState = h.viewModel.uiState
    XCTAssertEqual(pausedState.nfcMode, .awaitingOverwriteConfirmation)
    XCTAssertNotNil(pausedState.pendingOverwritePrompt)
    XCTAssertEqual(pausedState.pendingOverwritePrompt?.existingLabel, "Onion Powder")
    XCTAssertEqual(pausedState.pendingOverwritePrompt?.newLabel, "Garlic Powder")

    // User activates Confirm Overwrite and taps the sticker again
    h.viewModel.confirmOverwriteAndArmWrite()
    XCTAssertEqual(h.viewModel.uiState.nfcMode, .armedToWrite)
    XCTAssertTrue(h.viewModel.uiState.overwriteConfirmedForNextTap)

    h.viewModel.onTagDiscovered(parsedLabel: existingSticker)

    let finalState = h.viewModel.uiState
    XCTAssertEqual(finalState.nfcMode, .readAndInspect)
    XCTAssertNil(finalState.pendingOverwritePrompt)
    XCTAssertEqual(finalState.lastScannedLabel?.text, "Garlic Powder")
  }

  func testScanExistingStickerToEdit_loadsTextIntoEditorAndPreAuthorizesRewrite() {
    let h = makeHarness()
    h.viewModel.toggleScanToEditMode()
    XCTAssertEqual(h.viewModel.uiState.nfcMode, .armedToLoadForEdit)
    XCTAssertEqual(h.fakeNfcDriver.readSessionStartedCount, 1)

    let existingSticker = ParsedTagSpeakLabel(text: "Vitamin D3 2000 IU", hasTagSpeakAar: true)
    h.viewModel.onTagDiscovered(parsedLabel: existingSticker)

    let stateAfterScan = h.viewModel.uiState
    XCTAssertEqual(stateAfterScan.selectedTab, .write)
    XCTAssertEqual(stateAfterScan.draftLabelText, "Vitamin D3 2000 IU")
    XCTAssertEqual(stateAfterScan.nfcMode, .readAndInspect)

    // Modify text and write back
    h.viewModel.updateDraftLabelText("Vitamin D3 5000 IU")
    h.viewModel.toggleArmWrite()

    // Disable confirmBeforeOverwrite to verify direct rewrite setting
    h.viewModel.setConfirmBeforeOverwrite(false)
    h.viewModel.onTagDiscovered(parsedLabel: existingSticker)

    XCTAssertEqual(h.viewModel.uiState.lastScannedLabel?.text, "Vitamin D3 5000 IU")
  }

  func testAutoScanOnLaunch_startsReadSessionWhenEnabledOrTriggeredByShortcut() {
    let h = makeHarness()
    h.viewModel.handleAppDidBecomeActive(isTriggeredByShortcut: false)
    XCTAssertEqual(h.fakeNfcDriver.readSessionStartedCount, 0)

    h.viewModel.setAutoScanOnLaunch(true)
    h.viewModel.handleAppDidBecomeActive(isTriggeredByShortcut: false)
    XCTAssertEqual(h.fakeNfcDriver.readSessionStartedCount, 1)

    h.viewModel.setAutoScanOnLaunch(false)
    h.viewModel.handleAppDidBecomeActive(isTriggeredByShortcut: true)
    XCTAssertEqual(h.fakeNfcDriver.readSessionStartedCount, 2)
  }
}

private final class RecordingSpeechSpeaker: SpeechSpeaker {
  var spokenUtterances: [String] = []

  func speak(text: String, speechRate: Float, onComplete: (() -> Void)?) {
    spokenUtterances.append(text)
    onComplete?()
  }

  func stop() {}
}

@MainActor
private final class FakeNfcSessionDriver: NfcSessionDriver {
  var readSessionStartedCount = 0
  var writeSessionStartedCount = 0
  var cancelSessionCount = 0

  func beginReadSession(alertMessage: String) {
    readSessionStartedCount += 1
  }

  func beginWriteSession(labelText: String, alertMessage: String) {
    writeSessionStartedCount += 1
  }

  func cancelActiveSession() {
    cancelSessionCount += 1
  }
}
