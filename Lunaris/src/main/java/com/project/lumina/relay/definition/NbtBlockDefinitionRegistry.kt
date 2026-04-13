package com.project.lumina.relay.definition

import com.project.lumina.relay.util.BlockPaletteUtils
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.cloudburstmc.nbt.NbtMap
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition
import org.cloudburstmc.protocol.common.DefinitionRegistry


class NbtBlockDefinitionRegistry(
    definitions: List<NbtMap>,
    hashed: Boolean
) : DefinitionRegistry<BlockDefinition> {

    private val definitions = Int2ObjectOpenHashMap<NbtBlockDefinition>()

    init {
        var counter = 0
        for (definition in definitions) {
            val runtimeId = if (hashed) BlockPaletteUtils.createHash(definition) else counter++
            this.definitions.put(runtimeId, NbtBlockDefinition(runtimeId, definition))
        }
    }

    override fun getDefinition(runtimeId: Int): BlockDefinition? {
        return definitions.get(runtimeId)
    }

    override fun isRegistered(definition: BlockDefinition?): Boolean {
        return definition != null && (definitions.get(definition.runtimeId) == definition)
    }

    private val nameToDefinition: Map<String, NbtBlockDefinition> by lazy {
        val map = mutableMapOf<String, NbtBlockDefinition>()
        definitions.values.forEach { def ->
            val name = def.tag.getString("name") ?: return@forEach
            map[name] = def
            if (name.contains(":")) map[name.substringAfter(":")] = def
        }
        map
    }

    fun getDefinitionByName(name: String): NbtBlockDefinition? =
        nameToDefinition[name] ?: nameToDefinition[name.substringAfter(":")]

    @JvmRecord
    data class NbtBlockDefinition(val runtimeId: Int, val tag: NbtMap) : BlockDefinition {
        override fun getRuntimeId(): Int {
            return runtimeId
        }
    }

}