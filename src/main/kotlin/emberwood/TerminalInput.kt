package emberwood

/** Portable ANSI has no key-up events. Steering is latched; every jump is a pulse. */
class TerminalInput {
    var direction = 0
        private set
    private var jump = false
    private var dash = false
    private var shoot = false
    private var jumpTime = 0.0
    private var release = false

    fun key(key: String) {
        when (key) {
            "a", "left" -> direction = -1
            "d", "right" -> direction = 1
            "s", "down" -> direction = 0
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
        return frame
    }

    fun clear() {
        direction = 0
        jump = false
        dash = false
        shoot = false
        jumpTime = 0.0
        release = false
    }
}
