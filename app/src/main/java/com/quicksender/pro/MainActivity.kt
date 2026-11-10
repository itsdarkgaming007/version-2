package com.quicksender.pro

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quicksender.pro.databinding.ActivityMainBinding
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val allGroups = mutableListOf<GroupItem>()
    private val displayedGroups = mutableListOf<GroupItem>()
    private lateinit var adapter: GroupAdapter

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_lang", "en") ?: "en"
        super.attachBaseContext(QuickSenderApp.updateLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnMenuLeft.setOnClickListener { showSettingsMenu(it) }

        b.rvGroups.layoutManager = LinearLayoutManager(this)
        adapter = GroupAdapter(displayedGroups) { group ->
            val intent = Intent(this, GroupDetailActivity::class.java).apply {
                putExtra("groupId", group.id)
            }
            startActivity(intent)
        }
        b.rvGroups.adapter = adapter

        b.fabCenter.setOnClickListener { promptCreateGroup() }
        b.fabBottom.setOnClickListener { promptCreateGroup() }

        b.etSearchGroups.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterGroups(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun filterGroups(query: String) {
        displayedGroups.clear()
        if (query.isEmpty()) {
            displayedGroups.addAll(allGroups)
        } else {
            val q = query.lowercase()
            displayedGroups.addAll(allGroups.filter { it.name.lowercase().contains(q) })
        }
        adapter.notifyDataSetChanged()
    }

    private fun refreshList() {
        allGroups.clear()
        allGroups.addAll(GroupStore.loadGroups(this))
        filterGroups(b.etSearchGroups.text.toString())

        if (allGroups.isEmpty()) {
            b.emptyStateView.visibility = View.VISIBLE
            b.rvGroups.visibility = View.GONE
            b.fabBottom.visibility = View.GONE
            b.etSearchGroups.visibility = View.GONE
        } else {
            b.emptyStateView.visibility = View.GONE
            b.rvGroups.visibility = View.VISIBLE
            b.fabBottom.visibility = View.VISIBLE
            b.etSearchGroups.visibility = View.VISIBLE
        }
    }

    private fun promptCreateGroup() {
        val input = EditText(this).apply {
            hint = "Enter group name (e.g., VIP Customers)"
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.btn_create_group))
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newGroup = GroupItem(UUID.randomUUID().toString(), name)
                    allGroups.add(0, newGroup)
                    GroupStore.saveGroups(this, allGroups)
                    refreshList()
                    val intent = Intent(this, GroupDetailActivity::class.java).apply {
                        putExtra("groupId", newGroup.id)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Group name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSettingsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, getString(R.string.settings))
        popup.menu.add(0, 2, 1, getString(R.string.templates))
        popup.menu.add(0, 3, 2, getString(R.string.reports))
        popup.menu.add(0, 4, 3, getString(R.string.accessibility_perm))
        popup.menu.add(0, 5, 4, getString(R.string.share_app))
        popup.menu.add(0, 6, 5, getString(R.string.about))

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> startActivity(Intent(this, SettingsActivity::class.java))
                2 -> startActivity(Intent(this, TemplatesActivity::class.java))
                3 -> startActivity(Intent(this, ReportsActivity::class.java))
                4 -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                5 -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "QuickSender Pro v4.0 - Bulk Sender")
                    }
                    startActivity(Intent.createChooser(intent, "Share via"))
                }
                6 -> {
                    AlertDialog.Builder(this)
                        .setTitle("QuickSender Pro v4.0")
                        .setMessage("Production Edition\n• Custom Templates Library\n• Delivery Reports & History\n• Anti-Ban Batch Cooldowns\n• CSV Contact Exporter\n• Search Filter & Dual Contact Picker")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            true
        }
        popup.show()
    }

    class GroupAdapter(
        private val items: List<GroupItem>,
        private val onClick: (GroupItem) -> Unit
    ) : RecyclerView.Adapter<GroupAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvGroupName)
            val count: TextView = v.findViewById(R.id.tvMemberCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            holder.count.text = "${item.members.size} / 400 members"
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}