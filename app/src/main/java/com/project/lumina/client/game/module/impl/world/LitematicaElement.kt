package com.project.lumina.client.game.module.impl.world

import android.util.Log
import com.project.lumina.client.game.CheatCategory
import com.project.lumina.client.game.element.Element
import com.project.lumina.client.relay.NetBound
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket
import org.cloudburstmc.protocol.bedrock.packet.TextPacket
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket
import org.cloudburstmc.math.vector.Vector3i
import java.io.File

class LitematicaElement : Element(
    name        = "Litematica",
    displayName = "Litematica",
    category    = CheatCategory.World,
    desc        = "Place ghost block schematics in-game"
) {
    companion object {
        private const val TAG = "LitematicaElement"
        val SCHEMATIC_DIR = "/sdcard/BedrockForge/schematics"
    }

    private var blocks: List<SchematicBlock> = emptyList()
    private var originX = 0; private var originY = 0; private var originZ = 0
    private var currentLayer: Int? = null
    private var ghostActive = false
    private var currentFile = ""

    override fun beforePacketBound(packet: BedrockPacket, session: NetBound): Boolean {
        if (!isEnabled) return false
        if (packet is TextPacket) handleChat(packet.message.trim(), session)
        return false
    }

    private fun handleChat(msg: String, session: NetBound) {
        if (!msg.startsWith("%schem")) return
        val parts = msg.split(" ")
        when (parts.getOrNull(1)?.lowercase()) {
            "load" -> {
                val name = parts.getOrNull(2) ?: run { chat(session, "§cUsage: %schem load <filename>"); return }
                loadSchematic(name, session)
            }
            "place" -> {
                val x = parts.getOrNull(2)?.toIntOrNull()
                val y = parts.getOrNull(3)?.toIntOrNull()
                val z = parts.getOrNull(4)?.toIntOrNull()
                if (x == null || y == null || z == null) { chat(session, "§cUsage: %schem place <x> <y> <z>"); return }
                originX = x; originY = y; originZ = z
                placeGhost(session)
            }
            "layer" -> {
                currentLayer = parts.getOrNull(2)?.toIntOrNull()
                if (ghostActive) placeGhost(session)
                chat(session, if (currentLayer != null) "§aShowing layer §e${currentLayer}" else "§aShowing all layers")
            }
            "clear" -> { blocks = emptyList(); ghostActive = false; currentFile = ""; chat(session, "§aSchematic cleared") }
            "list"  -> listSchematics(session)
            else    -> {
                chat(session, "§6=== Litematica Commands ===")
                chat(session, "§e%schem load <file>   §7- Load schematic")
                chat(session, "§e%schem place <x y z> §7- Place at coords")
                chat(session, "§e%schem layer <n>     §7- Single layer")
                chat(session, "§e%schem layer         §7- All layers")
                chat(session, "§e%schem clear         §7- Remove ghosts")
                chat(session, "§e%schem list          §7- List files")
            }
        }
    }

    private fun loadSchematic(name: String, session: NetBound) {
        val dir = File(SCHEMATIC_DIR).also { if (!it.exists()) it.mkdirs() }
        val file = listOf(File(dir, name), File(dir, "$name.litematic"), File(dir, "$name.schematic"), File(dir, "$name.nbt"))
            .firstOrNull { it.exists() } ?: run {
            chat(session, "§cFile not found: $name")
            chat(session, "§7Put files in /sdcard/BedrockForge/schematics/")
            return
        }
        chat(session, "§7Loading §e${file.name}§7...")
        val parsed = LitematicaParser.parse(file)
        if (parsed.isEmpty()) { chat(session, "§cParse failed or empty"); return }
        blocks = parsed; currentFile = file.name; ghostActive = false
        chat(session, "§aLoaded §e${parsed.size} §ablocks from §e${file.name}")
        chat(session, "§7Use: §e%schem place <x> <y> <z>")
    }

    private fun placeGhost(session: NetBound) {
        if (blocks.isEmpty()) { chat(session, "§cNo schematic loaded"); return }
        val toPlace = if (currentLayer != null) blocks.filter { it.y == currentLayer } else blocks
        if (toPlace.isEmpty()) { chat(session, "§cNo blocks on layer ${currentLayer}"); return }
        var sent = 0
        for (block in toPlace) {
            val rid = NbtBlockDefinitionRegistry.getRuntimeIdByName(block.name)
            if (rid < 0) { Log.w(TAG, "No runtimeId for: ${block.name}"); continue }
            val pkt = UpdateBlockPacket().apply {
                blockPosition = Vector3i.from(block.x + originX, block.y + originY, block.z + originZ)
                this.runtimeId = rid.toLong()
                dataLayer = 0
                flags.add(UpdateBlockPacket.Flag.NETWORK)
            }
            session.clientBound(pkt)
            sent++
        }
        ghostActive = true
        chat(session, "§aPlaced §e$sent§a ghost blocks${if (currentLayer != null) " (layer ${currentLayer})" else ""}")
    }

    private fun listSchematics(session: NetBound) {
        val dir = File(SCHEMATIC_DIR)
        if (!dir.exists()) { chat(session, "§cFolder not found: $SCHEMATIC_DIR"); return }
        val files = dir.listFiles { f -> f.name.endsWith(".litematic") || f.name.endsWith(".schematic") || f.name.endsWith(".nbt") }
        if (files.isNullOrEmpty()) { chat(session, "§cNo schematic files found"); return }
        chat(session, "§6=== Schematics (${files.size}) ===")
        files.sortedBy { it.name }.forEach { chat(session, "§e- ${it.name}") }
    }

    private fun chat(session: NetBound, msg: String) {
        session.clientBound(TextPacket().apply {
            type = TextPacket.Type.RAW
            isNeedsTranslation = false
            message = msg
            xuid = ""
            platformChatId = ""
        })
    }
}
