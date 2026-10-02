package com.myvu.client.core.backup

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes SharedPreferences to JSON keeping each value's type, so a Long that
 * happens to fit in an Int is restored as a Long (avoids ClassCastException on read).
 * Credentials are never exported: backups are copied to public Downloads and Drive.
 */
internal object PreferencesCodec {

    private val SECRET_KEY = Regex("token|secret|api_key|apikey|password|credential", RegexOption.IGNORE_CASE)

    fun isSecret(key: String): Boolean = SECRET_KEY.containsMatchIn(key)

    fun export(values: Map<String, *>): JSONObject {
        val json = JSONObject()
        for ((key, value) in values) {
            if (isSecret(key)) continue
            val typed = when (value) {
                is Boolean -> typed("bool", value)
                is Int -> typed("int", value)
                is Long -> typed("long", value)
                is Float -> typed("float", value.toDouble())
                is String -> typed("string", value)
                is Set<*> -> typed("set", JSONArray(value.filterIsInstance<String>()))
                else -> null
            } ?: continue
            json.put(key, typed)
        }
        return json
    }

    /**
     * Writes [json] into [editor]. Accepts the typed v2 format and the untyped v1
     * format (plain JSON values). Secret keys are skipped so a restore never
     * overwrites credentials the user has on this device. Returns keys written.
     */
    fun import(json: JSONObject, editor: SharedPreferences.Editor): Int {
        var written = 0
        for (key in json.keys()) {
            if (isSecret(key)) continue
            val raw = json.get(key)
            val ok = if (raw is JSONObject && raw.has("t") && raw.has("v")) {
                putTyped(editor, key, raw.getString("t"), raw.get("v"))
            } else {
                putLegacy(editor, key, raw)
            }
            if (ok) written++
        }
        return written
    }

    /** Replaces every preference with [snapshot] (secrets included). Used to roll back a failed restore. */
    fun restoreSnapshot(snapshot: Map<String, *>, editor: SharedPreferences.Editor) {
        editor.clear()
        for ((key, value) in snapshot) {
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
    }

    private fun typed(type: String, value: Any): JSONObject = JSONObject().put("t", type).put("v", value)

    private fun putTyped(editor: SharedPreferences.Editor, key: String, type: String, v: Any): Boolean {
        when (type) {
            "bool" -> editor.putBoolean(key, v as Boolean)
            "int" -> editor.putInt(key, (v as Number).toInt())
            "long" -> editor.putLong(key, (v as Number).toLong())
            "float" -> editor.putFloat(key, (v as Number).toFloat())
            "string" -> editor.putString(key, v as String)
            "set" -> editor.putStringSet(key, (v as JSONArray).toStringSet())
            else -> return false
        }
        return true
    }

    private fun putLegacy(editor: SharedPreferences.Editor, key: String, v: Any): Boolean {
        when (v) {
            is Boolean -> editor.putBoolean(key, v)
            is Int -> editor.putInt(key, v)
            is Long -> editor.putLong(key, v)
            is Double -> editor.putFloat(key, v.toFloat())
            is String -> editor.putString(key, v)
            is JSONArray -> editor.putStringSet(key, v.toStringSet())
            else -> return false
        }
        return true
    }

    private fun JSONArray.toStringSet(): Set<String> = (0 until length()).map { getString(it) }.toSet()
}
