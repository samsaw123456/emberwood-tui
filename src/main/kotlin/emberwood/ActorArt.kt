package emberwood

import java.awt.AlphaComposite
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sin

/** Art follows model positions and timing; it cannot alter hitboxes or combat. */
internal object ActorArt {
    fun critter(
        a: Art,
        critter: Critter,
        age: Double,
        gentle: Boolean,
    ) {
        val position = critter.position(age)
        a.translated(position.x, position.y, critter.facing(age).toDouble()) { local ->
            when (critter.kind) {
                "slime" -> slime(local, age, gentle)
                "beetle" -> beetle(local, age, gentle)
                "bat" -> bat(local, age, gentle)
                else ->
                    local.frame(
                        "pixel/animals/sunnyland/opossum-run-sheet.png",
                        if (gentle) 0 else (age * 10).toInt() % 6,
                        36,
                        28,
                        -36,
                        -50,
                        72,
                        56,
                    )
            }
        }
    }

    private fun slime(
        a: Art,
        age: Double,
        gentle: Boolean,
    ) {
        val wobble = if (gentle) 0.0 else round(sin(age * 5) * 2)
        val edge = "183c36"
        a.shape(
            "88d662",
            edge,
            -25,
            -2,
            -26,
            -12,
            -22,
            -12,
            -22,
            -23,
            -16 + wobble,
            -23,
            -16 + wobble,
            -30,
            -7 + wobble,
            -30,
            -7 + wobble,
            -35,
            7 + wobble,
            -35,
            7 + wobble,
            -32,
            17,
            -32,
            17,
            -27,
            22,
            -27,
            22,
            -17,
            26,
            -17,
            26,
            -7,
            29,
            -7,
            29,
            0,
            18,
            0,
            18,
            3,
            9,
            3,
            9,
            0,
            -5,
            0,
            -5,
            3,
            -19,
            3,
            -19,
            0,
        )
        a.polygon(
            "399274",
            -24,
            -9,
            -15,
            -5,
            10,
            -5,
            25,
            -13,
            25,
            -2,
            15,
            -2,
            15,
            1,
            11,
            1,
            11,
            -2,
            -8,
            -2,
            -8,
            1,
            -17,
            1,
            -17,
            -2,
            -23,
            -2,
        )
        a.rect(-14 + wobble, -29, 8, 3, "e5ffc4")
        a.rect(-18 + wobble, -25, 4, 6, "c5f49c")
        a.rect(13, -13, 4, 4, "b2ef8e")
        a.rect(-20, -9, 3, 3, "b2ef8e")
        for (x in listOf(-10, 7)) {
            a.rect(x - 2, -22, 9, 12, edge)
            a.rect(x - 1, -22, 7, 8, "f3ffe2")
            a.rect(x, -18, 4, 6, "18343b")
        }
        a.line(-2, -8, 4, -8, 2, edge)
        a.rect(-1, -6, 5, 2, "c5f49c")
    }

    private fun beetle(
        a: Art,
        age: Double,
        gentle: Boolean,
    ) {
        val edge = "302936"
        repeat(3) { leg ->
            val x = -12 + leg * 13
            val step = if (gentle) 0.0 else round(sin(age * 13 + leg * PI) * 3)
            a.polyline("6b4650", 3, x, -12, x + 8, -5, x + 13 - step, 0)
        }
        a.shape("c66c38", edge, -16, -11, -17, -23, -10, -31, 1, -35, 16, -32, 24, -25, 27, -14, 20, -7, -7, -7)
        a.polygon("904347", 8, -32, 20, -26, 24, -15, 17, -10, 7, -10, 3, -28)
        a.polyline("ffce7b", 3, -12, -23, -7, -29, 0, -31, 8, -30)
        a.line(1, -32, 6, -10, 2, edge)
        for (y in listOf(-24, -18)) a.line(10, y, 18, y + 2, 2, "e9a35e")
        a.shape("6f4650", edge, -26, -23, -15, -22, -11, -15, -15, -7, -27, -7, -31, -13)
        for (offset in listOf(0, 8)) {
            a.polyline(edge, 3, -23 + offset, -22, -29 + offset, -31, -36 + offset, -33)
            a.rect(-38 + offset, -35, 4, 4, "ffd085")
        }
        a.rect(-27, -19, 7, 6, "fff1cb")
        a.rect(-27, -18, 3, 4, edge)
        a.line(-28, -10, -34, -6, 3, "edbe83")
        repeat(3) { leg ->
            val x = -13 + leg * 13
            val step = if (gentle) 0.0 else round(sin(age * 13 + leg * PI) * 3)
            a.polyline(edge, 3, x, -11, x - 4, -4, x - 8 + step, 1, x - 12 + step, 1)
            a.line(x - 4, -4, x - 8 + step, 0, 2, "d99564")
        }
    }

