package emberwood

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

/** Draws only the world in the original logical viewport; desktop code adds HUD and menus. */
class WorldRenderer {
    private val backdrop = BackdropArt()
    private val tree = "pixel/nature/sunnyland/tree.png"
    private val pine = "pixel/nature/sunnyland/pine.png"
    private val bush = "pixel/nature/sunnyland/bush.png"
    private val rock = "pixel/nature/sunnyland/rock-2.png"
    private val mushroom = "pixel/nature/sunnyland/shrooms.png"
    private val gem = "pixel/items/sunnyland/gem-sparkle-sheet.png"
    private val spike = "pixel/hazards/sunnyland/spikes.png"

    fun draw(
        graphics: Graphics2D,
        game: Game,
        cameraOverride: Double = game.cameraX,
    ) {
        val a = Art(graphics)
        val camera = round(cameraOverride)
        val level = game.level
        backdrop.draw(a, level, camera, level.age, game.reducedMotion)
        a.translated(-camera, 0) { world ->
            water(world, level, camera, game.reducedMotion)
            decorations(world, level, camera)
            level.platforms.filter { it.kind != "wall" && visible(it.bounds.x, camera, it.bounds.w) }.forEach { platform(world, it, level) }
            for (patch in level.spikes) {
                if (!visible(patch.x, camera, patch.w)) continue
                var x = patch.x
                while (x < patch.right) {
                    world.image(spike, x, patch.y, min(30.0, patch.right - x), 20)
                    x += 30
                }
                world.rect(patch.x, patch.bottom - 3, patch.w, 3, "e2987f")
            }
            collectibles(world, level, camera, game.reducedMotion)
            camps(world, level, camera, game.reducedMotion)
            level.critters
                .filter {
                    !it.out && visible(it.position(level.age).x, camera)
                }.forEach { ActorArt.critter(world, it, level.age, game.reducedMotion) }
            gate(world, level, game.reducedMotion)
            for (point in level.mushrooms) {
                if (!visible(point.x, camera)) continue
                world.image(mushroom, point.x - 34, point.y - 42, 68, 42)
                world.arc(point.x, point.y - 28, 40, 21.6, 136.8, 2, "c88c9c")
            }
            for (sign in level.signs) {
                if (!visible(sign.position.x, camera)) continue
                // Signs are read in the terminal status line; only the post belongs in the scene.
                world.rect(sign.position.x, sign.position.y, 5, 30, "a87e67")
                world.rect(sign.position.x - 10, sign.position.y, 25, 13, "ddba78")
            }
            game.boss?.let { boss ->
                ActorArt.boss(world, boss, game.reducedMotion)
                if (boss.engaged && !boss.defeated) {
                    val x = boss.arenaMin - 100
                    for (offset in listOf(0, 8, 16)) {
                        world.line(x + offset, 536, x + offset, 250, 5, "6a7653")
                        for (y in 270..529 step 38) world.line(x + offset, y, x - 16 + offset, y - 18, 5, "a4b979")
                    }
                }
            }
            ActorArt.fox(world, game)
            for (shot in game.shots) if (visible(shot.x, camera, 200.0)) projectile(world, shot, game.reducedMotion)
            for (particle in game.particles) {
                if (!visible(particle.x, camera)) continue
                val color = Assets.color(particle.color)
                world.rect(
                    particle.x,
                    particle.y,
                    4,
                    4,
                    Color(color.red, color.green, color.blue, (min(1.0, particle.life * 2) * 255).toInt().coerceIn(0, 255)),
                )
            }
        }
    }

    private fun visible(
        x: Double,
        camera: Double,
        width: Double = 140.0,
    ) = x + width > camera - 150 && x < camera + 1302

