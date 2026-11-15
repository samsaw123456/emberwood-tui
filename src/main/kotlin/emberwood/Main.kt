package emberwood

import com.williamcallahan.tui4j.compat.bubbletea.BlurMessage
import com.williamcallahan.tui4j.compat.bubbletea.Command
import com.williamcallahan.tui4j.compat.bubbletea.KeyPressMessage
import com.williamcallahan.tui4j.compat.bubbletea.Message
import com.williamcallahan.tui4j.compat.bubbletea.Model
import com.williamcallahan.tui4j.compat.bubbletea.QuitMessage
import com.williamcallahan.tui4j.compat.bubbletea.UpdateResult
import com.williamcallahan.tui4j.compat.bubbletea.WindowSizeMessage
import java.time.Duration
import kotlin.math.abs
import kotlin.system.exitProcess

private class Tick : Message

class TerminalGame(
    val game: Game = Game(),
    private val settings: Settings = Settings(),
    private val audio: Audio? = null,
    private val practice: Int? = null,
) : Model {
    val input = TerminalInput()
    private val renderer = TerminalRenderer()
    private var width = 100
    private var height = 36
    private var lastTick = System.nanoTime()
    private var accumulator = 0.0
    private var selection = 0
    private var recordedWin = false
    private var cachedView: String? = null
    private val small get() = width < 80 || height < 28
    private val staticScene get() = game.state in listOf(GameState.TITLE, GameState.PAUSED, GameState.CHAPTER_COMPLETE)

    init {
        game.reducedMotion = settings.gentle
        audio?.muted = settings.muted
    }

    override fun init(): Command = tick()

    private fun tick(): Command = Command.tick(Duration.ofMillis(33)) { Tick() }

    override fun update(message: Message): UpdateResult<out Model> {
        // These states freeze the world. Keys, focus and resizing still refresh their UI.
        if (message !is Tick || !staticScene) cachedView = null
        when (message) {
            is WindowSizeMessage -> {
                width = message.width()
                height = message.height()
                if (small) pause()
            }
            is BlurMessage -> pause()
            is Tick -> {
                val now = System.nanoTime()
                val elapsed = ((now - lastTick) / 1e9).coerceIn(0.0, 0.1)
                lastTick = now
                if (!small) {
                    accumulator += elapsed
                    while (accumulator >= 1.0 / 60) {
                        val before = game.state
                        game.step(1.0 / 60, input.frame(1.0 / 60))
                        if (before != game.state) input.clear()
                        accumulator -= 1.0 / 60
                    }
                } else {
                    accumulator = 0.0
                }
                game.drainSounds().forEach { audio?.play(it) }
                if (game.state == GameState.WON && !recordedWin) {
                    recordedWin = true
                    if (practice == null && (settings.best <= 0 || game.elapsed < settings.best)) {
                        settings.best = game.elapsed
                        settings.save()
                    }
                }
                return UpdateResult.from(this, tick())
            }
            is KeyPressMessage -> {
                if (message.getKey().paste()) return UpdateResult.from(this)
                val key = message.key()
                if (key == "q" || key == "ctrl+c") return UpdateResult.from(this, Command { QuitMessage() })
                if (key == "ctrl+z") {
                    pause()
                } else if (key == "m") {
                    settings.muted = !settings.muted
                    settings.save("muted")
                    audio?.muted = settings.muted
                } else if (key == "g") {
                    settings.gentle = !settings.gentle
                    game.reducedMotion = settings.gentle
                    settings.save("gentle")
                } else if (!small) {
                    handleKey(key)
                }
            }
        }
        return UpdateResult.from(this)
    }

    private fun pause() {
        input.clear()
        game.pause()
        audio?.stop()
    }

    private fun handleKey(key: String) {
        when (game.state) {
            GameState.TITLE, GameState.WON ->
                if (key == "enter") {
                    game.startRun()
                    practice?.let {
                        game.loadChapter(it)
                        game.equipment.rewardsAt(it, 0)
                    }
                    recordedWin = false
                    input.clear()
                }
            GameState.CHAPTER_COMPLETE ->
                if (key == "enter") {
                    game.nextChapter()
                    input.clear()
                }
            GameState.PAUSED ->
                when (key) {
                    "p", "esc" -> {
                        game.resume()
                        input.clear()
                    }
                    "up" -> selection = (selection - 1).coerceAtLeast(0)
                    "down", "tab" -> selection = (selection + 1) % Equipment.ITEMS.size
                    "enter" -> game.toggleEquipment(Equipment.ITEMS[selection].id)
                }
            GameState.PLAYING ->
                when (key) {
                    "p", "esc" -> pause()
                    "r" -> {
                        input.clear()
                        game.retry()
                    }
                    else -> input.key(key)
                }
            GameState.RESPAWNING -> if (key == "p" || key == "esc") pause()
        }
    }

    override fun view(): String {
        if (staticScene) cachedView?.let { return it }
        return renderView().also { if (staticScene) cachedView = it }
    }

    private fun renderView(): String {
        val cols = width.coerceIn(1, 180)
        val rows = height.coerceIn(1, 65)
        val sound =
            when {
                settings.muted -> "off"
                audio?.unavailable != null -> "unavailable"
                else -> "on"
            }
        val motion = if (settings.gentle) "reduced" else "full"
        val firefly = if (game.level.secretFound) "found" else "hidden"

        fun line(
            text: String,
            color: Int = 0xdce8d3,
        ): String =
            "\u001b[0m" + TerminalColors.background(0x0f171a) + TerminalColors.foreground(color) + text.take(cols).padEnd(cols) +
                "\u001b[0m"
        if (small) {
            return listOf(
                "Emberwood Terminal",
                "Enlarge to at least 80 columns x 28 rows.",
                "Current size: $width x $height. Game paused.",
                "M sound: $sound | G motion: $motion",
                "Q quit",
            ).take(rows).joinToString("\n") { line(it) }
        }
        val lines = mutableListOf<String>()
        lines +=
            line(" Emberwood  ${game.stageIndex + 1}/20  ${game.level.title}" + if (practice != null) "  [practice]" else "", 0xffc269)
        when (game.state) {
            GameState.TITLE -> {
                lines += line(" Guide the fox through twenty forests and six animal guardians.")
                lines += line(" Collect 3 acorns to open each exit. Campfires save your return point.")
                lines += line(" Tap A/D or Left/Right to RUN; tap S or Down to STOP.", 0xffd28a)
                lines += line(" Space/W/Up jump; press again for double-jump. X dash. J weapon.")
                lines += line(" P/Esc pause & equipment. Ctrl+Z pause. R retry. M sound. G motion.")
                lines += line(" Terminal keys are presses: steering continues after key release.")
                lines +=
                    line(
                        " Enter: ${if (practice == null) "start adventure" else "practice level ${practice + 1}"}    Q: quit" +
                            if (practice == null && settings.best > 0) "    Best: ${time(settings.best)}" else "",
                        0xffd28a,
                    )
            }
            GameState.PAUSED -> {
                lines += line(" Paused   P/Esc resume | Up/Down/Tab choose | Enter equip/remove", 0xffd28a)
                val start = (selection - 2).coerceIn(0, Equipment.ITEMS.size - 5)
                Equipment.ITEMS.subList(start, start + 5).forEachIndexed { i, item ->
                    val mark = if (selection == start + i) ">" else " "
                    val status =
                        if (item.id !in
                            game.equipment.unlocked
                        ) {
                            "locked"
                        } else if (game.equipment.isEquipped(item.id)) {
                            "equipped"
                        } else {
                            "owned"
                        }
                    lines += line(" $mark ${item.name.padEnd(19)} $status", if (mark == ">") 0xffd28a else 0xdce8d3)
                }
                val item = Equipment.ITEMS[selection]
                lines += line(" " + if (item.id in game.equipment.unlocked) item.description else item.unlockHint)
            }
            GameState.CHAPTER_COMPLETE -> {
                lines +=
                    line(
                        " Trail cleared! ${game.level.takenGems.size}/${game.level.gems.size} gems; firefly $firefly.",
                        0xffd28a,
                    )
                lines += line(" Enter: next trail    Q: quit")
            }
            GameState.WON -> {
                lines +=
                    line(
                        if (practice ==
                            null
                        ) {
                            " The forest is home again. All twenty trails cleared!"
                        } else {
                            " Practice complete. Try the full twenty-trail adventure!"
                        },
                        0xffd28a,
                    )
                lines += line(" ${game.collectedGems}/${game.totalGems} gems | ${game.deaths} returns to camp | ${time(game.elapsed)}")
                lines += line(" Enter: play again    Q: quit")
            }
            else -> {
                val armor = if (game.equipment.armor == null) 0 else game.equipment.armorCharge
                val steering =
                    when (input.direction) {
                        -1 -> "LEFT"
                        1 -> "RIGHT"
                        else -> "STOP"
                    }
                val status = if (game.state == GameState.RESPAWNING) "Returning to camp..." else "Steering: $steering"
                val dash = if (game.canDash) "ready" else "charging"
                val weapon = game.equipment.weapon?.name ?: "find a campfire"
                lines +=
                    line(
                        " Acorns ${game.level.takenAcorns.size}/3   Gems ${game.level.takenGems.size}/${game.level.gems.size}" +
                            "   Hearts ${game.playerHealth}/3   Armor $armor   ${time(game.elapsed)}",
                    )
                lines += line(" $status   Dash: $dash   Weapon: $weapon")
            }
        }
        val footer =
            listOf(
                line(" " + hint()),
                line(" A/D,Left/Right run | S/Down stop | Space/W/Up jump | X dash | J fire"),
                line(" P/Esc pause | R retry | M sound: $sound | G motion: $motion | Q quit"),
            )
        val artRows = rows - lines.size - footer.size - 1
        lines += renderer.render(game, cols, artRows.coerceAtLeast(1))
        lines += footer
        return lines.joinToString("\n")
    }

    private fun hint(): String {
        settings.problem?.let { return it }
        game.boss?.takeIf { it.engaged && !it.defeated }?.let { return "${it.name}: ${it.health}/3 - ${it.status}" }
        if (game.messageLeft > 0) return game.message
        val sign = game.level.signs.firstOrNull { abs(it.position.x - game.player.x) < 95 }
        if (sign != null) {
            val advice = sign.hint.replace("Shift", "X").replace("Hold jump to go higher", "Press jump again to double-jump")
            return "${sign.title}: $advice"
        }
        val firefly = if (game.level.secretFound) "found" else "hidden"
        val guardian = if (game.boss != null) " and guardian victory" else ""
        return "Camp ${game.level.campIndex + 1}/${game.level.camps.size} | Firefly: $firefly | Exit needs 3 acorns$guardian"
    }
}

