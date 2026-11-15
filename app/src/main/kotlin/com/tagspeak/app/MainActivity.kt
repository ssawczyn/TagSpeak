package com.tagspeak.app

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tagspeak.app.data.SharedPreferencesTagSpeakRepository
import com.tagspeak.app.nfc.NfcTagOperations
import com.tagspeak.app.speech.AndroidSpeechAnnouncer
import com.tagspeak.app.ui.main.MainScreen
import com.tagspeak.app.ui.main.MainScreenViewModel
import com.tagspeak.app.ui.theme.TagSpeakTheme

class MainActivity : ComponentActivity() {
  private var nfcAdapter: NfcAdapter? = null
  private var speechAnnouncer: AndroidSpeechAnnouncer? = null

  private val mainViewModel: MainScreenViewModel by viewModels {
    object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val prefs = SharedPreferencesTagSpeakRepository(applicationContext)
        val announcer =
          speechAnnouncer ?: AndroidSpeechAnnouncer(applicationContext).also {
            speechAnnouncer = it
          }
        return MainScreenViewModel(
          preferencesRepository = prefs,
          speechSpeaker = announcer,
        ) as T
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    nfcAdapter = NfcAdapter.getDefaultAdapter(this)

    enableEdgeToEdge()
    setContent {
      TagSpeakTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          MainScreen(viewModel = mainViewModel)
        }
      }
    }

    handleIncomingNfcIntent(intent)
  }

  override fun onResume() {
    super.onResume()
    AppForegroundTracker.setMainActivityResumed(true)
    enableNfcForegroundDispatch()
  }

  override fun onPause() {
    disableNfcForegroundDispatch()
    AppForegroundTracker.setMainActivityResumed(false)
    super.onPause()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingNfcIntent(intent)
  }

  private fun handleIncomingNfcIntent(incomingIntent: Intent?) {
    if (!NfcTagOperations.isNfcOrSimulatedIntent(incomingIntent)) return
    val tag = NfcTagOperations.extractTagFromIntent(incomingIntent)
    val parsedLabel = NfcTagOperations.readLabelFromIntent(incomingIntent)
    mainViewModel.onHardwareTagDiscovered(tag = tag, parsedFromIntent = parsedLabel)
  }

  private fun enableNfcForegroundDispatch() {
    val adapter = nfcAdapter ?: return
    if (!adapter.isEnabled) return

    val selfIntent =
      Intent(this, javaClass).apply {
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
      }
    val pendingFlags =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }
    val pendingIntent = PendingIntent.getActivity(this, 0, selfIntent, pendingFlags)
    try {
      adapter.enableForegroundDispatch(this, pendingIntent, null, null)
    } catch (_: Exception) {}
  }

  private fun disableNfcForegroundDispatch() {
    val adapter = nfcAdapter ?: return
    try {
      adapter.disableForegroundDispatch(this)
    } catch (_: Exception) {}
  }

  override fun onDestroy() {
    speechAnnouncer?.shutdown()
    speechAnnouncer = null
    super.onDestroy()
  }
}
