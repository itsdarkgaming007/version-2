package com.quicksender.pro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GroupItem(
    val id: String,
    var name: String,
    val members: MutableList<MemberItem> = mutableListOf()
)

data class MemberItem(
    var name: String,
    var phone: String,
    var customField: String = ""
)

data class TemplateItem(
    val id: String,
    var title: String,
    var message: String
)

data class ReportItem(
    val id: String,
    val date: String,
    val groupName: String,
    val totalCount: Int,
    val sentCount: Int,
    val status: String
)

object GroupStore {
    private const val PREFS_NAME = "quick_groups_prefs"
    private const val KEY_GROUPS = "groups_json"
    private const val KEY_TEMPLATES = "templates_json"
    private const val KEY_REPORTS = "reports_json"

    fun loadGroups(context: Context): MutableList<GroupItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_GROUPS, null) ?: return mutableListOf()
        val list = mutableListOf<GroupItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val members = mutableListOf<MemberItem>()
                val memArr = obj.optJSONArray("members") ?: JSONArray()
                for (j in 0 until memArr.length()) {
                    val mObj = memArr.getJSONObject(j)
                    members.add(MemberItem(
                        mObj.getString("name"),
                        mObj.getString("phone"),
                        mObj.optString("customField", "")
                    ))
                }
                list.add(GroupItem(id, name, members))
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveGroups(context: Context, groups: List<GroupItem>) {
        val arr = JSONArray()
        for (g in groups) {
            val obj = JSONObject()
            obj.put("id", g.id)
            obj.put("name", g.name)
            val memArr = JSONArray()
            for (m in g.members) {
                val mObj = JSONObject()
                mObj.put("name", m.name)
                mObj.put("phone", m.phone)
                mObj.put("customField", m.customField)
                memArr.put(mObj)
            }
            obj.put("members", memArr)
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GROUPS, arr.toString())
            .apply()
    }

    fun loadTemplates(context: Context): MutableList<TemplateItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_TEMPLATES, null)
        val list = mutableListOf<TemplateItem>()
        if (jsonStr == null) {
            list.add(TemplateItem("1", "Special Offer", "Hello {name}, we have an exclusive deal for you today! Contact us to claim."))
            list.add(TemplateItem("2", "Follow-up", "Hi {name}, following up regarding our previous discussion. Are you available for a quick call?"))
            saveTemplates(context, list)
            return list
        }
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(TemplateItem(obj.getString("id"), obj.getString("title"), obj.getString("message")))
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveTemplates(context: Context, templates: List<TemplateItem>) {
        val arr = JSONArray()
        for (t in templates) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("title", t.title)
            obj.put("message", t.message)
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TEMPLATES, arr.toString())
            .apply()
    }

    fun loadReports(context: Context): MutableList<ReportItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_REPORTS, null) ?: return mutableListOf()
        val list = mutableListOf<ReportItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(ReportItem(
                    obj.getString("id"),
                    obj.getString("date"),
                    obj.getString("groupName"),
                    obj.getInt("totalCount"),
                    obj.getInt("sentCount"),
                    obj.getString("status")
                ))
            }
        } catch (_: Exception) {}
        return list
    }

    fun addReport(context: Context, groupName: String, total: Int, sent: Int, status: String) {
        val list = loadReports(context)
        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        val item = ReportItem(System.currentTimeMillis().toString(), dateStr, groupName, total, sent, status)
        list.add(0, item)
        val arr = JSONArray()
        for (r in list.take(100)) {
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("date", r.date)
            obj.put("groupName", r.groupName)
            obj.put("totalCount", r.totalCount)
            obj.put("sentCount", r.sentCount)
            obj.put("status", r.status)
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REPORTS, arr.toString())
            .apply()
    }
}