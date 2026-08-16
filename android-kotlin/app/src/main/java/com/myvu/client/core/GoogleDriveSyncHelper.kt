package com.myvu.client.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.preference.PreferenceManager
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Robust Google Drive Cloud Synchronization for MYVU Client:
 * Supports:
 * 1. Web OAuth 2.0 (In-App WebView / Custom Tab with offline refresh_token)
 * 2. Play Services Google Sign-In
 * 3. Custom OAuth Client ID & Token input
 * 4. Automatic folder structure creation (/myvu/backup/data.zip)
 */
object GoogleDriveSyncHelper {

    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val USER_INFO_SCOPE = "https://www.googleapis.com/auth/userinfo.email"
    private const val OAUTH_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token"
    private const val DRIVE_FILES_API = "https://www.googleapis.com/drive/v3/files"
    private const val DRIVE_UPLOAD_API = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"

    // Default client ID (empty until configured by user in Settings)
    private const val DEFAULT_CLIENT_ID = ""
    private const val DEFAULT_CLIENT_SECRET = ""

    private const val PREF_GDRIVE_ACCESS_TOKEN = "gdrive_access_token"
    private const val PREF_GDRIVE_REFRESH_TOKEN = "gdrive_refresh_token"
    private const val PREF_GDRIVE_EXPIRES_AT = "gdrive_expires_at"
    private const val PREF_GDRIVE_USER_EMAIL = "gdrive_user_email"
    private const val PREF_GDRIVE_CLIENT_ID = "gdrive_client_id"
    private const val PREF_GDRIVE_CLIENT_SECRET = "gdrive_client_secret"

    fun hasValidClientId(context: Context): Boolean {
        val cid = getClientId(context)
        return cid.isNotBlank() && !cid.startsWith("88998899") && cid.contains(".apps.googleusercontent.com")
    }

    data class DriveBackupInfo(
        val exists: Boolean,
        val fileId: String? = null,
        val modifiedTime: String? = null,
        val sizeBytes: Long = 0L
    )

