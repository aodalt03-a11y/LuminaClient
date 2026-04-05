package com.project.lumina.client.game.module.impl.world

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class LitematicaCategoryContent(context: Context) : LinearLayout(context) {

    private val schematicDir = File(LitematicaElement.SCHEMATIC_DIR)
    private val adapter = SchematicAdapter(mutableListOf())
    private lateinit var statusText: TextView

    init { orientation = VERTICAL; build() }

    private fun build() {
        addView(TextView(context).apply {
            text = "Litematica Schematics"
            textSize = 16f
            setPadding(24, 16, 24, 8)
            setTextColor(0xFFFFAA00.toInt())
        })
        statusText = TextView(context).apply {
            text = "Dir: ${LitematicaElement.SCHEMATIC_DIR}"
            textSize = 11f
            setPadding(24, 0, 24, 8)
            setTextColor(0xFFAAAAAA.toInt())
        }
        addView(statusText)
        addView(Button(context).apply {
            text = "Refresh"
            setOnClickListener { refreshList() }
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also { it.setMargins(24, 4, 24, 4) }
        })
        addView(RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = this@LitematicaCategoryContent.adapter
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (200 * resources.displayMetrics.density).toInt()).also { it.setMargins(24, 4, 24, 4) }
        })
        addView(TextView(context).apply {
            text = "Commands:
%schem load <file>
%schem place <x> <y> <z>
%schem layer <n> | clear | list"
            textSize = 11f
            setPadding(24, 8, 24, 8)
            setTextColor(0xFFCCCCCC.toInt())
        })
        refreshList()
    }

    fun refreshList() {
        if (!schematicDir.exists()) schematicDir.mkdirs()
        val files = schematicDir.listFiles { f ->
            f.name.endsWith(".litematic") || f.name.endsWith(".schematic") || f.name.endsWith(".nbt")
        }?.sortedBy { it.name } ?: emptyList()
        adapter.update(files.map { it.name })
        statusText.text = "${files.size} file(s) in ${LitematicaElement.SCHEMATIC_DIR}"
    }

    inner class SchematicAdapter(private val items: MutableList<String>) : RecyclerView.Adapter<SchematicAdapter.VH>() {
        fun update(new: List<String>) { items.clear(); items.addAll(new); notifyDataSetChanged() }
        inner class VH(v: View) : RecyclerView.ViewHolder(v) { val tv = v as TextView }
        override fun onCreateViewHolder(parent: ViewGroup, vt: Int) = VH(TextView(parent.context).apply {
            id = android.R.id.text1; textSize = 13f; setPadding(16, 12, 16, 12)
            setTextColor(0xFFEEEEEE.toInt())
            layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
        })
        override fun onBindViewHolder(h: VH, pos: Int) { h.tv.text = items[pos] }
        override fun getItemCount() = items.size
    }
}
