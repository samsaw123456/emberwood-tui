package emberwood

import kotlin.math.max

data class Item(
    val id: String,
    val name: String,
    val category: String,
    val chapter: Int,
    val camp: Int,
    val description: String,
    val unlockHint: String,
    val capacity: Int = 0,
    val armorColor: String = "90aa70",
    val armorEdge: String = "354e44",
    val armorArt: String = "leaf",
    val cooldown: Double = 0.35,
    val speed: Double = 700.0,
    val range: Double = 700.0,
    val pierce: Int = 1,
    val gravity: Double = 0.0,
    val lift: Double = 0.0,
    val fuse: Double = 1.2,
    val radius: Double = 100.0,
    val angles: List<Double> = listOf(0.0),
)

class Equipment {
    val unlocked = mutableSetOf<String>()
    var weaponId = ""
        private set
    var armorId = ""
        private set
    private var lastArmor = ""
    var armorDamage = 0
        private set
    val weapon get() = ITEMS.find { it.id == weaponId }
    val armor get() = ITEMS.find { it.id == armorId }
    val armorCapacity get() = armor?.capacity ?: 0
    val armorCharge get() = max(0, (ITEMS.find { it.id == lastArmor }?.capacity ?: 0) - armorDamage)

    fun reset() {
        unlocked.clear()
        weaponId = ""
        armorId = ""
        lastArmor = ""
        armorDamage = 0
    }

    fun rewardsAt(
        chapter: Int,
        camp: Int,
    ): List<Item> {
        val rewards = ITEMS.filter { (chapter > it.chapter || chapter == it.chapter && camp >= it.camp) && it.id !in unlocked }
        for (item in rewards) {
            unlocked.add(item.id)
            equip(item.id, true)
        }
        return rewards
    }

    fun equip(
        id: String,
        enabled: Boolean,
    ): Boolean {
        val item = ITEMS.find { it.id == id } ?: return false
        if (id !in unlocked) return false
        if (item.category == "weapon") {
            if (enabled) {
                weaponId = id
            } else if (weaponId == id) {
                weaponId = ""
            }
        } else {
            if (enabled) {
                armorId = id
                lastArmor = id
            } else if (armorId == id) {
                armorId = ""
            }
        }
        return true
    }

    fun isEquipped(id: String) = id.isNotEmpty() && (weaponId == id || armorId == id)

    fun recharge() {
        armorDamage = 0
    }

    fun absorbHit(): Boolean {
        if (armor == null || armorCharge <= 0) return false
        armorDamage++
        return true
    }

