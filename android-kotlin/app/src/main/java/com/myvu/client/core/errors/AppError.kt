package com.myvu.client.core.errors

import com.myvu.client.core.LogBus
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * One error model for the app: a user-facing message in es-CO plus the technical
 * cause for the log. Build it with [AppError.from] at the boundary where a failure
 * becomes visible to the user, and report it with [report].
 */
sealed class AppError(val userMessage: String, val cause: Throwable?) {

    class Network(cause: Throwable?) :
        AppError("No hay conexión a internet o el servicio no respondió. Inténtalo de nuevo.", cause)

    class Timeout(cause: Throwable?) :
        AppError("El servicio tardó demasiado en responder. Inténtalo de nuevo.", cause)

    class Permission(val permission: String?, cause: Throwable?) :
        AppError("Falta un permiso${permission?.let { " ($it)" } ?: ""}. Revísalo en Ajustes > Permisos.", cause)

    class Config(val field: String, message: String = "Falta configurar $field en Ajustes.") :
        AppError(message, null)

    class Device(message: String, cause: Throwable? = null) : AppError(message, cause)

    class Unknown(cause: Throwable?) :
        AppError("Ocurrió un error inesperado. Quedó registrado en el registro de actividad.", cause)

    /** Logs this error under [where] and returns it, so callers can show [userMessage]. */
    fun report(where: String): AppError {
        val kind = this::class.simpleName
        LogBus.error("$where -> [$kind] ${cause?.javaClass?.simpleName ?: ""}: ${cause?.message ?: userMessage}", cause)
        return this
    }

    companion object {
        fun from(t: Throwable): AppError = when (t) {
            is SocketTimeoutException -> Timeout(t)
            is UnknownHostException, is SSLException -> Network(t)
            is SecurityException -> Permission(null, t)
            is IOException -> Network(t)
            else -> Unknown(t)
        }
    }
}

/**
 * Runs [block] and logs a warning instead of silently swallowing a failure.
 * Use for best-effort work whose failure must stay visible in the activity log
 * (e.g. pushing a setting or a HUD card to the glasses). Returns null on failure.
 */
inline fun <T> attempt(what: String, block: () -> T): T? = try {
    block()
} catch (e: Exception) {
    LogBus.warn("$what failed: ${e.javaClass.simpleName}: ${e.message}")
    null
}

/** Logs this throwable as an [AppError] under [where] and returns the es-CO user message. */
fun Throwable.userMessage(where: String): String = AppError.from(this).report(where).userMessage
