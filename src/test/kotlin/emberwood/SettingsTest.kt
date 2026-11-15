package emberwood

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SettingsTest {
    @Test fun `preferences persist without losing another window's settings or faster time`() {
        val root = Files.createTempDirectory("emberwood-settings-")
        try {
            val first = Settings(root)
            val other = Settings(root)
            first.muted = true
            first.best = 90.0
            first.save("muted")
            other.gentle = true
            other.best = 120.0
            other.save("gentle")
            val loaded = Settings(root)
            assertTrue(loaded.muted)
            assertTrue(loaded.gentle)
            assertEquals(90.0, loaded.best)
            loaded.best = 80.0
            loaded.save()
            assertEquals(80.0, Settings(root).best)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun `malformed settings are left intact and reported`() {
        val root = Files.createTempDirectory("emberwood-settings-")
        try {
            val file = root.resolve("settings.properties")
            val broken = "muted=\\uQQQQ\n"
            Files.writeString(file, broken)
            val settings = Settings(root)
            assertNotNull(settings.problem)
            settings.save("muted")
            assertNotNull(settings.problem)
            assertEquals(broken, Files.readString(file))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun `invalid best times do not create a record`() {
        val root = Files.createTempDirectory("emberwood-settings-")
        try {
            for (value in listOf("NaN", "Infinity", "-30", "not-a-number")) {
                Files.writeString(root.resolve("settings.properties"), "best=$value\n")
                val settings = Settings(root)
                assertEquals(0.0, settings.best)
                assertFalse(settings.muted)
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun `original cue envelope produces bounded non-silent PCM`() {
        val samples = Audio.tone(doubleArrayOf(440.0, 610.0), .065)
        assertTrue(samples.size > 2500)
        assertTrue(samples.any { kotlin.math.abs(it.toInt()) > 1000 })
        assertTrue(samples.all { kotlin.math.abs(it.toInt()) < 4000 })
        assertEquals(0, samples.first().toInt())
    }
}
