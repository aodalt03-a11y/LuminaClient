package com.project.lumina.client.game.module.impl.world

import com.project.lumina.client.R
import com.project.lumina.client.constructors.CheatCategory
import com.project.lumina.client.constructors.Element
import com.project.lumina.client.game.InterceptablePacket
import org.cloudburstmc.math.vector.Vector3i
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket

class LitematicaElement : Element(
    name = "Litematica",
    category = CheatCategory.World,
    displayNameResId = R.string.module_litematica
) {
    private var pendingBlocks: List<SchematicBlock> = emptyList()
    private var originX = 0
    private var originY = 0
    private var originZ = 0
    private var placed = false
    private var tickCounter = 0L

    fun loadSchematic(blocks: List<SchematicBlock>, ox: Int, oy: Int, oz: Int) {
        pendingBlocks = blocks
        originX = ox
        originY = oy
        originZ = oz
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
            for (i in start until end) {
                val sb = pendingBlocks[i]
                val worldPos = Vector3i.from(sb.pos.x + originX, sb.pos.y + originY, sb.pos.z + originZ)
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
