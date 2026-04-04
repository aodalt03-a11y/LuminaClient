package com.project.lumina.client.game.module.impl.world

import com.project.lumina.client.R
import com.project.lumina.client.constructors.CheatCategory
import com.project.lumina.client.constructors.Element
import com.project.lumina.client.game.InterceptablePacket
import com.project.lumina.client.game.module.api.setting.stringValue
import org.cloudburstmc.math.vector.Vector3i
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket
import java.io.File

class LitematicaElement : Element(
    name = "Litematica",
    category = CheatCategory.World,
    displayNameResId = R.string.module_litematica
) {
    private fun getSchematicFiles(): List<String> {
        val dirs = listOf("/sdcard/Download", "/sdcard/Downloads", "/sdcard/Documents")
        val exts = listOf(".litematic", ".schematic", ".nbt")
        val files = mutableListOf("none")
        for (dir in dirs) {
            File(dir).listFiles()?.filter { f -> exts.any { f.name.endsWith(it) } }
                ?.forEach { files.add(it.absolutePath) }
        }
        return files.distinct()
    }

    private val schematicFile by stringValue("File", "none", getSchematicFiles())
    private val offsetX by floatValue("OffsetX", 0f, -100f..100f)
    private val offsetY by floatValue("OffsetY", 0f, -100f..100f)
    private val offsetZ by floatValue("OffsetZ", 0f, -100f..100f)

    private var pendingBlocks: List<SchematicBlock> = emptyList()
    private var placed = false
    private var tickCounter = 0L

    override fun onEnabled() {
        super.onEnabled()
        if (schematicFile == "none") return
        val file = File(schematicFile)
        if (!file.exists()) return
        pendingBlocks = LitematicaParser.parse(file.inputStream(), file.name)
        placed = false
        tickCounter = 0L
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || placed || pendingBlocks.isEmpty()) return
        val packet = interceptablePacket.packet
        if (packet is PlayerAuthInputPacket) {
            tickCounter++
            if (tickCounter % 5L != 0L) return
            val batchSize = 64
            val start = ((tickCounter / 5L - 1) * batchSize).toInt()
            if (start >= pendingBlocks.size) { placed = true; return }
            val end = minOf(start + batchSize, pendingBlocks.size)
            val ox = session.localPlayer.posX.toInt() + offsetX.toInt()
            val oy = session.localPlayer.posY.toInt() + offsetY.toInt()
            val oz = session.localPlayer.posZ.toInt() + offsetZ.toInt()
            for (i in start until end) {
                val sb = pendingBlocks[i]
                val worldPos = Vector3i.from(sb.pos.x + ox, sb.pos.y + oy, sb.pos.z + oz)
                val runtimeId = try { session.blockMapping.getRuntimeByIdentifier("minecraft:${sb.blockName}") } catch (e: Exception) { 0 }
                if (runtimeId != 0) {
                    session.clientBound(UpdateBlockPacket().apply {
                        blockPosition = worldPos
                        dataLayer = 0
                        flags.add(UpdateBlockPacket.Flag.NETWORK)
                        definition = session.blockMapping.getDefinition(runtimeId)
                    })
                }
            }
        }
    }
}
