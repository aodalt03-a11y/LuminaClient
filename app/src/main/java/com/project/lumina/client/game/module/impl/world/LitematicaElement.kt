package com.project.lumina.client.game.module.impl.world

import android.util.Log
import com.project.lumina.client.constructors.CheatCategory
import com.project.lumina.client.constructors.Element
import com.project.lumina.client.game.InterceptablePacket
import com.project.lumina.relay.definition.Definitions
import com.project.lumina.relay.definition.NbtBlockDefinitionRegistry
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition
import org.cloudburstmc.protocol.bedrock.packet.TextPacket
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket
import org.cloudburstmc.math.vector.Vector3i
import java.io.File

class LitematicaElement : Element(
    name = "Litematica",
    category = CheatCategory.World
) {
    companion object {
        private const val TAG = "LitematicaElement"
        const val SCHEMATIC_DIR = "/sdcard/Lumina/schematics"
        val SEARCH_DIRS = listOf(
            "/sdcard/Lumina/schematics",
            "/sdcard/Download",
            "/sdcard/Downloads",
            "/sdcard/Documents",
            "/sdcard/Lumina",
            "/sdcard"
        )

        fun getAllSchematics(): List<File> {
            val exts = listOf(".litematic", ".schematic", ".nbt")
            return SEARCH_DIRS.flatMap { walkDir(File(it), exts) }.sortedBy { it.name }
        }

        fun walkDir(dir: File, exts: List<String>): List<File> {
            if (!dir.exists() || !dir.isDirectory) return emptyList()
            val results = mutableListOf<File>()
            dir.listFiles()?.forEach { f ->
                if (f.isDirectory) results += walkDir(f, exts)
                else if (exts.any { f.name.endsWith(it) }) results += f
            }
            return results
        }
    }

    private var blocks: List<SchematicBlock> = emptyList()
    private var originX = 0
    private var originY = 0
    private var originZ = 0
    private var currentLayer: Int? = null
    private var ghostActive = false

    // Built once per session from the actual registry
    private var nameToDefinition: Map<String, BlockDefinition>? = null

    private fun buildNameLookup(): Map<String, BlockDefinition> {
        // Use whichever registry was set by GamingPacketHandler on StartGamePacket
        val reg = session.luminaRelaySession.server.peer.codecHelper.blockDefinitions
            as? NbtBlockDefinitionRegistry
            ?: Definitions.blockDefinitionsHashed as? NbtBlockDefinitionRegistry
            ?: Definitions.blockDefinitions as? NbtBlockDefinitionRegistry
            ?: return emptyMap()

        val map = mutableMapOf<String, BlockDefinition>()
        // Access the internal definitions map via reflection once to build our lookup
        try {
            val field = NbtBlockDefinitionRegistry::class.java.getDeclaredField("definitions")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val defs = field.get(reg) as? Map<*, *> ?: return emptyMap()
            for ((_, v) in defs) {
                val defClass = v?.javaClass ?: continue
                val tagField = try { defClass.getDeclaredField("tag").also { it.isAccessible = true } } catch (e: Exception) { continue }
                val tag = tagField.get(v) ?: continue
                val nameMethod = try { tag.javaClass.getMethod("getString", String::class.java) } catch (e: Exception) { continue }
                val blockName = nameMethod.invoke(tag, "name") as? String ?: continue
                val def = v as? BlockDefinition ?: continue
                map[blockName] = def
                // Also store without namespace prefix for fallback
                if (blockName.contains(":")) map[blockName.substringAfter(":")] = def
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build name lookup: ${e.message}", e)
        }
        Log.d(TAG, "Built name lookup: ${map.size} entries")
        return map
    }

    private fun getDefinitionFor(name: String): BlockDefinition? {
        val lookup = nameToDefinition ?: buildNameLookup().also { nameToDefinition = it }
        return lookup[name] ?: lookup[name.substringAfter(":")]
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled) return
        val packet = interceptablePacket.packet
        if (packet is TextPacket) {
            val msg = packet.message.toString().trim()
            if (msg.startsWith("%schem")) {
                interceptablePacket.intercept()
                handleChat(msg)
            }
        }
    }

    override fun onDisabled() {
        super.onDisabled()
        nameToDefinition = null
        blocks = emptyList()
        ghostActive = false
    }

    private fun handleChat(msg: String) {
        val parts = msg.split(" ")
        when (parts.getOrNull(1)?.lowercase()) {
            "load" -> {
                val name = parts.getOrNull(2) ?: run { chat("\u00a7cUsage: %schem load <filename>"); return }
                loadSchematic(name)
            }
            "place" -> {
                val x = parts.getOrNull(2)?.toIntOrNull()
                val y = parts.getOrNull(3)?.toIntOrNull()
                val z = parts.getOrNull(4)?.toIntOrNull()
                if (x == null || y == null || z == null) { chat("\u00a7cUsage: %schem place <x> <y> <z>"); return }
                originX = x; originY = y; originZ = z
                placeGhost()
            }
            "layer" -> {
                currentLayer = parts.getOrNull(2)?.toIntOrNull()
                if (ghostActive) placeGhost()
                chat(if (currentLayer != null) "\u00a7aLayer \u00a7e$currentLayer" else "\u00a7aAll layers")
            }
            "clear" -> { blocks = emptyList(); ghostActive = false; chat("\u00a7aCleared") }
            "list"  -> listSchematics()
            "rebuild" -> { nameToDefinition = null; chat("\u00a7aRegistry cache cleared") }
            else -> {
                chat("\u00a76=== Litematica ===")
                chat("\u00a7e%schem load <file>")
                chat("\u00a7e%schem place <x> <y> <z>")
                chat("\u00a7e%schem layer <n>  \u00a77or blank for all")
                chat("\u00a7e%schem clear")
                chat("\u00a7e%schem list")
                chat("\u00a7e%schem rebuild  \u00a77reload registry")
            }
        }
    }

    private fun loadSchematic(name: String) {
        File(SCHEMATIC_DIR).also { if (!it.exists()) it.mkdirs() }
        val exts = listOf(".litematic", ".schematic", ".nbt")
        val file = SEARCH_DIRS.flatMap { walkDir(File(it), exts) }
            .firstOrNull { it.nameWithoutExtension == name || it.name == name }
            ?: run {
                chat("\u00a7cNot found: $name")
                chat("\u00a77Searched: ${SEARCH_DIRS.size} directories recursively")
                return
            }
        chat("\u00a77Loading \u00a7e${file.name}\u00a77...")
        val parsed = LitematicaParser.parse(file)
        if (parsed.isEmpty()) { chat("\u00a7cParse failed or empty"); return }
        blocks = parsed
        ghostActive = false
        nameToDefinition = null // Reset lookup for new session
        chat("\u00a7aLoaded \u00a7e${parsed.size} \u00a7ablocks from \u00a7e${file.name}")
        chat("\u00a77Use: \u00a7e%schem place <x> <y> <z>")
    }

    private fun placeGhost() {
        if (blocks.isEmpty()) { chat("\u00a7cNo schematic loaded"); return }
        val toPlace = if (currentLayer != null) blocks.filter { it.y == currentLayer } else blocks
        if (toPlace.isEmpty()) { chat("\u00a7cNo blocks on layer $currentLayer"); return }

        // Build lookup if not built yet
        val lookup = nameToDefinition ?: buildNameLookup().also { nameToDefinition = it }
        chat("\u00a77Registry entries: \u00a7e${lookup.size}")

        var sent = 0
        var missing = 0
        val missingNames = mutableSetOf<String>()

        for (block in toPlace) {
            val def = lookup[block.name] ?: lookup[block.name.substringAfter(":")]
            if (def == null) { missing++; missingNames.add(block.name); continue }

            session.clientBound(UpdateBlockPacket().apply {
                blockPosition = Vector3i.from(block.x + originX, block.y + originY, block.z + originZ)
                definition = def
                dataLayer = 0
                flags.add(UpdateBlockPacket.Flag.NEIGHBORS)
                flags.add(UpdateBlockPacket.Flag.NETWORK)
            })
            sent++
        }
        ghostActive = true
        chat("\u00a7aPlaced \u00a7e$sent\u00a7a blocks${if (missing > 0) " \u00a7c($missing unknown)" else ""}")
        if (missingNames.isNotEmpty()) {
            chat("\u00a7cUnknown: ${missingNames.take(3).joinToString()}${if (missingNames.size > 3) "..." else ""}")
        }
    }

    private fun listSchematics() {
        val files = getAllSchematics()
        if (files.isEmpty()) { chat("\u00a7cNo schematics found"); return }
        chat("\u00a76=== Schematics (${files.size}) ===")
        files.forEach { chat("\u00a7e- ${it.name} \u00a77(${it.parent})") }
    }

    private fun chat(msg: String) {
        if (!isSessionCreated) return
        session.displayClientMessage(msg)
    }
}
