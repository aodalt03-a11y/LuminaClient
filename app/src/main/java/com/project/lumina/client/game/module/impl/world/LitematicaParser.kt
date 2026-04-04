package com.project.lumina.client.game.module.impl.world

import org.cloudburstmc.math.vector.Vector3i
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.io.DataInputStream

data class SchematicBlock(val pos: Vector3i, val blockName: String)

object LitematicaParser {

    fun parse(input: InputStream, fileName: String): List<SchematicBlock> {
        return when {
            fileName.endsWith(".litematic") -> parseLitematic(input)
            fileName.endsWith(".schematic") -> parseSchematic(input)
            fileName.endsWith(".nbt") -> parseNbt(input)
            else -> emptyList()
        }
    }

    private fun readNbtTag(dis: DataInputStream): Pair<String, Any?> {
        val type = dis.readByte().toInt()
        if (type == 0) return Pair("", null) // TAG_End
        val nameLen = dis.readShort().toInt()
        val name = String(ByteArray(nameLen).also { dis.readFully(it) })
        val value = readPayload(type, dis)
        return Pair(name, value)
    }

    private fun readPayload(type: Int, dis: DataInputStream): Any? = when (type) {
        1 -> dis.readByte()
        2 -> dis.readShort()
        3 -> dis.readInt()
        4 -> dis.readLong()
        5 -> dis.readFloat()
        6 -> dis.readDouble()
        7 -> ByteArray(dis.readInt()).also { dis.readFully(it) }
        8 -> String(ByteArray(dis.readShort().toInt()).also { dis.readFully(it) })
        9 -> {
            val listType = dis.readByte().toInt()
            val size = dis.readInt()
            (0 until size).map { readPayload(listType, dis) }
        }
        10 -> {
            val map = mutableMapOf<String, Any?>()
            while (true) {
                val t = dis.readByte().toInt()
                if (t == 0) break
                val nLen = dis.readShort().toInt()
                val n = String(ByteArray(nLen).also { dis.readFully(it) })
                map[n] = readPayload(t, dis)
            }
            map
        }
        11 -> IntArray(dis.readInt()).also { arr -> repeat(arr.size) { arr[it] = dis.readInt() } }
        12 -> LongArray(dis.readInt()).also { arr -> repeat(arr.size) { arr[it] = dis.readLong() } }
        else -> null
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseLitematic(input: InputStream): List<SchematicBlock> {
        val blocks = mutableListOf<SchematicBlock>()
        try {
            val dis = DataInputStream(GZIPInputStream(input))
            // skip root tag header
            dis.readByte(); val rLen = dis.readShort().toInt(); dis.readFully(ByteArray(rLen))
            val root = readPayload(10, dis) as? Map<String, Any?> ?: return emptyList()
            val regions = root["Regions"] as? Map<String, Any?> ?: return emptyList()
            for ((_, regionVal) in regions) {
                val region = regionVal as? Map<String, Any?> ?: continue
                val palette = (region["BlockStatePalette"] as? List<*>)?.mapNotNull {
                    ((it as? Map<String, Any?>)?.get("Name") as? String)?.substringAfter(":")
                } ?: continue
                val size = region["Size"] as? Map<String, Any?> ?: continue
                val sx = ((size["x"] as? Number)?.toInt() ?: continue).let { if (it < 0) -it else it }
                val sy = ((size["y"] as? Number)?.toInt() ?: continue).let { if (it < 0) -it else it }
                val sz = ((size["z"] as? Number)?.toInt() ?: continue).let { if (it < 0) -it else it }
                val blockStates = region["BlockStates"] as? LongArray ?: continue
                val bits = maxOf(2, Integer.numberOfTrailingZeros(Integer.highestOneBit(palette.size - 1) shl 1))
                val mask = (1L shl bits) - 1L
                var i = 0
                outer@ for (y in 0 until sy) for (z in 0 until sz) for (x in 0 until sx) {
                    val bitPos = i.toLong() * bits
                    val idx = (bitPos / 64).toInt()
                    val off = (bitPos % 64).toInt()
                    if (idx >= blockStates.size) break@outer
                    val id = if (off + bits <= 64) {
                        ((blockStates[idx] ushr off) and mask).toInt()
                    } else {
                        val lo = (blockStates[idx] ushr off) and mask
                        val hi = if (idx + 1 < blockStates.size) blockStates[idx + 1] else 0L
                        (lo or (hi shl (64 - off))).and(mask).toInt()
                    }
                    val name = palette.getOrNull(id) ?: "air"
                    if (name != "air") blocks.add(SchematicBlock(Vector3i.from(x, y, z), name))
                    i++
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return blocks
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSchematic(input: InputStream): List<SchematicBlock> {
        val blocks = mutableListOf<SchematicBlock>()
        try {
            val dis = DataInputStream(GZIPInputStream(input))
            dis.readByte(); val rLen = dis.readShort().toInt(); dis.readFully(ByteArray(rLen))
            val root = readPayload(10, dis) as? Map<String, Any?> ?: return emptyList()
            val width = (root["Width"] as? Short)?.toInt() ?: return emptyList()
            val height = (root["Height"] as? Short)?.toInt() ?: return emptyList()
            val length = (root["Length"] as? Short)?.toInt() ?: return emptyList()
            val blockIds = root["Blocks"] as? ByteArray ?: return emptyList()
            val legacyMap = mapOf(1 to "stone", 2 to "grass", 3 to "dirt", 4 to "cobblestone",
                5 to "planks", 7 to "bedrock", 12 to "sand", 13 to "gravel",
                17 to "log", 18 to "leaves", 20 to "glass", 35 to "wool",
                41 to "gold_block", 42 to "iron_block", 45 to "brick_block",
                57 to "diamond_block", 73 to "redstone_ore", 89 to "glowstone")
            for (y in 0 until height) for (z in 0 until length) for (x in 0 until width) {
                val id = blockIds[y * length * width + z * width + x].toInt() and 0xFF
                if (id != 0) {
                    val name = legacyMap[id] ?: "stone"
                    blocks.add(SchematicBlock(Vector3i.from(x, y, z), name))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return blocks
    }

    private fun parseNbt(input: InputStream): List<SchematicBlock> = parseLitematic(input)
}
