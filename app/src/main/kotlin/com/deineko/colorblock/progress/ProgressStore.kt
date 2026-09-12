package com.deineko.colorblock.progress

import android.content.Context

/** Tracks which level the player has unlocked up to. Levels unlock sequentially. */
class ProgressStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("progress", Context.MODE_PRIVATE)

    fun highestUnlocked(): Int = prefs.getInt(KEY_UNLOCKED, 1)

    fun markCompleted(levelId: Int, totalLevels: Int) {
        val next = (levelId + 1).coerceAtMost(totalLevels)
        if (next > highestUnlocked()) {
            prefs.edit().putInt(KEY_UNLOCKED, next).apply()
        }
    }

    companion object {
        private const val KEY_UNLOCKED = "highest_unlocked"
    }
}
