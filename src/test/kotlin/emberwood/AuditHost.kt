package emberwood

import com.williamcallahan.tui4j.compat.bubbletea.BlurMessage
import com.williamcallahan.tui4j.compat.bubbletea.Command
import com.williamcallahan.tui4j.compat.bubbletea.Message
import com.williamcallahan.tui4j.compat.bubbletea.Model
import com.williamcallahan.tui4j.compat.bubbletea.UpdateResult
import java.nio.file.Files
import java.nio.file.Path

/** Test-only starting states. Every action travels through the live terminal input parser. */
fun main(args: Array<String>) {
    val scenario = args[0]
    val output = Path.of(args[1])
    val original = Levels.all.first()
    val level =
        original.copy(
            index = if (scenario == "motion") 10 else original.index,
            width = if (scenario == "motion") 4000.0 else 768.0,
            theme = if (scenario == "motion") "frost" else original.theme,
            signs = emptyList(),
            platforms = listOf(PlatformDef("ground", Rect(0.0, 536.0, 4000.0, 200.0))),
            critters = emptyList(),
            spikes = emptyList(),
            camps = listOf(Vec(100.0, 536.0)),
            mushrooms = emptyList(),
            gems = emptyList(),
            acorns = listOf(Vec(600.0, 510.0), Vec(650.0, 510.0), Vec(700.0, 510.0)),
            gate = Vec(730.0, 536.0),
        )
    val game = if (scenario == "campaign") Game() else Game(listOf(level, level.copy(index = 1)))
    if (scenario !in listOf("title", "campaign")) {
        game.startRun()
        if (scenario != "campaign") {
            game.player.reset(Vec(250.0, 536.0))
            if (scenario !in listOf("no_weapon", "locked_pause")) game.equipment.rewardsAt(19, 2)
            game.equipment.equip("bow", true)
            repeat(30) { game.step(1.0 / 60) }
            game.invincibleLeft = 0.0
        }
        when (scenario) {
            "paused", "locked_pause" -> game.pause()
            "respawning" -> {
                game.retry()
                game.respawnLeft = 5.0
            }
            "complete" -> game.state = GameState.CHAPTER_COMPLETE
            "won" -> game.state = GameState.WON
        }
    }
    val settings = Settings(output.parent.resolve("settings-$scenario"))
    Audio().use { audio ->
        val delegate = TerminalGame(game, settings, audio)
        var keyCount = 0
        var blurCount = 0
        var lastKey = ""
        val wrapper =
            object : Model {
                override fun init(): Command = delegate.init()

                override fun view(): String = delegate.view()

                override fun update(message: Message): UpdateResult<out Model> {
                    if (message is BlurMessage) blurCount++
                    if (message is com.williamcallahan.tui4j.compat.bubbletea.KeyPressMessage) {
                        keyCount++
                        lastKey = message.key()
                    }
                    val result = delegate.update(message)
                    val p = game.player
                    val boss = game.boss
                    val fields =
                        mapOf(
                            "keyCount" to keyCount,
                            "blurCount" to blurCount,
                            "lastKey" to lastKey,
                            "x" to p.x,
                            "y" to p.y,
                            "vx" to p.vx,
                            "vy" to p.vy,
                            "grounded" to p.grounded,
                            "state" to game.state.toString(),
                            "cameraX" to game.cameraX,
                            "direction" to delegate.input.direction,
                            "shots" to game.shots.size,
                            "particles" to game.particles.size,
                            "stage" to game.stageIndex,
                            "acorns" to game.level.takenAcorns.size,
                            "camp" to game.level.campIndex,
                            "deaths" to game.deaths,
                            "elapsed" to game.elapsed,
                            "weapon" to game.equipment.weaponId,
                            "armor" to game.equipment.armorId,
                            "muted" to settings.muted,
                            "gentle" to settings.gentle,
                            "audioAvailable" to (audio.unavailable == null),
                            "levelAge" to game.level.age,
                            "secretFound" to game.level.secretFound,
                            "bossArenaMin" to (boss?.arenaMin ?: 0.0),
                            "bossPhase" to (boss?.phase ?: "none"),
                            "bossX" to (boss?.x ?: 0.0),
                            "bossLanding" to (boss?.landingX ?: 0.0),
                            "bossHealth" to (boss?.health ?: 0),
                        )
                    val json =
                        fields.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
                            val encoded =
                                if (value is String) {
                                    "\"" +
                                        value
                                            .replace("\\", "\\\\")
                                            .replace("\"", "\\\"")
                                            .replace("\n", "\\n")
                                            .replace("\r", "\\r") + "\""
                                } else {
                                    value.toString()
                                }
                            "\"$key\":$encoded"
                        }
                    val temp = output.resolveSibling(output.fileName.toString() + ".tmp")
                    Files.writeString(temp, json)
                    Files.move(
                        temp,
                        output,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    )
                    return UpdateResult.from(this, result.command())
                }
            }
        terminalProgram(wrapper).run()
    }
}
