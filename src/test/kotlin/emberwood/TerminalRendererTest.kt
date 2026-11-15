package emberwood

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Decode the actual ANSI output rather than treating the backing raster as proof. */
class TerminalRendererTest {
    private val output = Path.of("build", "renderer-audit")

    @Test
    fun everyLevelRendersOriginalArtworkAtRepresentativeSizes() {
        Files.createDirectories(output)
        val renderer = TerminalRenderer(false)
        val game = Game().apply { startRun() }
        val sheet = BufferedImage(1200, 640, BufferedImage.TYPE_INT_RGB)
        val graphics = sheet.createGraphics()
        try {
            repeat(20) { index ->
                game.loadChapter(index)
                for ((columns, rows) in listOf(80 to 20, 100 to 24, 120 to 32, 160 to 44)) {
                    val ansi = renderer.render(game, columns, rows)
                    val image = decode(ansi, columns, rows)
                    Files.writeString(output.resolve("level-${index + 1}-${columns}x$rows.ansi"), ansi)
                    if (columns == 120) {
                        ImageIO.write(image, "png", output.resolve("level-${index + 1}.png").toFile())
                        graphics.drawImage(image, index % 5 * 240, index / 5 * 160, 240, 160, null)
                    }
                    val colors = mutableSetOf<Int>()
                    for (y in 0 until image.height) for (x in 0 until image.width) colors.add(image.getRGB(x, y))
                    assertTrue(colors.size > 35, "Level ${index + 1} should preserve a colorful world")
                }
            }
        } finally {
            graphics.dispose()
        }
        ImageIO.write(sheet, "png", output.resolve("all-levels.png").toFile())
    }

    @Test
    fun closerCameraKeepsFoxVisibleAtEveryEntryExitAndHighJump() {
        Files.createDirectories(output)
        val renderer = TerminalRenderer(false)
        val game = Game().apply { startRun() }
        for (index in game.levels.indices) {
            game.loadChapter(index)
            for ((name, point) in listOf(
                "entry" to game.level.camps.first(),
                "exit" to game.level.gate,
                "high" to Vec(game.level.width / 2, 100.0),
            )) {
                game.player.reset(point)
                game.invincibleLeft = 0.0
                // A retry may leave the simulation camera far away; the crop must still contain the fox.
                game.cameraX = 0.0
                val camera = renderer.cameraX(game)
                assertTrue(camera >= 0 && camera <= game.level.width - TerminalRenderer.VIEW_WIDTH)
                assertTrue(game.player.x - camera in 11.0..TerminalRenderer.VIEW_WIDTH - 11.0)
                val image = decode(renderer.render(game, 120, 32), 120, 32)
                var orange = 0
                for (y in 0 until image.height) {
                    for (x in 0 until image.width) {
                        if ((image.getRGB(x, y) and 0xffffff) in setOf(0xb5422d, 0xe87838)) orange++
                    }
                }
                assertTrue(orange >= 3, "Level ${index + 1} $name must visibly retain the fox's orange pixels")
                if (index == 0) ImageIO.write(image, "png", output.resolve("camera-$name.png").toFile())
            }
        }
    }

    @Test
    fun indexedPaletteRetainsColorAndUsesOnlySupportedSequences() {
        val game = Game().apply { startRun() }
        val ansi = TerminalRenderer(true).render(game, 120, 32)
        assertTrue(";2;" !in ansi, "256-color terminals must not receive truecolor SGR")
        assertTrue(";5;" in ansi)
        val image = decode(ansi, 120, 32)
        val colors = mutableSetOf<Int>()
        for (y in 0 until image.height) for (x in 0 until image.width) colors.add(image.getRGB(x, y))
        assertTrue(colors.size > 20, "The fallback should retain a colorful scene")
        Files.createDirectories(output)
        Files.writeString(output.resolve("palette-256.ansi"), ansi)
        ImageIO.write(image, "png", output.resolve("palette-256.png").toFile())
        assertEquals(16, TerminalColors.paletteIndex(0x000000))
        assertEquals(231, TerminalColors.paletteIndex(0xffffff))
    }

    @Test
    fun emptyViewportProducesNoControlSequences() {
        val renderer = TerminalRenderer(false)
        val game = Game()
        assertEquals("", renderer.render(game, 0, 20))
        assertEquals("", renderer.render(game, 80, 0))
        assertEquals("", renderer.render(game, -1, 20))
    }

    private fun decode(
        ansi: String,
        columns: Int,
        rows: Int,
    ): BufferedImage {
        val image = BufferedImage(columns, rows * 2, BufferedImage.TYPE_INT_RGB)
        val lines = ansi.split('\n')
        assertEquals(rows, lines.size, "ANSI output must have exactly the available playfield rows")
        for ((y, line) in lines.withIndex()) {
            var foreground = 0xffffff
            var background = 0
            var column = 0
            var position = 0
            while (position < line.length) {
                if (line[position] == '\u001b') {
                    assertEquals('[', line[position + 1])
                    val end = line.indexOf('m', position + 2)
                    assertTrue(end > position, "Only complete SGR sequences belong in a frame")
                    val codes = line.substring(position + 2, end).split(';').map { it.toInt() }
                    if (codes == listOf(0)) {
                        foreground = 0xffffff
                        background = 0
                    } else {
                        assertTrue(codes[0] == 38 || codes[0] == 48)
                        assertTrue(codes.drop(2).all { it in 0..255 })
                        val rgb =
                            if (codes[1] == 5) {
                                assertEquals(3, codes.size)
                                val index = codes[2]
                                if (index >= 232) {
                                    val gray = 8 + (index - 232) * 10
                                    (gray shl 16) or (gray shl 8) or gray
                                } else {
                                    assertTrue(index >= 16)
                                    val cube = intArrayOf(0, 95, 135, 175, 215, 255)
                                    val value = index - 16
                                    (cube[value / 36] shl 16) or (cube[value / 6 % 6] shl 8) or cube[value % 6]
                                }
                            } else {
                                assertEquals(5, codes.size)
                                assertEquals(2, codes[1])
                                (codes[2] shl 16) or (codes[3] shl 8) or codes[4]
                            }
                        if (codes[0] == 38) foreground = rgb else background = rgb
                    }
                    position = end + 1
                } else {
                    val character = line[position++]
                    assertTrue(character == ' ' || character == '▀')
                    assertTrue(column < columns, "ANSI row must not wrap")
                    image.setRGB(column, y * 2, if (character == '▀') foreground else background)
                    image.setRGB(column, y * 2 + 1, background)
                    column++
                }
            }
            assertEquals(columns, column)
            assertTrue(line.endsWith("\u001b[0m"), "Each row must restore terminal colors")
        }
        return image
    }
}
