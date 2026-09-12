package com.deineko.beachvolleyball.ui

import android.content.Context
import com.deineko.beachvolleyball.core.BallSpeed

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("beach_volleyball_settings", Context.MODE_PRIVATE)

    fun ballSpeed(): BallSpeed {
        val ordinal = prefs.getInt(KEY_SPEED, BallSpeed.NORMAL.ordinal)
        return BallSpeed.entries.getOrElse(ordinal) { BallSpeed.NORMAL }
    }

    fun setBallSpeed(speed: BallSpeed) {
        prefs.edit().putInt(KEY_SPEED, speed.ordinal).apply()
    }

    companion object {
        private const val KEY_SPEED = "ball_speed"
    }
}
