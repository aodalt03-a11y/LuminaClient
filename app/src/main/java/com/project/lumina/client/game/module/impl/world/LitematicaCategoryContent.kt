package com.project.lumina.client.game.module.impl.world

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.project.lumina.client.constructors.GameManager

@Composable
fun LitematicaCategoryContent() {
    val litematica = GameManager.elements.filterIsInstance<LitematicaElement>().firstOrNull()

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Litematica", fontSize = 18.sp, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        if (litematica == null) {
            Text("Module not found", color = Color.Red)
            return
        }

        val instance = LitematicaElement.pendingInstance
        if (instance == null) {
            Text("No schematic loaded.\nEnable Litematica module first.", color = Color.Gray, fontSize = 12.sp)
            return
        }

        val blockCounts: List<Pair<String, Int>> = instance.getBlockCounts()
            .entries
            .map { entry -> Pair(entry.key, entry.value) }
            .sortedByDescending { it.second }

        val total = blockCounts.sumOf { it.second }
        Text("Blocks needed: $total total", color = Color.White, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn {
            items(blockCounts) { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(pair.first, color = Color.White, fontSize = 12.sp)
                    Text("x${pair.second}", color = Color(0xFF4CAF50), fontSize = 12.sp)
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 0.5.dp)
            }
        }
    }
}
