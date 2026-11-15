package emberwood

import kotlin.math.roundToInt

/** Apple Terminal supports the xterm palette; modern truecolor terminals retain source RGB. */
object TerminalColors {
    val indexed: Boolean =
        when (System.getenv("EMBERWOOD_TUI_COLOR")) {
            "256" -> true
            "truecolor" -> false
            else -> System.getenv("TERM_PROGRAM") == "Apple_Terminal"
        }
    private val cube = intArrayOf(0, 95, 135, 175, 215, 255)

    fun foreground(
        rgb: Int,
        useIndexed: Boolean = indexed,
    ): String = sequence(rgb, true, useIndexed)

    fun background(
        rgb: Int,
        useIndexed: Boolean = indexed,
    ): String = sequence(rgb, false, useIndexed)

    private fun sequence(
        rgb: Int,
        foreground: Boolean,
        useIndexed: Boolean,
    ): String {
        val channel = if (foreground) 38 else 48
        return if (useIndexed) {
            "\u001b[$channel;5;${paletteIndex(rgb)}m"
        } else {
            "\u001b[$channel;2;${rgb ushr 16 and 255};${rgb ushr 8 and 255};${rgb and 255}m"
        }
    }

    internal fun paletteIndex(rgb: Int): Int {
        val red = rgb ushr 16 and 255
        val green = rgb ushr 8 and 255
        val blue = rgb and 255

        fun nearest(value: Int): Int {
            var best = 0
            for (index in 1..5) if (kotlin.math.abs(cube[index] - value) < kotlin.math.abs(cube[best] - value)) best = index
            return best
        }

        fun error(
            r: Int,
            g: Int,
            b: Int,
        ): Int = (red - r) * (red - r) + (green - g) * (green - g) + (blue - b) * (blue - b)
        val r = nearest(red)
        val g = nearest(green)
        val b = nearest(blue)
        val grayIndex = (((red + green + blue) / 3.0 - 8.0) / 10.0).roundToInt().coerceIn(0, 23)
        val gray = 8 + grayIndex * 10
        return if (error(gray, gray, gray) < error(cube[r], cube[g], cube[b])) 232 + grayIndex else 16 + 36 * r + 6 * g + b
    }
}