    private fun platform(
        a: Art,
        platform: Platform,
        level: LevelState,
    ) {
        val r = platform.bounds
        if (!platform.solid) {
            a.rect(r.x, r.y, r.w, r.h, Color(255, 186, 102, 40))
            return
        }
        val themeTint =
            when (level.theme) {
                "mill" -> "c7c4af"
                "canopy" -> "c0cda7"
                "cavern" -> "a4b8a1"
                "ridge" -> "dbc99b"
                "river" -> "b3c4ae"
                "frost" -> "b8d4d1"
                "ancient" -> "c6c18c"
                else -> "ffffff"
            }
        if (platform.kind == "ground") {
            a.rect(r.x, r.y, r.w, r.h, "393748")
            val tile = Assets.tinted("pixel/terrain/sunnyland/block-big.png", "6e7a99")
            val clip = a.g.clipBounds
            val left = maxOf(r.x, (clip?.x ?: r.x.toInt()).toDouble())
            var x = r.x + ((left - r.x) / 64).toInt() * 64
            val right = min(r.right, (clip?.let { it.x + it.width } ?: r.right.toInt()).toDouble())
            while (x < right) {
                var y = r.y
                while (y < r.bottom) {
                    val width = min(64.0, r.right - x).toInt()
                    val height = min(64.0, r.bottom - y).toInt()
                    a.g.drawImage(tile, x.toInt(), y.toInt(), x.toInt() + width, y.toInt() + height, 0, 0, width / 2, height / 2, null)
                    y += 64
                }
                x += 64
            }
            a.rect(r.x, r.y + 8, r.w, min(8.0, r.h - 8), "51806b")
            a.rect(r.x, r.y, r.w, min(8.0, r.h), if (level.theme == "frost") "cadbc5" else "a8c87c")
            for (i in 0 until (r.w / 34).toInt()) {
                val tuft = r.x + 13 + i * 34
                if (tuft < left - 20 || tuft > right) continue
                a.rect(tuft, r.y - 3, 3, 5, "a8c87c")
                a.rect(tuft + 5, r.y - 5, 3, 7, "a8c87c")
                a.rect(tuft + 11, r.y + 1, 3, 10, "88b675")
            }
        } else {
            val tint =
                if (platform.kind == "crumble") {
                    if (platform.crumbleTime >= 0 && (platform.crumbleTime * 14).toInt() % 2 == 0) "ffe2a4" else "f4b47c"
                } else {
                    themeTint
                }
            val image = Assets.tinted("pixel/terrain/sunnyland/platform-long.png", tint)
            var x = r.x
            while (x < r.right) {
                val width = min(64.0, r.right - x).toInt()
                a.g.drawImage(image, x.toInt(), r.y.toInt(), x.toInt() + width, r.y.toInt() + 32, 0, 0, width / 2, 16, null)
                x += 64
            }
            if (platform.kind == "moving") {
                a.rect(r.x + 2, r.y, r.w - 4, 4, "8edbdc")
                for (edge in listOf(r.x + 8, r.right - 8)) a.line(edge, r.y + 9, edge, r.y + 21, 3, "628c9b")
            } else if (platform.kind == "crumble") {
                a.rect(r.x + 2, r.y, r.w - 4, 4, "efad72")
                for (i in 0 until (r.w / 40).toInt()) {
                    val crack = r.x + 17 + i * 40
                    a.polyline("825851", 2, crack, r.y + 3, crack - 4, r.y + 11, crack + 3, r.y + 20)
                }
            }
        }
    }

    private fun decorations(
        a: Art,
        level: LevelState,
        camera: Double,
    ) {
        for (platform in level.platforms) {
            if (platform.kind != "ground") continue
            val r = platform.bounds
            var x = r.x + 55
            while (x < r.right - 90) {
                if (visible(x, camera, 250.0)) decoration(a, level.theme, x, (x / 53).toInt() % 4)
                x += 170 + x.toInt() % 110
            }
        }
    }

    private fun decoration(
        a: Art,
        theme: String,
        x: Double,
        kind: Int,
    ) {
        when (theme) {
            "cavern" ->
                if (kind % 2 == 0) {
                    a.image(rock, x - 50, 455, 100, 86, "a2b09a")
                    repeat(3) { i ->
                        a.polyline(
                            "7f8058",
                            3,
                            x - 27 + i * 19,
                            486 + i * 9,
                            x - 18 + i * 19,
                            505 + i * 9,
                            x - 24 + i * 19,
                            526 + i * 9,
                        )
                    }
                } else {
                    a.image(mushroom, x - 25, 502, 50, 38, "a9b990")
                    a.image(bush, x + 16, 512, 45, 28, "87a778")
                }
            "river" ->
                if (kind == 0) {
                    a.image(tree, x - 95, 338, 190, 198, "98b499")
                } else {
                    repeat(5) { stem ->
                        val tipX = x - 29 + stem * 13
                        val tipY = 498 - stem % 3 * 11
                        a.line(x - 20 + stem * 11, 536, tipX, tipY, 3, "63834b")
                        a.rect(tipX - 3, tipY - 12, 6, 15, "a19657")
                    }
                }
            "frost" ->
                when (kind) {
                    0 -> {
                        a.image(pine, x - 70, 294, 140, 242, "96b5ad")
                        repeat(3) {
                            a.line(
                                x - 18 - it * 9,
                                346 + it * 42,
                                x + 18 + it * 9,
                                346 + it * 42,
                                6,
                                "d3e0d5",
                            )
                        }
                    }
                    2 -> {
                        a.image(rock, x - 38, 475, 76, 65, "acc5c2")
                        a.line(x - 20, 487, x + 18, 487, 5, "dce5da")
                    }
                    else -> a.image(bush, x - 32, 502, 64, 39, "8daea0")
                }
            "ancient" ->
                when (kind) {
                    0 -> a.image(tree, x - 120, 308, 240, 228, "b5ad70")
                    2 -> {
                        a.image(rock, x - 38, 473, 76, 68, "b5b68c")
                        repeat(3) { a.line(x - 36 + it * 18, 524, x - 15 + it * 20, 536, 5, "7c8151") }
                    }
                    else -> a.image(bush, x - 46, 488, 92, 56, "bdbe78")
                }
            else ->
                when (kind) {
                    0 ->
                        if (theme ==
                            "ridge"
                        ) {
                            a.image(pine, x - 75, 286, 150, 250, "b2b78a")
                        } else {
                            a.image(tree, x - 119, 314, 238, 222, "a2bdab")
                        }
                    1 -> a.image(bush, x - 46, 488, 92, 56, "abc6a2")
                    2 -> a.image(rock, x - 46, 461, 92, 80)
                    else -> a.image(mushroom, x - 16, 512, 32, 30, "a5a0ba")
                }
        }
    }

