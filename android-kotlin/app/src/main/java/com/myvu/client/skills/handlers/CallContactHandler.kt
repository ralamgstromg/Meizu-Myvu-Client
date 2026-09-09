package com.myvu.client.skills.handlers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import com.myvu.client.core.ContactHelper
import com.myvu.client.core.LogBus
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject

/**
 * Hands-Free Call Contact Handler:
 * Resolves contact names with fuzzy matching, attempts 100% hands-free direct calling
 * via TelecomManager / ACTION_CALL, with fallback to ACTION_DIAL.
 */
class CallContactHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val target = args.optString("contact_or_number", "").trim()
        if (target.isEmpty()) {
            return SkillResult(false, "Falta especificar el contacto o número de teléfono a llamar.")
        }

        val resolved = ContactHelper.resolveContactPhone(context, target)
        val phoneNumber = resolved?.first ?: target
        val displayName = resolved?.second ?: target

        val cleanPhone = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (cleanPhone.isEmpty()) {
            return SkillResult(false, "No se encontró un número telefónico válido para '$target'.")
        }

        val hasCallPerm = context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

        if (hasCallPerm) {
            // 1. Try TelecomManager.placeCall (hands-free directly to Bluetooth/speaker)
            try {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                if (tm != null) {
                    val extras = Bundle().apply {
                        putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
                    }
                    @SuppressLint("MissingPermission")
                    tm.placeCall(Uri.parse("tel:${Uri.encode(cleanPhone)}"), extras)
                    LogBus.log("CallContactHandler: TelecomManager placed direct call to '$displayName' ($cleanPhone)")
                    return SkillResult(true, "📞 **Llamando manos libres** a **$displayName** ($cleanPhone).")
                }
            } catch (e: Exception) {
                LogBus.warn("CallContactHandler: TelecomManager placeCall failed: ${e.message}, falling back to ACTION_CALL")
            }

            // 2. Direct ACTION_CALL
            try {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:${Uri.encode(cleanPhone)}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(callIntent)
                LogBus.log("CallContactHandler: ACTION_CALL launched to '$displayName' ($cleanPhone)")
                return SkillResult(true, "📞 **Iniciando llamada directa** a **$displayName** ($cleanPhone).")
            } catch (e: Exception) {
                LogBus.warn("CallContactHandler: ACTION_CALL failed: ${e.message}, falling back to ACTION_DIAL")
            }
        }

        // 3. Fallback to ACTION_DIAL
        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${Uri.encode(cleanPhone)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(dialIntent)
            SkillResult(true, "📱 Abriendo marcador telefónico para llamar a **$displayName** ($cleanPhone).")
        } catch (e: Exception) {
            LogBus.error("CallContactHandler: Error launching dialer", e)
            SkillResult(false, "No se pudo iniciar la llamada a '$displayName': ${e.message}")
        }
    }
}
