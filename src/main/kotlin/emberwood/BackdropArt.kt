package emberwood

import java.awt.Color
import kotlin.math.PI
import kotlin.math.sin

/** Port of the original eight forest regions; distant shapes remain behind solid terrain. */
internal class BackdropArt {
    private val tree = "pixel/nature/sunnyland/tree.png"
    private val pine = "pixel/nature/sunnyland/pine.png"

    fun draw(
        a: Art,
        level: LevelState,
        camera: Double,
        time: Double,
        gentle: Boolean,
    ) {
        when (level.theme) {
            "mill" -> mill(a, camera, if (gentle) 0.0 else time)
            "canopy" -> canopy(a, camera)
            "cavern" -> cavern(a, camera)
            "ridge" -> ridge(a, camera, false, 0)
            "river" -> river(a, level.index - 5, camera, if (gentle) 0.0 else time)
            "frost" -> ridge(a, camera, true, level.index - 10)
            "ancient" -> ancient(a, level.index - 15, camera)
            else -> forest(a, camera)
        }
        if (level.theme in listOf("forest", "canopy", "ancient")) flies(a, camera, if (gentle) 0.0 else time)
        if (level.theme == "frost" && !gentle) {
            repeat(16) { flake ->
                a.rect(mod(flake * 173.0 - camera * .22 + time * 7, 1250.0) - 50, mod(flake * 71.0 + time * 10, 570.0), 2, 2, "dce6df")
            }
        }
    }

    private fun forest(
        a: Art,
        camera: Double,
    ) {
        val t = ((camera - 2600) / 1400).coerceIn(0.0, 1.0)
        val night = t * t * (3 - 2 * t)
        val top = mix("244855", "222b49", night)
        val bottom = mix("d7ba80", "718c9b", night)
        repeat(54) { band -> a.rect(0, band * 12, 1152, 13, mix(top, bottom, band / 53.0)) }
        val lightX = 875 - sin(camera * .0003) * 70
        a.circle(lightX, 260, 65, mix("edcf8e", "e4e6cd", night))
        for (i in -1..11) {
            val x = i * 120 - mod(camera * .10, 120.0)
            val height = 250 + sin(i * 5.7) * 70
            val shade = mix("6a897d", "607786", night)
            a.rect(x + 45, 510 - height, 12, height, shade)
            repeat(3) { tier ->
                val y = 510 - height + tier * 46
                val width = 38 + tier * 12
                a.polygon(shade, x + 51, y - 75, x + 51 - width, y + 36, x + 51 + width, y + 36)
            }
        }
        a.rect(0, 495, 1152, 153, mix("527573", "4a6575", night))
        trees(a, camera, .22, 290.0, "355960", .75)
        for (i in -1..4) {
            val x = i * 440 - mod(camera * .32, 440.0)
            a.image(tree, x, 252, 226, 270, "749380")
            a.image(pine, x + 254, 266, 148, 260, "658f80")
        }
        trees(a, camera, .47, 590.0, "173b43", 1.1)
        for (i in -1..8) {
            val x = i * 180 - mod(camera * .38, 180.0)
            a.circle(x, -40, 117, "163b44")
            a.circle(x + 90, -58, 124, "163b44")
            a.circle(x + 46, -35, 91, "1e454b")
        }
        repeat(7) { i ->
            val x = mod(i * 211.0 - camera * .4, 1400.0) - 80
            val y = 99 + sin(i * 4.0) * 29
            a.line(x, 0, x + 13, y, 3, "2e5655")
            a.circle(x + 8, y - 15, 5, "376561")
            a.circle(x + 16, y - 29, 5, "376561")
        }
    }