    companion object {
        val ITEMS =
            listOf(
                Item(
                    id = "bow",
                    name = "Bow",
                    category = "weapon",
                    description = "Fast, straight arrows. J to fire.",
                    unlockHint = "Emberwood: second campfire.",
                    chapter = 0,
                    camp = 1,
                    cooldown = 0.35,
                    speed = 700.0,
                    range = 700.0,
                    pierce = 1,
                ),
                Item(
                    id = "armor",
                    name = "Leaf armor",
                    category = "armor",
                    description = "Blocks 1 hit between campfires.",
                    unlockHint = "Emberwood: third campfire.",
                    chapter = 0,
                    camp = 2,
                    capacity = 1,
                    armorColor = "90aa70",
                    armorEdge = "354e44",
                    armorArt = "leaf",
                ),
                Item(
                    id = "spear",
                    name = "Throwing spear",
                    category = "weapon",
                    description = "Pierces two critters. J to throw.",
                    unlockHint = "Flooded Mill: second campfire.",
                    chapter = 1,
                    camp = 1,
                    cooldown = 0.6,
                    speed = 560.0,
                    range = 800.0,
                    pierce = 2,
                ),
                Item(
                    id = "bark_armor",
                    name = "Bark armor",
                    category = "armor",
                    description = "Blocks 2 hits between campfires.",
                    unlockHint = "High Canopy: second campfire.",
                    chapter = 2,
                    camp = 1,
                    capacity = 2,
                    armorColor = "b38a57",
                    armorEdge = "63472e",
                    armorArt = "bark",
                ),
                Item(
                    id = "seed_bomb",
                    name = "Seed bombs",
                    category = "weapon",
                    description = "Lob a seed that bursts nearby. J to throw.",
                    unlockHint = "Moss Caverns: second campfire.",
                    chapter = 3,
                    camp = 1,
                    cooldown = 0.8,
                    speed = 330.0,
                    range = 900.0,
                    pierce = 1,
                    gravity = 620.0,
                    lift = -240.0,
                    fuse = 1.2,
                    radius = 100.0,
                ),
                Item(
                    id = "ironwood_armor",
                    name = "Ironwood armor",
                    category = "armor",
                    description = "Blocks 3 hits between campfires.",
                    unlockHint = "Sunlit Ridge: second campfire.",
                    chapter = 4,
                    camp = 1,
                    capacity = 3,
                    armorColor = "83948c",
                    armorEdge = "3a514e",
                    armorArt = "ironwood",
                ),
                Item(
                    id = "reed_mail",
                    name = "Reed mail",
                    category = "armor",
                    description = "Woven reeds block 4 hits between camps.",
                    unlockHint = "Reedbank Trail: second campfire.",
                    chapter = 5,
                    camp = 1,
                    capacity = 4,
                    armorColor = "b6ad6a",
                    armorEdge = "4d6041",
                    armorArt = "reeds",
                ),
                Item(
                    id = "thorn_fan",
                    name = "Thorn fan",
                    category = "weapon",
                    description = "Three spreading thorns. Short range. J to fire.",
                    unlockHint = "Willow Narrows: second campfire.",
                    chapter = 6,
                    camp = 1,
                    cooldown = 0.65,
                    speed = 600.0,
                    range = 480.0,
                    pierce = 1,
                    angles = listOf(-0.22, 0.0, 0.22),
                ),
                Item(
                    id = "frost_armor",
                    name = "Frost armor",
                    category = "armor",
                    description = "A lined coat blocks 5 hits between camps.",
                    unlockHint = "Frostpine Pass: second campfire.",
                    chapter = 10,
                    camp = 1,
                    capacity = 5,
                    armorColor = "94b7b7",
                    armorEdge = "41626d",
                    armorArt = "frost",
                ),
                Item(
                    id = "stoneplate",
                    name = "Stoneplate",
                    category = "armor",
                    description = "Layered slate blocks 6 hits between camps.",
                    unlockHint = "Slate Heights: second campfire.",
                    chapter = 12,
                    camp = 1,
                    capacity = 6,
                    armorColor = "a9aaa3",
                    armorEdge = "505960",
                    armorArt = "slate",
                ),
                Item(
                    id = "copperbark_armor",
                    name = "Copperbark armor",
                    category = "armor",
                    description = "Riveted bark blocks 7 hits between camps.",
                    unlockHint = "Copperbark Woods: second campfire.",
                    chapter = 15,
                    camp = 1,
                    capacity = 7,
                    armorColor = "bc8559",
                    armorEdge = "68483b",
                    armorArt = "copper",
                ),
                Item(
                    id = "sunwood_lance",
                    name = "Sunwood lance",
                    category = "weapon",
                    description = "Pierces four critters. Long reach, slower throws.",
                    unlockHint = "Rootbound Ruins: second campfire.",
                    chapter = 16,
                    camp = 1,
                    cooldown = 1.0,
                    speed = 440.0,
                    range = 1050.0,
                    pierce = 4,
                ),
                Item(
                    id = "sunleaf_armor",
                    name = "Sunleaf armor",
                    category = "armor",
                    description = "Golden leaves block 8 hits between camps.",
                    unlockHint = "Sunleaf Crown: second campfire.",
                    chapter = 18,
                    camp = 1,
                    capacity = 8,
                    armorColor = "d1ba68",
                    armorEdge = "68703c",
                    armorArt = "sunleaf",
                ),
            )
    }
}
