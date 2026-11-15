package emberwood

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/** Original licensed resources are loaded once and never modified on disk. */
object Assets {
    private val images = mutableMapOf<String, BufferedImage>()
    private val tints = mutableMapOf<Pair<String, String>, BufferedImage>()
    private val colors = mutableMapOf<String, Color>()
    val regular: Font = font("DejaVuSans.ttf")
    val bold: Font = font("DejaVuSans-Bold.ttf")

    private fun font(name: String): Font =
        requireNotNull(javaClass.getResourceAsStream("/assets/fonts/$name")) { "Missing bundled font: $name" }
            .use { Font.createFont(Font.TRUETYPE_FONT, it) }

    fun image(path: String): BufferedImage =
        images.getOrPut(path) {
            requireNotNull(javaClass.getResourceAsStream("/assets/${path.removePrefix("assets/")}")) { "Missing bundled artwork: $path" }
                .use { requireNotNull(ImageIO.read(it)) { "Cannot decode artwork: $path" } }
        }

    fun color(
        hex: String,
        tint: String? = null,
    ): Color =
        colors.getOrPut(if (tint == null) hex else "$hex@$tint") {
            val base = Color(hex.toInt(16))
            if (tint == null) {
                base
            } else {
                val filter = color(tint)
                Color(base.red * filter.red / 255, base.green * filter.green / 255, base.blue * filter.blue / 255)
            }
        }

    fun tinted(
        path: String,
        hex: String,
    ): BufferedImage =
        tints.getOrPut(path to hex) {
            val source = image(path)
            val tint = color(hex)
            val output = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until source.height) {
                for (x in 0 until source.width) {
                    val pixel = source.getRGB(x, y)
                    val red = ((pixel ushr 16) and 255) * tint.red / 255
                    val green = ((pixel ushr 8) and 255) * tint.green / 255
                    val blue = (pixel and 255) * tint.blue / 255
                    output.setRGB(x, y, (pixel and -0x1000000) or (red shl 16) or (green shl 8) or blue)
                }
            }
            output
        }
}

/** Canvas coordinates retain the Godot game's 1152 x 648 world scale. */
internal class Art(
    val g: Graphics2D,
) {
    init {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    }

    fun rect(
        x: Number,
        y: Number,
        width: Number,
        height: Number,
        hex: String,
    ) = rect(x, y, width, height, Assets.color(hex))

    fun rect(
        x: Number,
        y: Number,
        width: Number,
        height: Number,
        color: Color,
    ) {
        g.color = color
        g.fill(Rectangle2D.Double(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble()))
    }

    fun circle(
        x: Number,
        y: Number,
        radius: Number,
        hex: String,
    ) = circle(x, y, radius, Assets.color(hex))

    fun circle(
        x: Number,
        y: Number,
        radius: Number,
        color: Color,
    ) {
        val r = radius.toDouble()
        g.color = color
        g.fill(Ellipse2D.Double(x.toDouble() - r, y.toDouble() - r, r * 2, r * 2))
    }

    fun line(
        x1: Number,
        y1: Number,
        x2: Number,
        y2: Number,
        width: Number,
        hex: String,
    ) {
        g.color = Assets.color(hex)
        g.stroke = BasicStroke(width.toFloat(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER)
        g.draw(
            java.awt.geom.Line2D
                .Double(x1.toDouble(), y1.toDouble(), x2.toDouble(), y2.toDouble()),
        )
    }

    fun polygon(
        hex: String,
        vararg coords: Number,
    ) = polygon(Assets.color(hex), *coords)

    fun polygon(
        color: Color,
        vararg coords: Number,
    ) {
        val path = path(coords, true)
        g.color = color
        g.fill(path)
    }

    fun shape(
        fill: String,
        edge: String,
        vararg coords: Number,
    ) {
        val path = path(coords, true)
        g.color = Assets.color(fill)
        g.fill(path)
        g.color = Assets.color(edge)
        g.stroke = BasicStroke(2f)
        g.draw(path)
    }

    fun polyline(
        hex: String,
        width: Number,
        vararg coords: Number,
    ) {
        g.color = Assets.color(hex)
        g.stroke = BasicStroke(width.toFloat(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER)
        g.draw(path(coords, false))
    }

    private fun path(
        coords: Array<out Number>,
        close: Boolean,
    ): Path2D.Double {
        require(coords.size >= 4 && coords.size % 2 == 0)
        val path = Path2D.Double()
        path.moveTo(coords[0].toDouble(), coords[1].toDouble())
        for (i in 2 until coords.size step 2) path.lineTo(coords[i].toDouble(), coords[i + 1].toDouble())
        if (close) path.closePath()
        return path
    }

    fun arc(
        x: Number,
        y: Number,
        radius: Number,
        start: Double,
        extent: Double,
        width: Number,
        hex: String,
    ) {
        val r = radius.toDouble()
        g.color = Assets.color(hex)
        g.stroke = BasicStroke(width.toFloat())
        g.draw(Arc2D.Double(x.toDouble() - r, y.toDouble() - r, r * 2, r * 2, start, extent, Arc2D.OPEN))
    }

    fun image(
        path: String,
        x: Number,
        y: Number,
        width: Number,
        height: Number,
        tint: String? = null,
    ) {
        val image = if (tint == null) Assets.image(path) else Assets.tinted(path, tint)
        g.drawImage(image, x.toDouble().roundToInt(), y.toDouble().roundToInt(), width.toInt(), height.toInt(), null)
    }

    fun frame(
        path: String,
        frame: Int,
        frameWidth: Int,
        frameHeight: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        tint: String? = null,
    ) {
        val image = if (tint == null) Assets.image(path) else Assets.tinted(path, tint)
        g.drawImage(image, x, y, x + width, y + height, frame * frameWidth, 0, (frame + 1) * frameWidth, frameHeight, null)
    }

    fun text(
        value: String,
        x: Number,
        y: Number,
        size: Int,
        hex: String,
        outline: String? = null,
        centered: Boolean = false,
    ) {
        g.font = Assets.regular.deriveFont(size.toFloat())
        val left = x.toFloat() - if (centered) g.fontMetrics.stringWidth(value) / 2f else 0f
        if (outline != null) {
            g.color = Assets.color(outline)
            for ((dx, dy) in arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                g.drawString(value, left + dx, y.toFloat() + dy)
            }
        }
        g.color = Assets.color(hex)
        g.drawString(value, left, y.toFloat())
    }

    fun translated(
        x: Number,
        y: Number,
        scaleX: Double = 1.0,
        scaleY: Double = 1.0,
        draw: (Art) -> Unit,
    ) {
        val local = g.create() as Graphics2D
        try {
            local.translate(x.toDouble(), y.toDouble())
            local.scale(scaleX, scaleY)
            draw(Art(local))
        } finally {
            local.dispose()
        }
    }
}
