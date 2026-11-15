package com.tagspeak.app.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Standardized NDEF representation of a TagSpeak sticker.
 *
 * Format specification:
 * - Record 1: NFC Forum Well-Known Text Record ([NdefRecord.TNF_WELL_KNOWN], [NdefRecord.RTD_TEXT])
 *   containing a status byte (UTF-8 flag + language code length), ISO language code (e.g. "en"),
 *   and the UTF-8 spoken label text. This ensures cross-platform compatibility (including iOS
 *   CoreNFC `wellKnownTypeTextPayload` and standard NFC readers).
 * - Record 2: Android Application Record (AAR) for `com.tagspeak.app` so Android dispatches the
 *   sticker tap directly to TagSpeak from the home screen without showing an app chooser dialog.
 */
data class ParsedTagSpeakLabel(
  val text: String,
  val languageCode: String = TagSpeakNdefCodec.DEFAULT_LANGUAGE_CODE,
  val hasTagSpeakAar: Boolean = false,
  val usedBytes: Int = 0,
  val maxCapacityBytes: Int? = null,
  val isWritable: Boolean = true,
) {
  val hasExistingLabel: Boolean
    get() = text.isNotBlank()
}

object TagSpeakNdefCodec {
  const val TAGSPEAK_PACKAGE_NAME = "com.tagspeak.app"
  const val CUSTOM_MIME_TYPE = "application/vnd.tagspeak.label"
  const val DEFAULT_LANGUAGE_CODE = "en"

  /**
   * Usable NDEF bytes on an inexpensive NTAG213 sticker (144 bytes total user memory minus TLV).
   */
  const val NTAG213_CAPACITY_BYTES = 137

  /**
   * Overhead of the NDEF headers + Android Application Record (`com.tagspeak.app`) so we can
   * accurately report remaining UTF-8 text bytes even before a sticker is scanned.
   */
  const val NDEF_AND_AAR_OVERHEAD_BYTES = 41

  /**
   * Default safe UTF-8 text capacity on a standard NTAG213 sticker (~96-100 bytes).
   */
  const val NTAG213_MAX_TEXT_BYTES = NTAG213_CAPACITY_BYTES - NDEF_AND_AAR_OVERHEAD_BYTES

  private val AAR_TYPE_BYTES = "android.com:pkg".toByteArray(StandardCharsets.US_ASCII)

  /**
   * Encodes [text] and [languageCode] into an NFC Forum `RTD_TEXT` record payload byte array.
   *
   * Per NFC Forum Text Record Type Definition:
   * - Byte 0: Bit 7 = 0 (UTF-8), Bits 5..0 = length of language code in US-ASCII bytes.
   * - Bytes 1..langLength: ISO/IANA language code in US-ASCII.
   * - Remaining bytes: UTF-8 encoded label text.
   */
  fun encodeRtdTextPayload(
    text: String,
    languageCode: String = DEFAULT_LANGUAGE_CODE,
  ): ByteArray {
    val sanitizedLang =
      languageCode.trim().lowercase(Locale.ROOT).ifEmpty { DEFAULT_LANGUAGE_CODE }.take(63)
    val langBytes = sanitizedLang.toByteArray(StandardCharsets.US_ASCII)
    val textBytes = text.toByteArray(StandardCharsets.UTF_8)
    val statusByte = (langBytes.size and 0x3F).toByte() // Bit 7 = 0 for UTF-8

    val payload = ByteArray(1 + langBytes.size + textBytes.size)
    payload[0] = statusByte
    System.arraycopy(langBytes, 0, payload, 1, langBytes.size)
    System.arraycopy(textBytes, 0, payload, 1 + langBytes.size, textBytes.size)
    return payload
  }

  /**
   * Decodes an NFC Forum `RTD_TEXT` record payload byte array into `(text, languageCode)`.
   * Returns `null` if the payload is malformed or shorter than the declared language length.
   */
  fun decodeRtdTextPayload(payload: ByteArray): Pair<String, String>? {
    if (payload.isEmpty()) return null
    val statusByte = payload[0].toInt() and 0xFF
    val isUtf16 = (statusByte and 0x80) != 0
    val langLength = statusByte and 0x3F
    if (payload.size < 1 + langLength) return null

    val languageCode =
      String(payload, 1, langLength, StandardCharsets.US_ASCII).ifBlank { DEFAULT_LANGUAGE_CODE }
    val textOffset = 1 + langLength
    val textLength = payload.size - textOffset
    val charset: Charset =
      if (isUtf16) StandardCharsets.UTF_16 else StandardCharsets.UTF_8
    val text = String(payload, textOffset, textLength, charset)
    return text to languageCode
  }

