#if canImport(AVFoundation)
import AVFoundation
import Foundation
#if canImport(TagSpeakCore)
import TagSpeakCore
#endif
#if canImport(UIKit)
import UIKit
#endif

/// Production `SpeechSpeaker` backed by `AVSpeechSynthesizer` configured with `.playback` and
/// `.duckOthers` so spoken labels play clearly even when the iPhone Silent/Mute switch is enabled
/// and are never cut off when the iOS `CoreNFC` system sheet dismisses and returns VoiceOver focus.
public final class IOSSpeechAnnouncer: NSObject, SpeechSpeaker, @unchecked Sendable {
  private let synthesizer = AVSpeechSynthesizer()
  private var completionCallbacks: [ObjectIdentifier: () -> Void] = [:]
  private let lock = NSLock()

  public override init() {
    super.init()
    synthesizer.delegate = self
    configureAudioSession()
  }

  private func configureAudioSession() {
    #if os(iOS)
    do {
      let session = AVAudioSession.sharedInstance()
      try session.setCategory(
        .playback,
        mode: .spokenAudio,
        options: [.duckOthers, .mixWithOthers]
      )
    } catch {
      // Non-fatal in simulator or restricted environments
    }
    #endif
  }

  public func speak(
    text: String,
    speechRate: Float,
    onComplete: (() -> Void)?
  ) {
    let cleanText = text.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !cleanText.isEmpty else {
      if let onComplete {
        DispatchQueue.main.async(execute: onComplete)
      }
      return
    }

    #if os(iOS)
    if UIAccessibility.isVoiceOverRunning {
      // When VoiceOver is active, route speech directly through VoiceOver's announcement
      // channel after the CoreNFC system sheet dismisses and @AccessibilityFocusState
      // has returned focus to the button that launched the scan (~550ms).
      DispatchQueue.main.asyncAfter(deadline: .now() + 0.55) {
        UIAccessibility.post(notification: .announcement, argument: cleanText)
        onComplete?()
      }
      return
    }
    #endif

    DispatchQueue.main.async { [weak self] in
      self?.speakImmediately(text: cleanText, speechRate: speechRate, onComplete: onComplete)
    }
  }

  private func speakImmediately(
    text: String,
    speechRate: Float,
    onComplete: (() -> Void)?
  ) {
    #if os(iOS)
    try? AVAudioSession.sharedInstance().setActive(true)
    #endif

    if synthesizer.isSpeaking {
      synthesizer.stopSpeaking(at: .immediate)
    }

    let utterance = AVSpeechUtterance(string: text)
    utterance.voice = AVSpeechSynthesisVoice(language: Locale.current.language.languageCode?.identifier ?? "en-US")

    // Map TagSpeak's 0.75x..2.0x multiplier onto AVSpeechUtterance's native rate scale
    let clampedMultiplier = min(max(speechRate, 0.5), 2.0)
    let mappedRate = AVSpeechUtteranceDefaultSpeechRate * clampedMultiplier
    utterance.rate = min(
      max(mappedRate, AVSpeechUtteranceMinimumSpeechRate),
      AVSpeechUtteranceMaximumSpeechRate
    )

    if let onComplete {
      lock.lock()
      completionCallbacks[ObjectIdentifier(utterance)] = onComplete
      lock.unlock()
    }

    synthesizer.speak(utterance)
  }

  public func stop() {
    lock.lock()
    completionCallbacks.removeAll()
    lock.unlock()
    if synthesizer.isSpeaking {
      synthesizer.stopSpeaking(at: .immediate)
    }
  }

  private func notifyCompletion(for utterance: AVSpeechUtterance) {
    lock.lock()
    let callback = completionCallbacks.removeValue(forKey: ObjectIdentifier(utterance))
    lock.unlock()
    if let callback {
      DispatchQueue.main.async(execute: callback)
    }
  }
}

extension IOSSpeechAnnouncer: AVSpeechSynthesizerDelegate {
  public func speechSynthesizer(
    _ synthesizer: AVSpeechSynthesizer,
    didFinish utterance: AVSpeechUtterance
  ) {
    notifyCompletion(for: utterance)
  }

  public func speechSynthesizer(
    _ synthesizer: AVSpeechSynthesizer,
    didCancel utterance: AVSpeechUtterance
  ) {
    notifyCompletion(for: utterance)
  }
}
#endif
