import Foundation

/// Standardized NDEF Type Name Format (TNF) values per NFC Forum specification.
public enum NdefTypeNameFormat: UInt8, Sendable, Equatable {
  case empty = 0x00
  case nfcWellKnown = 0x01
  case media = 0x02
  case absoluteURI = 0x03
  case nfcExternal = 0x04
  case unknown = 0x05
  case unchanged = 0x06
}

/// Platform-neutral NDEF record representation that bridges directly to `NFCNDEFPayload` on iOS
/// while allowing deterministic byte-level unit testing on macOS/Linux CLI without NFC hardware.
public struct RawNdefRecord: Sendable, Equatable {
  public let typeNameFormat: NdefTypeNameFormat
  public let type: Data
  public let identifier: Data
  public let payload: Data

  public init(
    typeNameFormat: NdefTypeNameFormat,
    type: Data,
    identifier: Data = Data(),
    payload: Data
  ) {
    self.typeNameFormat = typeNameFormat
    self.type = type
    self.identifier = identifier
    self.payload = payload
  }

  /// Total serialized byte length of this NDEF record (header + type + identifier + payload).
  public var serializedByteLength: Int {
    let isShortRecord = payload.count < 256
    let hasIdLength = !identifier.isEmpty
    let headerSize = (isShortRecord ? 3 : 6) + (hasIdLength ? 1 : 0)
    return headerSize + type.count + identifier.count + payload.count
  }
}

/// Parsed representation of a TagSpeak NFC sticker.
public struct ParsedTagSpeakLabel: Sendable, Equatable {
  public let text: String
  public let languageCode: String
  public let hasTagSpeakAar: Bool
  public let usedBytes: Int
  public let maxCapacityBytes: Int?
  public let isWritable: Bool

  public init(
    text: String,
    languageCode: String = TagSpeakNdefCodec.defaultLanguageCode,
    hasTagSpeakAar: Bool = false,
    usedBytes: Int = 0,
    maxCapacityBytes: Int? = nil,
    isWritable: Bool = true
  ) {
    self.text = text
    self.languageCode = languageCode
    self.hasTagSpeakAar = hasTagSpeakAar
    self.usedBytes = usedBytes
    self.maxCapacityBytes = maxCapacityBytes
    self.isWritable = isWritable
  }

  public var hasExistingLabel: Bool {
    !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
  }
}

/// Outcome of attempting to write a spoken label to an NFC sticker.
public enum TagWriteOutcome: Sendable, Equatable {
  case success(
    writtenLabel: String,
    previousLabel: String?,
    bytesWritten: Int,
    maxCapacityBytes: Int?
  )
  case overwriteConfirmationRequired(
    existingLabel: String,
    pendingNewLabel: String
  )
  case failure(
    reason: WriteFailureReason,
    details: String? = nil
  )
}

public enum WriteFailureReason: Sendable, Equatable {
  case emptyLabel
  case tagReadOnly
  case labelTooLarge
  case unsupportedTagFormat
  case ioError
}

/// Cross-platform NDEF codec matching Android's `TagSpeakNdefCodec.kt` byte-for-byte.
///
/// Format specification:
/// - Record 1: NFC Forum Well-Known Text Record (`nfcWellKnown` / `"T"`) containing a status byte
///   (UTF-8 flag + language code length), ISO language code (e.g. `"en"`), and the UTF-8 spoken
///   label text.
/// - Record 2: Android Application Record (`AAR`) for `com.tagspeak.app` so stickers written on
///   an iPhone still auto-launch TagSpeak with zero UI prompt when tapped by an Android phone.
public enum TagSpeakNdefCodec {
  public static let tagSpeakPackageName = "com.tagspeak.app"
  public static let customMimeType = "application/vnd.tagspeak.label"
  public static let defaultLanguageCode = "en"

  /// Usable NDEF bytes on a standard NTAG213 sticker (144 bytes user memory minus TLV overhead).
  public static let ntag213CapacityBytes = 137

  /// Usable NDEF bytes on a medium-capacity NTAG215 sticker (~504 bytes user memory minus TLV overhead).
  public static let ntag215CapacityBytes = 492

  /// Usable NDEF bytes on a large-capacity NTAG216 sticker (~888 bytes user memory minus TLV overhead).
  public static let ntag216CapacityBytes = 876

