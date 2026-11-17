import Foundation
#if canImport(XCTest)
import XCTest
@testable import TagSpeakCore
#endif

final class TagSpeakNdefCodecTests: XCTestCase {

  func testEncodeAndDecodeRtdTextPayload_roundTripsUtf8AndLanguageCode() {
    let label = "Ibuprofen 200mg — Take 2 tablets with food"
    let payload = TagSpeakNdefCodec.encodeRtdTextPayload(text: label, languageCode: "en")

    // Status byte: UTF-8 (bit 7 = 0) + language length 2 ("en")
    XCTAssertEqual(payload.first, 2)

    let decoded = TagSpeakNdefCodec.decodeRtdTextPayload(payload)
    XCTAssertNotNil(decoded)
    XCTAssertEqual(decoded?.text, label)
    XCTAssertEqual(decoded?.languageCode, "en")
  }

  func testDecodeRtdTextPayload_supportsUtf16PayloadsFromThirdPartyNfcTools() {
    let langBytes = Data("en".utf8)
    let text = "Chicken Broth"
    guard let utf16BeBytes = text.data(using: .utf16BigEndian) else {
      XCTFail("Failed to encode UTF-16BE")
      return
    }
    // Status byte with bit 7 set (0x80) for UTF-16 + lang length 2
    let statusByte = UInt8(0x80 | langBytes.count)
    var payload = Data([statusByte])
    payload.append(langBytes)
    // Prepend UTF-16BE BOM (0xFE, 0xFF)
    payload.append(contentsOf: [0xFE, 0xFF])
    payload.append(utf16BeBytes)

    let decoded = TagSpeakNdefCodec.decodeRtdTextPayload(payload)
    XCTAssertNotNil(decoded)
    XCTAssertEqual(decoded?.text, "Chicken Broth")
    XCTAssertEqual(decoded?.languageCode, "en")
  }

  func testDecodeRtdTextPayload_returnsNilForEmptyOrTruncatedPayloads() {
    XCTAssertNil(TagSpeakNdefCodec.decodeRtdTextPayload(Data()))
    // Status byte claims 5-byte language code, but payload is only 3 bytes total
    XCTAssertNil(TagSpeakNdefCodec.decodeRtdTextPayload(Data([5, UInt8(ascii: "e"), UInt8(ascii: "n")])))
  }

  func testEstimateNdefMessageBytes_matchesAndroidByteCalculation() {
    let text = "Cinnamon" // 8 UTF-8 bytes
    let estimated = TagSpeakNdefCodec.estimateNdefMessageBytes(text: text, languageCode: "en")
    // RTD_TEXT record: 3 header + 1 type + (1 status + 2 lang + 8 text) = 15 bytes
    // AAR record: 3 header + 15 type ("android.com:pkg") + 16 pkg ("com.tagspeak.app") = 34 bytes
    XCTAssertEqual(estimated, 49)
    XCTAssertLessThanOrEqual(estimated, TagSpeakNdefCodec.ntag213CapacityBytes)
  }

  func testCreateAndParseNdefRecords_includesRtdTextAndAndroidAarForCrossPlatformCompatibility() {
    let records = TagSpeakNdefCodec.createNdefRecords(labelText: " Smoked Paprika ")
    XCTAssertEqual(records.count, 2)

    let parsed = TagSpeakNdefCodec.parseNdefRecords(
      records,
      maxCapacityBytes: TagSpeakNdefCodec.ntag213CapacityBytes,
      isWritable: true
    )
    XCTAssertNotNil(parsed)
    XCTAssertEqual(parsed?.text, "Smoked Paprika")
    XCTAssertEqual(parsed?.languageCode, "en")
    XCTAssertEqual(parsed?.hasTagSpeakAar, true)
    XCTAssertEqual(
      parsed?.usedBytes,
      TagSpeakNdefCodec.estimateNdefMessageBytes(text: "Smoked Paprika")
    )
  }

  func testEvaluateOverwriteProtection_enforcesSettingAndConfirmationState() {
    // 1. Blank sticker: never asks for overwrite confirmation
    XCTAssertNil(
      TagSpeakNdefCodec.evaluateOverwriteProtection(
        existingLabel: nil,
        pendingNewLabel: "Cumin",
        confirmBeforeOverwrite: true,
        overwriteAlreadyConfirmed: false
      )
    )

    // 2. Existing label with confirmBeforeOverwrite = true and not yet confirmed: requires confirmation
    let prompt = TagSpeakNdefCodec.evaluateOverwriteProtection(
      existingLabel: "Paprika",
      pendingNewLabel: "Cumin",
      confirmBeforeOverwrite: true,
      overwriteAlreadyConfirmed: false
    )
    XCTAssertEqual(
      prompt,
      .overwriteConfirmationRequired(existingLabel: "Paprika", pendingNewLabel: "Cumin")
    )

    // 3. Once user confirms overwrite: allows write
    XCTAssertNil(
      TagSpeakNdefCodec.evaluateOverwriteProtection(
        existingLabel: "Paprika",
        pendingNewLabel: "Cumin",
        confirmBeforeOverwrite: true,
        overwriteAlreadyConfirmed: true
      )
    )

    // 4. When confirmBeforeOverwrite setting is disabled: allows immediate overwrite
    XCTAssertNil(
      TagSpeakNdefCodec.evaluateOverwriteProtection(
        existingLabel: "Paprika",
        pendingNewLabel: "Cumin",
        confirmBeforeOverwrite: false,
        overwriteAlreadyConfirmed: false
      )
    )
  }
}
