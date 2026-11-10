package com.quicksender.pro

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.quicksender.pro.databinding.ActivityBulkMessageBinding
import kotlin.math.max
import kotlin.random.Random

class BulkMessageActivity : AppCompatActivity() {

    private lateinit var b: ActivityBulkMessageBinding
    private var group: GroupItem? = null
    private var selectedMediaUri: Uri? = null
    private var mediaMimeType: String? = null

    private var isDispatching = false
    private var currentIndex = 0
    private var awaitingReturn = false
    private var batchSize = 25
    private var sentInCurrentBatch = 0
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_lang", "en") ?: "en"
        super.attachBaseContext(QuickSenderApp.updateLocale(newBase, lang))
    }

    private val mediaPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedMediaUri = uri
            showPreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityBulkMessageBinding.inflate(layoutInflater)
        setContentView(b.root)

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "QuickSender:DispatchLock")

        val groupId = intent.getStringExtra("groupId") ?: return finish()
        group = GroupStore.loadGroups(this).find { it.id == groupId } ?: return finish()

        b.toolbarBulk.setNavigationOnClickListener { finish() }

        b.btnAttachImage.setOnClickListener {
            mediaMimeType = "image/*"
            mediaPicker.launch("image/*")
        }
        b.btnAttachVideo.setOnClickListener {
            mediaMimeType = "video/*"
            mediaPicker.launch("video/*")
        }
        b.btnAttachDoc.setOnClickListener {
            mediaMimeType = "application/pdf"
            mediaPicker.launch("application/pdf")
        }
        b.btnRemoveMedia.setOnClickListener {
            selectedMediaUri = null
            mediaMimeType = null
            b.previewContainer.visibility = View.GONE
            b.tvCaptionLabel.text = "Message"
        }

        b.btnPickTemplate.setOnClickListener { showTemplatePicker() }

        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val defaultDelay = prefs.getInt("default_delay", 5)
        batchSize = prefs.getInt("batch_size", 25)

        b.sliderDelay.value = defaultDelay.toFloat()
        b.tvDelayLabel.text = "Anti-Ban Delay: ${defaultDelay}s (±1s jitter)"

        b.sliderDelay.addOnChangeListener { _, value, _ ->
            b.tvDelayLabel.text = "Anti-Ban Delay: ${value.toInt()}s (±1s jitter)"
        }

        b.btnSendAll.setOnClickListener {
            if (isDispatching) {
                stopQueue()
            } else {
                startQueue()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isDispatching && awaitingReturn) {
            awaitingReturn = false
            sentInCurrentBatch++

            if (sentInCurrentBatch >= batchSize) {
                // Batch cooldown pause
                sentInCurrentBatch = 0
                b.tvProgressStatus.text = "Batch Cooldown (30s) to protect WhatsApp account..."
                handler.postDelayed({ dispatchNext() }, 30000L)
            } else {
                val baseDelay = b.sliderDelay.value.toInt() * 1000L
                val jitter = Random.nextLong(-1000L, 1001L)
                val delay = max(2000L, baseDelay + jitter)
                handler.postDelayed({ dispatchNext() }, delay)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseLock()
    }

    private fun acquireLock() {
        if (wakeLock?.isHeld == false) wakeLock?.acquire(3600000L)
    }

    private fun releaseLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    private fun showTemplatePicker() {
        val list = GroupStore.loadTemplates(this)
        if (list.isEmpty()) {
            Toast.makeText(this, "No saved templates", Toast.LENGTH_SHORT).show()
            return
        }
        val titles = list.map { it.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Template")
            .setItems(titles) { _, which ->
                b.etMessage.setText(list[which].message)
            }
            .show()
    }

    private fun showPreview() {
        b.previewContainer.visibility = View.VISIBLE
        b.tvCaptionLabel.text = "Caption (Optional)"
        if (mediaMimeType?.startsWith("image") == true) {
            b.imgPreview.setImageURI(selectedMediaUri)
            b.imgPreview.visibility = View.VISIBLE
            b.tvMediaStatus.text = ""
        } else {
            b.imgPreview.visibility = View.GONE
            b.tvMediaStatus.text = "Attached: ${mediaMimeType?.substringAfter('/')?.uppercase()}"
        }
    }

    private fun startQueue() {
        val members = group?.members ?: return
        if (members.isEmpty()) return

        val text = b.etMessage.text.toString().trim()
        if (text.isEmpty() && selectedMediaUri == null) {
            Toast.makeText(this, "Enter a message or attach media", Toast.LENGTH_SHORT).show()
            return
        }

        acquireLock()
        isDispatching = true
        currentIndex = 0
        sentInCurrentBatch = 0
        b.btnSendAll.text = getString(R.string.pause)
        b.progressSend.visibility = View.VISIBLE
        b.tvProgressStatus.visibility = View.VISIBLE
        b.progressSend.max = members.size
        b.progressSend.progress = 0

        dispatchNext()
    }

    private fun stopQueue() {
        isDispatching = false
        awaitingReturn = false
        releaseLock()
        b.btnSendAll.text = "Resume Dispatch"
    }

    private fun dispatchNext() {
        val members = group?.members ?: return
        if (!isDispatching || currentIndex >= members.size) {
            isDispatching = false
            releaseLock()
            b.btnSendAll.text = getString(R.string.completed)
            GroupStore.addReport(this, group?.name ?: "Group", members.size, currentIndex, "Completed")
            return
        }

        val item = members[currentIndex]
        val pkg = if (b.rbBusiness.isChecked) "com.whatsapp.w4b" else "com.whatsapp"

        var msg = b.etMessage.text.toString()
        msg = Spintax.resolve(msg).replace("{name}", item.name).replace("{phone}", item.phone)

        val cleanPhone = if (item.phone.length <= 10) "91${item.phone}" else item.phone

        awaitingReturn = true
        currentIndex++
        b.progressSend.progress = currentIndex
        b.tvProgressStatus.text = "Sending: $currentIndex / ${members.size}"

        if (selectedMediaUri != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mediaMimeType ?: "*/*"
                setPackage(pkg)
                putExtra(Intent.EXTRA_STREAM, selectedMediaUri)
                if (msg.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, msg)
                putExtra("jid", "$cleanPhone@s.whatsapp.net")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(shareIntent)
            } catch (_: Exception) {
                openViaUrl(cleanPhone, msg, pkg)
            }
        } else {
            openViaUrl(cleanPhone, msg, pkg)
        }
    }

    private fun openViaUrl(phone: String, msg: String, pkg: String) {
        val uri = Uri.parse("https://wa.me/$phone?text=${Uri.encode(msg)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(pkg)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
        }
    }
}