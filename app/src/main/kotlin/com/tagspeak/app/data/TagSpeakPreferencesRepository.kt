package com.tagspeak.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TagSpeakSettings(
  val confirmBeforeOverwrite: Boolean = true,
  val speakAfterWrite: Boolean = true,
  val speechRate: Float = 1.0f,
)

interface TagSpeakPreferencesRepository {
  val settings: StateFlow<TagSpeakSettings>

  fun setConfirmBeforeOverwrite(enabled: Boolean)

  fun setSpeakAfterWrite(enabled: Boolean)

  fun setSpeechRate(rate: Float)
}

class InMemoryPreferencesRepository(
  initialSettings: TagSpeakSettings = TagSpeakSettings()
) : TagSpeakPreferencesRepository {
  private val _settings = MutableStateFlow(initialSettings)
  override val settings: StateFlow<TagSpeakSettings> = _settings.asStateFlow()

  override fun setConfirmBeforeOverwrite(enabled: Boolean) {
    _settings.update { it.copy(confirmBeforeOverwrite = enabled) }
  }

  override fun setSpeakAfterWrite(enabled: Boolean) {
    _settings.update { it.copy(speakAfterWrite = enabled) }
  }

  override fun setSpeechRate(rate: Float) {
    val clamped = rate.coerceIn(0.5f, 2.0f)
    _settings.update { it.copy(speechRate = clamped) }
  }
}

class SharedPreferencesTagSpeakRepository(context: Context) : TagSpeakPreferencesRepository {
  private val prefs =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _settings =
    MutableStateFlow(
      TagSpeakSettings(
        confirmBeforeOverwrite = prefs.getBoolean(KEY_CONFIRM_OVERWRITE, true),
        speakAfterWrite = prefs.getBoolean(KEY_SPEAK_AFTER_WRITE, true),
        speechRate = prefs.getFloat(KEY_SPEECH_RATE, 1.0f).coerceIn(0.5f, 2.0f),
      )
    )

  override val settings: StateFlow<TagSpeakSettings> = _settings.asStateFlow()

  override fun setConfirmBeforeOverwrite(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_CONFIRM_OVERWRITE, enabled).apply()
    _settings.update { it.copy(confirmBeforeOverwrite = enabled) }
  }

  override fun setSpeakAfterWrite(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_SPEAK_AFTER_WRITE, enabled).apply()
    _settings.update { it.copy(speakAfterWrite = enabled) }
  }

  override fun setSpeechRate(rate: Float) {
    val clamped = rate.coerceIn(0.5f, 2.0f)
    prefs.edit().putFloat(KEY_SPEECH_RATE, clamped).apply()
    _settings.update { it.copy(speechRate = clamped) }
  }

  companion object {
    private const val PREFS_NAME = "tagspeak_preferences"
    private const val KEY_CONFIRM_OVERWRITE = "confirm_before_overwrite"
    private const val KEY_SPEAK_AFTER_WRITE = "speak_after_write"
    private const val KEY_SPEECH_RATE = "speech_rate"
  }
}
