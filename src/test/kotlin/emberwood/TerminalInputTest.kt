package emberwood

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalInputTest {
    @Test
    fun holdingMovementRequiresContinuedKeyPressesAndBrakeStopsImmediately() {
        for (left in listOf("a", "left")) {
            for (right in listOf("d", "right")) {
                for (stop in listOf("s", "down")) {
                    val input = TerminalInput()
                    input.key(left)
                    repeat(60) {
                        assertTrue(input.frame(1.0 / 60).left)
                        input.key(left)
                    }
                    input.key(right)
                    val reverse = input.frame(1.0 / 60)
                    assertTrue(reverse.right)
                    assertFalse(reverse.left)
                    input.key(stop)
                    val idle = input.frame(1.0 / 60)
                    assertFalse(idle.left || idle.right)
                }
            }
        }
    }

    @Test
    fun movementStopsShortlyAfterKeyPressesStop() {
        val input = TerminalInput()
        input.key("d")
        var moving = 0
        repeat(30) {
            if (input.frame(1.0 / 60).right) moving++
        }
        assertTrue(moving in 10..16)
        repeat(10) { assertFalse(input.frame(1.0 / 60).right) }
    }

    @Test
    fun jumpPulsesReleaseAndEveryStateBoundaryClearsAllActions() {
        for (key in listOf(" ", "space", "w", "up")) {
            val input = TerminalInput()
            input.key(key)
            assertTrue(input.frame(1.0 / 60).jumpPressed)
            val frames = (1..40).map { input.frame(1.0 / 60) }
            assertEquals(1, frames.count { it.jumpReleased })
            assertFalse(frames.last().jumpHeld)
            input.key(key)
            input.key("x")
            input.key("j")
            input.key("d")
            input.clear()
            assertEquals(InputFrame(), input.frame(1.0 / 60))
        }
    }

    @Test
    fun actionEdgesAreConsumedOnceAndUnknownKeysAreInert() {
        val input = TerminalInput()
        input.key("x")
        input.key("j")
        val pressed = input.frame(1.0 / 60)
        assertTrue(pressed.dashPressed && pressed.shootPressed)
        assertEquals(InputFrame(), input.frame(1.0 / 60))
        listOf("shift", "A", "D", "enter", "f1", "ctrl+a").forEach { input.key(it) }
        assertEquals(InputFrame(), input.frame(1.0 / 60))
    }
}