    private fun bat(
        a: Art,
        age: Double,
        gentle: Boolean,
    ) {
        val edge = "302b43"
        val flap = if (gentle) .45 else (sin(age * 11) + 1) * .5

        fun wingY(
            start: Double,
            end: Double,
        ) = round(start + (end - start) * flap)
        val tipY = wingY(-39.0, -5.0)
        for (side in listOf(-1, 1)) {
            a.shape(
                "a58ed4",
                edge,
                side * 10,
                -20,
                side * 24,
                wingY(-30.0, -17.0),
                side * 45,
                tipY,
                side * 38,
                wingY(-15.0, 7.0),
                side * 31,
                wingY(-20.0, -2.0),
                side * 26,
                wingY(-5.0, 5.0),
                side * 21,
                -10,
                side * 15,
                -3,
                side * 10,
                -8,
            )
            a.line(side * 12, -20, side * 38, wingY(-15.0, 7.0), 2, "6d508f")
            a.line(side * 12, -20, side * 26, wingY(-5.0, 5.0), 2, "6d508f")
            a.line(side * 12, -20, side * 15, -3, 2, "6d508f")
            a.line(side * 12, -21, side * 45, tipY, 2, "d9b9ec")
            a.shape("79628f", edge, side * 3, -23, side * 15, -41, side * 13, -20)
            a.line(side * 8, -24, side * 12, -33, 3, "ebaebf")
        }
        a.shape("695479", edge, -12, -26, 12, -26, 15, -20, 13, -6, 7, 0, -7, 0, -13, -6, -15, -20)
        a.rect(-9, -23, 18, 4, "9376a3")
        for (x in listOf(-9, 3)) {
            a.rect(x, -19, 7, 7, "ffe8a5")
            a.rect(x + 2, -18, 3, 5, edge)
        }
        a.rect(-3, -11, 6, 3, "d69bad")
        a.line(-6, -7, 6, -7, 2, edge)
        for (x in listOf(-5, 3)) a.rect(x, -7, 2, 4, "fff0d7")
    }

    fun fox(
        a: Art,
        game: Game,
    ) {
        val player = game.player
        val animation =
            when {
                game.state == GameState.RESPAWNING || game.state == GameState.PAUSED && game.respawnLeft > 0 -> "hurt"
                game.state in listOf(GameState.CHAPTER_COMPLETE, GameState.WON) -> "victory"
                !player.grounded && game.state != GameState.TITLE -> "jump"
                abs(player.vx) > 15 -> "run"
                else -> "idle"
            }
        val frame =
            when (animation) {
                "run" -> (game.level.age * 12).toInt() % 6
                "idle" -> (game.level.age * 7).toInt() % 4
                "jump" -> if (player.vy < 0) 0 else 1
                "hurt" -> if (game.respawnLeft > .8 - 1.0 / 7) 0 else 1
                else -> 0
            } + 1
        a.translated(player.x, player.y, player.facing.toDouble()) { local ->
            if (game.invincibleLeft > 0) {
                val alpha =
                    if (game.reducedMotion) {
                        .75f
                    } else if ((game.invincibleLeft * 10).toInt() % 2 == 0) {
                        1f
                    } else {
                        .65f
                    }
                local.g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
            }
            local.image("pixel/characters/sunnyland/fox-$animation-frame-${frame.toString().padStart(2, '0')}.png", -32, -64, 64, 64)
            game.equipment.armor?.let { armor(local, it, game.equipment.armorCharge > 0) }
            weapon(local, game.equipment.weaponId)
            if (player.dashLeft > 0 &&
                !game.reducedMotion
            ) {
                repeat(3) { row -> local.line(-20 - row * 5, -10 - row * 9, -34 - row * 5, -10 - row * 9, 2, "d8d8a2") }
            }
        }
    }

