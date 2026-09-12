package com.deineko.beachvolleyball.core

import kotlin.math.abs

/** A deliberately beatable "vs computer" opponent: tracks the ball's x at a capped speed with no
 *  anticipation, so a 3-5 year old can consistently win points against it. */
object SimpleAi {
    private const val MAX_SPEED = 0.55f
    private const val DEAD_ZONE = 0.02f

    fun nextOpponentX(currentX: Float, ball: BallState, dtSeconds: Float): Float {
        val delta = ball.x - currentX
        if (abs(delta) < DEAD_ZONE) return currentX
        val step = MAX_SPEED * dtSeconds
        return (currentX + delta.coerceIn(-step, step)).coerceIn(Court.PADDLE_RADIUS, Court.WIDTH - Court.PADDLE_RADIUS)
    }
}
