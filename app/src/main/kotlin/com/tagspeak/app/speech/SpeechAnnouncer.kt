package com.tagspeak.app.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Abstraction for speaking labels and status announcements aloud, allowing deterministic
 * verification in unit and UI tests while using Android [TextToSpeech] on the accessibility
 * audio channel in production.
 */
interface SpeechSpeaker {
  fun speak(
    text: String,
    speechRate: Float = 1.0f,
    onComplete: (() -> Unit)? = null,
  )

  fun stop()

  fun shutdown()
}

/**
 * Production [SpeechSpeaker] backed by Android [TextToSpeech] configured on the
 * [AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY] stream so spoken labels share TalkBack's
 * accessibility volume channel.
 */
class AndroidSpeechAnnouncer(context: Context) : SpeechSpeaker, TextToSpeech.OnInitListener {
  private val appContext = context.applicationContext
  private val mainHandler = Handler(Looper.getMainLooper())
  private var tts: TextToSpeech? = null
  private var isReady = false
  private var initFailed = false

  private data class QueuedSpeech(
    val text: String,
    val rate: Float,
    val onComplete: (() -> Unit)?,
  )

  private var pendingSpeech: QueuedSpeech? = null
  private val completionCallbacks = ConcurrentHashMap<String, () -> Unit>()

  init {
    try {
      tts = TextToSpeech(appContext, this)
    } catch (_: Exception) {
      initFailed = true
    }
  }

  override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
      val engine = tts ?: return
      val accessibilityAudio =
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .build()
      engine.setAudioAttributes(accessibilityAudio)
      engine.language = Locale.getDefault()
      engine.setOnUtteranceProgressListener(
        object : UtteranceProgressListener() {
          override fun onStart(utteranceId: String?) = Unit

          override fun onDone(utteranceId: String?) {
            utteranceId?.let { notifyCompletion(it) }
          }

          @Deprecated("Deprecated in Java")
          override fun onError(utteranceId: String?) {
            utteranceId?.let { notifyCompletion(it) }
          }

          override fun onError(utteranceId: String?, errorCode: Int) {
            utteranceId?.let { notifyCompletion(it) }
          }
        }
      )
      isReady = true
      pendingSpeech?.let { queued ->
        pendingSpeech = null
        speakInternal(queued.text, queued.rate, queued.onComplete)
      }
    } else {
      initFailed = true
      pendingSpeech?.onComplete?.let { callback -> mainHandler.post(callback) }
      pendingSpeech = null
    }
  }

  override fun speak(
    text: String,
    speechRate: Float,
    onComplete: (() -> Unit)?,
  ) {
    val cleanText = text.trim()
    if (cleanText.isEmpty()) {
      onComplete?.let { mainHandler.post(it) }
      return
    }

    if (initFailed) {
      onComplete?.let { mainHandler.post(it) }
      return
    }

    if (!isReady) {
      pendingSpeech = QueuedSpeech(cleanText, speechRate, onComplete)
      // Safety fallback if TTS initialization hangs on a headless emulator
      if (onComplete != null) {
        mainHandler.postDelayed(
          {
            if (!isReady && pendingSpeech != null) {
              val callback = pendingSpeech?.onComplete
              pendingSpeech = null
              callback?.invoke()
            }
          },
          4000L,
        )
      }
      return
    }

    speakInternal(cleanText, speechRate, onComplete)
  }

  private fun speakInternal(
    text: String,
    speechRate: Float,
    onComplete: (() -> Unit)?,
  ) {
    val engine = tts
    if (engine == null) {
      onComplete?.let { mainHandler.post(it) }
      return
    }

    val utteranceId = UUID.randomUUID().toString()
    if (onComplete != null) {
      completionCallbacks[utteranceId] = onComplete
      // Safety timeout in case engine never invokes onDone
      val estimatedDurationMs = (text.length * 80L).coerceIn(2500L, 15000L)
      mainHandler.postDelayed({ notifyCompletion(utteranceId) }, estimatedDurationMs)
    }

    engine.setSpeechRate(speechRate.coerceIn(0.5f, 2.5f))
    val params = Bundle()
    val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    if (result == TextToSpeech.ERROR) {
      notifyCompletion(utteranceId)
    }
  }

  private fun notifyCompletion(utteranceId: String) {
    val callback = completionCallbacks.remove(utteranceId) ?: return
    mainHandler.post(callback)
  }

  override fun stop() {
    pendingSpeech = null
    completionCallbacks.clear()
    try {
      tts?.stop()
    } catch (_: Exception) {}
  }

  override fun shutdown() {
    stop()
    try {
      tts?.shutdown()
    } catch (_: Exception) {}
    tts = null
    isReady = false
  }
}
