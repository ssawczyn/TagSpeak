package com.tagspeak.app.nfc

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagSpeakNdefCodecTest {

  @Test
  fun encodeAndDecodeRtdTextPayload_roundTripsUtf8AndLanguageCode() {
    val label = "Ibuprofen 200mg — Take 2 tablets with food"
    val payload = TagSpeakNdefCodec.encodeRtdTextPayload(label, "en")

    // Status byte: UTF-8 (bit 7 = 0) + language length 2 ("en")
    assertEquals(2, payload[0].toInt() and 0xFF)

    val decoded = TagSpeakNdefCodec.decodeRtdTextPayload(payload)
    assertNotNull(decoded)
    assertEquals(label, decoded?.first)
    assertEquals("en", decoded?.second)
  }

  @Test
  fun decodeRtdTextPayload_supportsUtf16PayloadsFromThirdPartyNfcTools() {
    val langBytes = "en".toByteArray(StandardCharsets.US_ASCII)
    val text = "Chicken Broth"
    val utf16Bytes = text.toByteArray(StandardCharsets.UTF_16BE)
    // Status byte with bit 7 set (0x80) for UTF-16 + lang length 2
    val statusByte = (0x80 or langBytes.size).toByte()
    // Prepend UTF-16BE BOM (0xFE, 0xFF) so StandardCharsets.UTF_16 decodes cleanly
    val payload =
      byteArrayOf(statusByte) + langBytes + byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + utf16Bytes

    val decoded = TagSpeakNdefCodec.decodeRtdTextPayload(payload)
    assertNotNull(decoded)
    assertEquals("Chicken Broth", decoded?.first)
    assertEquals("en", decoded?.second)
  }

  @Test
  fun decodeRtdTextPayload_returnsNullForEmptyOrTruncatedPayloads() {
    assertNull(TagSpeakNdefCodec.decodeRtdTextPayload(ByteArray(0)))
    // Status byte claims 5-byte language code, but payload is only 3 bytes total
    assertNull(TagSpeakNdefCodec.decodeRtdTextPayload(byteArrayOf(5, 'e'.code.toByte(), 'n'.code.toByte())))
  }

  @Test
  fun estimateNdefMessageBytes_accountsForRtdTextHeaderAndAar() {
    val text = "Cinnamon" // 8 UTF-8 bytes
    val estimated = TagSpeakNdefCodec.estimateNdefMessageBytes(text, "en")
    // RTD_TEXT record: 3 header + 1 type + (1 status + 2 lang + 8 text) = 15 bytes
    // AAR record: 3 header + 15 type ("android.com:pkg") + 16 pkg ("com.tagspeak.app") = 34 bytes
    assertEquals(49, estimated)
    assertTrue(estimated <= TagSpeakNdefCodec.NTAG213_CAPACITY_BYTES)
  }

  @Test
  fun evaluateOverwriteProtection_enforcesSettingAndConfirmationState() {
    // 1. Blank sticker: never asks for overwrite confirmation
    assertNull(
      NfcTagOperations.evaluateOverwriteProtection(
        existingLabel = null,
        pendingNewLabel = "Cumin",
        confirmBeforeOverwrite = true,
        overwriteAlreadyConfirmed = false,
      )
    )

    // 2. Existing label with confirmBeforeOverwrite = true and not yet confirmed: requires confirmation
    val prompt =
      NfcTagOperations.evaluateOverwriteProtection(
        existingLabel = "Paprika",
        pendingNewLabel = "Cumin",
        confirmBeforeOverwrite = true,
        overwriteAlreadyConfirmed = false,
      )
    assertNotNull(prompt)
    assertEquals("Paprika", prompt?.existingLabel)
    assertEquals("Cumin", prompt?.pendingNewLabel)

    // 3. Once user confirms overwrite: allows write
    assertNull(
      NfcTagOperations.evaluateOverwriteProtection(
        existingLabel = "Paprika",
        pendingNewLabel = "Cumin",
        confirmBeforeOverwrite = true,
        overwriteAlreadyConfirmed = true,
      )
    )

    // 4. When confirmBeforeOverwrite setting is disabled: allows immediate overwrite
    assertNull(
      NfcTagOperations.evaluateOverwriteProtection(
        existingLabel = "Paprika",
        pendingNewLabel = "Cumin",
        confirmBeforeOverwrite = false,
        overwriteAlreadyConfirmed = false,
      )
    )
  }
}
