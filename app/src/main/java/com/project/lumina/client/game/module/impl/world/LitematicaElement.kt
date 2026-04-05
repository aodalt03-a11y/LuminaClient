package com.project.lumina.client.game.module.impl.world

import android.content.Intent
import android.os.Environment
import android.widget.Toast
import com.project.lumina.client.R
import com.project.lumina.client.application.AppContext
import com.project.lumina.client.constructors.CheatCategory
import com.project.lumina.client.constructors.Element
import com.project.lumina.client.game.InterceptablePacket
import org.cloudburstmc.math.vector.Vector3i
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket
import java.io.File

class LitematicaElement : Element(
    name = "Litematica",
    category = CheatCategory.Litematica,
    displayNameResId = R.string.module_litematica
) {
    private val offsetX by floatValue("OffsetX", 0f, -200f..200f)
    private val offsetY by floatValue("OffsetY", 0f, -200f..200f)
    private val offsetZ by floatValue("OffsetZ", 0f, -200f..200f)
    private val useTestSchematic by boolValue("TestMode", true)
    private val nextFile by boolValue("NextFile", false)

    private var schematicFiles: List<File> = emptyList()
    private var currentFileIndex = 0
    private var pendingBlocks: List<SchematicBlock> = emptyList()
    private var placed = false
    private var tickCounter = 0L

    companion object {
        var pendingInstance: LitematicaElement? = null
        fun onFilePicked(uri: android.net.Uri) {
            pendingInstance?.loadFromUri(uri)
        }
    }

    private fun scanFiles(): List<File> {
        val exts = listOf(".litematic", ".schematic", ".nbt")
        val dirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            File(Environment.getExternalStorageDirectory(), "Litematica"),
            File(Environment.getExternalStorageDirectory(), "games/com.mojang")
        )
        val found = mutableListOf<File>()
        for (dir in dirs) {
            dir.listFiles()?.filter { f -> exts.any { f.name.endsWith(it, ignoreCase = true) } }
                ?.let { found.addAll(it) }
        }
        return found.sortedByDescending { it.lastModified() }
    }

    fun loadFromUri(uri: android.net.Uri) {
        try {
            val ctx = AppContext.instance
            val name = uri.lastPathSegment ?: "file.litematic"
            val stream = ctx.contentResolver.openInputStream(uri) ?: return
            pendingBlocks = LitematicaParser.parse(stream, name)
            placed = false
            tickCounter = 0L
            Toast.makeText(ctx, "Loaded ${pendingBlocks.size} blocks from file", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadFile(file: File) {
        try {
            pendingBlocks = LitematicaParser.parse(file.inputStream(), file.name)
            placed = false
            tickCounter = 0L
            Toast.makeText(AppContext.instance, "Loaded: ${file.name} (${pendingBlocks.size} blocks)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(AppContext.instance, "Failed to load: ${file.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildTestSchematic(): List<SchematicBlock> {
        val blocks = mutableListOf<SchematicBlock>()
        for (x in 0..4) for (z in 0..4) blocks.add(SchematicBlock(Vector3i.from(x, 0, z), "stone"))
        for (x in 0..4) for (z in 0..4) blocks.add(SchematicBlock(Vector3i.from(x, 3, z), "glass"))
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
        pendingInstance = this
        placed = false
        tickCounter = 0L

        if (useTestSchematic) {
            pendingBlocks = buildTestSchematic()
            Toast.makeText(AppContext.instance, "Test schematic: ${pendingBlocks.size} blocks", Toast.LENGTH_SHORT).show()
            return
        }

        schematicFiles = scanFiles()
        if (schematicFiles.isEmpty()) {
            Toast.makeText(AppContext.instance, "No schematic files found! Put .litematic/.schematic in Downloads", Toast.LENGTH_LONG).show()
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            AppContext.instance.startActivity(Intent.createChooser(intent, "Select Schematic").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
            return
        }

        loadFile(schematicFiles[currentFileIndex])
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled) return

        val packet = interceptablePacket.packet

        // Handle NextFile toggle
        if (packet is MovePlayerPacket && nextFile) {
            if (schematicFiles.isNotEmpty()) {
                currentFileIndex = (currentFileIndex + 1) % schematicFiles.size
                loadFile(schematicFiles[currentFileIndex])
            }
            return
        }

        if (placed || pendingBlocks.isEmpty()) return

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
                val runtimeId = try {
                    var id = session.blockMapping.getRuntimeByIdentifier("minecraft:${sb.blockName}")
                    if (id == 0) id = session.blockMapping.getRuntimeByIdentifier("minecraft:${sb.blockName.lowercase()}")
                    if (id == 0) {
                        (0..10000).firstOrNull { rid ->
                            session.blockMapping.getDefinition(rid).identifier.contains(sb.blockName, ignoreCase = true)
                        } ?: 0
                    } else id
                } catch (e: Exception) { 0 }
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
    fun getBlockCounts(): Map<String, Int> {
        return pendingBlocks.groupingBy { it.blockName }.eachCount()
    }
}
