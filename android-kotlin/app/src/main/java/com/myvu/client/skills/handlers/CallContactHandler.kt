package com.myvu.client.skills.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject

class CallContactHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val target = args.optString("contact_or_number", "").trim()
        if (target.isEmpty()) {
            return SkillResult(false, "Falta especificar el contacto o número de teléfono.")
        }

        var phoneNumber = target
        if (!target.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }) {
            // Target is likely a contact name, perform search in ContactsContract
            val resolvedNumber = resolveContactName(context, target)
            if (resolvedNumber != null) {
                phoneNumber = resolvedNumber
            } else {
                LogBus.warn("CallContactHandler: Contact '$target' not found, attempting dial with raw input")
            }
        }

        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${Uri.encode(phoneNumber)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(dialIntent)
            SkillResult(true, "Iniciando llamada a $target ($phoneNumber)")
        } catch (e: Exception) {
            LogBus.error("CallContactHandler: Error launching dialer", e)
            SkillResult(false, "No se pudo iniciar la llamada: ${e.message}")
        }
    }

    private fun resolveContactName(context: Context, name: String): String? {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numberIndex >= 0) {
                        return cursor.getString(numberIndex)
                    }
                }
            }
        } catch (e: Exception) {
            LogBus.warn("CallContactHandler: Contacts permission or query failed: ${e.message}")
        }
        return null
    }
}
