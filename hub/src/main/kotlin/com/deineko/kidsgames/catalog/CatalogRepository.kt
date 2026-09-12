package com.deineko.kidsgames.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val CATALOG_URL =
    "https://raw.githubusercontent.com/MishaDeineko1990/game_for_my_kids/master/catalog/index.json"

/** Fetches the repo-hosted game catalog. Returns null on any network/parse failure so callers
 *  can silently fall back to "no update available" when offline -- this must never crash the app. */
object CatalogRepository {
    suspend fun fetch(): Catalog? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(CATALOG_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parse(body)
        }.getOrNull()
    }

    private fun parse(body: String): Catalog {
        val root = JSONObject(body)
        val hubJson = root.getJSONObject("hub")
        val hub = HubUpdateInfo(
            versionCode = hubJson.getInt("versionCode"),
            versionName = hubJson.getString("versionName"),
            apkUrl = hubJson.optString("apkUrl").takeIf { it.isNotBlank() },
            notes = hubJson.optString("notes", ""),
        )
        val gamesJson = root.getJSONArray("games")
        val games = buildList {
            for (i in 0 until gamesJson.length()) {
                val game = gamesJson.getJSONObject(i)
                add(
                    GameCatalogEntry(
                        id = game.getString("id"),
                        title = game.getString("title"),
                        description = game.optString("description", ""),
                        contentVersion = game.optInt("contentVersion", 1),
                    ),
                )
            }
        }
        return Catalog(hub, games)
    }
}
