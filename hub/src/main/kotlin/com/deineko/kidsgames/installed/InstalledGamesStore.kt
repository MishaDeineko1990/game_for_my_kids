package com.deineko.kidsgames.installed

import android.content.Context

/** Tracks which bundled games the player has chosen to keep on their home screen.
 *  "Removing" a game only hides it and clears its saved progress data -- the game's code
 *  always stays inside the hub APK, since games are not separately installable units. */
class InstalledGamesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("installed_games", Context.MODE_PRIVATE)

    fun isInstalled(gameId: String): Boolean = prefs.getBoolean(key(gameId), true)

    fun setInstalled(gameId: String, installed: Boolean) {
        prefs.edit().putBoolean(key(gameId), installed).apply()
    }

    private fun key(gameId: String) = "installed_$gameId"
}