private fun time(seconds: Double): String = "%d:%02d".format(seconds.toInt() / 60, seconds.toInt() % 60)

fun main(arguments: Array<String>) {
    val desktop = arguments.firstOrNull() == "--desktop"
    val args = if (desktop) arguments.drop(1).toTypedArray() else arguments
    if (args.contentEquals(arrayOf("--version"))) {
        val version = TerminalGame::class.java.`package`.implementationVersion ?: "development"
        println("Emberwood Terminal $version (JVM ${Runtime.version().feature()}, tui4j 0.3.5)")
        return
    }
    if (args.contentEquals(arrayOf("--help"))) {
        println(
            "Emberwood Terminal\nUsage: emberwood-tui [--practice 1..20 | --version | --help]\nA/D or arrows steer; S/Down stops; Space/W/Up jumps; X dashes; J fires.\nP/Esc pauses/resumes; Ctrl+Z pauses; R retries; M sound; G motion; Q quits.\nSteering continues after key release. Terminal needs UTF-8, color and 80x28 cells.",
        )
        return
    }
    val practice = if (args.size == 2 && args[0] == "--practice") args[1].toIntOrNull()?.takeIf { it in 1..20 }?.minus(1) else null
    if (args.isNotEmpty() && practice == null) {
        System.err.println("Invalid arguments. Use --help or --practice 1..20.")
        exitProcess(2)
    }
    if (System.console()?.isTerminal != true) {
        System.err.println("Open Emberwood in an interactive terminal. Use --help for usage.")
        exitProcess(2)
    }
    System.setProperty("java.awt.headless", "true")
    if (desktop) {
        // A desktop launcher commonly starts at 80x24; supporting terminals honor this request.
        print("\u001b[8;40;120t")
        System.out.flush()
    }
    Audio().use { audio ->
        runTerminal(TerminalGame(audio = audio, practice = practice))
    }
}