    private fun water(
        a: Art,
        level: LevelState,
        camera: Double,
        gentle: Boolean,
    ) {
        if (level.theme !in listOf("mill", "river")) return
        var bankEnd = 0.0
        for (platform in level.platforms.filter { it.kind == "ground" }) {
            val start = bankEnd
            val end = platform.bounds.x
            if (end > start && visible(start, camera, end - start)) {
                a.rect(start, 600, end - start, 120, "365d66")
                repeat(4) { row ->
                    val shift = if (gentle) 0.0 else (level.age * 12 + row * 17) % 70
                    var x = start
                    while (x <
                        end
                    ) {
                        a.line(min(x + shift, end), 607 + row * 14, min(x + shift + 29, end), 607 + row * 14, 2, "729494")
                        x += 70
                    }
                }
            }
            bankEnd = platform.bounds.right
        }
        if (level.theme ==
            "mill"
        ) {
            level.platforms.filter { it.kind == "ledge" && visible(it.bounds.x, camera) }.forEach {
                a.rect(
                    it.bounds.center.x - 5,
                    it.bounds.y + 25,
                    10,
                    680 - it.bounds.y,
                    "494b49",
                )
            }
        }
    }

    private fun collectibles(
        a: Art,
        level: LevelState,
        camera: Double,
        gentle: Boolean,
    ) {
        val frame = if (gentle) 0 else (level.age * 8).toInt() % 5
        level.gems.forEachIndexed { index, point ->
            if (index !in level.takenGems && visible(point.x, camera)) {
                val bob = if (gentle) 0.0 else sin(level.age * 2.5 + index) * 4
                a.frame(gem, frame, 15, 13, (point.x - 15).toInt(), (point.y + bob - 13).toInt(), 30, 26)
            }
        }
        level.acorns.forEachIndexed { index, point ->
            if (index !in level.takenAcorns && visible(point.x, camera)) {
                a.translated(point.x, point.y + if (gentle) 0.0 else sin(level.age * 2.5) * 4) { local ->
                    local.circle(0, 0, 27, Color(255, 194, 102, 31))
                    local.rect(-9, -4, 18, 18, "ffd68b")
                    local.rect(-5, 14, 10, 4, "efab62")
                    local.rect(-13, -10, 26, 9, "a8674d")
                    local.rect(-9, -14, 18, 4, "dd9562")
                    local.rect(0, -21, 4, 8, "b8d287")
                    local.rect(4, -20, 9, 4, "c5e6a0")
                    local.rect(-5, 0, 4, 9, "fff2bd")
                }
            }
        }
        if (!level.secretFound && visible(level.secret.x, camera)) {
            val pulse = if (gentle) 1.0 else .85 + .15 * sin(level.age * 3)
            a.circle(level.secret.x, level.secret.y, 20 * pulse, Color(255, 230, 128, 46))
            a.circle(level.secret.x, level.secret.y, 6, "fff5a5")
            for (side in listOf(-1, 1)) a.circle(level.secret.x + side * 9, level.secret.y - 4, 5, Color(230, 255, 230, 179))
        }
    }

