package com.tagspeak.app.data

import android.content.Context
import com.tagspeak.app.nfc.TagSpeakNdefCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class StickerTypeProfile(
  val id: String,
  val displayName: String,
  val shortLabel: String,
  val ndefCapacityBytes: Int,
  val maxRecommendedTextBytes: Int,
) {
  NTAG213(
    id = "NTAG213",
    displayName = "NTAG213 (standard, ~96 characters)",
    shortLabel = "standard NTAG213 sticker",
    ndefCapacityBytes = TagSpeakNdefCodec.NTAG213_CAPACITY_BYTES,
    maxRecommendedTextBytes = TagSpeakNdefCodec.NTAG213_MAX_TEXT_BYTES,
  ),
  NTAG215(
    id = "NTAG215",
    displayName = "NTAG215 (medium, ~448 characters)",
    shortLabel = "medium NTAG215 sticker",
    ndefCapacityBytes = TagSpeakNdefCodec.NTAG215_CAPACITY_BYTES,
    maxRecommendedTextBytes = TagSpeakNdefCodec.NTAG215_MAX_TEXT_BYTES,
  ),
  NTAG216(
    id = "NTAG216",
    displayName = "NTAG216 (large, ~832 characters)",
    shortLabel = "large NTAG216 sticker",
    ndefCapacityBytes = TagSpeakNdefCodec.NTAG216_CAPACITY_BYTES,
    maxRecommendedTextBytes = TagSpeakNdefCodec.NTAG216_MAX_TEXT_BYTES,
  );

  companion object {
    fun fromId(id: String?): StickerTypeProfile =
      entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NTAG213
  }
}

data class TagSpeakSettings(
  val confirmBeforeOverwrite: Boolean = true,
  val speakAfterWrite: Boolean = true,
  val speechRate: Float = 1.0f,
  val defaultStickerType: StickerTypeProfile = StickerTypeProfile.NTAG213,
)

interface TagSpeakPreferencesRepository {
  val settings: StateFlow<TagSpeakSettings>

  fun setConfirmBeforeOverwrite(enabled: Boolean)

  fun setSpeakAfterWrite(enabled: Boolean)

  fun setSpeechRate(rate: Float)

  fun setDefaultStickerType(stickerType: StickerTypeProfile)
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

  override fun setDefaultStickerType(stickerType: StickerTypeProfile) {
    _settings.update { it.copy(defaultStickerType = stickerType) }
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
        defaultStickerType =
          StickerTypeProfile.fromId(prefs.getString(KEY_DEFAULT_STICKER_TYPE, null)),
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

  override fun setDefaultStickerType(stickerType: StickerTypeProfile) {
    prefs.edit().putString(KEY_DEFAULT_STICKER_TYPE, stickerType.id).apply()
    _settings.update { it.copy(defaultStickerType = stickerType) }
  }

  companion object {
    private const val PREFS_NAME = "tagspeak_preferences"
    private const val KEY_CONFIRM_OVERWRITE = "confirm_before_overwrite"
    private const val KEY_SPEAK_AFTER_WRITE = "speak_after_write"
    private const val KEY_SPEECH_RATE = "speech_rate"
    private const val KEY_DEFAULT_STICKER_TYPE = "default_sticker_type"
  }
}