  /// Overhead of the NDEF headers + Android Application Record (`com.tagspeak.app`).
  public static let ndefAndAarOverheadBytes = 41

  /// Default safe UTF-8 text capacity on a standard NTAG213 sticker (96 bytes).
  public static let ntag213MaxTextBytes = ntag213CapacityBytes - ndefAndAarOverheadBytes

  /// Safe UTF-8 text capacity on a medium NTAG215 sticker (448 bytes, subtracting 3 extra bytes for long NDEF record header).
  public static let ntag215MaxTextBytes = ntag215CapacityBytes - ndefAndAarOverheadBytes - 3

  /// Safe UTF-8 text capacity on a large NTAG216 sticker (832 bytes, subtracting 3 extra bytes for long NDEF record header).
  public static let ntag216MaxTextBytes = ntag216CapacityBytes - ndefAndAarOverheadBytes - 3

  /// Computes the maximum UTF-8 text bytes (characters for ASCII text) that can fit inside a tag
  /// with `ndefMaxCapacityBytes` alongside TagSpeak's NDEF headers and Android Application Record.
  public static func maxTextBytesForTagCapacity(_ ndefMaxCapacityBytes: Int) -> Int {
    guard ndefMaxCapacityBytes > ndefAndAarOverheadBytes else { return 0 }
    let rawAvailable = ndefMaxCapacityBytes - ndefAndAarOverheadBytes
    // If payload (1 status + 2 lang + text) >= 256 bytes, NDEF uses a 4-byte length header (+3 bytes)
    if rawAvailable + 3 >= 256 {
      return max(252, rawAvailable - 3)
    } else {
      return rawAvailable
    }
  }

  public static let rtdTextTypeData = Data("T".utf8)
  public static let aarTypeData = Data("android.com:pkg".utf8)

  /// Encodes `text` and `languageCode` into an NFC Forum `RTD_TEXT` record payload.
  ///
  /// - Byte 0: Bit 7 = 0 (UTF-8), Bits 5..0 = length of language code in US-ASCII bytes.
  /// - Bytes 1..langLength: ISO/IANA language code in US-ASCII.
  /// - Remaining bytes: UTF-8 encoded label text.
  public static func encodeRtdTextPayload(
    text: String,
    languageCode: String = defaultLanguageCode
  ) -> Data {
    let trimmedLang = languageCode.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    let effectiveLang = trimmedLang.isEmpty ? defaultLanguageCode : String(trimmedLang.prefix(63))
    let langBytes = Data(effectiveLang.utf8)
    let textBytes = Data(text.utf8)
    let statusByte = UInt8(langBytes.count & 0x3F) // Bit 7 = 0 for UTF-8

    var payload = Data(capacity: 1 + langBytes.count + textBytes.count)
    payload.append(statusByte)
    payload.append(langBytes)
    payload.append(textBytes)
    return payload
  }

  /// Decodes an NFC Forum `RTD_TEXT` record payload into `(text, languageCode)`.
  /// Supports both UTF-8 and UTF-16 payloads. Returns `nil` if malformed or truncated.
  public static func decodeRtdTextPayload(_ payload: Data) -> (text: String, languageCode: String)? {
    guard let firstByte = payload.first else { return nil }
    let isUtf16 = (firstByte & 0x80) != 0
    let langLength = Int(firstByte & 0x3F)
    guard payload.count >= 1 + langLength else { return nil }

    let langStart = payload.startIndex + 1
    let langEnd = langStart + langLength
    let langData = payload[langStart..<langEnd]
    let rawLang = String(data: langData, encoding: .ascii)?
      .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    let languageCode = rawLang.isEmpty ? defaultLanguageCode : rawLang

    let textData = Data(payload[langEnd..<payload.endIndex])
    let encoding: String.Encoding = isUtf16 ? .utf16 : .utf8
    guard let decodedText = String(data: textData, encoding: encoding) else {
      return nil
    }
    return (text: decodedText, languageCode: languageCode)
  }

