package emberwood

import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Model-level route exercise of the terminal command adapter; separate PTY tests prove ANSI input. */
class TerminalRouteTest {
    @Test
    fun everyOriginalRouteAndGuardianWorksWithTerminalCommands() {
        val game = Game()
        val input = TerminalInput()
        game.startRun()
        val driver = TerminalRouteDriver(game, input)
        for (chapter in game.levels.indices) {
            for ((index, route) in game.level.definition.route
                .withIndex()) {
                assertTrue(
                    driver.follow(route),
                    "Terminal chapter ${chapter + 1}, route $index $route failed at ${game.player.position}; ${game.state}",
                )
            }
            if (game.boss != null) {
                assertTrue(driver.fightBoss(), "Terminal guardian ${game.boss?.name} failed at ${game.player.position}")
                assertTrue(driver.walk(game.level.gate.x))
            }
            assertEquals(3, game.level.takenAcorns.size, "Terminal chapter ${chapter + 1} required pickups")
            assertTrue(game.level.secretFound, "Terminal chapter ${chapter + 1} hidden firefly")
            if (chapter < game.levels.lastIndex) {
                assertEquals(GameState.CHAPTER_COMPLETE, game.state)
                game.nextChapter()
                input.clear()
            }
        }
        assertEquals(GameState.WON, game.state)
        assertEquals(0, game.deaths)
        assertEquals(13, game.equipment.unlocked.size)
        println("Terminal adapter campaign: ${game.elapsed}s, ${game.collectedGems}/${game.totalGems} gems, zero retries")
    }
}

/** Steering and jump commands only. The exported source route provides target surfaces. */
internal class TerminalRouteDriver(
    private val game: Game,
    private val input: TerminalInput,
) {
    private fun tick(
        targetX: Double? = null,
        jump: Boolean = false,
    ) {
        val distance = if (targetX == null) 0.0 else targetX - game.player.x
        val stop = max(4.0, abs(game.player.vx) * 0.06)
        val desired =
            when {
                distance < -stop -> -1
                distance > stop -> 1
                else -> 0
            }
        if (input.direction != desired) {
            input.key(
                when (desired) {
                    -1 -> "a"
                    1 -> "d"
                    else -> "s"
                },
            )
        }
        if (jump) input.key("space")
        game.step(1.0 / 60.0, input.frame(1.0 / 60.0))
    }

    fun walk(target: Double): Boolean {
        repeat(240) {
            tick(target)
            if (game.state == GameState.CHAPTER_COMPLETE || game.state == GameState.WON) return true
            if (game.state != GameState.PLAYING) return false
            if (abs(game.player.x - target) < 7 && abs(game.player.vx) < 30) return true
        }
        return false
    }

    fun follow(step: RouteStep): Boolean {
        if (step.kind == "walk") return walk(step.x)
        val moving =
            if (step.kind == "moving") {
                game.level.platforms.firstOrNull { it.kind == "moving" && abs(it.definition.bounds.x - step.x) < 2 }
            } else {
                null
            }
        if (step.kind != "bounce") repeat(2) { tick() }
        val launchX = game.player.x
        val clearShelf = moving == null && step.y < game.player.y && abs(step.x - game.player.x) < 120
        repeat(210) { frame ->
            val target = if (moving != null) Vec(moving.bounds.x + moving.bounds.w / 2, moving.bounds.y) else Vec(step.x, step.y)
            tick(
                if (clearShelf && frame < 4) launchX else target.x,
                jump = (frame == 0 && step.kind != "bounce") || frame == 20,
            )
            if (game.state == GameState.CHAPTER_COMPLETE || game.state == GameState.WON) return true
            if (game.state != GameState.PLAYING) return false
            if (step.kind == "mushroom" && game.player.vy < -700) return true
            if (game.player.grounded && abs(game.player.x - target.x) < 18 && abs(game.player.y - target.y) < 8) return true
        }
        return false
    }

    fun fightBoss(): Boolean {
        val boss = game.boss ?: return true
        if (!walk(boss.arenaMin + 60)) return false
        var previousPhase = ""
        var jumpFrame = -1
        var escapeX = boss.arenaMin + 300
        repeat(2400) { frame ->
            if (boss.defeated) return true
            if (game.state != GameState.PLAYING) return false
            if (boss.phase != previousPhase) {
                if (boss.phase == "warning") {
                    escapeX = boss.landingX + (if (boss.landingX < boss.arenaMin + 400) 210 else -210)
                } else if (boss.phase == "rest") {
                    jumpFrame = frame + 1
                }
                previousPhase = boss.phase
            }
            val target = if (boss.phase in listOf("warning", "leap", "landing", "hurt")) escapeX else boss.x
            tick(target, jump = jumpFrame >= 0 && frame - jumpFrame in listOf(0, 20))
        }
        return false
    }
}
