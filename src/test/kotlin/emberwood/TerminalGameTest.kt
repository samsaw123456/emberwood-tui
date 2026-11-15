package emberwood

import com.williamcallahan.tui4j.compat.bubbletea.KeyPressMessage
import com.williamcallahan.tui4j.compat.bubbletea.WindowSizeMessage
import com.williamcallahan.tui4j.compat.bubbletea.input.key.Key
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyType
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalGameTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun savedBestAndPreferencesRemainVisibleAfterStaticScreenChanges() {
        Settings(directory).apply {
            best = 125.9
            save()
        }
        val model = TerminalGame(settings = Settings(directory))
        assertTrue("Best: 2:05" in model.view())
        press(model, 'm')
        assertTrue("sound: off" in model.view())
        press(model, 'g')
        assertTrue("motion: reduced" in model.view())
        model.update(WindowSizeMessage(60, 20))
        assertTrue("Enlarge to at least" in model.view())
        model.update(WindowSizeMessage(120, 40))
        assertTrue("Best: 2:05" in model.view())
        assertTrue("motion: reduced" in model.view())
        assertFalse("Best:" in TerminalGame(settings = Settings(directory), practice = 3).view())
    }

    @Test
    fun pausedMenuReflectsSelectionAndEquipmentChangesBeforeResuming() {
        val game =
            Game().apply {
                startRun()
                equipment.rewardsAt(19, 2)
            }
        val model = TerminalGame(game, Settings(directory))
        press(model, 'p')
        assertTrue("Paused" in model.view())
        model.update(KeyPressMessage(Key(KeyType.keyCR)))
        assertTrue(Regex("> Bow\\s+equipped").containsMatchIn(model.view()))
        model.update(KeyPressMessage(Key(KeyType.KeyDown)))
        assertTrue("> Leaf armor" in model.view())
        model.update(KeyPressMessage(Key(KeyType.keyCR)))
        assertTrue(Regex("> Leaf armor\\s+equipped").containsMatchIn(model.view()))
        press(model, 'p')
        assertTrue("Steering: STOP" in model.view())
        assertFalse("Paused" in model.view())
    }

    private fun press(
        model: TerminalGame,
        key: Char,
    ) {
        model.update(KeyPressMessage(Key(KeyType.KeyRunes, charArrayOf(key))))
    }
}
