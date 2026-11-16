package emberwood

/**
 * Portable ANSI terminals usually send key presses but not releases.
 * Movement is held by repeated key events (keyboard auto-repeat while held)
 * and stops shortly after repeats stop.
 */
class TerminalInput {
    var direction = 0
        private set
    private var moveTime = 0.0
    private var jump = false
    private var dash = false
    private var shoot = false
    private var jumpTime = 0.0
    private var release = false

    fun key(key: String) {
        when (key) {
            "a", "left" -> {
                direction = -1
                moveTime = HOLD
            }
            "d", "right" -> {
                direction = 1
                moveTime = HOLD
            }
            "s", "down" -> {
                direction = 0
                moveTime = 0.0
            }
            " ", "space", "w", "up" -> jump = true
            "x" -> dash = true
            "j" -> shoot = true
        }
    }

    fun frame(dt: Double): InputFrame {
        if (jump) jumpTime = 0.35
        val frame =
            InputFrame(
                left = direction < 0,
                right = direction > 0,
                jumpHeld = jumpTime > 0,
                jumpPressed = jump,
                jumpReleased = release && !jump,
                dashPressed = dash,
                shootPressed = shoot,
            )
        jump = false
        dash = false
        shoot = false
        release = jumpTime > 0 && jumpTime <= dt
        jumpTime = (jumpTime - dt).coerceAtLeast(0.0)
        moveTime = (moveTime - dt).coerceAtLeast(0.0)
        if (moveTime <= 0.0) direction = 0
        return frame
    }

    fun clear() {
        direction = 0
        moveTime = 0.0
        jump = false
        dash = false
        shoot = false
        jumpTime = 0.0
        release = false
    }

    private companion object {
        const val HOLD = 0.22
    }
}