    fun getAppSha1Fingerprint(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            val cert = signatures?.firstOrNull()?.toByteArray() ?: return "No disponible"
            val md = MessageDigest.getInstance("SHA-1")
            val digest = md.digest(cert)
            digest.joinToString(":") { String.format("%02X", it) }
        } catch (e: Exception) {
            "Error SHA-1: ${e.message}"
        }
    }

    fun getClientId(context: Context): String {
        @Suppress("DEPRECATION")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(PREF_GDRIVE_CLIENT_ID, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_CLIENT_ID
    }

    fun getClientSecret(context: Context): String {
        @Suppress("DEPRECATION")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(PREF_GDRIVE_CLIENT_SECRET, "") ?: DEFAULT_CLIENT_SECRET
    }

    fun saveClientCredentials(context: Context, clientId: String, clientSecret: String) {
        @Suppress("DEPRECATION")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putString(PREF_GDRIVE_CLIENT_ID, clientId.trim())
            .putString(PREF_GDRIVE_CLIENT_SECRET, clientSecret.trim())
            .apply()
    }

    fun buildAuthUrl(clientId: String, redirectUri: String): String {
        val encodedScope = URLEncoder.encode("$DRIVE_SCOPE $USER_INFO_SCOPE", "UTF-8")
        val encodedRedirect = URLEncoder.encode(redirectUri, "UTF-8")
        val encodedClientId = URLEncoder.encode(clientId, "UTF-8")
        return "$OAUTH_AUTH_URL?client_id=$encodedClientId&redirect_uri=$encodedRedirect&response_type=code&scope=$encodedScope&access_type=offline&prompt=consent"
    }

    suspend fun exchangeAuthCodeForTokens(
        context: Context,
        authCode: String,
        clientId: String,
        clientSecret: String,
        redirectUri: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(OAUTH_TOKEN_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                doOutput = true
            }

            val params = "code=" + URLEncoder.encode(authCode, "UTF-8") +
                    "&client_id=" + URLEncoder.encode(clientId, "UTF-8") +
                    (if (clientSecret.isNotBlank()) "&client_secret=" + URLEncoder.encode(clientSecret, "UTF-8") else "") +
                    "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8") +
                    "&grant_type=authorization_code"

            conn.outputStream.use { it.write(params.toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode in 200..201) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val accessToken = json.optString("access_token")
                val refreshToken = json.optString("refresh_token")
                val expiresIn = json.optLong("expires_in", 3600L)
                val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

                // Retrieve user email
                val userEmail = fetchUserEmail(accessToken) ?: "Google User"

                @Suppress("DEPRECATION")
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                prefs.edit()
                    .putString(PREF_GDRIVE_ACCESS_TOKEN, accessToken)
                    .apply {
                        if (refreshToken.isNotBlank()) putString(PREF_GDRIVE_REFRESH_TOKEN, refreshToken)
                    }
                    .putLong(PREF_GDRIVE_EXPIRES_AT, expiresAt)
                    .putString(PREF_GDRIVE_USER_EMAIL, userEmail)
                    .putString(PREF_GDRIVE_CLIENT_ID, clientId)
                    .putString(PREF_GDRIVE_CLIENT_SECRET, clientSecret)
                    .apply()

                LogBus.log("GoogleDriveSync -> OAuth tokens exchanged successfully for $userEmail")
                return@withContext true
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                LogBus.error("GoogleDriveSync -> Token exchange failed (HTTP ${conn.responseCode}): $err", null)
            }
        } catch (e: Exception) {
            LogBus.error("GoogleDriveSync -> Token exchange error", e)
        }
        false
    }

    suspend fun refreshAccessToken(context: Context): String? = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val refreshToken = prefs.getString(PREF_GDRIVE_REFRESH_TOKEN, null) ?: return@withContext null
        val clientId = getClientId(context)
        val clientSecret = getClientSecret(context)

        try {
            val url = URL(OAUTH_TOKEN_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                doOutput = true
            }

            val params = "refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8") +
                    "&client_id=" + URLEncoder.encode(clientId, "UTF-8") +
                    (if (clientSecret.isNotBlank()) "&client_secret=" + URLEncoder.encode(clientSecret, "UTF-8") else "") +
                    "&grant_type=refresh_token"

            conn.outputStream.use { it.write(params.toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val newAccessToken = json.optString("access_token")
                val expiresIn = json.optLong("expires_in", 3600L)
                val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

                prefs.edit()
                    .putString(PREF_GDRIVE_ACCESS_TOKEN, newAccessToken)
                    .putLong(PREF_GDRIVE_EXPIRES_AT, expiresAt)
                    .apply()

                LogBus.log("GoogleDriveSync -> Access token refreshed successfully")
                return@withContext newAccessToken
            }
        } catch (e: Exception) {
            LogBus.error("GoogleDriveSync -> Refresh token error", e)
        }
        null
    }

    fun saveCustomToken(context: Context, token: String, email: String) {
        @Suppress("DEPRECATION")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putString(PREF_GDRIVE_ACCESS_TOKEN, token.trim())
            .putLong(PREF_GDRIVE_EXPIRES_AT, System.currentTimeMillis() + (30L * 24 * 3600 * 1000))
            .putString(PREF_GDRIVE_USER_EMAIL, email.trim())
            .apply()
    }

    fun getSignedInEmail(context: Context): String? {
        @Suppress("DEPRECATION")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val email = prefs.getString(PREF_GDRIVE_USER_EMAIL, null)
        if (!email.isNullOrBlank()) return email

        val gAccount = GoogleSignIn.getLastSignedInAccount(context)
        return gAccount?.email
    }

    fun isConnected(context: Context): Boolean {
        @Suppress("DEPRECATION")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val token = prefs.getString(PREF_GDRIVE_ACCESS_TOKEN, null)
        if (!token.isNullOrBlank()) return true
        return GoogleSignIn.getLastSignedInAccount(context) != null
    }

    fun disconnect(context: Context) {
        @Suppress("DEPRECATION")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .remove(PREF_GDRIVE_ACCESS_TOKEN)
            .remove(PREF_GDRIVE_REFRESH_TOKEN)
            .remove(PREF_GDRIVE_EXPIRES_AT)
            .remove(PREF_GDRIVE_USER_EMAIL)
            .apply()

        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            GoogleSignIn.getClient(context, gso).signOut()
        } catch (ignored: Exception) {}
    }

    suspend fun getValidAccessToken(context: Context): String? = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val token = prefs.getString(PREF_GDRIVE_ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(PREF_GDRIVE_EXPIRES_AT, 0L)

        if (!token.isNullOrBlank()) {
            if (System.currentTimeMillis() < (expiresAt - 60000L)) {
                return@withContext token
            }
            // Refresh if expired
            val refreshed = refreshAccessToken(context)
            if (refreshed != null) return@withContext refreshed
            // Return existing token as last resort
            return@withContext token
        }

        // Fallback to Google Play Services Account
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null && account.account != null) {
            try {
                val scope = "oauth2:$DRIVE_SCOPE"
                return@withContext GoogleAuthUtil.getToken(context.applicationContext, account.account!!, scope)
            } catch (e: Exception) {
                LogBus.error("GoogleDriveSync -> Play Services getToken error", e)
            }
        }
        null
    }

    private fun fetchUserEmail(accessToken: String): String? {
        return try {
            val url = URL("https://www.googleapis.com/oauth2/v3/userinfo")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(resp).optString("email")
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun getGoogleSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_SCOPE))
            .build()
    }

    // ==================== CLOUD OPERATIONS ====================

    suspend fun uploadBackupToDrive(
        context: Context,
        backupFile: File,
        onProgress: (String) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        onProgress("Obteniendo credenciales de Google Drive...")
        val token = getValidAccessToken(context)
        if (token.isNullOrBlank()) {
            onProgress("No hay autorización válida de Google Drive.")
            return@withContext false
        }

        try {
            onProgress("Verificando carpetas /myvu/backup/ en Google Drive...")
            val myvuFolderId = getOrCreateFolder(token, "myvu", null)
            val backupFolderId = getOrCreateFolder(token, "backup", myvuFolderId)

            onProgress("Subiendo archivo data.zip a /myvu/backup/...")
            val existingFileId = findFileIdInFolder(token, "data.zip", backupFolderId)

            val success = if (existingFileId != null) {
                updateFileContent(token, existingFileId, backupFile)
            } else {
                createNewFile(token, backupFolderId, "data.zip", backupFile)
            }

            if (success) {
                onProgress("¡Copia de seguridad subida exitosamente a /myvu/backup/data.zip!")
                LogBus.log("GoogleDriveSync -> Successfully uploaded backup to /myvu/backup/data.zip")
            } else {
                onProgress("Error al subir archivo a Google Drive.")
            }
            success
        } catch (e: Exception) {
            LogBus.error("GoogleDriveSync -> Upload error", e)
            onProgress("Error en subida: ${e.message}")
            false
        }
    }

    suspend fun checkCloudBackupInfo(context: Context): DriveBackupInfo = withContext(Dispatchers.IO) {
        val token = getValidAccessToken(context) ?: return@withContext DriveBackupInfo(exists = false)

        try {
            val myvuFolderId = findFolderId(token, "myvu", null) ?: return@withContext DriveBackupInfo(exists = false)
            val backupFolderId = findFolderId(token, "backup", myvuFolderId) ?: return@withContext DriveBackupInfo(exists = false)

            val query = URLEncoder.encode("'$backupFolderId' in parents and name = 'data.zip' and trashed = false", "UTF-8")
            val url = URL("$DRIVE_FILES_API?q=$query&fields=files(id,name,modifiedTime,size)")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
            }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val files = JSONObject(response).optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val fileObj = files.getJSONObject(0)
                    return@withContext DriveBackupInfo(
                        exists = true,
                        fileId = fileObj.optString("id"),
                        modifiedTime = fileObj.optString("modifiedTime"),
                        sizeBytes = fileObj.optLong("size", 0L)
                    )
                }
            }
        } catch (e: Exception) {
            LogBus.error("GoogleDriveSync -> Check backup error", e)
        }
        DriveBackupInfo(exists = false)
    }

    suspend fun downloadBackupFromDrive(
        context: Context,
        onProgress: (String) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        onProgress("Obteniendo credenciales de Google Drive...")
        val token = getValidAccessToken(context)
        if (token.isNullOrBlank()) {
            onProgress("No hay autorización válida de Google Drive.")
            return@withContext null
        }

        try {
            onProgress("Buscando /myvu/backup/data.zip en Google Drive...")
            val myvuFolderId = findFolderId(token, "myvu", null) ?: run {
                onProgress("No se encontró la carpeta /myvu en Drive.")
                return@withContext null
            }
            val backupFolderId = findFolderId(token, "backup", myvuFolderId) ?: run {
                onProgress("No se encontró la carpeta /myvu/backup en Drive.")
                return@withContext null
            }
            val fileId = findFileIdInFolder(token, "data.zip", backupFolderId) ?: run {
                onProgress("No se encontró data.zip en Drive.")
                return@withContext null
            }

            onProgress("Descargando data.zip desde la nube...")
            val downloadUrl = URL("$DRIVE_FILES_API/$fileId?alt=media")
            val conn = (downloadUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
            }

            if (conn.responseCode == 200) {
                val tempFile = File(context.cacheDir, "drive_download_data_${System.currentTimeMillis()}.zip")
                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                onProgress("Descarga completada con éxito.")
                return@withContext tempFile
            } else {
                onProgress("Error al descargar desde Drive (HTTP ${conn.responseCode})")
            }
        } catch (e: Exception) {
            LogBus.error("GoogleDriveSync -> Download error", e)
            onProgress("Error al descargar: ${e.message}")
        }
        null
    }

    private fun findFolderId(token: String, folderName: String, parentId: String?): String? {
        val parentClause = if (parentId != null) "'$parentId' in parents and " else ""
        val q = URLEncoder.encode("${parentClause}mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and trashed = false", "UTF-8")
        val url = URL("$DRIVE_FILES_API?q=$q&fields=files(id,name)")

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
        }

        if (conn.responseCode == 200) {
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val files = JSONObject(response).optJSONArray("files")
            if (files != null && files.length() > 0) {
                return files.getJSONObject(0).optString("id")
            }
        }
        return null
    }

    private fun getOrCreateFolder(token: String, folderName: String, parentId: String?): String {
        val existing = findFolderId(token, folderName, parentId)
        if (existing != null) return existing

        val url = URL(DRIVE_FILES_API)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            doOutput = true
        }

        val json = JSONObject().apply {
            put("name", folderName)
            put("mimeType", "application/vnd.google-apps.folder")
            if (parentId != null) {
                put("parents", org.json.JSONArray().put(parentId))
            }
        }

        conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }

        if (conn.responseCode in 200..201) {
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(response).optString("id")
        }
        throw IllegalStateException("No se pudo crear carpeta $folderName en Google Drive (HTTP ${conn.responseCode})")
    }

    private fun findFileIdInFolder(token: String, fileName: String, parentFolderId: String): String? {
        val q = URLEncoder.encode("'$parentFolderId' in parents and name = '$fileName' and trashed = false", "UTF-8")
        val url = URL("$DRIVE_FILES_API?q=$q&fields=files(id,name)")

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
        }

        if (conn.responseCode == 200) {
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val files = JSONObject(response).optJSONArray("files")
            if (files != null && files.length() > 0) {
                return files.getJSONObject(0).optString("id")
            }
        }
        return null
    }

    private fun createNewFile(
        token: String,
        parentFolderId: String,
        fileName: String,
        file: File
    ): Boolean {
        val boundary = "==MYVU_DRIVE_BOUNDARY_${System.currentTimeMillis()}=="
        val url = URL(DRIVE_UPLOAD_API)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            doOutput = true
        }

        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("parents", org.json.JSONArray().put(parentFolderId))
        }

        conn.outputStream.use { output ->
            writeMultipartBody(output, boundary, metadataJson, file)
        }

        return conn.responseCode in 200..201
    }

    private fun updateFileContent(
        token: String,
        fileId: String,
        file: File
    ): Boolean {
        val boundary = "==MYVU_DRIVE_BOUNDARY_${System.currentTimeMillis()}=="
        val url = URL("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=multipart")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            doOutput = true
        }

        val metadataJson = JSONObject().apply {
            put("name", file.name)
        }

        conn.outputStream.use { output ->
            writeMultipartBody(output, boundary, metadataJson, file)
        }

        return conn.responseCode in 200..201
    }

    private fun writeMultipartBody(
        output: OutputStream,
        boundary: String,
        metadataJson: JSONObject,
        file: File
    ) {
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        // 1. Metadata part
        output.write("$twoHyphens$boundary$lineEnd".toByteArray())
        output.write("Content-Type: application/json; charset=UTF-8$lineEnd$lineEnd".toByteArray())
        output.write(metadataJson.toString().toByteArray(Charsets.UTF_8))
        output.write(lineEnd.toByteArray())

        // 2. Media content part
        output.write("$twoHyphens$boundary$lineEnd".toByteArray())
        output.write("Content-Type: application/zip$lineEnd".toByteArray())
        output.write("Content-Transfer-Encoding: binary$lineEnd$lineEnd".toByteArray())

        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
        }
        output.write(lineEnd.toByteArray())
        output.write("$twoHyphens$boundary$twoHyphens$lineEnd".toByteArray())
        output.flush()
    }
}
