package com.project.lumina.client.game.module.impl.world

import android.util.Log
import java.io.File
import java.io.DataInputStream
import java.io.FileInputStream
import java.util.zip.GZIPInputStream

data class SchematicBlock(val x: Int, val y: Int, val z: Int, val name: String)

object LitematicaParser {
    private const val TAG = "LitematicaParser"

    fun parse(file: File): List<SchematicBlock> {
        return try {
            when {
                file.name.endsWith(".litematic") -> parseLitematic(file)
                file.name.endsWith(".schematic")  -> parseSchematic(file)
                file.name.endsWith(".nbt")        -> parseStructureNbt(file)
                else -> { Log.w(TAG, "Unknown type: ${file.name}"); emptyList() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse failed for ${file.name}: ${e.message}", e)
            emptyList()
        }
    }

    private const val TAG_END = 0; private const val TAG_BYTE = 1
    private const val TAG_SHORT = 2; private const val TAG_INT = 3
    private const val TAG_LONG = 4; private const val TAG_FLOAT = 5
    private const val TAG_DOUBLE = 6; private const val TAG_BYTE_ARRAY = 7
    private const val TAG_STRING = 8; private const val TAG_LIST = 9
    private const val TAG_COMPOUND = 10; private const val TAG_INT_ARRAY = 11
    private const val TAG_LONG_ARRAY = 12

    data class NbtTag(val type: Int, val value: Any?)

    private fun readNbt(file: File): Map<String, NbtTag> {
        val din = DataInputStream(GZIPInputStream(FileInputStream(file)))
        val rootType = din.readByte().toInt()
        if (rootType != TAG_COMPOUND) error("Expected TAG_Compound root, got $rootType")
        readNbtString(din)
        val result = readCompound(din)
        din.close()
        return result
    }

    private fun readNbtString(din: DataInputStream): String {
        val len = din.readShort().toInt() and 0xFFFF
        val bytes = ByteArray(len)
        din.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readPayload(din: DataInputStream, type: Int): Any? = when (type) {
        TAG_END        -> null
        TAG_BYTE       -> din.readByte()
        TAG_SHORT      -> din.readShort()
        TAG_INT        -> din.readInt()
        TAG_LONG       -> din.readLong()
        TAG_FLOAT      -> din.readFloat()
        TAG_DOUBLE     -> din.readDouble()
        TAG_BYTE_ARRAY -> { val len = din.readInt(); ByteArray(len).also { din.readFully(it) } }
        TAG_STRING     -> readNbtString(din)
        TAG_LIST       -> { val et = din.readByte().toInt(); val sz = din.readInt(); (0 until sz).map { NbtTag(et, readPayload(din, et)) } }
        TAG_COMPOUND   -> readCompound(din)
        TAG_INT_ARRAY  -> { val len = din.readInt(); IntArray(len) { din.readInt() } }
        TAG_LONG_ARRAY -> { val len = din.readInt(); LongArray(len) { din.readLong() } }
        else -> error("Unknown NBT type: $type")
    }

    private fun readCompound(din: DataInputStream): Map<String, NbtTag> {
        val map = mutableMapOf<String, NbtTag>()
        while (true) {
            val type = din.readByte().toInt()
            if (type == TAG_END) break
            map[readNbtString(din)] = NbtTag(type, readPayload(din, type))
        }
        return map
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, NbtTag>.compound(key: String) = (this[key]?.value as? Map<String, NbtTag>) ?: emptyMap()
    @Suppress("UNCHECKED_CAST")
    private fun Map<String, NbtTag>.list(key: String) = (this[key]?.value as? List<NbtTag>) ?: emptyList()
    private fun Map<String, NbtTag>.int(key: String) = when (val v = this[key]?.value) { is Int -> v; is Short -> v.toInt(); is Byte -> v.toInt(); else -> 0 }
    private fun Map<String, NbtTag>.str(key: String) = this[key]?.value as? String ?: ""
    private fun Map<String, NbtTag>.longs(key: String) = this[key]?.value as? LongArray ?: LongArray(0)
    private fun Map<String, NbtTag>.bytes(key: String) = this[key]?.value as? ByteArray ?: ByteArray(0)

    private fun ceilLog2(v: Int): Int { var n = v; var b = 0; while (n > 1) { n = n shr 1; b++ }; return if (1 shl b < v) b + 1 else b }

    private fun readPackedValue(longs: LongArray, index: Int, bitsPerEntry: Int): Int {
        val epl = 64 / bitsPerEntry
        val ai = index / epl
        val bi = (index % epl) * bitsPerEntry
        val mask = (1L shl bitsPerEntry) - 1L
        if (ai >= longs.size) return 0
        return ((longs[ai] ushr bi) and mask).toInt()
    }

    private fun parseLitematic(file: File): List<SchematicBlock> {
        val root = readNbt(file)
        val regions = root.compound("Regions")
        val blocks = mutableListOf<SchematicBlock>()
        for ((_, rt) in regions) {
            @Suppress("UNCHECKED_CAST")
            val region = rt.value as? Map<String, NbtTag> ?: continue
            blocks += parseLitematicRegion(region)
        }
        Log.d(TAG, "Litematic: ${blocks.size} blocks")
        return blocks
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseLitematicRegion(region: Map<String, NbtTag>): List<SchematicBlock> {
        val paletteList = region.list("BlockStatePalette")
        if (paletteList.isEmpty()) return emptyList()
        val palette = paletteList.map { e -> (e.value as? Map<String, NbtTag>)?.str("Name")?.ifEmpty { "minecraft:air" } ?: "minecraft:air" }
        val size = region.compound("Size")
        val sX = Math.abs(size.int("x")); val sY = Math.abs(size.int("y")); val sZ = Math.abs(size.int("z"))
        val volume = sX * sY * sZ
        if (volume <= 0) return emptyList()
        val bpe = maxOf(2, ceilLog2(palette.size))
        val rawLongs = region.longs("BlockStates")
        val pos = region.compound("Position")
        val oX = pos.int("x"); val oY = pos.int("y"); val oZ = pos.int("z")
        val blocks = mutableListOf<SchematicBlock>()
        for (i in 0 until volume) {
            val name = palette.getOrElse(readPackedValue(rawLongs, i, bpe)) { "minecraft:air" }
            if (name == "minecraft:air") continue
            blocks += SchematicBlock(i % sX + oX, i / (sX * sZ) + oY, (i / sX) % sZ + oZ, name)
        }
        return blocks
    }

    private fun parseSchematic(file: File): List<SchematicBlock> {
        val root = readNbt(file)
        if (root.containsKey("Palette")) return parseSpongeSchematic(root)
        val w = root.int("Width"); val h = root.int("Height"); val l = root.int("Length")
        val ids = root.bytes("Blocks"); val data = root.bytes("Data")
        val blocks = mutableListOf<SchematicBlock>()
        for (y in 0 until h) for (z in 0 until l) for (x in 0 until w) {
            val i = (y * l + z) * w + x
            val id = ids.getOrElse(i) { 0 }.toInt() and 0xFF
            if (id == 0) continue
            blocks += SchematicBlock(x, y, z, legacyIdToName(id, data.getOrElse(i) { 0 }.toInt() and 0x0F))
        }
        Log.d(TAG, "MCEdit schematic: ${blocks.size} blocks")
        return blocks
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSpongeSchematic(root: Map<String, NbtTag>): List<SchematicBlock> {
        val w = root.int("Width"); val h = root.int("Height"); val l = root.int("Length")
        val pm = mutableMapOf<Int, String>()
        for ((name, tag) in root.compound("Palette")) pm[tag.value as? Int ?: continue] = name.substringBefore("[")
        val bd = root.bytes("BlockData")
        val blocks = mutableListOf<SchematicBlock>()
        var i = 0; var pos = 0
        while (pos < bd.size) {
            var value = 0; var shift = 0; var b: Int
            do { b = bd[pos++].toInt() and 0xFF; value = value or ((b and 0x7F) shl shift); shift += 7 } while ((b and 0x80) != 0 && pos < bd.size)
            val name = pm.getOrElse(value) { "minecraft:air" }
            if (name != "minecraft:air") blocks += SchematicBlock(i % w, i / (w * l), (i / w) % l, name)
            i++
        }
        Log.d(TAG, "Sponge schematic: ${blocks.size} blocks")
        return blocks
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseStructureNbt(file: File): List<SchematicBlock> {
        val root = readNbt(file)
        val palette = root.list("palette").map { e -> (e.value as? Map<String, NbtTag>)?.str("Name")?.ifEmpty { "minecraft:air" } ?: "minecraft:air" }
        val blocks = mutableListOf<SchematicBlock>()
        for (entry in root.list("blocks")) {
            val c = entry.value as? Map<String, NbtTag> ?: continue
            val name = palette.getOrElse(c.int("state")) { "minecraft:air" }
            if (name == "minecraft:air") continue
            val pl = c.list("pos"); if (pl.size < 3) continue
            blocks += SchematicBlock(pl[0].value as? Int ?: continue, pl[1].value as? Int ?: continue, pl[2].value as? Int ?: continue, name)
        }
        Log.d(TAG, "Structure NBT: ${blocks.size} blocks")
        return blocks
    }

    private fun legacyIdToName(id: Int, meta: Int): String = when (id) {
        0 -> "minecraft:air"; 1 -> "minecraft:stone"; 2 -> "minecraft:grass_block"
        3 -> "minecraft:dirt"; 4 -> "minecraft:cobblestone"
        5 -> when (meta) { 1->"minecraft:spruce_planks"; 2->"minecraft:birch_planks"; 3->"minecraft:jungle_planks"; 4->"minecraft:acacia_planks"; 5->"minecraft:dark_oak_planks"; else->"minecraft:oak_planks" }
        7 -> "minecraft:bedrock"; 8, 9 -> "minecraft:water"; 10, 11 -> "minecraft:lava"
        12 -> "minecraft:sand"; 13 -> "minecraft:gravel"; 14 -> "minecraft:gold_ore"
        15 -> "minecraft:iron_ore"; 16 -> "minecraft:coal_ore"; 17 -> "minecraft:oak_log"
        18 -> "minecraft:oak_leaves"; 20 -> "minecraft:glass"; 24 -> "minecraft:sandstone"
        35 -> "minecraft:white_wool"; 41 -> "minecraft:gold_block"; 42 -> "minecraft:iron_block"
        45 -> "minecraft:bricks"; 46 -> "minecraft:tnt"; 49 -> "minecraft:obsidian"
        54 -> "minecraft:chest"; 56 -> "minecraft:diamond_ore"; 57 -> "minecraft:diamond_block"
        58 -> "minecraft:crafting_table"; 61, 62 -> "minecraft:furnace"
        73, 74 -> "minecraft:redstone_ore"; 79 -> "minecraft:ice"; 80 -> "minecraft:snow_block"
        82 -> "minecraft:clay"; 86 -> "minecraft:pumpkin"; 87 -> "minecraft:netherrack"
        88 -> "minecraft:soul_sand"; 89 -> "minecraft:glowstone"; 91 -> "minecraft:jack_o_lantern"
        98 -> "minecraft:stone_bricks"; 112 -> "minecraft:nether_bricks"; 121 -> "minecraft:end_stone"
        123, 124 -> "minecraft:redstone_lamp"; 133 -> "minecraft:emerald_block"
        152 -> "minecraft:redstone_block"; 155 -> "minecraft:quartz_block"
        159 -> "minecraft:white_terracotta"; 172 -> "minecraft:terracotta"
        173 -> "minecraft:coal_block"; 174 -> "minecraft:packed_ice"
        else -> "minecraft:stone"
    }
}
