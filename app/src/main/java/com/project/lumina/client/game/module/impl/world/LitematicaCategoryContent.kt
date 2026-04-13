package com.project.lumina.client.game.module.impl.world

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@androidx.compose.runtime.Composable
fun LitematicaCategoryContent() {
    var files by remember { mutableStateOf(listOf<String>()) }

    fun refresh() {
        files = LitematicaElement.getAllSchematics().map { "${it.name} (${it.parent})" }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Litematica", color = Color(0xFFFFAA00), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("${files.size} schematic(s) found", color = Color(0xFFAAAAAA), fontSize = 11.sp)
        Button(
            onClick = { refresh() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Refresh", color = Color.White) }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(180.dp)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp)),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (files.isEmpty()) {
                item { Text("No schematics found", color = Color(0xFF666666), fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
            } else {
                items(files) { name ->
                    Text(name, color = Color(0xFFEEEEEE), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Commands:", color = Color(0xFFCCCCCC), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        listOf("%schem load <file>", "%schem place <x> <y> <z>", "%schem layer <n>", "%schem clear", "%schem list").forEach {
            Text(it, color = Color(0xFFFFCC44), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
