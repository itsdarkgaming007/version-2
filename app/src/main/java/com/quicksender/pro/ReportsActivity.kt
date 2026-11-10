package com.quicksender.pro

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quicksender.pro.databinding.ActivityReportsBinding

class ReportsActivity : AppCompatActivity() {

    private lateinit var b: ActivityReportsBinding

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_lang", "en") ?: "en"
        super.attachBaseContext(QuickSenderApp.updateLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbarReports.setNavigationOnClickListener { finish() }

        b.rvReports.layoutManager = LinearLayoutManager(this)
        val list = GroupStore.loadReports(this)
        b.rvReports.adapter = ReportAdapter(list)
    }

    class ReportAdapter(private val list: List<ReportItem>) : RecyclerView.Adapter<ReportAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val group: TextView = v.findViewById(R.id.tvReportGroup)
            val date: TextView = v.findViewById(R.id.tvReportDate)
            val stats: TextView = v.findViewById(R.id.tvReportStats)
            val status: TextView = v.findViewById(R.id.tvReportStatus)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_report, parent, false)
            return VH(v)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.group.text = item.groupName
            holder.date.text = item.date
            holder.stats.text = "Dispatched: ${item.sentCount} of ${item.totalCount} contacts"
            holder.status.text = item.status
        }
        override fun getItemCount(): Int = list.size
    }
}