    private fun trees(
        a: Art,
        camera: Double,
        parallax: Double,
        spacing: Double,
        color: String,
        size: Double,
    ) {
        for (i in -1..(1152 / spacing).toInt() + 2) {
            val x = i * spacing - mod(camera * parallax, spacing)
            val w = 27 * size
            val lean = sin(i * 2.7) * 24
            a.polygon(
                color,
                x - w * 1.5,
                570,
                x - w * .5,
                240,
                x + lean - w * .35,
                -20,
                x + lean + w * .35,
                -20,
                x + w * .6,
                300,
                x + w * 1.7,
                570,
            )
            a.line(x, 243, x - 86 * size, 154, 14 * size, color)
            a.line(x, 310, x + 92 * size, 218, 11 * size, color)
            a.line(x - 58 * size, 181, x - 55 * size, 112, 7 * size, color)
            a.line(x + 59 * size, 250, x + 138 * size, 234, 6 * size, color)
            repeat(5) { leaf -> a.circle(x - 80 * size + leaf * 42 * size, 103 + sin(leaf * 3.1 + i) * 25, 60 * size, color) }
        }
    }

    private fun flies(
        a: Art,
        camera: Double,
        time: Double,
    ) {
        repeat(28) { i ->
            val phase = i * 2.399
            val x = mod(i * 157.0 - camera * .6 + sin(time * .4 + phase) * 15, 1400.0) - 100
            val y = 180 + mod(i * 91.0, 370.0) + sin(time * .6 + phase) * 12
            a.rect(x, y, 2, 2, Color(1f, .92f, .65f, (.4 + .3 * sin(time * 1.5 + phase)).toFloat()))
        }
    }

    private fun mill(
        a: Art,
        camera: Double,
        time: Double,
    ) {
        a.rect(0, 0, 1152, 648, "8d9992")
        repeat(6) { a.rect(0, 235 + it * 60, 1152, 61, mix("8d9992", "536d71", (it + 1) / 6.0)) }
        trees(a, camera, .12, 410.0, "657c75", .7)
        val x = 440 - camera * .24
        a.rect(x, 260, 360, 300, "535955")
        a.polygon("484e4c", x - 25, 260, x + 170, 148, x + 390, 260)
        for (post in 0..360 step 90) a.rect(x + post, 260, 13, 300, "353f3f")
        for (row in listOf(280, 390, 515)) a.rect(x, row, 360, 10, "353f3f")
        for (window in listOf(28, 123, 218)) {
            a.rect(x + window, 305, 44, 56, "adad8f")
            a.line(x + window + 22, 305, x + window + 22, 361, 5, "353f3f")
        }
        a.arc(x + 410, 455, 94, 0.0, 360.0, 11, "3b4948")
        a.arc(x + 410, 455, 75, 0.0, 360.0, 5, "78877c")
        repeat(10) { spoke ->
            val angle = time * .10 + spoke * PI * 2 / 10
            val dx = kotlin.math.cos(angle)
            val dy = sin(angle)
            a.line(x + 410, 455, x + 410 + dx * 96, 455 + dy * 96, 7, "3b4948")
            a.line(x + 410 + dx * 98 - dy * 16, 455 + dy * 98 + dx * 16, x + 410 + dx * 98 + dy * 16, 455 + dy * 98 - dx * 16, 7, "73837a")
        }
        a.circle(x + 410, 455, 14, "384748")
    }

    private fun canopy(
        a: Art,
        camera: Double,
    ) {
        a.rect(0, 0, 1152, 648, "9caba2")
        repeat(16) { a.rect(0, 260 + it * 26, 1152, 27, mix("9caba2", "d6c9a6", it / 15.0)) }
        repeat(3) { layer ->
            for (i in -1..6) {
                val x = i * 240 - mod(camera * (.05 + layer * .04), 240.0)
                a.polygon(listOf("95a99d", "7e9a8e", "647e76")[layer], x, 648, x + 110, 470 + layer * 40 + sin(i * 4.0) * 20, x + 220, 648)
            }
        }
        for (i in -1..2) {
            val x = i * 660 - mod(camera * .28, 660.0)
            a.polygon("596d63", x, 648, x + 35, 100, x + 66, -50, x + 92, -50, x + 80, 390, x + 100, 648)
            a.polyline("596d63", 22, x + 53, 355, x + 160, 312, x + 260, 220)
            a.image(pine, x + 115, 151, 164, 260, "8a9a78")
        }
    }

