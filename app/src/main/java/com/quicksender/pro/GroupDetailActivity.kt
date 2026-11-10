package com.quicksender.pro

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quicksender.pro.databinding.ActivityGroupDetailBinding

class GroupDetailActivity : AppCompatActivity() {

    private lateinit var b: ActivityGroupDetailBinding
    private var group: GroupItem? = null
    private var allGroups = mutableListOf<GroupItem>()
    private val displayedMembers = mutableListOf<MemberItem>()
    private lateinit var adapter: MemberAdapter

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_lang", "en") ?: "en"
        super.attachBaseContext(QuickSenderApp.updateLocale(newBase, lang))
    }

    private val csvPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importCsv(uri)
    }

    private val phoneContactPicker = registerForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) importFromPhoneContact(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityGroupDetailBinding.inflate(layoutInflater)
        setContentView(b.root)

        val groupId = intent.getStringExtra("groupId") ?: return finish()
        allGroups = GroupStore.loadGroups(this)
        group = allGroups.find { it.id == groupId } ?: return finish()

        b.toolbarDetail.title = group?.name
        b.toolbarDetail.setNavigationOnClickListener { finish() }

        b.rvMembers.layoutManager = LinearLayoutManager(this)
        adapter = MemberAdapter(displayedMembers) { member ->
            group?.members?.remove(member)
            GroupStore.saveGroups(this, allGroups)
            filterMembers(b.etSearchMembers.text.toString())
        }
        b.rvMembers.adapter = adapter
        updateList()

        b.btnAddContacts.setOnClickListener { showAddOptions() }
        b.btnExportGroup.setOnClickListener { exportContacts() }
        b.btnRenameGroup.setOnClickListener { promptRename() }
        b.btnDeleteGroup.setOnClickListener { promptDelete() }

        b.btnCreateBulk.setOnClickListener {
            if (group?.members.isNullOrEmpty()) {
                Toast.makeText(this, "Add contacts first before creating message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, BulkMessageActivity::class.java).apply {
                putExtra("groupId", group?.id)
            }
            startActivity(intent)
        }

        b.etSearchMembers.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterMembers(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterMembers(query: String) {
        displayedMembers.clear()
        val all = group?.members ?: return
        if (query.isEmpty()) {
            displayedMembers.addAll(all)
        } else {
            val q = query.lowercase()
            displayedMembers.addAll(all.filter { it.name.lowercase().contains(q) || it.phone.contains(q) })
        }
        adapter.notifyDataSetChanged()
        b.tvCountInfo.text = "Members (${all.size} / 400)"
    }

    private fun updateList() {
        filterMembers(b.etSearchMembers.text.toString())
    }

    private fun showAddOptions() {
        val options = arrayOf("Import CSV / Excel", "Paste Numbers Manually", "Pick from Phonebook")
        AlertDialog.Builder(this)
            .setTitle("Add Members")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> csvPicker.launch("*/*")
                    1 -> promptPasteNumbers()
                    2 -> phoneContactPicker.launch(null)
                }
            }
            .show()
    }

    private fun importFromPhoneContact(contactUri: Uri) {
        try {
            val cursor = contentResolver.query(contactUri, null, null, null, null) ?: return
            if (cursor.moveToFirst()) {
                val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val id = cursor.getString(idIdx)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val name = cursor.getString(nameIdx) ?: "Friend"

                val phones = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    arrayOf(id),
                    null
                )
                if (phones != null && phones.moveToFirst()) {
                    val pIdx = phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val phone = phones.getString(pIdx).filter { it.isDigit() }
                    phones.close()

                    if (phone.length >= 10 && (group?.members?.size ?: 0) < 400) {
                        if (group?.members?.none { it.phone == phone } == true) {
                            group?.members?.add(MemberItem(name, phone))
                            GroupStore.saveGroups(this, allGroups)
                            updateList()
                            Toast.makeText(this, "Added: $name", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            cursor.close()
        } catch (_: Exception) {}
    }

    private fun promptPasteNumbers() {
        val input = EditText(this).apply {
            hint = "Format (one per line):\n9876543210\nJohn, 9876543210"
            minLines = 6
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Paste Contacts")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                parseAndAdd(input.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importCsv(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            parseAndAdd(text)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to read file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseAndAdd(raw: String) {
        val target = group ?: return
        var added = 0
        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split(',', ';', '\t').map { it.trim().trim('"') }
            var phone = ""
            var name = "Friend"

            if (parts.size >= 2) {
                if (parts[0].any { it.isDigit() } && parts[0].filter { it.isDigit() }.length >= 10) {
                    phone = parts[0].filter { it.isDigit() }
                    name = parts[1].ifEmpty { "Friend" }
                } else {
                    name = parts[0].ifEmpty { "Friend" }
                    phone = parts[1].filter { it.isDigit() }
                }
            } else if (parts.isNotEmpty()) {
                phone = parts[0].filter { it.isDigit() }
            }

            if (phone.length >= 10 && target.members.size < 400) {
                if (!target.members.any { it.phone == phone }) {
                    target.members.add(MemberItem(name, phone))
                    added++
                }
            }
        }
        GroupStore.saveGroups(this, allGroups)
        updateList()
        Toast.makeText(this, "Added $added member(s)", Toast.LENGTH_SHORT).show()
    }

    private fun exportContacts() {
        val members = group?.members ?: return
        if (members.isEmpty()) {
            Toast.makeText(this, "No members to export", Toast.LENGTH_SHORT).show()
            return
        }
        val sb = StringBuilder("Name,Phone\n")
        for (m in members) {
            sb.append("${m.name},${m.phone}\n")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${group?.name}_contacts.csv")
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        startActivity(Intent.createChooser(intent, "Export Contacts"))
    }

    private fun promptRename() {
        val input = EditText(this).apply {
            setText(group?.name)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.rename))
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    group?.name = newName
                    b.toolbarDetail.title = newName
                    GroupStore.saveGroups(this, allGroups)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptDelete() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete))
            .setMessage("Are you sure you want to delete this group?")
            .setPositiveButton("Delete") { _, _ ->
                allGroups.removeAll { it.id == group?.id }
                GroupStore.saveGroups(this, allGroups)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    class MemberAdapter(
        private val members: List<MemberItem>,
        private val onDelete: (MemberItem) -> Unit
    ) : RecyclerView.Adapter<MemberAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvMemberName)
            val phone: TextView = v.findViewById(R.id.tvMemberPhone)
            val btnDel: TextView = v.findViewById(R.id.btnDeleteMember)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_member, parent, false)
            return VH(v)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = members[position]
            holder.name.text = m.name
            holder.phone.text = "+${m.phone}"
            holder.btnDel.setOnClickListener { onDelete(m) }
        }
        override fun getItemCount(): Int = members.size
    }
}