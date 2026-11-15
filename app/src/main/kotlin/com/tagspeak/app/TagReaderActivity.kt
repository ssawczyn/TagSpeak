package com.tagspeak.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.tagspeak.app.data.SharedPreferencesTagSpeakRepository
import com.tagspeak.app.nfc.NfcTagOperations
import com.tagspeak.app.speech.AndroidSpeechAnnouncer
import com.tagspeak.app.speech.SpeechSpeaker

/**
 * Translucent, zero-UI NFC reader activity that handles sticker taps from outside the app
 * (such as the Android home screen).
 *
 * Design for Accessibility (TalkBack & TTS):
 * - Uses `@style/Theme.TagSpeak.TranslucentReader` and an empty window title (`title = ""`) so
 *   TalkBack does not announce window-title chrome ("TagSpeak") before the sticker's label.
 * - Immediately speaks the decoded NDEF label aloud on the accessibility audio stream and
 *   auto-closes (`finish()`) as soon as the spoken announcement finishes.
 * - If [MainActivity] is already open in the foreground, forwards the intent to [MainActivity]
 *   so the user can inspect or edit the label in the app.
 */
class TagReaderActivity : ComponentActivity() {
  private var speechSpeaker: SpeechSpeaker? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Suppress window title announcements so TalkBack users hear only the sticker label
    title = ""
    disablePendingTransitions()

    if (AppForegroundTracker.isMainActivityInForeground) {
      forwardToMainActivity(intent)
      finishSilently()
      return
    }

    handleSilentReadIntent(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (AppForegroundTracker.isMainActivityInForeground) {
      forwardToMainActivity(intent)
      finishSilently()
      return
    }
    handleSilentReadIntent(intent)
  }

  private fun handleSilentReadIntent(incomingIntent: Intent?) {
    val parsed = NfcTagOperations.readLabelFromIntent(incomingIntent)
    val labelToSpeak = parsed?.text?.trim().orEmpty()
    if (labelToSpeak.isEmpty()) {
      finishSilently()
      return
    }

    val prefs = SharedPreferencesTagSpeakRepository(this)
    val speechRate = prefs.settings.value.speechRate

    val announcer = speechSpeaker ?: AndroidSpeechAnnouncer(this).also { speechSpeaker = it }
    announcer.speak(
      text = labelToSpeak,
      speechRate = speechRate,
      onComplete = { finishSilently() },
    )
  }

  private fun forwardToMainActivity(sourceIntent: Intent?) {
    val forwardIntent =
      Intent(this, MainActivity::class.java).apply {
        action = sourceIntent?.action
        sourceIntent?.extras?.let { putExtras(it) }
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      }
    startActivity(forwardIntent)
  }

  private fun finishSilently() {
    if (!isFinishing) {
      finish()
      disablePendingTransitions()
    }
  }

  private fun disablePendingTransitions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
      overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    } else {
      @Suppress("DEPRECATION")
      overridePendingTransition(0, 0)
    }
  }

  override fun onDestroy() {
    speechSpeaker?.shutdown()
    speechSpeaker = null
    super.onDestroy()
  }
}