    private fun cavern(
        a: Art,
        camera: Double,
    ) {
        a.rect(0, 0, 1152, 648, "30473f")
        repeat(2) { layer ->
            val spacing = 340.0 + layer * 110
            for (i in -1..4) {
                val x = i * spacing - mod(camera * (.08 + layer * .10), spacing)
                val shoulder = 225 + sin(i * 3.7) * 65
                a.polygon(
                    if (layer ==
                        0
                    ) {
                        "40554a"
                    } else {
                        "526254"
                    },
                    x,
                    648,
                    x + 15,
                    shoulder + 150,
                    x + 70,
                    shoulder,
                    x + 165,
                    shoulder - 35,
                    x + 245,
                    shoulder + 70,
                    x + 310,
                    648,
                )
                a.polyline("344a40", 5, x + 70, shoulder, x + 120, shoulder + 100, x + 90, shoulder + 240)
            }
        }
        for (offset in listOf(0, 1500)) {
            val x = 705 - mod(camera * .14, 1500.0) + offset
            a.polygon("73856b", x, 0, x + 86, 0, x + 122, 250, x + 50, 315, x - 38, 230)
            a.line(x + 40, 0, x + 25, 110, 14, "c0c191")
        }
        for (i in -1..5) {
            val x = i * 290 - mod(camera * .32, 290.0)
            val tip = 100 + sin(i * 4.1) * 27
            a.polygon("263e36", x - 15, 0, x + 305, 0, x + 300, 75, x + 225, 95, x + 192, tip + 45, x + 155, 95, x + 70, 130, x + 28, 77)
            a.polyline("7c7755", 7, x + 66, 20, x + 92, 105, x + 75, 160, x + 115, 225)
            a.line(x + 80, 150, x + 42, 180, 3, "7c7755")
            a.line(x + 110, 214, x + 137, 199, 3, "7c7755")
            repeat(4) { a.rect(x + 145 + it * 13, 83 + it % 2 * 8, 9, 18 + it * 3, "72815b") }
        }
    }

    private fun ridge(
        a: Art,
        camera: Double,
        snow: Boolean,
        climb: Int,
    ) {
        val sky = if (snow) listOf("a8b9bf", "b5c6ca", "aebfc0", "bdcbca", "cbd3cd")[climb] else "dfcfaa"
        a.rect(0, 0, 1152, 648, sky)
        repeat(4) { cloud ->
            val x = cloud * 340 - mod(camera * .06, 340.0) - 70
            a.rect(x, 145 + cloud % 2 * 45, 150, 12, if (snow) "dce3d9" else "f0e5c8")
            a.rect(x + 35, 133 + cloud % 2 * 45, 83, 12, if (snow) "dce3d9" else "f0e5c8")
        }
        repeat(3) { layer ->
            for (i in -1..3) {
                val x = i * 480 - mod(camera * (.04 + layer * .05), 480.0)
                val peak = (if (snow) 185 else 235) + layer * 75 + sin(i * 4.0 + climb) * 32
                val color = (if (snow) listOf("92a9ac", "748e94", "536f79") else listOf("a7b4a7", "8b9a8e", "707e70"))[layer]
                a.polygon(color, x - 60, 648, x + 160, peak, x + 220, peak + 75, x + 300, peak + 58, x + 490, 648)
                if (snow ||
                    layer == 0
                ) {
                    a.polygon(
                        "dce3d9",
                        x + 108,
                        peak + 94,
                        x + 160,
                        peak,
                        x + 220,
                        peak + 75,
                        x + 186,
                        peak + 61,
                        x + 160,
                        peak + 47,
                    )
                }
            }
        }
        for (i in -1..3) {
            val x = i * 410 - mod(camera * .31, 410.0)
            if (!snow || climb != 2) {
                a.image(pine, x, 292, 150, 260, if (snow) "7e9a93" else "8c9875")
                if (snow) {
                    repeat(
                        3,
                    ) { tier -> a.line(x + 77 - 19 - tier * 11, 334 + tier * 44, x + 77 + 19 + tier * 11, 334 + tier * 44, 5, "cbd9d1") }
                }
            } else {
                repeat(4) {
                    a.polygon(
                        "607d82",
                        x + it * 37,
                        590,
                        x + it * 37 + 43,
                        384 + it * 19,
                        x + it * 37 + 67,
                        407 + it * 19,
                        x + it * 37 + 61,
                        590,
                    )
                }
            }
        }
        if (snow && climb in listOf(1, 3)) waterfall(a, 690 - camera * .16, 280.0, 55.0, 270.0, 0.0)
        if (snow && climb == 4) {
            repeat(4) { cloud ->
                val x = cloud * 360 - mod(camera * .09, 360.0)
                a.rect(x - 100, 388 + cloud % 2 * 33, 245, 18, "d0dad3")
                a.rect(x - 56, 374 + cloud % 2 * 33, 149, 18, "d0dad3")
            }
        }
    }

