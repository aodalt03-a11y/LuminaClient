package com.project.lumina.client.game.module.impl.world

import android.util.Log
import com.project.lumina.client.R
import com.project.lumina.client.application.AppContext
import com.project.lumina.client.constructors.CheatCategory
import com.project.lumina.client.constructors.Element
import com.project.lumina.client.game.InterceptablePacket
import com.project.lumina.client.game.registry.BlockMappingProvider
import org.cloudburstmc.math.vector.Vector3i
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket

class LitematicaElement : Element(
    name = "Litematica",
    category = CheatCategory.World,
    displayNameResId = R.string.module_litematica_display_name
) {

    // Maps block name (e.g. "minecraft:stone") -> runtime ID for this session
    private var nameToRuntimeId: Map<String, Int> = emptyMap()
    private var paletteReady = false

    // Hardcoded template: 5x1 stone platform, offset -2..+2 on X, Y-1, Z=0
    // Format: Triple(dx, dy, dz) relative to player position when enabled
    private val templateBlocks: List<Triple<Int, Int, Int>> = buildList {
        for (dx in -2..2) {
            add(Triple(dx, -1, 0))
        }
        for (dx in -2..2) {
            add(Triple(dx, -1, 1))
        }
        for (dx in -2..2) {
            add(Triple(dx, -1, 2))
        }
    }
    private val templateBlockName = "minecraft:stone"

    // Origin stored when module is enabled so ghost blocks stay fixed
    private var originX = 0
    private var originY = 0
    private var originZ = 0
    private var originSet = false

    private var tickCounter = 0L

    override fun onEnabled() {
        super.onEnabled()
        originSet = false
        tickCounter = 0L
        if (!paletteReady) {
            session.displayClientMessage("[Litematica] Waiting for block palette...")
        }
    }

    override fun onDisabled() {
        super.onDisabled()
        originSet = false
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet

        // Capture palette from StartGamePacket
        if (packet is StartGamePacket) {
            buildPalette(packet)
            return
        }

        if (!isEnabled) return
        if (!paletteReady) return
        if (packet !is PlayerAuthInputPacket) return

        // Set origin once when first enabled
        if (!originSet) {
            originX = session.localPlayer.posX.toInt()
            originY = session.localPlayer.posY.toInt()
            originZ = session.localPlayer.posZ.toInt()
            originSet = true
            session.displayClientMessage("[Litematica] Ghost blocks placed at ($originX, $originY, $originZ)")
        }

        // Re-send ghost blocks every 20 ticks (1 second) to keep them visible
        tickCounter++
        if (tickCounter % 20 != 0L) return

        sendGhostBlocks()
    }

    private fun buildPalette(packet: StartGamePacket) {
        val palette = packet.blockPalette

        if (palette != null && palette.isNotEmpty()) {
            // Geyser path: palette list index = runtime ID
            val map = mutableMapOf<String, Int>()
            palette.forEachIndexed { index, entry ->
                val name = entry.getString("name", "")
                if (name.isNotEmpty() && !map.containsKey(name)) {
                    map[name] = index
                }
            }
            nameToRuntimeId = map
            paletteReady = true
            Log.i("Litematica", "Built Geyser palette: ${map.size} blocks")
        } else {
            // Vanilla Bedrock path: load from assets
            try {
                val provider = BlockMappingProvider(AppContext.instance)
                val mapping = provider.craftMapping(session.protocolVersion)
                val map = mutableMapOf<String, Int>()
                // BlockMapping exposes getRuntimeByIdentifier
                // We build the reverse map by iterating known names
                // Use the known template block name at minimum
                val rid = mapping.getRuntimeByIdentifier(templateBlockName)
                map[templateBlockName] = rid
                // Also resolve air
                map["minecraft:air"] = mapping.airId
                nameToRuntimeId = map
                paletteReady = true
                Log.i("Litematica", "Built vanilla palette from assets (partial, protocol=${session.protocolVersion})")
            } catch (e: Exception) {
                Log.e("Litematica", "Failed to build vanilla palette", e)
                paletteReady = false
            }
        }
    }

    private fun sendGhostBlocks() {
        val runtimeId = nameToRuntimeId[templateBlockName] ?: run {
            session.displayClientMessage("[Litematica] Block '$templateBlockName' not in palette")
            return
        }

        val definition = SimpleBlockDefinition(runtimeId)

        for ((dx, dy, dz) in templateBlocks) {
            val pos = Vector3i.from(originX + dx, originY + dy, originZ + dz)
            val pkt = UpdateBlockPacket().apply {
                blockPosition = pos
                this.definition = definition
                dataLayer = 0
                flags.addAll(UpdateBlockPacket.FLAG_ALL)
            }
            session.clientBound(pkt)
        }
    }

    // Minimal BlockDefinition wrapper for a known runtime ID
    private inner class SimpleBlockDefinition(private val rid: Int) : BlockDefinition {
        override fun getRuntimeId() = rid
    }
}
