package com.tagspeak.app

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracks whether [MainActivity] is currently resumed in the foreground so NFC taps can distinguish
 * between a background/home-screen zero-UI tap and an in-app foreground tap.
 */
object AppForegroundTracker {
  private val mainResumed = AtomicBoolean(false)

  val isMainActivityInForeground: Boolean
    get() = mainResumed.get()

  fun setMainActivityResumed(resumed: Boolean) {
    mainResumed.set(resumed)
  }
}
