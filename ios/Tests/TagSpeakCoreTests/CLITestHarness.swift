import Foundation

#if !canImport(XCTest)
open class XCTestCase {
  open func setUp() {}
  open func tearDown() {}
  public init() {}
}

private var cliFailureCount = 0

public func XCTFail(_ message: String = "", file: StaticString = #file, line: UInt = #line) {
  cliFailureCount += 1
  print("  [FAIL] \(file):\(line) — \(message)")
}

public func XCTAssertTrue(
  _ expression: @autoclosure () -> Bool,
  _ message: String = "",
  file: StaticString = #file,
  line: UInt = #line
) {
  if !expression() {
    XCTFail("XCTAssertTrue failed. \(message)", file: file, line: line)
  }
}

public func XCTAssertFalse(
  _ expression: @autoclosure () -> Bool,
  _ message: String = "",
  file: StaticString = #file,
  line: UInt = #line
) {
  if expression() {
    XCTFail("XCTAssertFalse failed. \(message)", file: file, line: line)
  }
}

public func XCTAssertNotNil<T>(
  _ expression: @autoclosure () -> T?,
  _ message: String = "",
  file: StaticString = #file,
  line: UInt = #line
) {
  if expression() == nil {
    XCTFail("XCTAssertNotNil failed: value was nil. \(message)", file: file, line: line)
  }
}

public func XCTAssertNil<T>(
  _ expression: @autoclosure () -> T?,
  _ message: String = "",
  file: StaticString = #file,
  line: UInt = #line
) {
  if let val = expression() {
    XCTFail("XCTAssertNil failed: got \(val). \(message)", file: file, line: line)
  }
}

public func XCTAssertEqual<T: Equatable>(
  _ lhs: @autoclosure () -> T,
  _ rhs: @autoclosure () -> T,
  _ message: String = "",
  file: StaticString = #file,
  line: UInt = #line
) {
  let left = lhs()
  let right = rhs()
  if left != right {
    XCTFail("XCTAssertEqual failed: (\(left)) != (\(right)). \(message)", file: file, line: line)
  }
}

public func XCTAssertLessThanOrEqual<T: Comparable>(
  _ lhs: @autoclosure () -> T,
  _ rhs: @autoclosure () -> T,
  _ message: String = "",
  file: StaticString = #file,
  line: UInt = #line
) {
  let left = lhs()
  let right = rhs()
  if !(left <= right) {
    XCTFail(
      "XCTAssertLessThanOrEqual failed: (\(left)) > (\(right)). \(message)",
      file: file,
      line: line
    )
  }
}

@main
struct CLITestMain {
  @MainActor
  static func main() {
    print("Running TagSpeak iOS Core Unit Tests...")
    var totalTests = 0

    let codecTests = TagSpeakNdefCodecTests()
    let codecCases: [(String, () -> Void)] = [
      (
        "testEncodeAndDecodeRtdTextPayload_roundTripsUtf8AndLanguageCode",
        codecTests.testEncodeAndDecodeRtdTextPayload_roundTripsUtf8AndLanguageCode
      ),
      (
        "testDecodeRtdTextPayload_supportsUtf16PayloadsFromThirdPartyNfcTools",
        codecTests.testDecodeRtdTextPayload_supportsUtf16PayloadsFromThirdPartyNfcTools
      ),
      (
        "testDecodeRtdTextPayload_returnsNilForEmptyOrTruncatedPayloads",
        codecTests.testDecodeRtdTextPayload_returnsNilForEmptyOrTruncatedPayloads
      ),
      (
        "testEstimateNdefMessageBytes_matchesAndroidByteCalculation",
        codecTests.testEstimateNdefMessageBytes_matchesAndroidByteCalculation
      ),
      (
        "testCreateAndParseNdefRecords_includesRtdTextAndAndroidAarForCrossPlatformCompatibility",
        codecTests.testCreateAndParseNdefRecords_includesRtdTextAndAndroidAarForCrossPlatformCompatibility
      ),
      (
        "testEvaluateOverwriteProtection_enforcesSettingAndConfirmationState",
        codecTests.testEvaluateOverwriteProtection_enforcesSettingAndConfirmationState
      ),
    ]

    for (name, testFn) in codecCases {
      totalTests += 1
      let beforeFailures = cliFailureCount
      codecTests.setUp()
      testFn()
      codecTests.tearDown()
      if cliFailureCount == beforeFailures {
        print("  [PASS] TagSpeakNdefCodecTests.\(name)")
      }
    }

    let vmTests = MainScreenViewModelTests()
    let vmCases: [(String, () -> Void)] = [
      (
        "testTapLabeledStickerInDefaultMode_speaksLabelAndOpensInspectTab",
        vmTests.testTapLabeledStickerInDefaultMode_speaksLabelAndOpensInspectTab
      ),
      (
        "testWriteToBlankSticker_writesAndAnnouncesConfirmation",
        vmTests.testWriteToBlankSticker_writesAndAnnouncesConfirmation
      ),
      (
        "testWriteToExistingLabeledSticker_promptsForOverwriteConfirmationThenWritesAfterConfirm",
        vmTests.testWriteToExistingLabeledSticker_promptsForOverwriteConfirmationThenWritesAfterConfirm
      ),
      (
        "testScanExistingStickerToEdit_loadsTextIntoEditorAndPreAuthorizesRewrite",
        vmTests.testScanExistingStickerToEdit_loadsTextIntoEditorAndPreAuthorizesRewrite
      ),
      (
        "testAutoScanOnLaunch_startsReadSessionWhenEnabledOrTriggeredByShortcut",
        vmTests.testAutoScanOnLaunch_startsReadSessionWhenEnabledOrTriggeredByShortcut
      ),
      (
        "testDefaultStickerTypeAndHardwareCapacity_updatesWarningsAndHonorsActualTagSize",
        vmTests.testDefaultStickerTypeAndHardwareCapacity_updatesWarningsAndHonorsActualTagSize
      ),
    ]

    for (name, testFn) in vmCases {
      totalTests += 1
      let beforeFailures = cliFailureCount
      vmTests.setUp()
      testFn()
      vmTests.tearDown()
      if cliFailureCount == beforeFailures {
        print("  [PASS] MainScreenViewModelTests.\(name)")
      }
    }

    if cliFailureCount > 0 {
      print("\nFAILED: \(cliFailureCount) assertion(s) failed across \(totalTests) tests.")
      Foundation.exit(1)
    } else {
      print("\nSUCCESS: All \(totalTests) TagSpeak iOS unit tests passed.")
    }
  }
}
#endif
