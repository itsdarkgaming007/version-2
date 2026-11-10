package com.quicksender.pro

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quicksender.pro.databinding.ActivityTemplatesBinding
import java.util.UUID

class TemplatesActivity : AppCompatActivity() {

    private lateinit var b: ActivityTemplatesBinding
    private val templates = mutableListOf<TemplateItem>()
    private lateinit var adapter: TemplateAdapter

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_lang", "en") ?: "en"
        super.attachBaseContext(QuickSenderApp.updateLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTemplatesBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbarTemplates.setNavigationOnClickListener { finish() }

        b.rvTemplates.layoutManager = LinearLayoutManager(this)
        templates.addAll(GroupStore.loadTemplates(this))
        adapter = TemplateAdapter(templates) { item -> promptEdit(item) }
        b.rvTemplates.adapter = adapter

        b.btnAddTemplate.setOnClickListener { promptCreate() }
    }

    private fun promptCreate() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val etTitle = EditText(this).apply { hint = "Template Title (e.g., Welcome Offer)" }
        val etBody = EditText(this).apply {
            hint = "Message body (e.g., Hello {name}...)"
            minLines = 4
        }
        layout.addView(etTitle)
        layout.addView(etBody)

        AlertDialog.Builder(this)
            .setTitle("Create Template")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val title = etTitle.text.toString().trim()
                val body = etBody.text.toString().trim()
                if (title.isNotEmpty() && body.isNotEmpty()) {
                    val t = TemplateItem(UUID.randomUUID().toString(), title, body)
                    templates.add(0, t)
                    GroupStore.saveTemplates(this, templates)
                    adapter.notifyDataSetChanged()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptEdit(item: TemplateItem) {
        val options = arrayOf("Use / View", "Delete")
        AlertDialog.Builder(this)
            .setTitle(item.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        AlertDialog.Builder(this)
                            .setTitle(item.title)
                            .setMessage(item.message)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    1 -> {
                        templates.remove(item)
                        GroupStore.saveTemplates(this, templates)
                        adapter.notifyDataSetChanged()
                    }
                }
            }
            .show()
    }

    class TemplateAdapter(
        private val list: List<TemplateItem>,
        private val onClick: (TemplateItem) -> Unit
    ) : RecyclerView.Adapter<TemplateAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.tvTemplateTitle)
            val body: TextView = v.findViewById(R.id.tvTemplateBody)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_template, parent, false)
            return VH(v)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.title.text = item.title
            holder.body.text = item.message
            holder.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount(): Int = list.size
    }
}