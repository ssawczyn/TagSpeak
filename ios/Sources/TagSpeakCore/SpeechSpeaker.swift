import Foundation

/// Abstraction for speaking labels and status announcements aloud, allowing deterministic
/// verification in unit tests while using `AVSpeechSynthesizer` + VoiceOver coordination in production.
public protocol SpeechSpeaker: AnyObject {
  func speak(
    text: String,
    speechRate: Float,
    onComplete: (() -> Void)?
  )
  func stop()
}

extension SpeechSpeaker {
  public func speak(text: String, speechRate: Float = 1.0) {
    speak(text: text, speechRate: speechRate, onComplete: nil)
  }
}
