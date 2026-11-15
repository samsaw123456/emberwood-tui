package emberwood

/** The TSV is a lossless export of the original twenty levels, not a generated substitute. */
object Levels {
    val all: List<LevelDef> by lazy {
        val stream = checkNotNull(Levels::class.java.getResourceAsStream("/levels.tsv")) { "Missing levels.tsv" }
        stream.bufferedReader().use { parse(it.readText()) }
    }

    fun parse(text: String): List<LevelDef> {
        val levels = mutableListOf<LevelDef>()
        var header = emptyList<String>()
        val platforms = mutableListOf<PlatformDef>()
        val spikes = mutableListOf<Rect>()
        val gems = mutableListOf<Vec>()
        val acorns = mutableListOf<Vec>()
        val camps = mutableListOf<Vec>()
        val mushrooms = mutableListOf<Vec>()
        val critters = mutableListOf<CritterDef>()
        val signs = mutableListOf<Sign>()
        val route = mutableListOf<RouteStep>()
        for ((lineIndex, raw) in text.lineSequence().withIndex()) {
            if (raw.isBlank() || raw.startsWith('#')) continue
            val row = raw.split('\t')

            fun num(index: Int): Double = row[index].toDouble().also { require(it.isFinite()) }

            fun point(index: Int) = Vec(num(index), num(index + 1))
            try {
                when (row[0]) {
                    "LEVEL" -> {
                        require(header.isEmpty() && row.size == 10)
                        header = row
                    }
                    "PLATFORM" -> platforms.add(PlatformDef(row[1], Rect(num(2), num(3), num(4), num(5)), point(6)))
                    "SPIKE" -> spikes.add(Rect(num(1), num(2), num(3), num(4)))
                    "GEM" -> gems.add(point(1))
                    "ACORN" -> acorns.add(point(1))
                    "CAMP" -> camps.add(point(1))
                    "MUSHROOM" -> mushrooms.add(point(1))
                    "CRITTER" -> critters.add(CritterDef(row[1], point(2), num(4), num(5)))
                    "SIGN" -> signs.add(Sign(point(1), row[3], row[4]))
                    "ROUTE" -> route.add(RouteStep(row[1], num(2), num(3)))
                    "END" -> {
                        require(header.isNotEmpty() && acorns.size == 3 && camps.isNotEmpty())
                        val index = header[1].toInt()
                        require(index == levels.size)
                        levels.add(
                            LevelDef(
                                index,
                                header[2],
                                header[3].toDouble(),
                                header[4],
                                Vec(header[5].toDouble(), header[6].toDouble()),
                                header[7],
                                Vec(header[8].toDouble(), header[9].toDouble()),
                                platforms.toList(),
                                spikes.toList(),
                                gems.toList(),
                                acorns.toList(),
                                camps.toList(),
                                mushrooms.toList(),
                                critters.toList(),
                                signs.toList(),
                                route.toList(),
                            ),
                        )
                        header = emptyList()
                        platforms.clear()
                        spikes.clear()
                        gems.clear()
                        acorns.clear()
                        camps.clear()
                        mushrooms.clear()
                        critters.clear()
                        signs.clear()
                        route.clear()
                    }
                    else -> error("Unknown record ${row[0]}")
                }
            } catch (failure: RuntimeException) {
                throw IllegalArgumentException("Invalid level data at line ${lineIndex + 1}: ${failure.message}", failure)
            }
        }
        require(header.isEmpty()) { "Truncated level data" }
        require(levels.size == 20) { "Expected twenty levels, found ${levels.size}" }
        return levels
    }
}
