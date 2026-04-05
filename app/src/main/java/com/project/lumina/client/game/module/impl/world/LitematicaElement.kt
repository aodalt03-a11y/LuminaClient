package com.project.lumina.client.game.module.impl.world

import android.content.Intent
import android.net.Uri
import com.project.lumina.client.R
import com.project.lumina.client.application.AppContext
import com.project.lumina.client.constructors.CheatCategory
import com.project.lumina.client.constructors.Element
import com.project.lumina.client.game.InterceptablePacket
import org.cloudburstmc.math.vector.Vector3i
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket
import java.io.File
import java.io.InputStream

class LitematicaElement : Element(
    name = "Litematica",
    category = CheatCategory.World,
    displayNameResId = R.string.module_litematica
) {
    private val offsetX by floatValue("OffsetX", 0f, -100f..100f)
    private val offsetY by floatValue("OffsetY", 0f, -100f..100f)
    private val offsetZ by floatValue("OffsetZ", 0f, -100f..100f)

    private var pendingBlocks: List<SchematicBlock> = emptyList()
    private var placed = false
    private var tickCounter = 0L
    private var selectedUri: Uri? = null

    companion object {
        var pendingInstance: LitematicaElement? = null
        fun onFilePicked(uri: Uri) {
            pendingInstance?.loadFromUri(uri)
        }
    }

    fun loadFromUri(uri: Uri) {
        try {
            val ctx = AppContext.instance
            val name = uri.lastPathSegment ?: "file.litematic"
            val stream: InputStream = ctx.contentResolver.openInputStream(uri) ?: return
            pendingBlocks = LitematicaParser.parse(stream, name)
            placed = false
            tickCounter = 0L
            selectedUri = uri
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun debugBlockMapping() {
        val testNames = listOf("minecraft:stone", "minecraft:glass", "minecraft:dirt", "minecraft:planks")
        for (name in testNames) {
            val id = try { session.blockMapping.getRuntimeByIdentifier(name) } catch (e: Exception) { -1 }
            android.util.Log.d("Litematica", "identifier '\$name' -> runtimeId=\$id")
        }
    }

    private fun buildTestSchematic(): List<SchematicBlock> {
        val blocks = mutableListOf<SchematicBlock>()
        for (x in 0..4) for (z in 0..4) {
            blocks.add(SchematicBlock(Vector3i.from(x, 0, z), "stone"))
        }
        for (x in 0..4) for (z in 0..4) {
            blocks.add(SchematicBlock(Vector3i.from(x, 3, z), "glass"))
        }
        for (y in 0..3) {
            blocks.add(SchematicBlock(Vector3i.from(0, y, 0), "stone"))
            blocks.add(SchematicBlock(Vector3i.from(4, y, 0), "stone"))
            blocks.add(SchematicBlock(Vector3i.from(0, y, 4), "stone"))
            blocks.add(SchematicBlock(Vector3i.from(4, y, 4), "stone"))
        }
        return blocks
    }

    override fun onEnabled() {
        super.onEnabled()
        pendingBlocks = buildTestSchematic()
        placed = false
        tickCounter = 0L
        debugBlockMapping()
        android.util.Log.d("Litematica", "Loaded test schematic: \${pendingBlocks.size} blocks")
        android.widget.Toast.makeText(AppContext.instance, "Litematica: \${pendingBlocks.size} blocks loaded", android.widget.Toast.LENGTH_SHORT).show()
        pendingInstance = this
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        AppContext.instance.startActivity(Intent.createChooser(intent, "Select Schematic").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || placed || pendingBlocks.isEmpty()) return
        val packet = interceptablePacket.packet
        if (packet is MovePlayerPacket) {
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
