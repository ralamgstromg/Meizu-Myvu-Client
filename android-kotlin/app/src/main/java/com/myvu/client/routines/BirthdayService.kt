package com.myvu.client.routines

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.myvu.client.core.LogBus
import java.util.Calendar

/** Contact birthdays from the address book (ContactsContract birthday events). */
object BirthdayService {

    data class Birthday(val name: String, val month: Int, val day: Int, val year: Int?)

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun all(context: Context): List<Birthday> {
        if (!hasPermission(context)) return emptyList()
        val result = mutableListOf<Birthday>()
        try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.CommonDataKinds.Event.START_DATE),
                "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
                arrayOf(
                    ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()
                ),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    parseDate(c.getString(1))?.let { (y, m, d) -> result.add(Birthday(name, m, d, y)) }
                }
            }
        } catch (e: Exception) {
            LogBus.warn("BirthdayService -> could not read contacts: ${e.message}")
        }
        return result.distinctBy { Triple(it.name, it.month, it.day) }
    }

    /** Birthdays falling within [days] days from [today] (0 = today only). */
    fun upcoming(all: List<Birthday>, today: Calendar, days: Int): List<Pair<Birthday, Int>> {
        val out = mutableListOf<Pair<Birthday, Int>>()
        for (offset in 0..days) {
            val cal = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, offset) }
            val m = cal.get(Calendar.MONTH) + 1
            val d = cal.get(Calendar.DAY_OF_MONTH)
            all.filter { it.month == m && it.day == d }.forEach { out.add(it to offset) }
        }
        return out
    }

    /** Spoken summary, e.g. "Hoy cumple años Ana (30 años). Mañana: Luis." */
    fun summary(context: Context, today: Calendar = Calendar.getInstance(), days: Int = 1): String {
        if (!hasPermission(context)) return "Para avisarte de cumpleaños, permite el acceso a contactos."
        val list = upcoming(all(context), today, days)
        if (list.isEmpty()) return "No hay cumpleaños hoy ni mañana."
        val year = today.get(Calendar.YEAR)
        return list.groupBy { it.second }.entries.joinToString(" ") { (offset, items) ->
            val whenLabel = when (offset) {
                0 -> "Hoy cumple años"
                1 -> "Mañana cumple años"
                else -> "En $offset días cumple años"
            }
            val names = items.joinToString(", ") { (b, _) ->
                // Age being turned; wrong only for a birthday on Jan 1-2 seen from Dec 31.
                val age = b.year?.let { year - it }
                if (age != null && age in 1..120) "${b.name} ($age años)" else b.name
            }
            "$whenLabel $names."
        }
    }

    /** Parses "YYYY-MM-DD" and "--MM-DD" (no year). Returns (year?, month, day). */
    internal fun parseDate(raw: String?): Triple<Int?, Int, Int>? {
        if (raw.isNullOrBlank()) return null
        val noYear = Regex("^--(\\d{2})-?(\\d{2})").find(raw)
        if (noYear != null) return Triple(null, noYear.groupValues[1].toInt(), noYear.groupValues[2].toInt())
        val full = Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(raw) ?: return null
        return Triple(full.groupValues[1].toInt(), full.groupValues[2].toInt(), full.groupValues[3].toInt())
    }
}