    private fun river(
        a: Art,
        reach: Int,
        camera: Double,
        time: Double,
    ) {
        a.rect(0, 0, 1152, 648, listOf("a4b8a4", "9caf9c", "b7bca6", "9eafa4", "aabdb0")[reach])
        trees(a, camera, .10, 370.0, "799184", .64)
        a.rect(0, 420, 1152, 228, "6e928b")
        repeat(
            5,
        ) { row -> for (i in -1..5) a.rect(i * 230 - mod(camera * .12 + time * 4, 230.0) + row * 31, 439 + row * 32, 107, 3, "9bb2a0") }
        when (reach) {
            2 -> aqueduct(a, 270 - camera * .20)
            4 -> {
                val x = 720 - camera * .17
                a.rect(x - 100, 200, 340, 370, "698576")
                waterfall(a, x, 218.0, 90.0, 330.0, time)
                aqueduct(a, x - 540)
            }
            else -> for (i in -1..3) {
                val x = i * 470 - mod(camera * .28, 470.0)
                a.image(tree, x, 270, 230, 260, "75967f")
                if (reach == 1) repeat(6) { a.line(x + 30 + it * 29, 300, x + 38 + it * 29, 390 + it % 3 * 27, 4, "64866b") }
                if (reach == 3) {
                    a.line(x + 290, 485, x + 290, 455, 3, "536f69")
                    a.line(x + 290, 455, x + 279, 437, 5, "536f69")
                    a.line(x + 279, 437, x + 267, 439, 3, "536f69")
                }
            }
        }
        for (i in -1..8) {
            val x = i * 160 - mod(camera * .40, 160.0)
            repeat(4) { stem ->
                val y = 516 - stem % 3 * 18
                a.line(x + stem * 11, 610, x + stem * 13, y, 3, "547b65")
                a.rect(x + stem * 13 - 3, y - 15, 6, 18, "6e7860")
            }
        }
    }

    private fun aqueduct(
        a: Art,
        start: Double,
    ) {
        repeat(5) { i ->
            val x = start + i * 200
            if (i != 2) {
                a.rect(x - 7, 255, 214, 35, "74877b")
                a.line(x, 267, x + 197, 267, 4, "a1ad96")
            }
            a.rect(x - 9, 305, 30, 270, "697d72")
            a.arc(x + 102, 365, 94, 0.0, 180.0, 21, "7d8d7e")
            repeat(5) { a.line(x - 8, 340 + it * 42, x + 20, 340 + it * 42, 3, "536e64") }
        }
    }

