package com.deineko.colorblock.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.deineko.colorblock.core.BlockColor

fun BlockColor.toComposeColor(): Color = Color(0xFF000000L or hex)

fun Color.lighten(amount: Float): Color = lerp(this, Color.White, amount)

fun Color.darken(amount: Float): Color = lerp(this, Color.Black, amount)
