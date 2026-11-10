package com.quicksender.pro

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.random.Random

class SendAutomationService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return

        if (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") {
            val send = findSendButton(root, pkg)
            if (send != null) {
                clickNode(send)
                handler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }, 1000)
                return
            }

            val ok = findOkButton(root)
            if (ok != null) {
                clickNode(ok)
                handler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }, 800)
            }
        }
    }

    private fun findSendButton(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        val ids = listOf("$pkg:id/send", "com.whatsapp:id/send", "com.whatsapp.w4b:id/send")
        for (id in ids) {
            val list = root.findAccessibilityNodeInfosByViewId(id)
            if (!list.isNullOrEmpty()) return list[0]
        }
        val byText = root.findAccessibilityNodeInfosByText("Send") ?: return null
        return byText.firstOrNull {
            val t = it.text?.toString()?.trim()
            val d = it.contentDescription?.toString()?.trim()
            !it.isEditable && (t.equals("Send", true) || d.equals("Send", true))
        }
    }

    private fun findOkButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val byText = root.findAccessibilityNodeInfosByText("OK")
        val match = byText?.firstOrNull { it.text?.toString()?.trim().equals("OK", true) }
        if (match != null) return match
        return root.findAccessibilityNodeInfosByViewId("android:id/button1")?.firstOrNull()
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 8) {
            if (n.isClickable && n.isEnabled) {
                return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            n = n.parent
            depth++
        }
        return false
    }

    override fun onInterrupt() {}
}

object Spintax {
    private val pattern = Regex("""\{([^\{\}|]*(?:\|[^\{\}|]*)+)\}""")

    fun resolve(input: String, random: Random = Random.Default): String {
        var s = input
        var guard = 0
        while (guard++ < 10_000) {
            val m = pattern.find(s) ?: break
            val options = m.groupValues[1].split('|')
            s = s.replaceRange(m.range, options[random.nextInt(options.size)])
        }
        return s
    }
}
