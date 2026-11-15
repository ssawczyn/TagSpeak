package com.tagspeak.app.ui.main

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tagspeak.app.data.InMemoryPreferencesRepository
import com.tagspeak.app.data.TagSpeakPreferencesRepository
import com.tagspeak.app.data.TagSpeakSettings
import com.tagspeak.app.nfc.NfcTagOperations
import com.tagspeak.app.nfc.ParsedTagSpeakLabel
import com.tagspeak.app.nfc.TagSpeakNdefCodec
import com.tagspeak.app.nfc.TagWriteOutcome
import com.tagspeak.app.nfc.WriteFailureReason
import com.tagspeak.app.speech.SpeechSpeaker
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class TagSpeakTab {
  WRITE,
  INSPECT,
  SETTINGS,
}

enum class NfcOperationMode {
  /** Default mode: touching a sticker reads, announces, and inspects it. */
  READ_AND_INSPECT,

  /** Armed to write [TagSpeakUiState.draftLabelText] to the next sticker touched. */
  ARMED_TO_WRITE,

  /** Armed to read an existing sticker's label into the text editor for editing. */
  ARMED_TO_LOAD_FOR_EDIT,

  /** Waiting for user confirmation before overwriting an existing label on a sticker. */
  AWAITING_OVERWRITE_CONFIRMATION,
}

data class PendingOverwritePrompt(
  val existingLabel: String,
  val newLabel: String,
)

data class TagSpeakUiState(
  val selectedTab: TagSpeakTab = TagSpeakTab.WRITE,
  val draftLabelText: String = "",
  val nfcMode: NfcOperationMode = NfcOperationMode.READ_AND_INSPECT,
  val overwriteConfirmedForNextTap: Boolean = false,
  val pendingOverwritePrompt: PendingOverwritePrompt? = null,
  val lastScannedLabel: ParsedTagSpeakLabel? = null,
  val statusAnnouncement: String =
    "Ready. Enter a label to write, or touch any TagSpeak sticker to read it.",
  val settings: TagSpeakSettings = TagSpeakSettings(),
) {
  val draftTextUtf8Bytes: Int
    get() = draftLabelText.trim().toByteArray(StandardCharsets.UTF_8).size

  val maxRecommendedTextBytes: Int
    get() = TagSpeakNdefCodec.NTAG213_MAX_TEXT_BYTES

  val isDraftOverRecommendedSize: Boolean
    get() = draftTextUtf8Bytes > maxRecommendedTextBytes

  val canArmWrite: Boolean
    get() = draftLabelText.isNotBlank()
}

