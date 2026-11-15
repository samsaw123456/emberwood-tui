package emberwood

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** World +x is right, +y is down; the fox origin is at its feet. */
data class Vec(
    val x: Double,
    val y: Double,
) {
    operator fun plus(other: Vec) = Vec(x + other.x, y + other.y)

    operator fun minus(other: Vec) = Vec(x - other.x, y - other.y)

    operator fun times(scale: Double) = Vec(x * scale, y * scale)

    fun distance(other: Vec) = hypot(x - other.x, y - other.y)

    fun lerp(
        other: Vec,
        amount: Double,
    ) = this + (other - this) * amount
}

data class Rect(
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
) {
    val right get() = x + w
    val bottom get() = y + h
    val center get() = Vec(x + w / 2, y + h / 2)

    fun intersects(other: Rect) = x < other.right && right > other.x && y < other.bottom && bottom > other.y

    fun contains(point: Vec) = point.x >= x && point.x < right && point.y >= y && point.y < bottom

    fun grow(amount: Double) = Rect(x - amount, y - amount, w + amount * 2, h + amount * 2)

    fun nearest(point: Vec) = Vec(point.x.coerceIn(x, right), point.y.coerceIn(y, bottom))

    /** Segment sweep includes its origin so shots cannot bypass nearby thin walls. */
    fun entryFraction(
        start: Vec,
        end: Vec,
    ): Double {
        var near = 0.0
        var far = 1.0
        for (axis in 0..1) {
            val origin = if (axis == 0) start.x else start.y
            val movement = if (axis == 0) end.x - start.x else end.y - start.y
            val lower = if (axis == 0) x else y
            val upper = if (axis == 0) right else bottom
            if (abs(movement) < 1e-10) {
                if (origin < lower || origin > upper) return Double.POSITIVE_INFINITY
            } else {
                val first = (lower - origin) / movement
                val last = (upper - origin) / movement
                near = max(near, min(first, last))
                far = min(far, max(first, last))
                if (near > far) return Double.POSITIVE_INFINITY
            }
        }
        return near
    }
}

enum class GameState { TITLE, PLAYING, PAUSED, RESPAWNING, CHAPTER_COMPLETE, WON }

data class InputFrame(
    val left: Boolean = false,
    val right: Boolean = false,
    val jumpHeld: Boolean = false,
    val jumpPressed: Boolean = false,
    val jumpReleased: Boolean = false,
    val dashHeld: Boolean = false,
    val dashPressed: Boolean = false,
    val shootPressed: Boolean = false,
)

class Player {
    var x = 140.0
    var y = 536.0
    var vx = 0.0
    var vy = 0.0
    var facing = 1
    var grounded = false
    var jumpsLeft = 2
    var dashLeft = 0.0
    var dashCooldown = 0.0
    var airDashAvailable = true
    var dashDirection = 1
    var coyoteLeft = 0.0
    var jumpBuffer = 0.0
    var jumpCutAvailable = false
    var ignoreJump = false
    var ignoreDash = false
    var standing: Platform? = null
    val position get() = Vec(x, y)
    val bounds get() = Rect(x - 11, y - 36, 22.0, 36.0)
    val canDash get() = dashLeft <= 0 && dashCooldown <= 0 && airDashAvailable

    fun reset(
        point: Vec,
        held: InputFrame = InputFrame(),
    ) {
        x = point.x
        y = point.y
        vx = 0.0
        vy = 0.0
        facing = 1
        grounded = false
        jumpsLeft = 2
        dashLeft = 0.0
        dashCooldown = 0.0
        airDashAvailable = true
        coyoteLeft = 0.0
        jumpBuffer = 0.0
        jumpCutAvailable = false
        ignoreJump = held.jumpHeld
        ignoreDash = held.dashHeld
        standing = null
    }

    fun bounce(speed: Double) {
        vy = speed
        jumpsLeft = 1
        coyoteLeft = 0.0
        jumpBuffer = 0.0
        jumpCutAvailable = false
        dashLeft = 0.0
        grounded = false
        standing = null
    }
}

data class PlatformDef(
    val kind: String,
    val bounds: Rect,
    val motion: Vec = Vec(0.0, 0.0),
)

class Platform(
    val definition: PlatformDef,
) {
    val kind get() = definition.kind
    var bounds = definition.bounds
    var previous = bounds
    var crumbleTime = -1.0
    val solid get() = kind != "crumble" || crumbleTime < 0.55

    fun reset() {
        bounds = definition.bounds
        previous = bounds
        crumbleTime = -1.0
    }

    fun advance(
        age: Double,
        dt: Double,
    ) {
        previous = bounds
        if (kind == "moving") {
            val offset = definition.motion * sin(age * 1.35)
            bounds = definition.bounds.copy(x = definition.bounds.x + offset.x, y = definition.bounds.y + offset.y)
        }
        if (crumbleTime >= 0) {
            crumbleTime += dt
            if (crumbleTime >= 3.5) crumbleTime = -1.0
        }
    }

    fun stepOn() {
        if (kind == "crumble" && crumbleTime < 0) crumbleTime = 0.0
    }
}

data class CritterDef(
    val kind: String,
    val home: Vec,
    val range: Double,
    val speed: Double,
)

