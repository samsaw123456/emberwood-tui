package emberwood

import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimulationTest {
    private val dt = 1.0 / 60.0

    private fun flat(): Game {
        val source = Levels.all.first()
        val world =
            source.copy(
                platforms = listOf(PlatformDef("ground", Rect(0.0, 536.0, 2000.0, 180.0))),
                spikes = emptyList(),
                critters = emptyList(),
                mushrooms = emptyList(),
                gate = Vec(1900.0, 536.0),
            )
        return Game(listOf(world)).apply {
            startRun()
            repeat(3) { step(dt) }
        }
    }

    private fun advance(
        game: Game,
        frames: Int,
        input: InputFrame = InputFrame(),
    ) {
        repeat(frames) {
            game.step(
                dt,
                input.copy(
                    jumpPressed =
                        input.jumpPressed && it == 0,
                    dashPressed = input.dashPressed && it == 0,
                    shootPressed = input.shootPressed && it == 0,
                ),
            )
        }
    }

    @Test fun originalContentIsComplete() {
        assertEquals(20, Levels.all.size)
        assertEquals(60, Levels.all.sumOf { it.acorns.size })
        assertEquals(378, Levels.all.sumOf { it.gems.size })
        assertTrue(Levels.all.all { it.route.isNotEmpty() && it.platforms.isNotEmpty() && it.camps.isNotEmpty() })
        assertEquals(
            setOf("opossum", "slime", "beetle", "bat"),
            Levels.all
                .flatMap { it.critters }
                .map { it.kind }
                .toSet(),
        )
        assertEquals(5, Equipment.ITEMS.count { it.category == "weapon" })
        assertEquals((1..8).toList(), Equipment.ITEMS.filter { it.category == "armor" }.map { it.capacity })
        assertEquals(6, Boss.PROFILES.size)
        assertEquals(
            6,
            Boss.PROFILES
                .map { it.species }
                .toSet()
                .size,
        )
    }

    @Test fun motionOpposingInputsBrakingAndPause() {
        val g = flat()
        val x = g.player.x
        advance(g, 25, InputFrame(right = true))
        assertTrue(g.player.x > x + 70)
        advance(g, 12)
        assertEquals(0.0, g.player.vx)
        val right = g.player.x
        advance(g, 30, InputFrame(left = true))
        assertTrue(g.player.x < right - 85)
        advance(g, 12, InputFrame(left = true, right = true))
        assertEquals(0.0, g.player.vx)
        val stopped = g.player.position
        val age = g.level.age
        g.pause()
        advance(g, 60, InputFrame(left = true, jumpHeld = true, jumpPressed = true, dashPressed = true, shootPressed = true))
        assertEquals(stopped, g.player.position)
        assertEquals(age, g.level.age)
        g.resume()
        advance(g, 12, InputFrame(right = true))
        assertTrue(g.player.x > stopped.x)
    }

    @Test fun jumpHeightAirBudgetAndEarlyRelease() {
        fun apex(short: Boolean): Double {
            val g = flat()
            g.step(dt, InputFrame(jumpHeld = true, jumpPressed = true))
            var top = g.player.y
            repeat(50) {
                g.step(dt, InputFrame(jumpHeld = !short, jumpReleased = short && it == 0))
                top = minOf(top, g.player.y)
            }
            return 536 - top
        }
        assertTrue(apex(false) > 85)
        assertTrue(apex(true) < apex(false) * 0.65)
        val g = flat()
        g.step(dt, InputFrame(jumpHeld = true, jumpPressed = true))
        assertEquals(1, g.player.jumpsLeft)
        g.step(dt, InputFrame(jumpReleased = true))
        g.step(dt, InputFrame(jumpHeld = true, jumpPressed = true))
        assertEquals(0, g.player.jumpsLeft)
        assertTrue(g.player.vy < -450)
        g.step(dt, InputFrame(jumpReleased = true))
        val speed = g.player.vy
        g.step(dt, InputFrame(jumpHeld = true, jumpPressed = true))
        assertTrue(g.player.vy > speed)
    }

    @Test fun dashDistanceCooldownAirLimitAndWallSweep() {
        for (rate in listOf(30, 60, 120)) {
            val g = flat()
            val before = g.player.x
            var time = 0.0
            while (time < 0.16 - 1e-8) {
                val slice = minOf(1.0 / rate, 0.16 - time)
                g.step(slice, InputFrame(dashHeld = true, dashPressed = time == 0.0))
                time += slice
            }
            assertEquals(99.2, g.player.x - before, 0.01)
            advance(g, 60, InputFrame(dashHeld = true))
            assertTrue(g.player.x - before < 120, "Holding dash must not repeat")
        }
        val wall = flat()
        wall.level.platforms.add(Platform(PlatformDef("wall", Rect(180.0, 0.0, 2.0, 536.0))))
        advance(wall, 15, InputFrame(dashHeld = true, dashPressed = true))
        assertTrue(wall.player.x <= 169.001)
        assertEquals(0.0, wall.player.dashLeft)
        val g = flat()
        g.player.reset(Vec(200.0, 100.0))
        g.step(dt, InputFrame(dashHeld = true, dashPressed = true))
        assertEquals(0.0, g.player.vy)
        advance(g, 40)
        assertFalse(g.player.canDash)
        g.step(dt, InputFrame(dashHeld = true, dashPressed = true))
        assertEquals(0.0, g.player.dashLeft)
    }

    @Test fun movingPlatformsCarryAndCrumblingBridgesRecover() {
        val g = flat()
        g.level.platforms.clear()
        val lift = Platform(PlatformDef("moving", Rect(100.0, 400.0, 180.0, 28.0), Vec(70.0, 35.0)))
        g.level.platforms.add(lift)
        g.player.reset(Vec(160.0, 380.0))
        advance(g, 15)
        assertTrue(g.player.grounded)
        val offset = g.player.x - lift.bounds.x
        advance(g, 90)
        assertTrue(g.player.grounded)
        assertEquals(offset, g.player.x - lift.bounds.x, 0.2)
        assertEquals(lift.bounds.y, g.player.y, 0.01)
        g.pause()
        val stopped = lift.bounds
        advance(g, 50)
        assertEquals(stopped, lift.bounds)
        g.resume()
        val crumble = Platform(PlatformDef("crumble", Rect(0.0, 536.0, 500.0, 28.0)))
        g.level.platforms.clear()
        g.level.platforms.add(crumble)
        g.player.reset(Vec(140.0, 536.0))
        advance(g, 20)
        assertTrue(crumble.solid)
        advance(g, 20)
        assertFalse(crumble.solid)
        assertFalse(g.player.grounded)
        crumble.advance(0.0, 4.0)
        assertTrue(crumble.solid)
    }

    @Test fun risingLiftCatchesFeetAtItsPreviousSurface() {
        val g = flat()
        g.level.platforms.clear()
        val lift = Platform(PlatformDef("moving", Rect(100.0, 400.0, 180.0, 28.0), Vec(0.0, 35.0)))
        g.level.platforms.add(lift)
        g.level.age = kotlin.math.PI / 1.35
        g.player.reset(Vec(160.0, 400.0))
        g.step(dt)
        assertTrue(g.player.grounded, "Ascending lift must catch feet instead of clipping through them")
        assertEquals(lift.bounds.y, g.player.y, 0.001)
    }

    @Test fun armorIsEarnedAndCannotBeRefilledBySwapping() {
        val gear = Equipment()
        assertFalse(gear.equip("bow", true))
        assertFalse(gear.absorbHit())
        assertEquals(listOf("bow"), gear.rewardsAt(0, 1).map { it.id })
        assertEquals("bow", gear.weaponId)
        gear.rewardsAt(0, 2)
        assertTrue(gear.absorbHit())
        assertFalse(gear.absorbHit())
        gear.equip("armor", false)
        gear.equip("armor", true)
        assertEquals(0, gear.armorCharge)
        gear.rewardsAt(2, 1)
        assertEquals(1, gear.armorCharge)
        gear.recharge()
        assertEquals(2, gear.armorCharge)
        gear.reset()
        assertTrue(gear.unlocked.isEmpty())
    }

    @Test fun everyWeaponHitsEverySpeciesAndStopsAtTerrain() {
        for (weapon in Equipment.ITEMS.filter { it.category == "weapon" }) {
            for (species in listOf("opossum", "slime", "beetle", "bat")) {
                val g = flat()
                val target = Critter(CritterDef(species, Vec(450.0, 536.0), 0.0, 0.0))
                g.level.critters.add(target)
                g.equipment.rewardsAt(19, 1)
                g.equipment.equip(weapon.id, true)
                val targetY = target.bounds(g.level.age).center.y
                val origin = if (weapon.id == "seed_bomb") Vec(190.0, 516.0) else Vec(390.0, targetY)
                g.shots.add(Shot(weapon, origin.x, origin.y, weapon.speed, weapon.lift, 1))
                advance(g, 100)
                assertTrue(target.out, "${weapon.id} must defeat $species")
            }
            val g = flat()
            val target = Critter(CritterDef("opossum", Vec(450.0, 536.0), 0.0, 0.0))
            g.level.critters.add(target)
            g.level.platforms.add(Platform(PlatformDef("wall", Rect(350.0, 0.0, 3.0, 536.0))))
            g.shots.add(Shot(weapon, 280.0, 516.0, weapon.speed, weapon.lift, 1))
            advance(g, 150)
            assertFalse(target.out, "${weapon.id} must not cross solid walls")
            assertTrue(g.shots.isEmpty())
        }
    }

    @Test fun bossHasExactlyThreeOpeningsAndStaysDefeatedOnRetry() {
        for (chapter in listOf(2, 5, 8, 11, 14, 17)) {
            val g = Game()
            g.startRun()
            g.loadChapter(chapter)
            g.level.takenAcorns.addAll(g.level.acorns.indices)
            val b = g.boss!!
            g.player.reset(Vec(b.arenaMin + 60, 536.0))
            g.step(dt)
            assertTrue(b.engaged)
            assertEquals("warning", b.phase)
            val bow = Equipment.ITEMS.first()
            g.shots.add(Shot(bow, b.x - 60, b.y - 40, bow.speed, 0.0, 1))
            advance(g, 5)
            assertEquals(3, b.health)
            for (remaining in 2 downTo 0) {
                b.phase = "rest"
                b.phaseTime = 0.0
                g.shots.add(Shot(bow, b.x - 60, b.y - 40, bow.speed, 0.0, 1))
                advance(g, 5)
                assertEquals(remaining, b.health)
                g.shots.add(Shot(bow, b.x - 60, b.y - 40, bow.speed, 0.0, 1))
                advance(g, 5)
                assertEquals(remaining, b.health)
            }
            assertTrue(b.defeated)
            assertTrue(g.level.gateOpen)
            g.retry()
            advance(g, 55)
            assertTrue(g.boss!!.defeated)
            assertTrue(g.level.gateOpen)
            assertEquals(3, g.playerHealth)
        }
    }

    @Test fun pickupsPersistRetryResetsMomentumAndHeldActions() {
        val g = flat()
        val gem = g.level.gems.first()
        g.player.reset(gem + Vec(0.0, 20.0))
        g.step(dt)
        assertTrue(0 in g.level.takenGems)
        g.step(dt, InputFrame(jumpHeld = true, jumpPressed = true, dashHeld = true))
        g.retry()
        advance(g, 55, InputFrame(jumpHeld = true, dashHeld = true))
        assertTrue(g.player.grounded)
        assertEquals(0.0, g.player.dashLeft)
        assertTrue(0 in g.level.takenGems)
        g.startRun()
        assertTrue(g.level.takenGems.isEmpty())
        assertEquals(0, g.deaths)
    }

    @Test fun lateCoyoteAndBufferedJumpAreBounded() {
        val g = flat()
        g.level.platforms.clear()
        g.level.platforms.add(Platform(PlatformDef("ledge", Rect(100.0, 400.0, 80.0, 28.0))))
        g.player.reset(Vec(174.0, 400.0))
        advance(g, 3)
        advance(g, 9, InputFrame(right = true))
        assertFalse(g.player.grounded)
        g.step(dt, InputFrame(jumpHeld = true, jumpPressed = true))
        assertTrue(g.player.vy < -500)
        assertEquals(1, g.player.jumpsLeft)
        g.player.reset(Vec(210.0, 350.0))
        advance(g, 12)
        assertEquals(1, g.player.jumpsLeft)
        g.step(dt, InputFrame(jumpHeld = true, jumpPressed = true))
        assertEquals(0, g.player.jumpsLeft)
        val buffered = flat()
        buffered.player.reset(Vec(200.0, 526.0))
        buffered.player.vy = 300.0
        buffered.player.jumpsLeft = 0
        buffered.step(dt, InputFrame(jumpHeld = true, jumpPressed = true))
        advance(buffered, 3, InputFrame(jumpHeld = true))
        assertTrue(buffered.player.vy < -450, "Press before landing is buffered into a jump")
    }

    @Test fun externalBouncePreservesAirDashBudgetAndIgnoresOldJumpRelease() {
        val g = flat()
        g.step(dt, InputFrame(jumpHeld = true, jumpPressed = true))
        g.step(dt, InputFrame(jumpHeld = true, dashHeld = true, dashPressed = true))
        assertTrue(g.player.dashLeft > 0)
        g.player.bounce(-770.0)
        assertEquals(1, g.player.jumpsLeft)
        assertFalse(g.player.airDashAvailable)
        g.step(dt, InputFrame(jumpReleased = true))
        assertTrue(g.player.vy < -700, "Release of old jump must not cut mushroom/stomp bounce")
        assertEquals(0.0, g.player.dashLeft)
    }

    @Test fun allOrdinaryMonstersHaveSharedStompAndArmorRules() {
        for (species in listOf("opossum", "slime", "beetle", "bat")) {
            val g = flat()
            val target = Critter(CritterDef(species, Vec(400.0, 536.0), 0.0, 0.0))
            g.level.critters.add(target)
            val bounds = target.bounds(g.level.age)
            g.player.reset(Vec(400.0, bounds.y - 10))
            g.player.vy = 240.0
            advance(g, 7)
            assertTrue(target.out, "Descending stomp defeats $species")
            assertTrue(g.player.vy < 0)
            target.out = false
            g.player.reset(Vec(400.0, bounds.center.y + 12))
            g.equipment.rewardsAt(0, 2)
            g.invincibleLeft = 0.0
            g.step(dt)
            assertEquals(GameState.PLAYING, g.state)
            assertEquals(0, g.equipment.armorCharge)
            assertFalse(target.out)
            g.invincibleLeft = 0.0
            g.step(dt)
            assertEquals(GameState.RESPAWNING, g.state)
        }
    }

    @Test fun projectilePiercingAndNearestTerrainPriority() {
        for (weaponId in listOf("bow", "spear", "sunwood_lance")) {
            val g = flat()
            val weapon = Equipment.ITEMS.first { it.id == weaponId }
            repeat(5) { g.level.critters.add(Critter(CritterDef("opossum", Vec(350.0 + it * 70, 536.0), 0.0, 0.0))) }
            g.shots.add(Shot(weapon, 300.0, 516.0, weapon.speed, 0.0, 1))
            advance(g, 90)
            assertEquals(weapon.pierce, g.level.critters.count { it.out })
            assertTrue(
                g.level.critters
                    .take(weapon.pierce)
                    .all { it.out },
                "Nearest targets are hit first",
            )
        }
        val g = flat()
        g.level.platforms.add(Platform(PlatformDef("wall", Rect(300.0, 450.0, 1.0, 86.0))))
        val target = Critter(CritterDef("opossum", Vec(325.0, 536.0), 0.0, 0.0))
        g.level.critters.add(target)
        val bow = Equipment.ITEMS.first()
        g.shots.add(Shot(bow, 299.0, 516.0, bow.speed, 0.0, 1))
        advance(g, 10)
        assertFalse(target.out, "One pixel wall must win over a target immediately behind it")
    }

    @Test fun restingCapAcceptsStompFromAboveButNeverSideEntry() {
        fun scenario(): Game =
            Game().apply {
                startRun()
                loadChapter(2)
                level.takenAcorns.addAll(level.acorns.indices)
                player.reset(Vec(2660.0, 536.0))
                step(dt)
                boss!!.phase = "rest"
                boss!!.phaseTime = 0.0
            }
        val g = scenario()
        val b = g.boss!!
        g.player.reset(Vec(b.x, b.capBounds.y - 3))
        g.player.vy = 300.0
        g.step(dt)
        assertEquals(2, b.health)
        assertTrue(g.player.vy < -500)
        val side = scenario()
        val other = side.boss!!
        side.player.reset(Vec(other.x, other.capBounds.bottom - 1))
        side.player.vy = 100.0
        side.step(dt)
        assertEquals(3, other.health)
    }

    @Test fun chapterCompletionDoesNotDoubleCountCollectedGems() {
        val g = Game()
        g.startRun()
        g.level.takenAcorns.addAll(g.level.acorns.indices)
        g.level.takenGems.add(0)
        g.player.reset(g.level.gate)
        g.step(dt)
        assertEquals(GameState.CHAPTER_COMPLETE, g.state)
        assertEquals(1, g.collectedGems)
        assertEquals(g.level.gems.size, g.totalGems)
    }

    @Test fun fallingUnderGateCannotCompleteAChapter() {
        val g = Game()
        g.startRun()
        g.level.takenAcorns.addAll(g.level.acorns.indices)
        g.player.reset(Vec(g.level.gate.x, 700.0))
        g.step(dt)
        assertEquals(GameState.PLAYING, g.state, "A fox below the floor cannot enter the visible gate")
        advance(g, 20)
        assertEquals(GameState.RESPAWNING, g.state)
    }

    @Test fun invalidTimeCannotMutateSimulation() {
        val g = flat()
        val before = g.player.position
        for (invalid in listOf(-0.01, 0.251, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { g.step(invalid, InputFrame(right = true)) }
            assertEquals(before, g.player.position)
        }
        g.step(0.0, InputFrame(right = true, jumpPressed = true))
        assertEquals(before, g.player.position)
    }

    @Test fun equipmentTriggersEveryWeaponFacingBothDirectionsAndHonorsCooldown() {
        val locked = flat()
        locked.step(dt, InputFrame(shootPressed = true))
        assertTrue(locked.shots.isEmpty(), "No free weapon before its checkpoint")
        for (weapon in Equipment.ITEMS.filter { it.category == "weapon" }) {
            for (direction in listOf(-1, 1)) {
                val g = flat()
                g.equipment.rewardsAt(19, 1)
                g.equipment.equip(weapon.id, true)
                g.player.facing = direction
                val start = g.player.x
                g.step(dt, InputFrame(shootPressed = true))
                assertEquals(weapon.angles.size, g.shots.size, "${weapon.id} projectile count")
                assertTrue(g.shots.all { (it.x - start) * direction > 0 }, "${weapon.id} follows facing")
                g.step(dt, InputFrame(shootPressed = true))
                assertEquals(weapon.angles.size, g.shots.size, "${weapon.id} cooldown blocks repeated trigger")
                assertTrue(g.shotCooldown > 0.0)
            }
        }
    }

    @Test fun allArmorCapacitiesAbsorbRealHazardsThenRetryAndRecharge() {
        for (armor in Equipment.ITEMS.filter { it.category == "armor" }) {
            val source = flat().levels.single()
            val danger = source.copy(spikes = listOf(Rect(300.0, 516.0, 80.0, 20.0)))
            val g = Game(listOf(danger))
            g.startRun()
            g.equipment.rewardsAt(19, 1)
            g.equipment.equip(armor.id, true)
            repeat(armor.capacity) { hit ->
                g.player.reset(Vec(330.0, 536.0))
                g.invincibleLeft = 0.0
                g.step(dt)
                assertEquals(GameState.PLAYING, g.state, "${armor.id} hit ${hit + 1}")
                assertEquals(armor.capacity - hit - 1, g.equipment.armorCharge)
            }
            g.invincibleLeft = 0.0
            g.step(dt)
            assertEquals(GameState.RESPAWNING, g.state, "${armor.id} exhausted")
            advance(g, 55)
            assertEquals(GameState.PLAYING, g.state)
            assertEquals(armor.capacity, g.equipment.armorCharge)
            assertEquals(1, g.deaths)
        }
    }

    @Test fun missingAcornsKeepGuardianArenaOpenForBacktracking() {
        for (chapter in listOf(2, 5, 8, 11, 14, 17)) {
            val g = Game()
            g.startRun()
            g.loadChapter(chapter)
            val boss = g.boss!!
            g.player.reset(Vec(boss.arenaMin + 60, 536.0))
            g.step(dt)
            assertFalse(boss.engaged)
            assertFalse(g.level.gateOpen)
            assertTrue(g.level.platforms.none { it.kind == "wall" && it.bounds.x == boss.arenaMin - 100 })
            g.player.reset(g.level.gate)
            g.step(dt)
            assertEquals(GameState.PLAYING, g.state)
            assertTrue(g.message.contains("Acorn"))
        }
    }

    @Test fun pauseDuringRetryFreezesTimerAndRestoresTheRetryState() {
        val g = flat()
        g.retry()
        advance(g, 10)
        val left = g.respawnLeft
        val position = g.player.position
        g.pause()
        advance(g, 120, InputFrame(right = true, jumpPressed = true, dashPressed = true))
        assertEquals(left, g.respawnLeft)
        assertEquals(position, g.player.position)
        g.resume()
        assertEquals(GameState.RESPAWNING, g.state)
        advance(g, 50)
        assertEquals(GameState.PLAYING, g.state)
        assertEquals(1, g.deaths)
    }

    @Test fun newRunClearsProgressEquipmentProjectilesAndGuardianVictory() {
        val g = Game()
        g.startRun()
        g.loadChapter(17)
        g.equipment.rewardsAt(19, 1)
        g.boss!!.defeated = true
        g.level.takenAcorns.addAll(g.level.acorns.indices)
        g.level.secretFound = true
        g.shots.add(Shot(Equipment.ITEMS.first(), 400.0, 400.0, 200.0, 0.0, 1))
        g.retry()
        g.startRun()
        assertEquals(GameState.PLAYING, g.state)
        assertEquals(0, g.stageIndex)
        assertEquals(0, g.deaths)
        assertEquals(0.0, g.elapsed)
        assertEquals(null, g.boss)
        assertTrue(g.shots.isEmpty())
        assertTrue(g.equipment.unlocked.isEmpty())
        assertTrue(g.level.takenAcorns.isEmpty())
        assertFalse(g.level.secretFound)
        assertTrue(g.chapterResults.isEmpty())
    }

    @Test fun terminalJumpPulseRetainsEnoughHeightForTheFirstAcornShelf() {
        val g = flat()
        val input = TerminalInput()
        var apex = g.player.y
        repeat(80) { frame ->
            if (frame == 0 || frame == 20) input.key("space")
            g.step(dt, input.frame(dt))
            apex = minOf(apex, g.player.y)
        }
        assertTrue(536.0 - apex >= 150.0, "Terminal pulse double-jump must reach 150-unit shelf; reached ${536.0 - apex}")
    }

    @Test fun completeCampaignRequiresAllAcornsAndAllSixBosses() {
        val g = Game()
        g.startRun()
        val driver = RouteDriver(g)
        for (chapter in g.levels.indices) {
            for ((index, route) in g.level.definition.route
                .withIndex()) {
                assertTrue(
                    driver.follow(route),
                    "Chapter ${chapter + 1}, step $index $route failed at ${g.player.position} state ${g.state}",
                )
            }
            if (g.boss != null) {
                assertTrue(driver.fightBoss(), "Boss ${g.boss?.name} failed at ${g.player.position}")
                assertTrue(driver.walk(g.level.gate.x))
            }
            assertEquals(3, g.level.takenAcorns.size, "Chapter ${chapter + 1}")
            assertTrue(g.level.secretFound, "Chapter ${chapter + 1} firefly")
            if (chapter < 19) {
                assertEquals(GameState.CHAPTER_COMPLETE, g.state)
                g.nextChapter()
                assertEquals(chapter + 1, g.stageIndex)
            }
        }
        assertEquals(GameState.WON, g.state)
        assertEquals(0, g.deaths)
        assertEquals(13, g.equipment.unlocked.size)
        println("Campaign: ${g.elapsed} seconds, ${g.collectedGems}/${g.totalGems} gems, zero retries")
    }
}

/** Simulation inputs only; independent assertions compare them to the exported original route. */
internal class RouteDriver(
    private val game: Game,
) {
    private var jumping = false
    private var previousJump = false

    private fun tick(targetX: Double? = null) {
        val distance = if (targetX == null) 0.0 else targetX - game.player.x
        val stop = max(4.0, abs(game.player.vx) * 0.06)
        val input =
            InputFrame(
                left = distance < -stop,
                right = distance > stop,
                jumpHeld = jumping,
                jumpPressed = jumping && !previousJump,
                jumpReleased =
                    !jumping && previousJump,
            )
        previousJump = jumping
        game.step(1.0 / 60.0, input)
    }

    fun walk(target: Double): Boolean {
        jumping = false
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
            if (step.kind ==
                "moving"
            ) {
                game.level.platforms.firstOrNull { it.kind == "moving" && abs(it.definition.bounds.x - step.x) < 2 }
            } else {
                null
            }
        if (step.kind != "bounce") {
            jumping = false
            repeat(2) { tick() }
            jumping = true
        }
        val launchX = game.player.x
        val clearShelf = moving == null && step.y < game.player.y && abs(step.x - game.player.x) < 120
        repeat(210) { frame ->
            val target = if (moving != null) Vec(moving.bounds.x + moving.bounds.w / 2, moving.bounds.y) else Vec(step.x, step.y)
            if (frame == 19 || frame == 60) {
                jumping = false
            } else if (frame == 20) {
                jumping = true
            }
            tick(if (clearShelf && frame < 4) launchX else target.x)
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
            if (boss.defeated) {
                jumping = false
                return true
            }
            if (game.state != GameState.PLAYING) return false
            if (boss.phase != previousPhase) {
                if (boss.phase == "warning") {
                    escapeX = boss.landingX + (if (boss.landingX < boss.arenaMin + 400) 210 else -210)
                } else if (boss.phase == "rest") {
                    jumping = false
                    jumpFrame = frame + 1
                }
                previousPhase = boss.phase
            }
            val target =
                if (boss.phase in listOf("warning", "leap", "landing", "hurt")) {
                    jumping = false
                    escapeX
                } else {
                    boss.x
                }
            if (jumpFrame >= 0) {
                when (frame - jumpFrame) {
                    0, 20 -> jumping = true
                    19, 65 -> jumping = false
                }
            }
            tick(target)
        }
        return false
    }
}
