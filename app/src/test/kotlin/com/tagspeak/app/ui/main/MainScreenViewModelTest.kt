package com.tagspeak.app.ui.main

import com.tagspeak.app.data.InMemoryPreferencesRepository
import com.tagspeak.app.nfc.ParsedTagSpeakLabel
import com.tagspeak.app.speech.SpeechSpeaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  private lateinit var fakeSpeaker: RecordingSpeechSpeaker
  private lateinit var preferencesRepository: InMemoryPreferencesRepository
  private lateinit var viewModel: MainScreenViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    fakeSpeaker = RecordingSpeechSpeaker()
    preferencesRepository = InMemoryPreferencesRepository()
    viewModel = MainScreenViewModel(preferencesRepository, fakeSpeaker)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun tapLabeledStickerInDefaultMode_speaksLabelAndOpensInspectTab() = runTest {
    val collectJob = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }

    val scanned =
      ParsedTagSpeakLabel(
        text = "All-Purpose Flour",
        hasTagSpeakAar = true,
        usedBytes = 58,
        maxCapacityBytes = 137,
        isWritable = true,
      )
    viewModel.onHardwareTagDiscovered(tag = null, parsedFromIntent = scanned)

    val state = viewModel.uiState.value
    assertEquals(TagSpeakTab.INSPECT, state.selectedTab)
    assertEquals("All-Purpose Flour", state.lastScannedLabel?.text)
    assertEquals(listOf("All-Purpose Flour"), fakeSpeaker.spokenUtterances)

    collectJob.cancel()
  }

  @Test
  fun writeToBlankSticker_writesAndAnnouncesConfirmation() = runTest {
    val collectJob = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }

    viewModel.updateDraftLabelText("Black Pepper")
    viewModel.toggleArmWrite()
    assertEquals(NfcOperationMode.ARMED_TO_WRITE, viewModel.uiState.value.nfcMode)

    // Simulate touching a blank sticker
    viewModel.onHardwareTagDiscovered(tag = null, parsedFromIntent = null)

    val state = viewModel.uiState.value
    assertEquals(NfcOperationMode.READ_AND_INSPECT, state.nfcMode)
    assertEquals("Black Pepper", state.lastScannedLabel?.text)
    assertTrue(state.statusAnnouncement.contains("Black Pepper"))
    assertEquals(
      listOf("Label written to sticker: \"Black Pepper\"."),
      fakeSpeaker.spokenUtterances,
    )

    collectJob.cancel()
  }

  @Test
  fun writeToExistingLabeledSticker_promptsForOverwriteConfirmationThenWritesAfterConfirm() =
    runTest {
      val collectJob = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }

      viewModel.updateDraftLabelText("Garlic Powder")
      viewModel.toggleArmWrite()

      val existingSticker = ParsedTagSpeakLabel(text = "Onion Powder", hasTagSpeakAar = true)
      viewModel.onHardwareTagDiscovered(tag = null, parsedFromIntent = existingSticker)

      // Should pause and ask for confirmation
      val pausedState = viewModel.uiState.value
      assertEquals(
        NfcOperationMode.AWAITING_OVERWRITE_CONFIRMATION,
        pausedState.nfcMode,
      )
      assertNotNull(pausedState.pendingOverwritePrompt)
      assertEquals("Onion Powder", pausedState.pendingOverwritePrompt?.existingLabel)
      assertEquals("Garlic Powder", pausedState.pendingOverwritePrompt?.newLabel)

      // User activates Confirm Overwrite and taps the sticker again
      viewModel.confirmOverwriteAndArmWrite()
      assertEquals(NfcOperationMode.ARMED_TO_WRITE, viewModel.uiState.value.nfcMode)
      assertTrue(viewModel.uiState.value.overwriteConfirmedForNextTap)

      viewModel.onHardwareTagDiscovered(tag = null, parsedFromIntent = existingSticker)

      val finalState = viewModel.uiState.value
      assertEquals(NfcOperationMode.READ_AND_INSPECT, finalState.nfcMode)
      assertNull(finalState.pendingOverwritePrompt)
      assertEquals("Garlic Powder", finalState.lastScannedLabel?.text)

      collectJob.cancel()
    }

  @Test
  fun scanExistingStickerToEdit_loadsTextIntoEditorAndPreAuthorizesRewrite() = runTest {
    val collectJob = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }

    viewModel.toggleScanToEditMode()
    assertEquals(NfcOperationMode.ARMED_TO_LOAD_FOR_EDIT, viewModel.uiState.value.nfcMode)

    val existingSticker =
      ParsedTagSpeakLabel(text = "Vitamin D3 2000 IU", hasTagSpeakAar = true)
    viewModel.onHardwareTagDiscovered(tag = null, parsedFromIntent = existingSticker)

    val stateAfterScan = viewModel.uiState.value
    assertEquals(TagSpeakTab.WRITE, stateAfterScan.selectedTab)
    assertEquals("Vitamin D3 2000 IU", stateAfterScan.draftLabelText)
    assertEquals(NfcOperationMode.READ_AND_INSPECT, stateAfterScan.nfcMode)

    // Modify text and write back
    viewModel.updateDraftLabelText("Vitamin D3 5000 IU")
    viewModel.toggleArmWrite()

    // Disable confirmBeforeOverwrite to verify direct rewrite setting
    viewModel.setConfirmBeforeOverwrite(false)
    viewModel.onHardwareTagDiscovered(tag = null, parsedFromIntent = existingSticker)

    assertEquals("Vitamin D3 5000 IU", viewModel.uiState.value.lastScannedLabel?.text)

    collectJob.cancel()
  }
}

private class RecordingSpeechSpeaker : SpeechSpeaker {
  val spokenUtterances = mutableListOf<String>()

  override fun speak(
    text: String,
    speechRate: Float,
    onComplete: (() -> Unit)?,
  ) {
    spokenUtterances.add(text)
    onComplete?.invoke()
  }

  override fun stop() = Unit

  override fun shutdown() = Unit
}