    private fun armor(
        a: Art,
        item: Item,
        charged: Boolean,
    ) {
        val base = if (charged) Assets.color(item.armorColor) else Assets.color(item.armorColor).darker()
        val edge = item.armorEdge
        a.rect(-7, -22, 16, 15, edge)
        a.rect(-5, -22, 12, 12, base)
        when (item.armorArt) {
            "leaf" -> {
                a.rect(-1, -21, 2, 13, "c3b171")
                a.line(-5, -18, 5, -14, 2, "526b48")
            }
            "bark", "ironwood", "copper" -> {
                for (x in listOf(-3, 3)) a.line(x, -21, x - 1, -10, 2, edge)
                a.rect(-9, -23, 7, 5, base.brighter())
                a.rect(5, -23, 7, 5, base.brighter())
                if (item.armorArt == "ironwood") {
                    for (x in listOf(-5, 5)) a.rect(x, -19, 2, 2, "d5d4a8")
                    a.rect(-6, -11, 14, 4, "b8c1ab")
                }
                if (item.armorArt == "copper") {
                    a.line(-5, -22, 6, -9, 3, edge)
                    for (x in listOf(-6, 6)) for (y in listOf(-21, -11)) a.rect(x, y, 3, 3, "e1bd80")
                }
            }
            "reeds" -> {
                for (x in listOf(-5, -1, 3, 7)) a.rect(x, -25, 2, 18, base.brighter())
                for (y in listOf(-20, -14, -9)) a.line(-7, y, 9, y, 2, edge)
            }
            "frost" -> {
                a.rect(-9, -26, 21, 6, "d8ded0")
                a.rect(-10, -20, 4, 14, base.darker())
                a.rect(-2, -20, 3, 12, "d8ded0")
                a.rect(7, -20, 4, 8, "d8ded0")
            }
            "slate" -> {
                for (y in listOf(-23, -17, -11)) {
                    a.rect(-7, y, 16, 5, base.brighter())
                    a.line(-6, y + 4, 8, y + 4, 1, edge)
                }
                a.rect(-11, -25, 8, 8, edge)
                a.rect(5, -25, 8, 8, edge)
            }
            "sunleaf" -> {
                for (side in listOf(-1, 1)) a.polygon(base.brighter(), side * 2, -23, side * 13, -29, side * 10, -18, side * 3, -15)
                a.rect(-2, -20, 5, 7, "f0dc9a")
                a.line(-5, -11, 7, -11, 2, edge)
            }
        }
        a.rect(-5, -8, 12, 3, "a5784e")
    }

    fun weapon(
        a: Art,
        id: String,
    ) {
        when (id) {
            "bow", "thorn_fan" -> {
                a.polyline("cb975b", 3, 15, -34, 21, -29, 24, -21, 21, -13, 15, -8)
                a.line(15, -33, 15, -9, 1, "f0ddad")
                a.rect(19, -24, 5, 6, "795239")
                if (id == "thorn_fan") for (offset in listOf(-6, 0, 6)) a.line(12, -19, 31, -19 + offset, 2, "b4ce78")
            }
            "spear", "sunwood_lance" -> {
                a.line(-13, -19, 32, -19, 3, "9b683d")
                a.line(-13, -20, 30, -20, 1, "d2ad70")
                a.polygon("c9d4c7", 29, -24, 42, -19, 29, -14)
                a.rect(26, -22, 4, 6, "5c7068")
                if (id == "sunwood_lance") a.rect(11, -25, 18, 4, "e2c16d")
            }
            "seed_bomb" -> {
                a.rect(-15, -19, 9, 14, "795b39")
                a.rect(-14, -18, 7, 3, "c29a55")
                a.circle(22, -21, 8, "547344")
                a.rect(17, -27, 7, 11, "a5b56a")
                a.line(22, -28, 25, -33, 2, "b6c98b")
                a.line(24, -31, 29, -32, 3, "7e9b56")
            }
        }
    }

