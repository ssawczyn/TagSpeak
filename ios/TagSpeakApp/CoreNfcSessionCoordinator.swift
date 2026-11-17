#if canImport(CoreNFC)
import CoreNFC
#endif
import Foundation
#if canImport(TagSpeakCore)
import TagSpeakCore
#endif

#if canImport(CoreNFC)
/// Bridges iOS `CoreNFC` (`NFCNDEFReaderSession`) to `MainScreenViewModel` and `TagSpeakNdefCodec`.
@MainActor
public final class CoreNfcSessionCoordinator: NSObject, NfcSessionDriver {
  public weak var viewModel: MainScreenViewModel?

  private var activeSession: NFCNDEFReaderSession?
  private var isWriteOperation = false
  private var pendingLabelToWrite = ""

  public override init() {
    super.init()
  }

  public func beginReadSession(alertMessage: String) {
    cancelActiveSession()
    guard NFCNDEFReaderSession.readingAvailable else {
      viewModel?.handleWriteOutcome(
        .failure(
          reason: .unsupportedTagFormat,
          details: "NFC tag reading is not supported on this device."
        )
      )
      return
    }

    isWriteOperation = false
    pendingLabelToWrite = ""
    let session = NFCNDEFReaderSession(
      delegate: self,
      queue: nil,
      invalidateAfterFirstRead: false
    )
    session.alertMessage = alertMessage
    activeSession = session
    session.begin()
  }

  public func beginWriteSession(labelText: String, alertMessage: String) {
    cancelActiveSession()
    guard NFCNDEFReaderSession.readingAvailable else {
      viewModel?.handleWriteOutcome(
        .failure(
          reason: .unsupportedTagFormat,
          details: "NFC tag writing is not supported on this device."
        )
      )
      return
    }

    isWriteOperation = true
    pendingLabelToWrite = labelText
    let session = NFCNDEFReaderSession(
      delegate: self,
      queue: nil,
      invalidateAfterFirstRead: false
    )
    session.alertMessage = alertMessage
    activeSession = session
    session.begin()
  }

  public func cancelActiveSession() {
    activeSession?.invalidate()
    activeSession = nil
  }

  // MARK: - NDEF Conversion Helpers

  public static func toRawRecords(_ message: NFCNDEFMessage) -> [RawNdefRecord] {
    message.records.map { payload in
      let tnf = NdefTypeNameFormat(rawValue: payload.typeNameFormat.rawValue) ?? .unknown
      return RawNdefRecord(
        typeNameFormat: tnf,
        type: payload.type,
        identifier: payload.identifier,
        payload: payload.payload
      )
    }
  }

  public static func toCoreNfcMessage(_ rawRecords: [RawNdefRecord]) -> NFCNDEFMessage {
    let payloads = rawRecords.map { record in
      let tnf = NFCTypeNameFormat(rawValue: record.typeNameFormat.rawValue) ?? .unknown
      return NFCNDEFPayload(
        format: tnf,
        type: record.type,
        identifier: record.identifier,
        payload: record.payload
      )
    }
    return NFCNDEFMessage(records: payloads)
  }
}

extension CoreNfcSessionCoordinator: @preconcurrency NFCNDEFReaderSessionDelegate {
  public func readerSessionDidBecomeActive(_ session: NFCNDEFReaderSession) {}

  public func readerSession(_ session: NFCNDEFReaderSession, didInvalidateWithError error: Error) {
    activeSession = nil
  }

  public func readerSession(_ session: NFCNDEFReaderSession, didDetectNDEFs messages: [NFCNDEFMessage]) {
    // Fallback callback when `didDetect tags:` is not invoked
    for message in messages {
      let rawRecords = Self.toRawRecords(message)
      if let parsed = TagSpeakNdefCodec.parseNdefRecords(rawRecords) {
        session.invalidate()
        activeSession = nil
        Task { @MainActor in
          self.viewModel?.onTagDiscovered(parsedLabel: parsed)
        }
        return
      }
    }
    session.invalidate()
    activeSession = nil
    Task { @MainActor in
      self.viewModel?.onTagDiscovered(parsedLabel: nil)
    }
  }

