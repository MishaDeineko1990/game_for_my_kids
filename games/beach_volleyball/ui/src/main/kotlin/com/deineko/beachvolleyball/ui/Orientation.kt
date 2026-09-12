package com.deineko.beachvolleyball.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/** Forces the hosting Activity to [orientation] while this composable is on screen, restoring
 *  whatever it was before on dispose. The hub's MainActivity handles orientation config changes
 *  itself (`android:configChanges` in its manifest), so this does not recreate the Activity or
 *  lose any navigation/game state -- only color_block stays portrait-only; this game asked for
 *  landscape specifically, and each game is free to request whichever orientation it needs. */
@Composable
fun LockOrientation(orientation: Int) {
    val context = LocalContext.current
    DisposableEffect(orientation) {
        val activity = context.findActivity()
        val original = activity?.requestedOrientation
        activity?.requestedOrientation = orientation
        onDispose {
            activity?.requestedOrientation = original ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