class Critter(
    val definition: CritterDef,
) {
    val kind get() = definition.kind
    var out = false
    val pace get() =
        when (kind) {
            "slime" -> 0.75
            "beetle" -> 1.25
            "bat" -> 0.9
            else -> 1.0
        }

    fun position(age: Double): Vec {
        val phase = age * definition.speed * pace
        val height =
            when (kind) {
                "slime" -> max(0.0, sin(phase * 2)) * 22
                "bat" -> 38 + sin(phase * 1.7) * 10
                else -> 0.0
            }
        return definition.home + Vec(sin(phase) * definition.range, -height)
    }

    fun facing(age: Double) = if (cos(age * definition.speed * pace) > 0) -1 else 1

    fun bounds(age: Double): Rect {
        val size =
            when (kind) {
                "slime" -> Vec(42.0, 27.0)
                "beetle" -> Vec(44.0, 27.0)
                "bat" -> Vec(36.0, 24.0)
                else -> Vec(50.0, 29.0)
            }
        val point = position(age)
        return Rect(point.x - size.x / 2, point.y - size.y, size.x, size.y)
    }
}

data class Sign(
    val position: Vec,
    val title: String,
    val hint: String,
)

data class RouteStep(
    val kind: String,
    val x: Double,
    val y: Double,
)

data class LevelDef(
    val index: Int,
    val title: String,
    val width: Double,
    val theme: String,
    val gate: Vec,
    val gateLabel: String,
    val secret: Vec,
    val platforms: List<PlatformDef>,
    val spikes: List<Rect>,
    val gems: List<Vec>,
    val acorns: List<Vec>,
    val camps: List<Vec>,
    val mushrooms: List<Vec>,
    val critters: List<CritterDef>,
    val signs: List<Sign>,
    val route: List<RouteStep>,
)

class LevelState(
    val definition: LevelDef,
) {
    val platforms = definition.platforms.map { Platform(it) }.toMutableList()
    val critters = definition.critters.map { Critter(it) }.toMutableList()
    val takenGems = mutableSetOf<Int>()
    val takenAcorns = mutableSetOf<Int>()
    var secretFound = false
    var campIndex = 0
    var age = 0.0
    var gateOpen = (definition.index + 1) % 3 != 0
    val index get() = definition.index
    val title get() = definition.title
    val width get() = definition.width
    val theme get() = definition.theme
    val gate get() = definition.gate
    val gateLabel get() = definition.gateLabel
    val secret get() = definition.secret
    val spikes get() = definition.spikes
    val gems get() = definition.gems
    val acorns get() = definition.acorns
    val camps get() = definition.camps
    val mushrooms get() = definition.mushrooms
    val signs get() = definition.signs

    fun resetHazards() {
        age = 0.0
        critters.forEach { it.out = false }
        platforms.forEach { it.reset() }
    }
}

data class GuardianProfile(
    val name: String,
    val species: String,
    val capColor: String,
    val warning: Double,
    val leap: Double,
    val height: Double,
    val wave: Double,
    val rest: Double,
)

data class Wave(
    var x: Double,
    val speed: Double,
)

class Boss(
    val chapter: Int,
) {
    val profile = PROFILES[chapter / 3]
    val name get() = profile.name
    val species get() = profile.species
    val arenaMin = if (chapter > 2) 3000.0 else 2600.0
    val arenaMax = arenaMin + 800.0
    var x = arenaMin + 370
    var y = 536.0
    var landingX = x
    var startX = x
    var startY = y
    var phase = "waiting"
    var phaseTime = 0.0
    var age = 0.0
    var contactCooldown = 0.0
    var facingLeft = true
    var health = 3
    var engaged = false
    var defeated = false
    val waves = mutableListOf<Wave>()
    val bodyHeight get() = if (species == "opossum") 56.0 else 72.0
    val bounds get() = Rect(x - 44, y - bodyHeight, 88.0, bodyHeight)
    val capY get() =
        (if (species == "opossum") -61.0 else -79.0) +
            when {
                phase == "leap" && species == "frog" -> -32.0
                phase == "warning" -> 7.0
                defeated -> 14.0
                else -> 0.0
            }
    val capBounds get() = Rect(x - 37, y + capY - 13, 74.0, 26.0)
    val status get() =
        when (phase) {
            "warning" -> "Move away from the marked landing spot."
            "leap", "landing" -> "Jump over the ripples."
            "rest" -> "Gold cap: double-jump onto it, or shoot!"
            "hurt" -> "The guardian is getting its breath back."
            "defeated" -> "$name has yielded. The path is open."
            else -> "$name guards the clearing."
        }

    companion object {
        val PROFILES =
            listOf(
                GuardianProfile("Old Croak", "frog", "627545", 1.1, 0.82, 110.0, 170.0, 2.1),
                GuardianProfile("Reed Rumbler", "opossum", "899b42", 1.2, 0.9, 130.0, 185.0, 2.2),
                GuardianProfile("Marsh Tusk", "boar", "56898f", 1.05, 0.75, 100.0, 200.0, 2.1),
                GuardianProfile("Frost Fang", "wolf", "8cabc4", 1.2, 1.0, 155.0, 210.0, 2.25),
                GuardianProfile("Cloudwing", "eagle", "9694b4", 1.1, 0.95, 180.0, 220.0, 2.2),
                GuardianProfile("Lantern Owl", "owl", "b8823f", 1.0, 0.85, 140.0, 235.0, 2.1),
            )
    }
}

class Shot(
    val weapon: Item,
    var x: Double,
    var y: Double,
    var vx: Double,
    var vy: Double,
    val direction: Int,
) {
    var traveled = 0.0
    var age = 0.0
    var hitsLeft = weapon.pierce
    var spent = false
    var burstLeft = 0.0
    val position get() = Vec(x, y)
}

data class Particle(
    var x: Double,
    var y: Double,
    var vx: Double,
    var vy: Double,
    var life: Double,
    val color: String,
)

data class ChapterResult(
    val gems: Int,
    val total: Int,
    val secret: Boolean,
)