class MainScreenViewModel(
  private val preferencesRepository: TagSpeakPreferencesRepository =
    InMemoryPreferencesRepository(),
  private val speechSpeaker: SpeechSpeaker? = null,
) : ViewModel() {

  private data class InternalState(
    val selectedTab: TagSpeakTab = TagSpeakTab.WRITE,
    val draftLabelText: String = "",
    val nfcMode: NfcOperationMode = NfcOperationMode.READ_AND_INSPECT,
    val overwriteConfirmedForNextTap: Boolean = false,
    val pendingOverwritePrompt: PendingOverwritePrompt? = null,
    val lastScannedLabel: ParsedTagSpeakLabel? = null,
    val statusAnnouncement: String =
      "Ready. Enter a label to write, or touch any TagSpeak sticker to read it.",
  )

  private val internalState = MutableStateFlow(InternalState())

  val uiState: StateFlow<TagSpeakUiState> =
    combine(internalState, preferencesRepository.settings) { state, prefs ->
        TagSpeakUiState(
          selectedTab = state.selectedTab,
          draftLabelText = state.draftLabelText,
          nfcMode = state.nfcMode,
          overwriteConfirmedForNextTap = state.overwriteConfirmedForNextTap,
          pendingOverwritePrompt = state.pendingOverwritePrompt,
          lastScannedLabel = state.lastScannedLabel,
          statusAnnouncement = state.statusAnnouncement,
          settings = prefs,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TagSpeakUiState(settings = preferencesRepository.settings.value),
      )

  fun selectTab(tab: TagSpeakTab) {
    internalState.update { current ->
      current.copy(selectedTab = tab)
    }
  }

  fun updateDraftLabelText(newText: String) {
    internalState.update { current ->
      val nextMode =
        if (newText.isBlank() && current.nfcMode == NfcOperationMode.ARMED_TO_WRITE) {
          NfcOperationMode.READ_AND_INSPECT
        } else {
          current.nfcMode
        }
      current.copy(
        draftLabelText = newText,
        nfcMode = nextMode,
        overwriteConfirmedForNextTap = false,
        pendingOverwritePrompt = null,
      )
    }
  }

  fun clearDraftLabel() {
    internalState.update { current ->
      current.copy(
        draftLabelText = "",
        nfcMode = NfcOperationMode.READ_AND_INSPECT,
        overwriteConfirmedForNextTap = false,
        pendingOverwritePrompt = null,
        statusAnnouncement = "Label text cleared.",
      )
    }
  }

  fun toggleArmWrite() {
    val current = internalState.value
    if (current.nfcMode == NfcOperationMode.ARMED_TO_WRITE) {
      internalState.update {
        it.copy(
          nfcMode = NfcOperationMode.READ_AND_INSPECT,
          overwriteConfirmedForNextTap = false,
          pendingOverwritePrompt = null,
          statusAnnouncement = "Canceled waiting for sticker.",
        )
      }
      return
    }

    val cleanLabel = current.draftLabelText.trim()
    if (cleanLabel.isEmpty()) {
      internalState.update {
        it.copy(statusAnnouncement = "Please enter label text before writing to a sticker.")
      }
      return
    }

    internalState.update {
      it.copy(
        selectedTab = TagSpeakTab.WRITE,
        nfcMode = NfcOperationMode.ARMED_TO_WRITE,
        overwriteConfirmedForNextTap = false,
        pendingOverwritePrompt = null,
        statusAnnouncement =
          "Ready to write label: \"$cleanLabel\". Touch the back of your phone to an NFC sticker.",
      )
    }
  }

  fun toggleScanToEditMode() {
    val current = internalState.value
    if (current.nfcMode == NfcOperationMode.ARMED_TO_LOAD_FOR_EDIT) {
      internalState.update {
        it.copy(
          nfcMode = NfcOperationMode.READ_AND_INSPECT,
          statusAnnouncement = "Canceled scan to edit.",
        )
      }
    } else {
      internalState.update {
        it.copy(
          selectedTab = TagSpeakTab.WRITE,
          nfcMode = NfcOperationMode.ARMED_TO_LOAD_FOR_EDIT,
          overwriteConfirmedForNextTap = false,
          pendingOverwritePrompt = null,
          statusAnnouncement =
            "Scan to edit ready. Touch an existing TagSpeak sticker to load its label into the editor.",
        )
      }
    }
  }

  fun confirmOverwriteAndArmWrite() {
    val current = internalState.value
    val prompt = current.pendingOverwritePrompt ?: return
    val message =
      "Overwrite confirmed. Touch the sticker again to replace \"${prompt.existingLabel}\" with \"${prompt.newLabel}\"."
    internalState.update {
      it.copy(
        nfcMode = NfcOperationMode.ARMED_TO_WRITE,
        overwriteConfirmedForNextTap = true,
        pendingOverwritePrompt = null,
        statusAnnouncement = message,
      )
    }
    speakAloud(message)
  }

  fun cancelOverwritePrompt() {
    internalState.update {
      it.copy(
        nfcMode = NfcOperationMode.READ_AND_INSPECT,
        overwriteConfirmedForNextTap = false,
        pendingOverwritePrompt = null,
        statusAnnouncement = "Write canceled. Existing sticker label was not changed.",
      )
    }
  }

  fun editLastScannedLabel() {
    val scanned = internalState.value.lastScannedLabel ?: return
    val text = scanned.text.trim()
    internalState.update {
      it.copy(
        selectedTab = TagSpeakTab.WRITE,
        draftLabelText = text,
        nfcMode = NfcOperationMode.READ_AND_INSPECT,
        overwriteConfirmedForNextTap = true,
        pendingOverwritePrompt = null,
        statusAnnouncement =
          "Loaded \"$text\" into editor. Modify the text and activate Write Label to Sticker.",
      )
    }
  }

  fun speakLastScannedLabelAgain() {
    val text = internalState.value.lastScannedLabel?.text?.trim().orEmpty()
    if (text.isNotEmpty()) {
      speakAloud(text)
      internalState.update {
        it.copy(statusAnnouncement = "Speaking label: \"$text\"")
      }
    }
  }

  fun testSpeechOutput() {
    val sample = "TagSpeak speech output is active and ready."
    speakAloud(sample)
    internalState.update {
      it.copy(statusAnnouncement = sample)
    }
  }

  fun setConfirmBeforeOverwrite(enabled: Boolean) {
    preferencesRepository.setConfirmBeforeOverwrite(enabled)
    val stateText = if (enabled) "enabled" else "disabled"
    internalState.update {
      it.copy(statusAnnouncement = "Confirm before overwriting existing labels $stateText.")
    }
  }

  fun setSpeakAfterWrite(enabled: Boolean) {
    preferencesRepository.setSpeakAfterWrite(enabled)
    val stateText = if (enabled) "enabled" else "disabled"
    internalState.update {
      it.copy(statusAnnouncement = "Speak confirmation after writing $stateText.")
    }
  }

  fun setSpeechRate(rate: Float) {
    preferencesRepository.setSpeechRate(rate)
    val formatted = String.format(java.util.Locale.US, "%.2fx", rate)
    internalState.update {
      it.copy(statusAnnouncement = "Speech speed set to $formatted.")
    }
  }

  /**
   * Handles a hardware NFC [Tag] and optional pre-parsed [ParsedTagSpeakLabel] when the app is
   * in the foreground.
   */
  fun onHardwareTagDiscovered(
    tag: Tag?,
    parsedFromIntent: ParsedTagSpeakLabel?,
  ) {
    val current = internalState.value
    val prefs = preferencesRepository.settings.value

    when (current.nfcMode) {
      NfcOperationMode.ARMED_TO_WRITE,
      NfcOperationMode.AWAITING_OVERWRITE_CONFIRMATION -> {
        if (tag != null) {
          val outcome =
            NfcTagOperations.writeLabelToTag(
              tag = tag,
              labelText = current.draftLabelText,
              confirmBeforeOverwrite = prefs.confirmBeforeOverwrite,
              overwriteAlreadyConfirmed = current.overwriteConfirmedForNextTap,
            )
          handleWriteOutcome(outcome)
        } else {
          // Simulated write (no physical Tag parcelable attached)
          val simulatedOutcome =
            simulateWriteOutcome(
              existingLabel = parsedFromIntent?.text,
              newLabel = current.draftLabelText,
              confirmBeforeOverwrite = prefs.confirmBeforeOverwrite,
              overwriteAlreadyConfirmed = current.overwriteConfirmedForNextTap,
            )
          handleWriteOutcome(simulatedOutcome)
        }
      }

      NfcOperationMode.ARMED_TO_LOAD_FOR_EDIT -> {
        val existingText = parsedFromIntent?.text?.trim().orEmpty()
        if (existingText.isEmpty()) {
          val message = "This sticker does not contain an existing text label to edit."
          internalState.update {
            it.copy(
              nfcMode = NfcOperationMode.READ_AND_INSPECT,
              statusAnnouncement = message,
            )
          }
          speakAloud(message)
        } else {
          val message =
            "Loaded existing label for editing: \"$existingText\". Modify text and activate Write Label to Sticker."
          internalState.update {
            it.copy(
              selectedTab = TagSpeakTab.WRITE,
              draftLabelText = existingText,
              lastScannedLabel = parsedFromIntent,
              nfcMode = NfcOperationMode.READ_AND_INSPECT,
              overwriteConfirmedForNextTap = true,
              pendingOverwritePrompt = null,
              statusAnnouncement = message,
            )
          }
          speakAloud(message)
        }
      }

      NfcOperationMode.READ_AND_INSPECT -> {
        if (parsedFromIntent != null && parsedFromIntent.text.isNotBlank()) {
          val cleanText = parsedFromIntent.text.trim()
          internalState.update {
            it.copy(
              selectedTab = TagSpeakTab.INSPECT,
              lastScannedLabel = parsedFromIntent,
              statusAnnouncement = "Sticker label: \"$cleanText\"",
            )
          }
          speakAloud(cleanText)
        } else {
          val msg = "Blank or unformatted NFC sticker detected. Switch to Write Label to record a label."
          internalState.update {
            it.copy(statusAnnouncement = msg)
          }
          speakAloud(msg)
        }
      }
    }
  }

  /**
   * Pure helper for simulated/unit-tested tag writes when no physical hardware [Tag] is present.
   */
  internal fun simulateWriteOutcome(
    existingLabel: String?,
    newLabel: String,
    confirmBeforeOverwrite: Boolean,
    overwriteAlreadyConfirmed: Boolean,
  ): TagWriteOutcome {
    val cleanNew = newLabel.trim()
    if (cleanNew.isEmpty()) {
      return TagWriteOutcome.Failure(WriteFailureReason.EMPTY_LABEL)
    }

    val confirmationNeeded =
      NfcTagOperations.evaluateOverwriteProtection(
        existingLabel = existingLabel,
        pendingNewLabel = cleanNew,
        confirmBeforeOverwrite = confirmBeforeOverwrite,
        overwriteAlreadyConfirmed = overwriteAlreadyConfirmed,
      )
    if (confirmationNeeded != null) {
      return confirmationNeeded
    }

    val estimatedBytes = TagSpeakNdefCodec.estimateNdefMessageBytes(cleanNew)
    if (estimatedBytes > TagSpeakNdefCodec.NTAG213_CAPACITY_BYTES) {
      return TagWriteOutcome.Failure(
        reason = WriteFailureReason.LABEL_TOO_LARGE,
        details =
          "Label requires $estimatedBytes bytes, exceeding standard 137-byte NTAG213 capacity.",
      )
    }

    return TagWriteOutcome.Success(
      writtenLabel = cleanNew,
      previousLabel = existingLabel?.takeIf { it.isNotBlank() },
      bytesWritten = estimatedBytes,
      maxCapacityBytes = TagSpeakNdefCodec.NTAG213_CAPACITY_BYTES,
    )
  }

  internal fun handleWriteOutcome(outcome: TagWriteOutcome) {
    val prefs = preferencesRepository.settings.value
    when (outcome) {
      is TagWriteOutcome.Success -> {
        val updatedScanned =
          ParsedTagSpeakLabel(
            text = outcome.writtenLabel,
            hasTagSpeakAar = true,
            usedBytes = outcome.bytesWritten,
            maxCapacityBytes = outcome.maxCapacityBytes,
            isWritable = true,
          )
        val announcement = "Label written to sticker: \"${outcome.writtenLabel}\"."
        internalState.update {
          it.copy(
            nfcMode = NfcOperationMode.READ_AND_INSPECT,
            overwriteConfirmedForNextTap = false,
            pendingOverwritePrompt = null,
            lastScannedLabel = updatedScanned,
            statusAnnouncement = announcement,
          )
        }
        if (prefs.speakAfterWrite) {
          speakAloud(announcement)
        }
      }

      is TagWriteOutcome.OverwriteConfirmationRequired -> {
        val warningMessage =
          "Sticker already has label: \"${outcome.existingLabel}\". Activate Confirm Overwrite to replace it with \"${outcome.pendingNewLabel}\"."
        internalState.update {
          it.copy(
            nfcMode = NfcOperationMode.AWAITING_OVERWRITE_CONFIRMATION,
            pendingOverwritePrompt =
              PendingOverwritePrompt(
                existingLabel = outcome.existingLabel,
                newLabel = outcome.pendingNewLabel,
              ),
            statusAnnouncement = warningMessage,
          )
        }
        speakAloud(warningMessage)
      }

      is TagWriteOutcome.Failure -> {
        val errorMessage =
          when (outcome.reason) {
            WriteFailureReason.EMPTY_LABEL ->
              "Cannot write an empty label. Please enter label text first."
            WriteFailureReason.TAG_READ_ONLY ->
              "Write failed: This NFC sticker is locked as read-only."
            WriteFailureReason.LABEL_TOO_LARGE ->
              "Write failed: ${outcome.details ?: "The label text is too long for this sticker's memory capacity."}"
            WriteFailureReason.UNSUPPORTED_TAG_FORMAT ->
              "Write failed: This sticker does not support NDEF formatting."
            WriteFailureReason.IO_ERROR ->
              "Write failed: Sticker moved away too quickly. Hold phone steady against the sticker and try again."
          }
        internalState.update {
          it.copy(statusAnnouncement = errorMessage)
        }
        speakAloud(errorMessage)
      }
    }
  }

  private fun speakAloud(text: String) {
    val rate = preferencesRepository.settings.value.speechRate
    speechSpeaker?.speak(text = text, speechRate = rate)
  }
}