  public func readerSession(_ session: NFCNDEFReaderSession, didDetect tags: [NFCNDEFTag]) {
    guard let tag = tags.first else { return }
    if tags.count > 1 {
      session.alertMessage = "More than one NFC sticker detected. Please touch only one sticker."
      DispatchQueue.global().asyncAfter(deadline: .now() + .milliseconds(500)) {
        session.restartPolling()
      }
      return
    }

    session.connect(to: tag) { [weak self] connectError in
      guard let self else { return }
      if connectError != nil {
        session.invalidate(
          errorMessage: "Sticker moved away too quickly. Hold your iPhone steady and try again."
        )
        Task { @MainActor in
          if self.isWriteOperation {
            self.viewModel?.handleWriteOutcome(.failure(reason: .ioError))
          }
        }
        return
      }

      tag.queryNDEFStatus { status, capacity, statusError in
        if statusError != nil || status == .notSupported {
          session.invalidate(errorMessage: "This sticker does not support NDEF formatting.")
          Task { @MainActor in
            if self.isWriteOperation {
              self.viewModel?.handleWriteOutcome(.failure(reason: .unsupportedTagFormat))
            }
          }
          return
        }

        let isWritable = (status == .readWrite)

        tag.readNDEF { existingMessage, _ in
          let existingParsed: ParsedTagSpeakLabel? = existingMessage.flatMap { msg in
            let rawRecords = Self.toRawRecords(msg)
            return TagSpeakNdefCodec.parseNdefRecords(
              rawRecords,
              maxCapacityBytes: capacity,
              isWritable: isWritable
            )
          }

          Task { @MainActor in
            if !self.isWriteOperation {
              session.alertMessage =
                existingParsed?.text ?? "Blank NFC sticker detected."
              session.invalidate()
              self.activeSession = nil
              self.viewModel?.onTagDiscovered(parsedLabel: existingParsed)
              return
            }

            // Evaluate write rules (read-only check, overwrite confirmation, byte capacity)
            guard let vm = self.viewModel else {
              session.invalidate()
              return
            }

            let evaluation = vm.simulateOrEvaluateWriteOutcome(
              existingLabel: existingParsed?.text,
              newLabel: self.pendingLabelToWrite,
              maxCapacityBytes: capacity,
              isWritable: isWritable
            )

            switch evaluation {
            case .overwriteConfirmationRequired:
              session.invalidate(
                errorMessage:
                  "Sticker already contains a label. Confirm overwrite in TagSpeak to replace it."
              )
              self.activeSession = nil
              vm.handleWriteOutcome(evaluation)

            case .failure(_, let details):
              session.invalidate(errorMessage: details ?? "Unable to write to this sticker.")
              self.activeSession = nil
              vm.handleWriteOutcome(evaluation)

            case .success(let writtenLabel, _, _, _):
              let rawRecords = TagSpeakNdefCodec.createNdefRecords(labelText: writtenLabel)
              let coreNfcMessage = Self.toCoreNfcMessage(rawRecords)

              tag.writeNDEF(coreNfcMessage) { writeError in
                if let writeError {
                  session.invalidate(
                    errorMessage: "Write failed: \(writeError.localizedDescription)"
                  )
                  Task { @MainActor in
                    self.activeSession = nil
                    vm.handleWriteOutcome(
                      .failure(reason: .ioError, details: writeError.localizedDescription)
                    )
                  }
                } else {
                  session.alertMessage = "Label written: \(writtenLabel)"
                  session.invalidate()
                  Task { @MainActor in
                    self.activeSession = nil
                    vm.handleWriteOutcome(evaluation)
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
#else
@MainActor
public final class CoreNfcSessionCoordinator: NSObject, NfcSessionDriver {
  public weak var viewModel: MainScreenViewModel?

  public override init() {
    super.init()
  }

  public func beginReadSession(alertMessage: String) {
    viewModel?.handleWriteOutcome(
      .failure(
        reason: .unsupportedTagFormat,
        details: "CoreNFC is only available on physical iOS devices."
      )
    )
  }

  public func beginWriteSession(labelText: String, alertMessage: String) {
    viewModel?.handleWriteOutcome(
      .failure(
        reason: .unsupportedTagFormat,
        details: "CoreNFC is only available on physical iOS devices."
      )
    )
  }

  public func cancelActiveSession() {}
}
#endif