    private fun camps(
        a: Art,
        level: LevelState,
        camera: Double,
        gentle: Boolean,
    ) {
        level.camps.forEachIndexed { index, point ->
            if (!visible(point.x, camera)) return@forEachIndexed
            a.translated(point.x, point.y) { local ->
                local.line(-20, -4, 20, -4, 7, "a87e67")
                local.line(-14, -12, 14, 2, 6, "715550")
                if (index <= level.campIndex) {
                    val flicker = if (gentle) 0.0 else sin(level.age * 12) * 3
                    local.circle(0, -17, 36, Color(255, 158, 77, 31))
                    local.polygon("ffa65d", -12, -7, -6, -25, 2, -40 - flicker, 5, -22, 13, -6)
                    local.circle(0, -13, 7, "ffe6a1")
                } else {
                    for (item in Equipment.ITEMS.filter { it.chapter == level.index && it.camp == index }) {
                        local.translated(0, if (level.index >= 5) -90 else -66) { reward ->
                            if (item.category == "weapon") {
                                reward.translated(-15, 20) { ActorArt.weapon(it, item.id) }
                            } else {
                                reward.polygon(item.armorColor, -14, -16, 14, -16, 12, 5, 0, 19, -12, 5)
                                reward.line(0, -12, 0, 12, 3, "e6d9a8")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun gate(
        a: Art,
        level: LevelState,
        gentle: Boolean,
    ) {
        val unlocked = level.takenAcorns.size == level.acorns.size && level.gateOpen
        a.translated(level.gate.x, level.gate.y) { local ->
            local.rect(-42, -100, 84, 100, "223c40")
            local.arc(0, -93, 43, 0.0, 180.0, 12, "b0b2aa")
            for (side in listOf(-1, 1)) {
                local.rect(side * 45 - 7, -96, 14, 96, "889c94")
                for (y in 18..95 step 24) local.line(side * 45 - 7, -y, side * 45 + 7, -y, 3, "536f6c")
            }
            if (unlocked) {
                local.rect(-33, -94, 66, 94, Color(153, 242, 179, 77))
                repeat(6) { n ->
                    val time = if (gentle) 0.0 else level.age
                    val y = ((n * 19 - time * 25) % 94 + 94) % 94
                    local.circle(sin(n * 6.0 + time) * 22, -y, 3, "c3f5be")
                }
            }
            repeat(3) { local.circle((it - 1) * 24, -150, 7, if (it in level.takenAcorns) "ffdc96" else "526761") }
        }
    }

    private fun projectile(
        a: Art,
        shot: Shot,
        gentle: Boolean,
    ) {
        a.translated(shot.x, shot.y) { local ->
            if (shot.burstLeft > 0) {
                val progress = 1 - shot.burstLeft / .24
                val spread = shot.weapon.radius * if (gentle) .55 else .25 + progress * .75
                local.g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (1 - progress).toFloat().coerceIn(0f, 1f))
                repeat(10) { index ->
                    local.rect(
                        cos(index * PI * 2 / 10) * spread - 4,
                        sin(index * PI * 2 / 10) * spread - 4,
                        8,
                        8,
                        if (index %
                            2 ==
                            0
                        ) {
                            "e1c281"
                        } else {
                            "9db777"
                        },
                    )
                }
                local.rect(-9, -9, 18, 18, "e0c482")
            } else if (shot.weapon.id == "seed_bomb") {
                if (!gentle) local.g.rotate(shot.age * shot.direction * 5)
                local.rect(-7, -6, 14, 12, "9d633c")
                local.rect(-5, -7, 9, 5, "d3ae68")
                local.rect(-3, -11, 3, 6, "71915b")
                local.rect(0, -10, 6, 3, "9aae6a")
            } else {
                local.g.rotate(atan2(shot.vy, shot.vx))
                when (shot.weapon.id) {
                    "spear", "sunwood_lance" -> {
                        local.line(-48, 0, -10, 0, 4, "ad7951")
                        local.line(-31, -3, -26, 3, 2, "d5b97d")
                        local.polygon("c6d3ca", -14, -6, 0, 0, -14, 6, -10, 0)
                        if (shot.weapon.id == "sunwood_lance") local.line(-42, -4, -20, -4, 3, "e2c16d")
                    }
                    "thorn_fan" -> local.polygon("b4ce78", -18, -5, 0, 0, -18, 5, -12, 0)
                    else -> {
                        local.line(-24, 0, -5, 0, 3, "b88c55")
                        local.line(-23, -4, -17, 0, 3, "c8d6a5")
                        local.line(-23, 4, -17, 0, 3, "c8d6a5")
                        local.polygon("f3e4bd", -7, -4, 0, 0, -7, 4)
                    }
                }
            }
        }
    }
}
