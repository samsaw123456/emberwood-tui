package emberwood

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

/** Separate preferences from the Godot app; failed writes never remove an existing save. */
class Settings(
    val directory: Path = defaultDirectory(),
) {
    var muted = false
    var gentle = false
    var best = 0.0
    var problem: String? = null
    private val file = directory.resolve("settings.properties")

    init {
        try {
            val values = read()
            muted = values.getProperty("muted") == "true"
            gentle = values.getProperty("gentle") == "true"
            best = values.getProperty("best")?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: 0.0
        } catch (error: Exception) {
            problem = "Couldn't read settings: ${error.message}"
        }
    }

    private fun read(): Properties = Properties().apply { if (Files.exists(file)) Files.newInputStream(file).use { load(it) } }

    fun save(changed: String = "best") {
        try {
            Files.createDirectories(directory)
            java.nio.channels.FileChannel
                .open(
                    directory.resolve("settings.lock"),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.WRITE,
                ).use { channel ->
                    channel.lock().use {
                        val values = read()
                        val previous = values.getProperty("best")?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: 0.0
                        if (previous > 0 && (best <= 0 || previous < best)) best = previous
                        values.setProperty("best", best.toString())
                        if (changed == "muted" || !values.containsKey("muted")) values.setProperty("muted", muted.toString())
                        if (changed == "gentle" || !values.containsKey("gentle")) values.setProperty("gentle", gentle.toString())
                        val temp = Files.createTempFile(directory, "settings-", ".tmp")
                        try {
                            Files.newOutputStream(temp).use { values.store(it, "Emberwood Terminal") }
                            try {
                                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                            } catch (
                                _: java.nio.file.AtomicMoveNotSupportedException,
                            ) {
                                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
                            }
                        } finally {
                            Files.deleteIfExists(temp)
                        }
                    }
                }
            problem = null
        } catch (error: Exception) {
            problem = "Couldn't save settings: ${error.message}"
            System.err.println(problem)
        }
    }

    companion object {
        fun defaultDirectory(): Path {
            val home = System.getProperty("user.home")
            if (System
                    .getProperty(
                        "os.name",
                    ).startsWith("Mac")
            ) {
                return Path.of(home, "Library", "Application Support", "Emberwood Terminal")
            }
            val base = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.startsWith("/") } ?: "$home/.config"
            return Path.of(base, "emberwood-tui")
        }
    }
}
