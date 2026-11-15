package emberwood

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Fixed-step game rules. Rendering, physical input, audio devices and persistence live outside. */
class Game(
    val levels: List<LevelDef> = Levels.all,
    seed: Int = 0,
) {
    val player = Player()
    val equipment = Equipment()
    var level = LevelState(levels.first())
        private set
    var boss: Boss? = null
        private set
    var state = GameState.TITLE
        internal set
    private var beforePause = GameState.PLAYING
    val stageIndex get() = level.index
    var elapsed = 0.0
        private set
    var chapterStartedAt = 0.0
        private set
    var deaths = 0
        private set
    var playerHealth = 3
        private set
    var cameraX = 0.0
        internal set
    var reducedMotion = false
    var invincibleLeft = 0.8
    var shotCooldown = 0.0
        private set
    var respawnLeft = 0.0
        internal set
    private var mushroomCooldown = 0.0
    private var gateHintCooldown = 0.0
    var message = ""
        private set
    var messageLeft = 0.0
        private set
    val shots = mutableListOf<Shot>()
    val particles = mutableListOf<Particle>()
    val chapterResults = mutableListOf<ChapterResult>()
    private val soundEvents = mutableListOf<String>()
    private val random = Random(seed)
    private var arenaWall: Platform? = null
    private var lastInput = InputFrame()
    val totalGems get() = chapterResults.sumOf { it.total } + if (state == GameState.CHAPTER_COMPLETE) 0 else level.gems.size
    val collectedGems get() = chapterResults.sumOf { it.gems } + if (state == GameState.CHAPTER_COMPLETE) 0 else level.takenGems.size
    val allSecrets get() = chapterResults.all { it.secret } && level.secretFound
    val canDash get() = state == GameState.PLAYING && player.canDash

    init {
        loadChapter(0)
    }

    fun startRun() {
        chapterResults.clear()
        equipment.reset()
        elapsed = 0.0
        deaths = 0
        soundEvents.clear()
        loadChapter(0)
        state = GameState.PLAYING
        toast("Find three acorns to leave the forest. Campfires mark your return point.")
    }

    /** Also used by isolated test fixtures; ordinary play reaches this only through Continue. */
    fun loadChapter(index: Int) {
        require(index in levels.indices)
        level = LevelState(levels[index])
        boss = if ((index + 1) % 3 == 0) Boss(index) else null
        arenaWall = null
        playerHealth = 3
        chapterStartedAt = elapsed
        shots.clear()
        particles.clear()
        shotCooldown = 0.0
        invincibleLeft = 0.8
        mushroomCooldown = 0.0
        gateHintCooldown = 0.0
        respawnLeft = 0.0
        player.reset(level.camps.first(), lastInput)
        cameraX = 0.0
    }

    fun nextChapter() {
        if (state != GameState.CHAPTER_COMPLETE || stageIndex >= levels.lastIndex) return
        loadChapter(stageIndex + 1)
        state = GameState.PLAYING
        toast("${level.title} — find three acorns and the next trail marker.")
    }

    fun pause() {
        if (state != GameState.PLAYING && state != GameState.RESPAWNING) return
        beforePause = state
        state = GameState.PAUSED
        soundEvents.clear()
    }

    fun resume() {
        if (state == GameState.PAUSED) state = beforePause
    }

    fun retry() {
        respawn("Back to your campfire")
    }

    fun toggleEquipment(id: String): Boolean = state == GameState.PAUSED && equipment.equip(id, !equipment.isEquipped(id))

    fun toast(text: String) {
        message = text
        messageLeft = 3.3
    }

    fun drainSounds(): List<String> = soundEvents.toList().also { soundEvents.clear() }

    /** Input edges are consumed only once, even when a delayed frame needs several fixed steps. */
    fun step(
        dt: Double,
        input: InputFrame = InputFrame(),
    ) {
        require(dt.isFinite() && dt >= 0.0 && dt <= 0.25) { "Simulation interval must be finite and within 0..0.25 seconds" }
        lastInput = input
        if (state == GameState.PAUSED || state == GameState.TITLE || state == GameState.CHAPTER_COMPLETE) return
        var left = dt
        var current = input
        while (left > 1e-10) {
            val slice = min(left, 1.0 / 60.0)
            tick(slice, current)
            current = input.copy(jumpPressed = false, jumpReleased = false, dashPressed = false, shootPressed = false)
            left -= slice
        }
    }

    private fun tick(
        dt: Double,
        input: InputFrame,
    ) {
        messageLeft = max(0.0, messageLeft - dt)
        particles.forEach {
            it.life -= dt
            it.x += it.vx * dt
            it.y += it.vy * dt
            it.vy += 220 * dt
        }
        particles.removeAll { it.life <= 0 }
        if (reducedMotion) particles.clear()
        if (state == GameState.RESPAWNING) {
            respawnLeft -= dt
            if (respawnLeft <= 0) finishRespawn(input)
        }
        if (state != GameState.PLAYING) {
            updateCamera(dt)
            return
        }
        elapsed += dt
        invincibleLeft = max(0.0, invincibleLeft - dt)
        mushroomCooldown = max(0.0, mushroomCooldown - dt)
        gateHintCooldown = max(0.0, gateHintCooldown - dt)
        shotCooldown = max(0.0, shotCooldown - dt)
        level.age += dt
        level.platforms.forEach { it.advance(level.age, dt) }
        val previousFeet = player.position
        movePlayer(dt, input)
        checkHazards()
        if (state != GameState.PLAYING) {
            updateCamera(dt)
            return
        }
        checkPickups()
        checkMushrooms(previousFeet)
        updateBoss(dt, previousFeet)
        if (state != GameState.PLAYING) {
            updateCamera(dt)
            return
        }
        if (input.shootPressed) shoot()
        updateShots(dt)
        checkCampsAndGate()
        updateCamera(dt)
    }

    private fun updateCamera(dt: Double) {
        val target = (player.x - 380).coerceIn(0.0, max(0.0, level.width - 1152))
        cameraX = if (reducedMotion) target else cameraX + (target - cameraX) * (1 - exp(-8 * dt))
    }

    private fun movePlayer(
        dt: Double,
        input: InputFrame,
    ) {
        val p = player
        p.dashCooldown = max(0.0, p.dashCooldown - dt)
        val support = p.standing
        if (p.grounded && support != null && support.solid) {
            p.x += support.bounds.x - support.previous.x
            p.y += support.bounds.y - support.previous.y
        } else if (support != null && !support.solid) {
            p.grounded = false
            p.standing = null
        }
        val wasGrounded = p.grounded
        if (wasGrounded) {
            p.coyoteLeft = 0.12
            p.jumpsLeft = 2
            p.airDashAvailable = true
        } else {
            p.coyoteLeft = max(0.0, p.coyoteLeft - dt)
            if (p.dashLeft <= 0) p.vy = min(900.0, p.vy + 1500 * dt)
            if (p.coyoteLeft <= 0 && p.jumpsLeft == 2) p.jumpsLeft = 1
        }
        p.jumpBuffer = max(0.0, p.jumpBuffer - dt)
        if (!input.jumpHeld) p.ignoreJump = false
        if (input.jumpPressed && !p.ignoreJump) p.jumpBuffer = 0.12
        val direction = (if (input.right) 1 else 0) - (if (input.left) 1 else 0)
        if (p.dashLeft <= 0 && direction != 0) p.facing = direction
        var startingJump = false
        if (p.jumpBuffer > 0 && p.jumpsLeft > 0) {
            val double = !wasGrounded && p.coyoteLeft <= 0
            p.dashLeft = 0.0
            p.vy = if (double) -510.0 else -540.0
            p.jumpsLeft--
            p.jumpBuffer = 0.0
            p.coyoteLeft = 0.0
            p.jumpCutAvailable = true
            p.grounded = false
            p.standing = null
            startingJump = true
            sound(if (double) "double" else "jump")
            burst(p.position, "bdd7c3", if (double) 7 else 4)
        }
        if (!input.dashHeld) p.ignoreDash = false
        if (input.dashPressed && !p.ignoreDash && p.canDash && !startingJump) {
            p.dashLeft = 0.16
            p.dashCooldown = 0.65
            p.dashDirection = p.facing
            p.airDashAvailable = false
            sound("dash")
        }
        if (p.dashLeft > 0) {
            p.vx = p.dashDirection * 620.0 * min(1.0, p.dashLeft / dt)
            p.vy = 0.0
        } else {
            p.vx = approach(p.vx, direction * 250.0, (if (wasGrounded) 2200 else 1500) * dt)
        }
        if (input.jumpReleased) {
            if (p.jumpCutAvailable && p.vy < 0) p.vy *= 0.45
            p.jumpCutAvailable = false
        }
        val hitWall = collidePlayer(p.vx * dt, p.vy * dt)
        if (p.dashLeft > 0) {
            p.dashLeft = if (hitWall) 0.0 else max(0.0, p.dashLeft - dt)
            if (p.dashLeft <= 0) p.vx = p.vx.coerceIn(-250.0, 250.0)
        }
        p.standing?.stepOn()
    }

    private fun collidePlayer(
        dx: Double,
        dy: Double,
    ): Boolean {
        val p = player
        var actualX = dx
        val body = p.bounds
        for (platform in level.platforms) {
            if (!platform.solid) continue
            val b = platform.bounds
            if (body.y >= b.bottom - 0.001 || body.bottom <= b.y + 0.001) continue
            if (dx > 0 && body.right <= b.x + 0.001 && body.right + actualX >= b.x) actualX = min(actualX, b.x - body.right)
            if (dx < 0 && body.x >= b.right - 0.001 && body.x + actualX <= b.right) actualX = max(actualX, b.right - body.x)
        }
        p.x += actualX
        val wall = abs(actualX - dx) > 1e-8
        if (wall) p.vx = 0.0
        var actualY = dy
        p.grounded = false
        p.standing = null
        val shifted = p.bounds
        for (platform in level.platforms) {
            if (!platform.solid) continue
            val b = platform.bounds
            if (shifted.x >= b.right - 0.001 || shifted.right <= b.x + 0.001) continue
            // Compare the prior top as well: an ascending lift can meet falling feet
            // between steps even if its new top is already slightly above them.
            if (dy >= 0 && shifted.bottom <= max(b.y, platform.previous.y) + 0.01 && shifted.bottom + actualY >= b.y - 0.01) {
                actualY = min(actualY, b.y - shifted.bottom)
                p.grounded = true
                p.standing = platform
            } else if (dy < 0 && shifted.y >= b.bottom - 0.001 && shifted.y + actualY <= b.bottom) {
                actualY = max(actualY, b.bottom - shifted.y)
            }
        }
        p.y += actualY
        if (p.grounded || abs(actualY - dy) > 1e-8) p.vy = 0.0
        return wall
    }

    private fun checkHazards() {
        if (player.y > 730) {
            respawn("A little tumble. Try that jump again!")
            return
        }
        val bounds = player.bounds
        for (spike in level.spikes) {
            if (invincibleLeft <= 0 && bounds.intersects(spike.grow(-3.0))) {
                if (!absorbHit()) respawn("Ouch! Jump over the spikes.")
                return
            }
        }
        for (critter in level.critters) {
            if (critter.out) continue
            val target = critter.bounds(level.age)
            if (!bounds.intersects(target)) continue
            if (player.vy > 40 && player.y < target.center.y) {
                critter.out = true
                player.bounce(-440.0)
                burst(critter.position(level.age) - Vec(0.0, 20.0), "d7b2df", 12)
                sound("bounce")
            } else if (invincibleLeft <= 0) {
                if (!absorbHit()) respawn("Boop! Hop over critters, or bounce on top.")
                return
            }
        }
    }

    private fun checkPickups() {
        val bounds = player.bounds.grow(9.0)
        level.gems.forEachIndexed { index, point ->
            if (index !in level.takenGems && bounds.contains(point)) {
                level.takenGems.add(index)
                burst(point, "b1f0dc", 9)
                sound("gem")
            }
        }
        level.acorns.forEachIndexed { index, point ->
            if (index !in level.takenAcorns && bounds.grow(5.0).contains(point)) {
                level.takenAcorns.add(index)
                burst(point, "ffcf88", 24)
                sound("acorn")
                toast(
                    if (level.takenAcorns.size <
                        3
                    ) {
                        "Magic acorn ${level.takenAcorns.size} / 3!"
                    } else {
                        "All three acorns collected. Follow the trail to the gate."
                    },
                )
            }
        }
        if (!level.secretFound && bounds.contains(level.secret)) {
            level.secretFound = true
            burst(level.secret, "ffeda3", 28)
            sound("acorn")
            toast("A hidden firefly! You found the forest's little secret.")
        }
    }

    private fun checkMushrooms(previous: Vec) {
        for (point in level.mushrooms) {
            // A falling fox must approach the cap from above, never be rescued from below ground.
            if (mushroomCooldown <= 0 &&
                player.vy >= 0 &&
                abs(player.x - point.x) < 32 &&
                player.y > point.y - 28 &&
                previous.y <= point.y + 2
            ) {
                player.bounce(-770.0)
                mushroomCooldown = 0.3
                burst(point - Vec(0.0, 25.0), "f8b5d5", 18)
                sound("bounce")
            }
        }
    }

    private fun checkCampsAndGate() {
        level.camps.forEachIndexed { index, point ->
            if (index > level.campIndex && player.position.distance(point) < 50) activateCamp(index)
        }
        // A fall below the visible portal must never count as reaching the exit.
        if (abs(player.x - level.gate.x) >= 42 || player.y <= level.gate.y - 126 || player.y > level.gate.y + 2) return
        if (level.takenAcorns.size == level.acorns.size && level.gateOpen) {
            player.vx = 0.0
            player.vy = 0.0
            state = if (stageIndex == levels.lastIndex) GameState.WON else GameState.CHAPTER_COMPLETE
            if (state ==
                GameState.CHAPTER_COMPLETE
            ) {
                chapterResults.add(ChapterResult(level.takenGems.size, level.gems.size, level.secretFound))
            }
            sound(if (state == GameState.WON) "win" else "acorn")
            if (state == GameState.WON) burst(player.position - Vec(0.0, 60.0), "ffdda1", 70)
        } else if (gateHintCooldown <= 0) {
            gateHintCooldown = 4.0
            val missing = level.acorns.indices.firstOrNull { it !in level.takenAcorns }
            toast(
                if (missing !=
                    null
                ) {
                    "Acorn ${missing + 1} is still on the trail to the left."
                } else {
                    "${boss?.name} guards this gate. Attack the gold cap during rest."
                },
            )
        }
    }

    private fun activateCamp(index: Int) {
        if (index <= level.campIndex) return
        level.campIndex = index
        val rewards = equipment.rewardsAt(stageIndex, index)
        equipment.recharge()
        sound("camp")
        burst(level.camps[index] - Vec(0.0, 25.0), "ffc887", 20)
        toast(
            if (rewards.isNotEmpty()) {
                "New gear: ${rewards.joinToString { it.name }}. J uses your weapon; Esc chooses equipment."
            } else if ("armor" in
                equipment.unlocked
            ) {
                "Campfire lit! Armor recharged."
            } else {
                "Campfire lit! Your treasure is safe."
            },
        )
    }

    private fun respawn(text: String) {
        if (state != GameState.PLAYING) return
        deaths++
        state = GameState.RESPAWNING
        shots.clear()
        respawnLeft = 0.8
        burst(player.position - Vec(0.0, 24.0), "f2b694", 15)
        sound("hurt")
        toast(text)
    }

    private fun finishRespawn(input: InputFrame) {
        level.resetHazards()
        val oldBoss = boss
        if (oldBoss != null) {
            playerHealth = 3
            if (!oldBoss.defeated) boss = Boss(stageIndex)
            level.gateOpen = boss?.defeated == true
        }
        removeArenaWall()
        player.reset(level.camps[level.campIndex], input)
        equipment.recharge()
        shotCooldown = 0.0
        invincibleLeft = 1.0
        mushroomCooldown = 0.0
        cameraX = (player.x - 380).coerceIn(0.0, max(0.0, level.width - 1152))
        state = GameState.PLAYING
    }

    private fun absorbHit(): Boolean {
        if (!equipment.absorbHit()) return false
        invincibleLeft = 1.3
        sound("armor")
        burst(player.position - Vec(0.0, 24.0), "c7dcac", 12)
        toast("${equipment.armor?.name} blocked the hit. Blocks left: ${equipment.armorCharge}.")
        return true
    }

    private fun updateBoss(
        dt: Double,
        previousFeet: Vec,
    ) {
        val b = boss ?: return
        if (!b.engaged && !b.defeated && player.x >= b.arenaMin + 30 && level.takenAcorns.size == level.acorns.size) {
            b.engaged = true
            playerHealth = 3
            activateCamp(level.camps.lastIndex)
            arenaWall = Platform(PlatformDef("wall", Rect(b.arenaMin - 100, 250.0, 18.0, 286.0))).also { level.platforms.add(it) }
            warn(b)
            sound("camp")
            toast("${b.name}: dodge the landing mark, then attack the gold cap.")
        }
        if (!b.engaged || b.defeated) return
        b.age += dt
        b.phaseTime += dt
        b.contactCooldown = max(0.0, b.contactCooldown - dt)
        when (b.phase) {
            "warning" ->
                if (b.phaseTime >= b.profile.warning - (3 - b.health) * 0.1) {
                    b.startX = b.x
                    b.startY = b.y
                    phase(b, "leap")
                }
            "leap" -> {
                val progress = min(b.phaseTime / b.profile.leap, 1.0)
                b.x = b.startX + (b.landingX - b.startX) * progress
                b.y = 536 - sin(progress * PI) * b.profile.height
                if (progress >= 1) {
                    b.x = b.landingX
                    b.y = 536.0
                    phase(b, "landing")
                    val speed = b.profile.wave + (3 - b.health) * 25
                    for (direction in listOf(-1, 1)) b.waves.add(Wave(b.x + direction * 46, speed * direction))
                }
            }
            "landing" -> if (b.phaseTime >= 0.28) phase(b, "rest")
            "rest" -> if (b.phaseTime >= b.profile.rest) warn(b)
            "hurt" -> if (b.phaseTime >= 0.65) warn(b)
        }
        b.waves.forEach { it.x += it.speed * dt }
        b.waves.removeAll { it.x <= b.arenaMin - 30 || it.x >= b.arenaMax + 30 }
        if (player.y > 566) return
        val cap = b.capBounds
        if (b.phase == "rest" && player.vy > 30 && previousFeet.y <= cap.y + 3 && player.y >= cap.y) {
            val progress = ((cap.y - previousFeet.y) / (player.y - previousFeet.y)).coerceIn(0.0, 1.0)
            val crossingX = previousFeet.x + (player.x - previousFeet.x) * progress
            if (crossingX + 11 > cap.x && crossingX - 11 < cap.right) {
                player.bounce(-560.0)
                hitBoss(b)
                return
            }
        }
        if (b.contactCooldown > 0) return
        if ((b.phase != "hurt" && player.bounds.intersects(b.bounds.grow(-4.0))) ||
            b.waves.any { player.bounds.intersects(Rect(it.x - 15, 520.0, 30.0, 16.0)) }
        ) {
            b.contactCooldown = 0.35
            bossHitsPlayer()
        }
    }

    private fun phase(
        b: Boss,
        next: String,
    ) {
        b.phase = next
        b.phaseTime = 0.0
    }

    private fun warn(b: Boss) {
        phase(b, "warning")
        b.landingX = player.x.coerceIn(b.arenaMin + 120, b.arenaMax - 120)
        b.facingLeft = b.landingX < b.x
    }

    private fun hitBoss(b: Boss): Boolean {
        if (state != GameState.PLAYING || !b.engaged || b.defeated || b.phase != "rest") return false
        b.health--
        b.waves.clear()
        b.contactCooldown = 0.65
        phase(b, "hurt")
        sound("acorn")
        burst(Vec(b.x, b.y - 70), "d7c277", 20)
        if (b.health == 0) {
            b.defeated = true
            b.engaged = false
            phase(b, "defeated")
            level.gateOpen = true
            removeArenaWall()
            sound("win")
            toast("${b.name} yields the path. The gate is just ahead.")
        }
        return true
    }

    private fun bossHitsPlayer() {
        if (state != GameState.PLAYING || invincibleLeft > 0 || absorbHit()) return
        playerHealth--
        if (playerHealth <= 0) {
            respawn("Back to the arena campfire. Watch the landing mark before you jump.")
            return
        }
        invincibleLeft = 1.3
        burst(player.position - Vec(0.0, 24.0), "dcaa82", 10)
        sound("hurt")
        toast("$playerHealth hearts left. Land on the gold cap while the guardian rests.")
    }

    private fun removeArenaWall() {
        arenaWall?.let { level.platforms.remove(it) }
        arenaWall = null
    }

    private fun shoot() {
        val weapon = equipment.weapon ?: return
        if (shotCooldown > 0) return
        shotCooldown = weapon.cooldown
        for (angle in weapon.angles) {
            val radians = angle * player.facing
            val initialX = player.facing * weapon.speed
            val vx = initialX * cos(radians) - weapon.lift * sin(radians)
            val vy = initialX * sin(radians) + weapon.lift * cos(radians)
            shots.add(Shot(weapon, player.x, player.y - 20, vx, vy, player.facing))
        }
        sound(if (weapon.id == "bow") "shoot" else weapon.id)
    }

    private data class Hit(
        val fraction: Double,
        val target: String,
        val critter: Critter? = null,
        val normal: Vec = Vec(0.0, 0.0),
    )

    private fun firstHit(
        start: Vec,
        end: Vec,
    ): Hit? {
        var hit: Hit? = null
        for (platform in level.platforms) {
            if (!platform.solid) continue
            val fraction = platform.bounds.entryFraction(start, end)
            if (fraction.isFinite() && (hit == null || fraction < hit.fraction)) {
                val point = start.lerp(end, fraction)
                val b = platform.bounds
                val normal =
                    when {
                        abs(point.x - b.x) < 0.001 -> Vec(-1.0, 0.0)
                        abs(point.x - b.right) < 0.001 -> Vec(1.0, 0.0)
                        abs(point.y - b.y) < 0.001 -> Vec(0.0, -1.0)
                        else -> Vec(0.0, 1.0)
                    }
                hit = Hit(fraction, "blocked", normal = normal)
            }
        }
        for (critter in level.critters) {
            if (critter.out) continue
            val fraction = critter.bounds(level.age).entryFraction(start, end)
            if (fraction.isFinite() && (hit == null || fraction < hit.fraction)) hit = Hit(fraction, "critter", critter)
        }
        val b = boss
        if (b != null && !b.defeated) {
            for (bounds in listOf(b.bounds, b.capBounds)) {
                val fraction = bounds.entryFraction(start, end)
                if (fraction.isFinite() && (hit == null || fraction < hit.fraction)) hit = Hit(fraction, "boss")
            }
        }
        return hit
    }

    private fun updateShots(dt: Double) {
        for (shot in shots) {
            if (shot.spent) {
                shot.burstLeft = max(0.0, shot.burstLeft - dt)
                continue
            }
            shot.age += dt
            var movement = Vec(shot.vx * dt, shot.vy * dt + shot.weapon.gravity * dt * dt * 0.5)
            shot.vy += shot.weapon.gravity * dt
            val remaining = shot.weapon.range - shot.traveled
            val distance = hypot(movement.x, movement.y)
            if (distance > remaining) movement *= remaining / distance
            var start = shot.position
            val end = start + movement
            while (!shot.spent && shot.hitsLeft > 0) {
                val hit = firstHit(start, end)
                if (hit == null) {
                    shot.x = end.x
                    shot.y = end.y
                    break
                }
                val point = start.lerp(end, hit.fraction)
                shot.x = point.x
                shot.y = point.y
                if (shot.weapon.id == "seed_bomb") {
                    shot.x += hit.normal.x * 2
                    shot.y += hit.normal.y * 2
                    detonate(shot)
                    break
                }
                if (hit.target == "critter") {
                    hit.critter!!.out = true
                    shot.hitsLeft--
                    sound("bounce")
                } else if (hit.target == "boss") {
                    boss?.let { hitBoss(it) }
                }
                burst(point, if (hit.target == "blocked") "bdcda2" else "d7b2df", 5)
                if (hit.target != "critter" || shot.hitsLeft <= 0) {
                    shot.spent = true
                    break
                }
                start = point
            }
            if (shot.spent) continue
            shot.traveled += hypot(movement.x, movement.y)
            if (shot.weapon.id == "seed_bomb" && shot.age >= shot.weapon.fuse) {
                detonate(shot)
            } else if (shot.traveled >= shot.weapon.range) {
                shot.spent = true
            }
        }
        shots.removeAll { it.spent && it.burstLeft <= 0 }
    }

    private fun detonate(shot: Shot) {
        shot.spent = true
        shot.burstLeft = 0.24

        fun reaches(bounds: Rect): Boolean {
            val target = bounds.nearest(shot.position)
            return shot.position.distance(target) <= shot.weapon.radius &&
                level.platforms.none { it.solid && it.bounds.entryFraction(shot.position, target).isFinite() }
        }
        for (critter in level.critters) {
            if (!critter.out && reaches(critter.bounds(level.age))) {
                critter.out = true
                sound("bounce")
                burst(critter.position(level.age), "d7b2df", 5)
            }
        }
        boss?.let { if (!it.defeated && (reaches(it.bounds) || reaches(it.capBounds))) hitBoss(it) }
    }

    private fun sound(name: String) {
        soundEvents.add(name)
    }

    private fun burst(
        point: Vec,
        color: String,
        count: Int,
    ) {
        if (reducedMotion) return
        repeat(count) {
            particles.add(
                Particle(
                    point.x,
                    point.y,
                    random.nextDouble(-110.0, 110.0),
                    random.nextDouble(-150.0, -30.0),
                    random.nextDouble(0.3, 0.8),
                    color,
                ),
            )
        }
    }

    private fun approach(
        value: Double,
        target: Double,
        amount: Double,
    ) = if (value <
        target
    ) {
        min(target, value + amount)
    } else {
        max(target, value - amount)
    }
}