  /**
   * Estimates the total NDEF message byte size when written with [text] and TagSpeak's AAR.
   */
  fun estimateNdefMessageBytes(
    text: String,
    languageCode: String = DEFAULT_LANGUAGE_CODE,
  ): Int {
    val rtdPayloadSize = encodeRtdTextPayload(text, languageCode).size
    val rtdHeaderSize = if (rtdPayloadSize < 256) 3 else 6
    val rtdRecordSize = rtdHeaderSize + 1 + rtdPayloadSize // 1 byte for type "T"
    val aarRecordSize = 3 + AAR_TYPE_BYTES.size + TAGSPEAK_PACKAGE_NAME.length // 34 bytes
    return rtdRecordSize + aarRecordSize
  }

  /**
   * Builds a standardized TagSpeak [NdefMessage] containing:
   * 1. An NFC Forum `RTD_TEXT` record with the UTF-8 spoken label.
   * 2. An Android Application Record (AAR) for [TAGSPEAK_PACKAGE_NAME].
   */
  fun createNdefMessage(
    labelText: String,
    languageCode: String = Locale.getDefault().language.ifBlank { DEFAULT_LANGUAGE_CODE },
    packageName: String = TAGSPEAK_PACKAGE_NAME,
  ): NdefMessage {
    val normalizedText = labelText.trim()
    val textPayload = encodeRtdTextPayload(normalizedText, languageCode)
    val textRecord =
      NdefRecord(
        NdefRecord.TNF_WELL_KNOWN,
        NdefRecord.RTD_TEXT,
        ByteArray(0),
        textPayload,
      )
    val aarRecord = NdefRecord.createApplicationRecord(packageName)
    return NdefMessage(arrayOf(textRecord, aarRecord))
  }

  /**
   * Parses an [NdefMessage] into a [ParsedTagSpeakLabel], supporting:
   * - NFC Forum `RTD_TEXT` (`TNF_WELL_KNOWN` + `RTD_TEXT`)
   * - `TNF_MIME_MEDIA` (`text/plain` or `application/vnd.tagspeak.label`)
   */
  fun parseNdefMessage(
    message: NdefMessage?,
    maxCapacityBytes: Int? = null,
    isWritable: Boolean = true,
  ): ParsedTagSpeakLabel? {
    if (message == null) return null
    val records = message.records ?: return null
    if (records.isEmpty()) return null

    var extractedText: String? = null
    var extractedLanguage: String = DEFAULT_LANGUAGE_CODE
    var hasTagSpeakAar = false

    for (record in records) {
      val tnf = record.tnf
      val type = record.type ?: ByteArray(0)
      val payload = record.payload ?: ByteArray(0)

      if (tnf == NdefRecord.TNF_EXTERNAL_TYPE && type.contentEquals(AAR_TYPE_BYTES)) {
        val pkg = String(payload, StandardCharsets.US_ASCII)
        if (pkg == TAGSPEAK_PACKAGE_NAME) {
          hasTagSpeakAar = true
        }
        continue
      }

      if (extractedText == null) {
        when {
          tnf == NdefRecord.TNF_WELL_KNOWN && type.contentEquals(NdefRecord.RTD_TEXT) -> {
            val decoded = decodeRtdTextPayload(payload)
            if (decoded != null) {
              extractedText = decoded.first
              extractedLanguage = decoded.second
            }
          }
          tnf == NdefRecord.TNF_MIME_MEDIA -> {
            val mimeType = String(type, StandardCharsets.US_ASCII).lowercase(Locale.ROOT)
            if (mimeType == "text/plain" || mimeType == CUSTOM_MIME_TYPE) {
              extractedText = String(payload, StandardCharsets.UTF_8)
            }
          }
        }
      }
    }

    val finalText = extractedText ?: return null
    return ParsedTagSpeakLabel(
      text = finalText,
      languageCode = extractedLanguage,
      hasTagSpeakAar = hasTagSpeakAar,
      usedBytes = message.byteArrayLength,
      maxCapacityBytes = maxCapacityBytes,
      isWritable = isWritable,
    )
  }
}
