package com.project.lumina.client.game.module.impl.world

import android.util.Log
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream

data class SchematicBlock(val x: Int, val y: Int, val z: Int, val name: String)

object LitematicaParser {
    private const val TAG = "LitematicaParser"

    fun parse(file: File): List<SchematicBlock> = try {
        when {
            file.name.endsWith(".litematic") -> parseLitematic(file)
            file.name.endsWith(".schematic")  -> parseSchematic(file)
            file.name.endsWith(".nbt")        -> parseStructureNbt(file)
            else -> emptyList()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Parse failed: ${e.message}", e)
        emptyList()
    }

    // ── NBT constants ─────────────────────────────────────────────────────────
    private const val T_END = 0; private const val T_BYTE = 1; private const val T_SHORT = 2
    private const val T_INT = 3; private const val T_LONG = 4; private const val T_FLOAT = 5
    private const val T_DOUBLE = 6; private const val T_BYTES = 7; private const val T_STR = 8
    private const val T_LIST = 9; private const val T_COMPOUND = 10
    private const val T_INTS = 11; private const val T_LONGS = 12

    data class Tag(val type: Int, val value: Any?)

    private fun readNbt(file: File): Map<String, Tag> {
        val din = DataInputStream(GZIPInputStream(FileInputStream(file)))
        val type = din.readByte().toInt()
        if (type != T_COMPOUND) error("Expected compound root")
        readStr(din)
        return readCompound(din).also { din.close() }
    }

    private fun readStr(din: DataInputStream): String {
        val len = din.readShort().toInt() and 0xFFFF
        return String(ByteArray(len).also { din.readFully(it) }, Charsets.UTF_8)
    }

    private fun readPayload(din: DataInputStream, type: Int): Any? = when (type) {
        T_END    -> null
        T_BYTE   -> din.readByte()
        T_SHORT  -> din.readShort()
        T_INT    -> din.readInt()
        T_LONG   -> din.readLong()
        T_FLOAT  -> din.readFloat()
        T_DOUBLE -> din.readDouble()
        T_BYTES  -> ByteArray(din.readInt()).also { din.readFully(it) }
        T_STR    -> readStr(din)
        T_LIST   -> { val et = din.readByte().toInt(); val sz = din.readInt(); (0 until sz).map { Tag(et, readPayload(din, et)) } }
        T_COMPOUND -> readCompound(din)
        T_INTS   -> IntArray(din.readInt()) { din.readInt() }
        T_LONGS  -> LongArray(din.readInt()) { din.readLong() }
        else -> error("Unknown tag $type")
    }

    private fun readCompound(din: DataInputStream): Map<String, Tag> {
        val map = mutableMapOf<String, Tag>()
        while (true) {
            val t = din.readByte().toInt()
            if (t == T_END) break
            map[readStr(din)] = Tag(t, readPayload(din, t))
        }
        return map
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Tag>.compound(k: String) = (this[k]?.value as? Map<String, Tag>) ?: emptyMap()
    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Tag>.list(k: String) = (this[k]?.value as? List<Tag>) ?: emptyList()
    private fun Map<String, Tag>.int(k: String) = when (val v = this[k]?.value) { is Int -> v; is Short -> v.toInt(); is Byte -> v.toInt(); else -> 0 }
    private fun Map<String, Tag>.str(k: String) = this[k]?.value as? String ?: ""
    private fun Map<String, Tag>.longs(k: String) = this[k]?.value as? LongArray ?: LongArray(0)
    private fun Map<String, Tag>.bytes(k: String) = this[k]?.value as? ByteArray ?: ByteArray(0)

    private fun ceilLog2(v: Int): Int { var n = v; var b = 0; while (n > 1) { n = n shr 1; b++ }; return if (1 shl b < v) b + 1 else b }
    private fun readPacked(longs: LongArray, idx: Int, bpe: Int): Int {
        val epl = 64 / bpe; val ai = idx / epl; val bi = (idx % epl) * bpe
        if (ai >= longs.size) return 0
        return ((longs[ai] ushr bi) and ((1L shl bpe) - 1L)).toInt()
    }

    // ── .litematic ────────────────────────────────────────────────────────────
    private fun parseLitematic(file: File): List<SchematicBlock> {
        val root = readNbt(file)
        val regions = root.compound("Regions")
        val blocks = mutableListOf<SchematicBlock>()
        for ((_, rt) in regions) {
            @Suppress("UNCHECKED_CAST")
            val region = rt.value as? Map<String, Tag> ?: continue
            blocks += parseLitematicRegion(region)
        }
        Log.d(TAG, "Litematic: ${blocks.size} blocks")
        return blocks
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseLitematicRegion(r: Map<String, Tag>): List<SchematicBlock> {
        val pal = r.list("BlockStatePalette")
        if (pal.isEmpty()) return emptyList()
        val palette = pal.map { (it.value as? Map<String, Tag>)?.str("Name")?.ifEmpty { "minecraft:air" } ?: "minecraft:air" }
        val size = r.compound("Size")
        val sX = Math.abs(size.int("x")); val sY = Math.abs(size.int("y")); val sZ = Math.abs(size.int("z"))
        val vol = sX * sY * sZ; if (vol <= 0) return emptyList()
        val bpe = maxOf(2, ceilLog2(palette.size))
        val raw = r.longs("BlockStates")
        val pos = r.compound("Position")
        val oX = pos.int("x"); val oY = pos.int("y"); val oZ = pos.int("z")
        val blocks = mutableListOf<SchematicBlock>()
        for (i in 0 until vol) {
            val name = palette.getOrElse(readPacked(raw, i, bpe)) { "minecraft:air" }
            if (name == "minecraft:air") continue
            blocks += SchematicBlock(i % sX + oX, i / (sX * sZ) + oY, (i / sX) % sZ + oZ, name)
        }
        return blocks
    }

    // ── .schematic ────────────────────────────────────────────────────────────
    private fun parseSchematic(file: File): List<SchematicBlock> {
        val root = readNbt(file)
        if (root.containsKey("Palette")) return parseSponge(root)
        val w = root.int("Width"); val h = root.int("Height"); val l = root.int("Length")
        val ids = root.bytes("Blocks"); val data = root.bytes("Data")
        val blocks = mutableListOf<SchematicBlock>()
        for (y in 0 until h) for (z in 0 until l) for (x in 0 until w) {
            val i = (y * l + z) * w + x
            val id = ids.getOrElse(i) { 0 }.toInt() and 0xFF
            if (id == 0) continue
            blocks += SchematicBlock(x, y, z, legacyName(id, data.getOrElse(i) { 0 }.toInt() and 0x0F))
        }
        Log.d(TAG, "MCEdit: ${blocks.size} blocks")
        return blocks
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSponge(root: Map<String, Tag>): List<SchematicBlock> {
        val w = root.int("Width"); val h = root.int("Height"); val l = root.int("Length")
        val pm = mutableMapOf<Int, String>()
        for ((name, tag) in root.compound("Palette")) pm[tag.value as? Int ?: continue] = name.substringBefore("[")
        val bd = root.bytes("BlockData")
        val blocks = mutableListOf<SchematicBlock>()
        var i = 0; var p = 0
        while (p < bd.size) {
            var v = 0; var s = 0; var b: Int
            do { b = bd[p++].toInt() and 0xFF; v = v or ((b and 0x7F) shl s); s += 7 } while ((b and 0x80) != 0 && p < bd.size)
            val name = pm.getOrElse(v) { "minecraft:air" }
            if (name != "minecraft:air") blocks += SchematicBlock(i % w, i / (w * l), (i / w) % l, name)
            i++
        }
        Log.d(TAG, "Sponge: ${blocks.size} blocks")
        return blocks
    }

    // ── .nbt (vanilla structure) ──────────────────────────────────────────────
    @Suppress("UNCHECKED_CAST")
    private fun parseStructureNbt(file: File): List<SchematicBlock> {
        val root = readNbt(file)
        val palette = root.list("palette").map { (it.value as? Map<String, Tag>)?.str("Name") ?: "minecraft:air" }
        val blocks = mutableListOf<SchematicBlock>()
        for (entry in root.list("blocks")) {
            val c = entry.value as? Map<String, Tag> ?: continue
            val name = palette.getOrElse(c.int("state")) { "minecraft:air" }
            if (name == "minecraft:air") continue
            val pl = c.list("pos"); if (pl.size < 3) continue
            blocks += SchematicBlock(pl[0].value as? Int ?: continue, pl[1].value as? Int ?: continue, pl[2].value as? Int ?: continue, name)
        }
        Log.d(TAG, "Structure: ${blocks.size} blocks")
        return blocks
    }

    private fun legacyName(id: Int, meta: Int) = when (id) {
        1 -> "minecraft:stone"; 2 -> "minecraft:grass_block"; 3 -> "minecraft:dirt"
        4 -> "minecraft:cobblestone"
        5 -> when (meta) { 1->"minecraft:spruce_planks"; 2->"minecraft:birch_planks"; 3->"minecraft:jungle_planks"; 4->"minecraft:acacia_planks"; 5->"minecraft:dark_oak_planks"; else->"minecraft:oak_planks" }
        7 -> "minecraft:bedrock"; 8,9 -> "minecraft:water"; 10,11 -> "minecraft:lava"
        12 -> "minecraft:sand"; 13 -> "minecraft:gravel"; 17 -> "minecraft:oak_log"
        20 -> "minecraft:glass"; 35 -> "minecraft:white_wool"; 41 -> "minecraft:gold_block"
        42 -> "minecraft:iron_block"; 45 -> "minecraft:bricks"; 49 -> "minecraft:obsidian"
        57 -> "minecraft:diamond_block"; 73,74 -> "minecraft:redstone_ore"
        79 -> "minecraft:ice"; 80 -> "minecraft:snow_block"; 87 -> "minecraft:netherrack"
        89 -> "minecraft:glowstone"; 98 -> "minecraft:stone_bricks"
        else -> "minecraft:stone"
    }
}
