package com.nezammonitor.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SinaEngineer(
    val name: String = "",
    val family: String = "",
    val fullName: String = "",
    val phone: String = "",
    val group: String = "",
    val design: String = "",
    val supervision: String = "",
    val execution: String = ""
)

object SinaEngineerStore {
    private var map: Map<String, SinaEngineer>? = null
    private var list: List<SinaEngineer>? = null

    private fun normalize(s: String): String =
        s.trim().replace(Regex("\\s+"), " ")

    fun getAll(context: Context): List<SinaEngineer> {
        if (list != null) return list!!
        return load(context)
    }

    fun findByName(context: Context, name: String): SinaEngineer? {
        if (name.isBlank()) return null
        val m = getMap(context)
        val key = normalize(name)
        // exact
        m[key]?.let { return it }
        // try removing zero-width chars
        val cleaned = key.replace("\u200c", "").replace("\u200f", "")
        if (cleaned != key) m[cleaned]?.let { return it }
        // contains fallback: find first where fullName contains name or vice versa
        // linear scan (5753 is small)
        for ((k, v) in m) {
            if (k.contains(key) || key.contains(k)) return v
        }
        return null
    }

    fun getMap(context: Context): Map<String, SinaEngineer> {
        map?.let { return it }
        load(context)
        return map!!
    }

    private fun load(context: Context): List<SinaEngineer> {
        try {
            val json = context.assets.open("sina_engineers.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<SinaEngineer>>() {}.type
            val parsed: List<SinaEngineer> = Gson().fromJson(json, type) ?: emptyList()
            list = parsed
            val m = mutableMapOf<String, SinaEngineer>()
            for (e in parsed) {
                val k = normalize(e.fullName)
                if (k.isNotBlank() && !m.containsKey(k)) m[k] = e
            }
            map = m
            return parsed
        } catch (e: Exception) {
            list = emptyList()
            map = emptyMap()
            return emptyList()
        }
    }

    fun dial(context: Context, phone: String) {
        val cleaned = phone.replace(Regex("[^0-9+]"), "")
        if (cleaned.isBlank()) return
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleaned"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(intent) } catch (_: Exception) {}
    }
}

fun formatSinaLevels(e: SinaEngineer): String {
    val parts = mutableListOf<String>()
    if (e.group.isNotBlank()) parts.add("گروه: ${e.group}")
    if (e.design.isNotBlank()) parts.add("طراحی: ${e.design}")
    if (e.supervision.isNotBlank()) parts.add("نظارت: ${e.supervision}")
    if (e.execution.isNotBlank()) parts.add("اجرا: ${e.execution}")
    return parts.joinToString(" | ")
}