    fun boss(
        a: Art,
        boss: Boss,
        gentle: Boolean,
    ) {
        if (boss.phase in listOf("warning", "leap")) {
            a.line(boss.landingX - 45, 534, boss.landingX + 45, 534, 3, "dfb87a")
            for (side in listOf(-1, 1)) a.line(boss.landingX + side * 45, 534, boss.landingX + side * 45, 526, 3, "dfb87a")
            a.line(boss.landingX - 6, 519, boss.landingX + 6, 529, 3, "dfb87a")
            a.line(boss.landingX - 6, 529, boss.landingX + 6, 519, 3, "dfb87a")
        }
        for (wave in boss.waves) {
            val direction = if (wave.speed < 0) -1 else 1
            a.polyline(
                "c5d9bd",
                4,
                wave.x - direction * 17,
                534,
                wave.x - direction * 7,
                525,
                wave.x + direction * 5,
                521,
                wave.x + direction * 16,
                534,
            )
            a.line(wave.x - 19, 536, wave.x + 18, 536, 3, "799b8b")
        }
        a.translated(boss.x, boss.y) { local ->
            val tint =
                when {
                    boss.defeated -> "b4c9b4"
                    boss.phase == "hurt" -> "efd7ad"
                    else -> null
                }
            val squash = boss.species != "frog" && boss.phase == "warning"
            val direction = if (boss.facingLeft) 1.0 else -1.0
            local.translated(0, 0, direction * if (squash) 1.04 else 1.0, if (squash) .94 else 1.0) { animal ->
                when (boss.species) {
                    "frog" -> {
                        val y =
                            when {
                                boss.phase == "leap" -> -128
                                boss.phase == "warning" -> -99
                                boss.defeated -> -96
                                else -> -108
                            }
                        if (boss.phase == "leap") {
                            val frame = if (boss.phaseTime < boss.profile.leap / 2) "01" else "02"
                            animal.image("pixel/animals/sunnyland/frog-jump-frame-$frame.png", -70, y, 140, 128, tint)
                        } else {
                            animal.frame(
                                "pixel/animals/sunnyland/frog-idle-sheet.png",
                                if (boss.defeated ||
                                    gentle
                                ) {
                                    0
                                } else {
                                    (boss.age * 5).toInt() % 4
                                },
                                35,
                                32,
                                if (boss.phase ==
                                    "warning"
                                ) {
                                    -74
                                } else {
                                    -70
                                },
                                y,
                                if (boss.phase ==
                                    "warning"
                                ) {
                                    148
                                } else {
                                    140
                                },
                                if (boss.phase ==
                                    "warning"
                                ) {
                                    118
                                } else if (boss.defeated) {
                                    114
                                } else {
                                    128
                                },
                                tint,
                            )
                        }
                    }
                    "opossum" ->
                        animal.frame(
                            "pixel/animals/sunnyland/opossum-run-sheet.png",
                            if (gentle ||
                                boss.phase != "leap"
                            ) {
                                0
                            } else {
                                (boss.age * 8).toInt() % 6
                            },
                            36,
                            28,
                            -66,
                            -94,
                            132,
                            100,
                            tint,
                        )
                    "eagle" ->
                        animal.frame(
                            "pixel/animals/sunnyland/eagle-fly-sheet.png",
                            if (gentle ||
                                boss.defeated
                            ) {
                                0
                            } else {
                                (boss.age * 7).toInt() % 4
                            },
                            40,
                            41,
                            -60,
                            -104,
                            120,
                            112,
                            tint,
                        )
                    else -> pixelGuardian(animal, boss.species, tint)
                }
            }
            val y = boss.capY
            val leaf = if (boss.phase == "rest") "b9aa68" else boss.profile.capColor
            val count = boss.chapter / 3
            repeat(count) { point ->
                val x = (point - (count - 1) / 2.0) * 9
                local.line(x, y + 2, x, y - 10, 3, leaf)
                local.rect(x - 2, y - 12, 4, 4, "ffe29b")
            }
            for (side in listOf(-1, 1)) {
                local.polygon(leaf, 0, y + 7, side * 24, y + 2, side * 37, y - 13, side * 10, y - 8)
                local.line(0, y + 5, side * 27, y - 7, 2, "766442")
                if (boss.phase == "rest") {
                    val bob = if (gentle) 0.0 else sin(boss.age * 5) * 3
                    local.polyline("ffe29b", 3, side * 32 - 4, y - 20 + bob, side * 32, y - 16 + bob, side * 32 + 4, y - 20 + bob)
                }
            }
            local.rect(-18, y + 6, 36, 7, if (boss.phase == "rest") "b79b57" else "766442")
            if (boss.phase == "rest") local.rect(-14, y + 4, 28, 4, "ffe29b")
        }
    }

    private fun pixelGuardian(
        a: Art,
        species: String,
        tint: String?,
    ) {
        val palette =
            when (species) {
                "boar" -> mapOf('d' to "352d2a", 'r' to "865d43", 'l' to "ad8364", 'p' to "c98b77", 'w' to "f4ead5", 'k' to "202c31")
                "wolf" -> mapOf('d' to "303c4c", 'r' to "788eaa", 'l' to "d3e0e8", 'p' to "a9bdcf", 'w' to "f4ead5", 'k' to "202c31")
                else -> mapOf('d' to "44352e", 'r' to "946c3c", 'l' to "d6b478", 'p' to "e6ac4e", 'w' to "ffe6a8", 'k' to "202c31")
            }
        GuardianPixels.rows.getValue(species).forEachIndexed { y, row ->
            row.forEachIndexed { x, ink ->
                if (ink !=
                    '.'
                ) {
                    a.rect((x - row.length / 2.0) * 4, y * 4 - 80, 4, 4, Assets.color(palette.getValue(ink), tint))
                }
            }
        }
    }
}
