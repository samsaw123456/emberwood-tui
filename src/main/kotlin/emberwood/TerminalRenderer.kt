package emberwood

import java.awt.image.BufferedImage

/**
 * Every terminal cell holds two pixels from the original licensed artwork. The
 * game geometry stays in its original units. A closer camera makes the original
 * fox recognizable; terminal text stays separate from the pixel artwork.
 */
class TerminalRenderer(
    private val indexedColor: Boolean = TerminalColors.indexed,
) {
    private val world = WorldRenderer()
    private var frame = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)

    fun render(
        game: Game,
        columns: Int,
        rows: Int,
    ): String {
        if (columns <= 0 || rows <= 0) return ""
        if (frame.width != columns || frame.height != rows * 2) {
            frame = BufferedImage(columns, rows * 2, BufferedImage.TYPE_INT_RGB)
        }
        val graphics = frame.createGraphics()
        try {
            graphics.scale(columns / VIEW_WIDTH, rows * 2 / VIEW_HEIGHT)
            graphics.clipRect(0, 0, VIEW_WIDTH.toInt(), VIEW_HEIGHT.toInt())
            // Keep normal jump arcs visible; follow only a high spring/double jump.
            val top = (game.player.y - 110.0).coerceIn(0.0, 648.0 - VIEW_HEIGHT)
            graphics.translate(0.0, -top)
            world.draw(graphics, game, cameraX(game))
        } finally {
            graphics.dispose()
        }
        return encode(frame)
    }

    internal fun cameraX(game: Game): Double {
        val originalTarget = (game.player.x - 380.0).coerceIn(0.0, (game.level.width - 1152.0).coerceAtLeast(0.0))
        // Bound inherited easing after retries: the player must never leave the crop.
        val lag = (originalTarget - game.cameraX).coerceIn(-100.0, 100.0)
        return (game.player.x - 280.0 - lag).coerceIn(0.0, (game.level.width - VIEW_WIDTH).coerceAtLeast(0.0))
    }

    companion object {
        const val VIEW_WIDTH = 768.0
        const val VIEW_HEIGHT = 432.0
    }

    /** Keep adjacent cell colors in effect to avoid redundant SGR sequences. */
    private fun encode(image: BufferedImage): String =
        buildString(image.width * image.height * 16) {
            for (y in 0 until image.height step 2) {
                var foreground = -1
                var background = -1
                for (x in 0 until image.width) {
                    val top = image.getRGB(x, y) and 0xffffff
                    val bottom = image.getRGB(x, y + 1) and 0xffffff
                    if (bottom != background) {
                        color(bottom, false)
                        background = bottom
                    }
                    if (top == bottom) {
                        append(' ')
                    } else {
                        if (top != foreground) {
                            color(top, true)
                            foreground = top
                        }
                        append('▀')
                    }
                }
                append("\u001b[0m")
                if (y + 2 < image.height) append('\n')
            }
        }

    private fun StringBuilder.color(
        rgb: Int,
        foreground: Boolean,
    ) {
        append(
            if (foreground) TerminalColors.foreground(rgb, indexedColor) else TerminalColors.background(rgb, indexedColor),
        )
    }
}
