package com.tagspeak.app.nfc

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build

/**
 * Outcome of attempting to write a spoken label to an NFC sticker.
 */
sealed interface TagWriteOutcome {
  data class Success(
    val writtenLabel: String,
    val previousLabel: String?,
    val bytesWritten: Int,
    val maxCapacityBytes: Int?,
  ) : TagWriteOutcome

  data class OverwriteConfirmationRequired(
    val existingLabel: String,
    val pendingNewLabel: String,
  ) : TagWriteOutcome

  data class Failure(
    val reason: WriteFailureReason,
    val details: String? = null,
  ) : TagWriteOutcome
}

enum class WriteFailureReason {
  EMPTY_LABEL,
  TAG_READ_ONLY,
  LABEL_TOO_LARGE,
  UNSUPPORTED_TAG_FORMAT,
  IO_ERROR,
}

object NfcTagOperations {
  /**
   * Custom action and extras allowing automated tests and `adb` commands to simulate scanning
   * or writing a TagSpeak sticker on devices/emulators without physical NFC hardware.
   */
  const val ACTION_SIMULATE_TAG_TAP = "com.tagspeak.app.action.SIMULATE_TAG_TAP"
  const val EXTRA_SIMULATED_LABEL = "com.tagspeak.app.extra.SIMULATED_LABEL"

  /**
   * Checks whether [intent] represents an NFC tag discovery or a simulated tag tap.
   */
  fun isNfcOrSimulatedIntent(intent: Intent?): Boolean {
    val action = intent?.action ?: return false
    return action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
      action == NfcAdapter.ACTION_TECH_DISCOVERED ||
      action == NfcAdapter.ACTION_TAG_DISCOVERED ||
      action == ACTION_SIMULATE_TAG_TAP
  }

  /**
   * Extracts a [ParsedTagSpeakLabel] from an incoming NFC [Intent].
   */
  fun readLabelFromIntent(intent: Intent?): ParsedTagSpeakLabel? {
    if (intent == null) return null

    // 1. Support simulated label extra for automated accessibility verification & emulator testing
    val simulatedText = intent.getStringExtra(EXTRA_SIMULATED_LABEL)
    if (simulatedText != null) {
      val estimatedBytes = TagSpeakNdefCodec.estimateNdefMessageBytes(simulatedText)
      return ParsedTagSpeakLabel(
        text = simulatedText,
        languageCode = TagSpeakNdefCodec.DEFAULT_LANGUAGE_CODE,
        hasTagSpeakAar = true,
        usedBytes = estimatedBytes,
        maxCapacityBytes = TagSpeakNdefCodec.NTAG213_CAPACITY_BYTES,
        isWritable = true,
      )
    }

    // 2. Check physical Tag object for capacity/writability metadata if available
    val tag = extractTagFromIntent(intent)
    val ndefTech = tag?.let { Ndef.get(it) }
    val maxCapacity = ndefTech?.maxSize
    val isWritable = ndefTech?.isWritable ?: true

    // 3. Parse NDEF messages attached to the intent
    val rawMessages =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
          ?.filterIsInstance<NdefMessage>()
          ?.toTypedArray()
      }

    if (!rawMessages.isNullOrEmpty()) {
      for (ndefMessage in rawMessages) {
        val parsed = TagSpeakNdefCodec.parseNdefMessage(ndefMessage, maxCapacity, isWritable)
        if (parsed != null) return parsed
      }
    }

    // 4. Fallback to cached NdefMessage on the Ndef tech object
    val cachedMessage = ndefTech?.cachedNdefMessage
    if (cachedMessage != null) {
      return TagSpeakNdefCodec.parseNdefMessage(cachedMessage, maxCapacity, isWritable)
    }

