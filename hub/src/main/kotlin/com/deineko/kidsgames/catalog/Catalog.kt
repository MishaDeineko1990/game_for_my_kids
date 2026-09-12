package com.deineko.kidsgames.catalog

/** Describes the hub app's own latest build, as published in the repo's catalog/index.json. */
data class HubUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String?,
    val notes: String,
)

/** One game's metadata as published in the catalog. `contentVersion` will drive downloadable
 *  level-pack updates once a game's content moves from bundled-in-the-APK to fetched-from-repo. */
data class GameCatalogEntry(
    val id: String,
    val title: String,
    val description: String,
    val contentVersion: Int,
)

data class Catalog(
    val hub: HubUpdateInfo,
    val games: List<GameCatalogEntry>,
)