  /// Estimates the total NDEF message byte size when written with `text` and TagSpeak's AAR.
  public static func estimateNdefMessageBytes(
    text: String,
    languageCode: String = defaultLanguageCode
  ) -> Int {
    let rtdPayloadSize = encodeRtdTextPayload(text: text, languageCode: languageCode).count
    let rtdHeaderSize = rtdPayloadSize < 256 ? 3 : 6
    let rtdRecordSize = rtdHeaderSize + 1 + rtdPayloadSize // 1 byte for type "T"
    let aarRecordSize = 3 + aarTypeData.count + tagSpeakPackageName.utf8.count // 34 bytes
    return rtdRecordSize + aarRecordSize
  }

  /// Builds the standardized two-record TagSpeak NDEF message:
  /// 1. NFC Forum `RTD_TEXT` record with the UTF-8 spoken label.
  /// 2. Android Application Record (`AAR`) for `com.tagspeak.app`.
  public static func createNdefRecords(
    labelText: String,
    languageCode: String = defaultLanguageCode,
    packageName: String = tagSpeakPackageName
  ) -> [RawNdefRecord] {
    let normalizedText = labelText.trimmingCharacters(in: .whitespacesAndNewlines)
    let textPayload = encodeRtdTextPayload(text: normalizedText, languageCode: languageCode)
    let textRecord = RawNdefRecord(
      typeNameFormat: .nfcWellKnown,
      type: rtdTextTypeData,
      identifier: Data(),
      payload: textPayload
    )
    let aarRecord = RawNdefRecord(
      typeNameFormat: .nfcExternal,
      type: aarTypeData,
      identifier: Data(),
      payload: Data(packageName.utf8)
    )
    return [textRecord, aarRecord]
  }

  /// Parses an array of NDEF records into a `ParsedTagSpeakLabel`, supporting:
  /// - NFC Forum `RTD_TEXT` (`nfcWellKnown` + `"T"`)
  /// - `media` (`text/plain` or `application/vnd.tagspeak.label`)
  public static func parseNdefRecords(
    _ records: [RawNdefRecord],
    maxCapacityBytes: Int? = nil,
    isWritable: Bool = true
  ) -> ParsedTagSpeakLabel? {
    guard !records.isEmpty else { return nil }

    var extractedText: String?
    var extractedLanguage = defaultLanguageCode
    var hasTagSpeakAar = false
    var totalBytes = 0

    for record in records {
      totalBytes += record.serializedByteLength

      if record.typeNameFormat == .nfcExternal && record.type == aarTypeData {
        if let pkg = String(data: record.payload, encoding: .ascii), pkg == tagSpeakPackageName {
          hasTagSpeakAar = true
        }
        continue
      }

      if extractedText == nil {
        switch record.typeNameFormat {
        case .nfcWellKnown where record.type == rtdTextTypeData:
          if let decoded = decodeRtdTextPayload(record.payload) {
            extractedText = decoded.text
            extractedLanguage = decoded.languageCode
          }
        case .media:
          if let mimeType = String(data: record.type, encoding: .ascii)?.lowercased(),
            mimeType == "text/plain" || mimeType == customMimeType,
            let text = String(data: record.payload, encoding: .utf8)
          {
            extractedText = text
          }
        default:
          break
        }
      }
    }

    guard let finalText = extractedText else { return nil }
    return ParsedTagSpeakLabel(
      text: finalText,
      languageCode: extractedLanguage,
      hasTagSpeakAar: hasTagSpeakAar,
      usedBytes: totalBytes,
      maxCapacityBytes: maxCapacityBytes,
      isWritable: isWritable
    )
  }

  /// Evaluates overwrite protection rules before writing to a sticker.
  public static func evaluateOverwriteProtection(
    existingLabel: String?,
    pendingNewLabel: String,
    confirmBeforeOverwrite: Bool,
    overwriteAlreadyConfirmed: Bool
  ) -> TagWriteOutcome? {
    let cleanExisting = existingLabel?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    let cleanNew = pendingNewLabel.trimmingCharacters(in: .whitespacesAndNewlines)
    if confirmBeforeOverwrite
      && !overwriteAlreadyConfirmed
      && !cleanExisting.isEmpty
      && cleanExisting != cleanNew
    {
      return .overwriteConfirmationRequired(
        existingLabel: cleanExisting,
        pendingNewLabel: cleanNew
      )
    }
    return nil
  }
}