    return null
  }

  /**
   * Extracts the [Tag] parcelable from an NFC [Intent], if present.
   */
  fun extractTagFromIntent(intent: Intent?): Tag? {
    if (intent == null) return null
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
    }
  }

  /**
   * Evaluates overwrite protection rules before writing to a sticker.
   * Separated as a pure function so unit tests can verify overwrite confirmation logic directly.
   */
  fun evaluateOverwriteProtection(
    existingLabel: String?,
    pendingNewLabel: String,
    confirmBeforeOverwrite: Boolean,
    overwriteAlreadyConfirmed: Boolean,
  ): TagWriteOutcome.OverwriteConfirmationRequired? {
    val cleanExisting = existingLabel?.trim().orEmpty()
    val cleanNew = pendingNewLabel.trim()
    if (
      confirmBeforeOverwrite &&
        !overwriteAlreadyConfirmed &&
        cleanExisting.isNotEmpty() &&
        cleanExisting != cleanNew
    ) {
      return TagWriteOutcome.OverwriteConfirmationRequired(
        existingLabel = cleanExisting,
        pendingNewLabel = cleanNew,
      )
    }
    return null
  }

  /**
   * Writes [labelText] to a physical NFC [Tag] in standardized TagSpeak NDEF format.
   */
  fun writeLabelToTag(
    tag: Tag,
    labelText: String,
    confirmBeforeOverwrite: Boolean,
    overwriteAlreadyConfirmed: Boolean,
  ): TagWriteOutcome {
    val cleanLabel = labelText.trim()
    if (cleanLabel.isEmpty()) {
      return TagWriteOutcome.Failure(WriteFailureReason.EMPTY_LABEL)
    }

    val message = TagSpeakNdefCodec.createNdefMessage(cleanLabel)
    val messageBytes = message.byteArrayLength

    val ndef = Ndef.get(tag)
    if (ndef != null) {
      return try {
        ndef.connect()
        if (!ndef.isWritable) {
          return TagWriteOutcome.Failure(WriteFailureReason.TAG_READ_ONLY)
        }

        val existingParsed = TagSpeakNdefCodec.parseNdefMessage(ndef.cachedNdefMessage)
        val existingText = existingParsed?.text?.takeIf { it.isNotBlank() }

        val confirmationNeeded =
          evaluateOverwriteProtection(
            existingLabel = existingText,
            pendingNewLabel = cleanLabel,
            confirmBeforeOverwrite = confirmBeforeOverwrite,
            overwriteAlreadyConfirmed = overwriteAlreadyConfirmed,
          )
        if (confirmationNeeded != null) {
          return confirmationNeeded
        }

        val maxSize = ndef.maxSize
        if (messageBytes > maxSize) {
          val maxTextChars = TagSpeakNdefCodec.maxTextBytesForTagCapacity(maxSize)
          return TagWriteOutcome.Failure(
            reason = WriteFailureReason.LABEL_TOO_LARGE,
            details =
              "Label is ${cleanLabel.length} characters ($messageBytes total NDEF bytes), but this sticker only holds up to $maxTextChars characters ($maxSize total NDEF bytes).",
          )
        }

        ndef.writeNdefMessage(message)
        TagWriteOutcome.Success(
          writtenLabel = cleanLabel,
          previousLabel = existingText,
          bytesWritten = messageBytes,
          maxCapacityBytes = maxSize,
        )
      } catch (e: Exception) {
        TagWriteOutcome.Failure(WriteFailureReason.IO_ERROR, e.localizedMessage)
      } finally {
        try {
          ndef.close()
        } catch (_: Exception) {}
      }
    }

    // Support unformatted factory stickers via NdefFormatable
    val formatable = NdefFormatable.get(tag)
    if (formatable != null) {
      return try {
        formatable.connect()
        formatable.format(message)
        TagWriteOutcome.Success(
          writtenLabel = cleanLabel,
          previousLabel = null,
          bytesWritten = messageBytes,
          maxCapacityBytes = null,
        )
      } catch (e: Exception) {
        TagWriteOutcome.Failure(WriteFailureReason.IO_ERROR, e.localizedMessage)
      } finally {
        try {
          formatable.close()
        } catch (_: Exception) {}
      }
    }

    return TagWriteOutcome.Failure(WriteFailureReason.UNSUPPORTED_TAG_FORMAT)
  }
}