    private fun waterfall(
        a: Art,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        time: Double,
    ) {
        a.rect(x, y, width, height, "a0b6ae")
        repeat(5) { a.rect(x + it * width / 6 + 4, y, width / 6 * .55, height, "b4cbc0") }
        repeat((height / 52).toInt()) {
            a.line(x + 6, y + it * 52 + mod(time * 26, 52.0), x + width - 6, y + it * 52 + mod(time * 26, 52.0), 2, "ceddd4")
        }
    }

    private fun ancient(
        a: Art,
        depth: Int,
        camera: Double,
    ) {
        a.rect(0, 0, 1152, 648, listOf("a7aa7d", "929d7c", "6d8372", "b5b184", "b9b990")[depth])
        trees(a, camera, .07, 410.0, "778c6c", .85)
        for (i in -1..3) {
            val x = i * 520 - mod(camera * .25, 520.0)
            val bark = if (depth == 2) "4b6552" else "6a6e49"
            a.polygon(bark, x - 15, 648, x + 49, 290, x + 58, -20, x + 107, -20, x + 101, 320, x + 155, 648)
            a.polyline(bark, 19, x + 72, 265, x + 191, 190, x + 295, 170)
            repeat(5) { leaf -> a.rect(x - 24 + leaf * 64, 60 + sin(leaf * 3.4 + i) * 25, 106, 64, if (depth < 3) "7f8c51" else "a3a464") }
            if (depth == 0) repeat(6) { a.line(x + 61, 165 + it * 55, x + 91, 152 + it * 55, 5, "b18958") }
            if (depth == 2) {
                a.line(x + 219, 180, x + 219, 235, 3, "4d6250")
                a.rect(x + 209, 235, 20, 27, "596148")
                a.rect(x + 213, 239, 12, 18, "d6bf79")
            }
            if (depth == 3) {
                a.line(x + 80, 365, x + 300, 292, 12, "77804c")
                repeat(4) { a.rect(x + 145 + it * 42, 317 - it * 14, 33, 13, "b8b66b") }
            }
        }
        if (depth == 1) {
            repeat(4) { pillar ->
                val x = 210 - camera * .16 + pillar * 260
                val top = 315 + pillar % 2 * 38
                a.rect(x, top, 61, 270, "737e61")
                a.rect(x - 9, top - 13, 79, 23, "8c9371")
                repeat(5) { a.line(x + 2, top + 33 + it * 43, x + 60, top + 33 + it * 43, 3, "596e53") }
                a.polyline("4f694d", 9, x + 17, top - 10, x + 44, top + 52, x + 20, top + 141, x + 49, top + 237)
            }
        }
        if (depth == 4) {
            val x = 760 - camera * .14
            a.polygon("5b714e", x - 190, 648, x - 80, 350, x - 63, 60, x + 65, 60, x + 92, 370, x + 240, 648)
            for (side in listOf(-1, 1)) {
                a.polyline("5b714e", 36, x, 185, x + side * 140, 110, x + side * 260, 92)
                a.polyline("5b714e", 29, x + side * 65, 442, x + side * 220, 525, x + side * 330, 555)
            }
            repeat(5) { a.arc(x + 4, 303, 27 + it * 9, -131.4, 230.4, 3, "89905c") }
            repeat(7) { a.rect(x - 300 + it * 84, 19 + it % 3 * 17, 137, 82, "929c57") }
        }
    }

    private fun mod(
        value: Double,
        divisor: Double,
    ) = ((value % divisor) + divisor) % divisor

    private fun mix(
        first: String,
        second: String,
        amount: Double,
    ) = mix(Assets.color(first), Assets.color(second), amount)

    private fun mix(
        first: Color,
        second: Color,
        amount: Double,
    ) = Color(
        (first.red + (second.red - first.red) * amount).toInt().coerceIn(0, 255),
        (first.green + (second.green - first.green) * amount).toInt().coerceIn(0, 255),
        (first.blue + (second.blue - first.blue) * amount).toInt().coerceIn(0, 255),
    )
}
