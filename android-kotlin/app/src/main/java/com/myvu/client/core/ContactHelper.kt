package com.myvu.client.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import java.text.Normalizer

object ContactHelper {

    fun normalize(text: String): String {
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase().trim()
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    data class ContactMatch(
        val number: String,
        val displayName: String,
        val score: Int,
        val isExact: Boolean
    )

    data class ParsedMessageAction(
        val recipientQuery: String,
        val resolvedPhone: String?,
        val resolvedName: String?,
        val message: String
    )

    fun cleanText(text: String): String {
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
        val withoutDiacritics = nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        // Reemplazar signos de puntuación, emojis, símbolos por espacios
        return withoutDiacritics.replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun extractTokens(text: String): List<String> {
        return cleanText(text).split(Regex("\\s+")).filter { it.length >= 2 }
    }

    fun calculateScore(searchQuery: String, contactName: String): Int {
        val cleanSearch = cleanText(searchQuery)
        val cleanContact = cleanText(contactName)
        if (cleanSearch.isEmpty() || cleanContact.isEmpty()) return 0

        // 1. Coincidencia idéntica total
        if (cleanSearch == cleanContact) return 2000

        val sTokens = extractTokens(searchQuery)
        val cTokens = extractTokens(contactName)
        if (sTokens.isEmpty() || cTokens.isEmpty()) return 0

        var score = 0

        // 2. Coincidencia de Prefijo o Subcadena continua
        if (cleanContact.startsWith(cleanSearch)) {
            score += 500
        } else if (cleanContact.contains(cleanSearch)) {
            score += 250
        }

        // 3. Emparejamiento de Tokens y Tasa de Cobertura de la Consulta (Subset Containment)
        var matchedSTokens = 0
        val matchedContactIndices = mutableListOf<Int>()

        for (sToken in sTokens) {
            var bestTokenScore = 0
            var matchedCIdx = -1

            for ((cIdx, cToken) in cTokens.withIndex()) {
                if (sToken == cToken) {
                    if (bestTokenScore < 60) {
                        bestTokenScore = 60
                        matchedCIdx = cIdx
                    }
                } else if (cToken.startsWith(sToken) || sToken.startsWith(cToken)) {
                    if (bestTokenScore < 35) {
                        bestTokenScore = 35
                        matchedCIdx = cIdx
                    }
                } else {
                    val dist = levenshteinDistance(sToken, cToken)
                    val maxLen = maxOf(sToken.length, cToken.length)
                    if (maxLen >= 4 && dist <= 2) {
                        val levScore = 30 - (dist * 10)
                        if (levScore > bestTokenScore) {
                            bestTokenScore = levScore
                            matchedCIdx = cIdx
                        }
                    }
                }
            }

            if (bestTokenScore > 0) {
                matchedSTokens++
                score += bestTokenScore
                if (matchedCIdx >= 0) {
                    matchedContactIndices.add(matchedCIdx)
                }
            }
        }

        // Bonificación por cobertura de consulta: si TODOS los tokens pedidos están en el contacto
        if (matchedSTokens == sTokens.size) {
            score += 350 // Subset Containment total (ej: "matias castro" dentro de "matias castro hijo")
        } else if (matchedSTokens > 0) {
            score += (matchedSTokens * 200) / sTokens.size
        }

        // Bonificación por preservación de orden secuencial
        var inOrder = true
        for (i in 0 until matchedContactIndices.size - 1) {
            if (matchedContactIndices[i] >= matchedContactIndices[i + 1]) {
                inOrder = false
                break
            }
        }
        if (inOrder && matchedContactIndices.size > 1) {
            score += 80
        }

        // Penalización muy leve por palabras excesivas en el contacto para desempatar al más conciso
        val extraWords = maxOf(0, cTokens.size - sTokens.size)
        score -= minOf(40, extraWords * 5)

        return maxOf(0, score)
    }

    fun cleanPunctuation(text: String): String {
        return text.trim()
            .replace(Regex("[?.,!;:]+$"), "")
            .replace(Regex("^[¿¡]+"), "")
            .trim()
    }

    /**
     * Finds the best matching contact from Android Contacts Provider using advanced fuzzy & subset scoring.
     */
    fun findBestContactMatch(context: Context, target: String): ContactMatch? {
        val cleanTarget = cleanPunctuation(target)
        if (cleanTarget.isEmpty()) return null

        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            LogBus.warn("ContactHelper -> READ_CONTACTS permission not granted")
            return null
        }

        try {
            var bestNumber: String? = null
            var bestName: String? = null
            var bestScore = Int.MIN_VALUE
            var bestIsExact = false

            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)

                if (numIdx >= 0 && nameIdx >= 0) {
                    while (cursor.moveToNext()) {
                        val contactName = cursor.getString(nameIdx) ?: continue
                        val contactNumber = cursor.getString(numIdx) ?: continue

                        val score = calculateScore(cleanTarget, contactName)
                        if (score >= 2000) {
                            LogBus.log("ContactHelper -> Exact match '$contactName' ($contactNumber, score: $score)")
                            return ContactMatch(contactNumber, contactName, score, true)
                        }

                        if (score > bestScore && score >= 30) {
                            bestScore = score
                            bestNumber = contactNumber
                            bestName = contactName
                            bestIsExact = false
                        }
                    }
                }
            }

            if (bestNumber != null && bestName != null) {
                LogBus.log("ContactHelper -> Best fuzzy match '$cleanTarget' -> '$bestName' ($bestNumber, score: $bestScore)")
                return ContactMatch(bestNumber, bestName, bestScore, bestIsExact)
            }
        } catch (e: Exception) {
            LogBus.warn("ContactHelper -> Contacts query failed: ${e.message}")
        }

        return null
    }

    /**
     * Resolves a phone number from either raw digits or contact display name.
     * Returns Pair(phoneNumber, displayName) or null if unresolvable.
     */
    fun resolveContactPhone(context: Context, target: String): Pair<String, String>? {
        val cleanTarget = cleanPunctuation(target)
        if (cleanTarget.isEmpty()) return null

        // 1. If it already looks like a direct phone number
        val digitOnly = cleanTarget.replace(Regex("[^0-9+]"), "")
        if (digitOnly.length >= 7 && (cleanTarget.matches(Regex("^[0-9+#* -]+$")) || cleanTarget.startsWith("+"))) {
            return Pair(cleanTarget, cleanTarget)
        }

        val bestMatch = findBestContactMatch(context, cleanTarget)
        if (bestMatch != null) {
            return Pair(bestMatch.number, bestMatch.displayName)
        }

        return if (digitOnly.isNotEmpty()) Pair(digitOnly, cleanTarget) else null
    }

    /**
     * Resolves email address from email string or contact display name.
     */
    fun resolveContactEmail(context: Context, target: String): Pair<String, String>? {
        val cleanTarget = cleanPunctuation(target)
        if (cleanTarget.isEmpty()) return null

        if (cleanTarget.contains("@") && cleanTarget.contains(".")) {
            return Pair(cleanTarget, cleanTarget)
        }

        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            LogBus.warn("ContactHelper -> READ_CONTACTS permission not granted for email lookup")
            return null
        }

        try {
            val normalizedSearch = normalize(cleanTarget)
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                    ContactsContract.CommonDataKinds.Email.DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val emailIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME)

                if (emailIdx >= 0 && nameIdx >= 0) {
                    var bestEmail: String? = null
                    var bestName: String? = null

                    while (cursor.moveToNext()) {
                        val contactName = cursor.getString(nameIdx) ?: continue
                        val emailAddr = cursor.getString(emailIdx) ?: continue
                        val normalizedContact = normalize(contactName)

                        if (normalizedContact == normalizedSearch) {
                            return Pair(emailAddr, contactName)
                        }
                        if (normalizedContact.contains(normalizedSearch) && bestEmail == null) {
                            bestEmail = emailAddr
                            bestName = contactName
                        }
                    }

                    if (bestEmail != null && bestName != null) {
                        return Pair(bestEmail, bestName)
                    }
                }
            }
        } catch (e: Exception) {
            LogBus.warn("ContactHelper -> Email query failed: ${e.message}")
        }

        return null
    }

    /**
     * Extracts recipient and message body from natural voice queries in Spanish.
     */
    fun extractRecipientAndMessage(context: Context, rawText: String): ParsedMessageAction {
        val cleanRaw = rawText.trim()
            .replace(Regex("(?iu)^[¿¡?\\s]*(qué|que|oye|eh|ah|hola|por\\s+favor)\\s*[,.]?\\s*"), "")
            .replace(Regex("(?iu)^(enviar?|envio|envió|envia|envía|envias|envías|manda|mandar?|mando|mandó|mandale|mandarle|escribe|escribir?|escribirle)\\s+(un\\s+)?(mensaje|whatsapp|telegram|sms|mensaje\\s+de\\s+texto|texto)?(\\s+de\\s+whatsapp|\\s+por\\s+whatsapp|\\s+de\\s+telegram|\\s+por\\s+telegram)?(\\s+a|\\s+al|\\s+para)?\\s*|(?iu)^(a\\s+mi|a|al|para)\\s+"), "")
            .trim()

        if (cleanRaw.isBlank()) {
            return ParsedMessageAction("", null, null, "")
        }

        // 1. Explicit Delimiters: ':' or '|'
        if (cleanRaw.contains(":") || cleanRaw.contains("|")) {
            val parts = cleanRaw.split(Regex("[:|]"), 2)
            val candRecipient = cleanPunctuation(parts[0])
            val candMsg = parts[1].trim()
            val resolved = resolveContactPhone(context, candRecipient)
            return ParsedMessageAction(candRecipient, resolved?.first, resolved?.second, candMsg)
        }

        // 2. Sentence boundary: Period followed by space (Whisper STT output)
        // Example: "Matías Castro. Hola hijo, ¿cómo vas?"
        val periodParts = cleanRaw.split(Regex("(?iu)(?<=[a-z0-9áéíóúñüÁÉÍÓÚÑÜ])\\.\\s+"), 2)
        if (periodParts.size == 2) {
            val candRecipient = cleanPunctuation(periodParts[0])
            val candMsg = periodParts[1].trim()
            val match = findBestContactMatch(context, candRecipient)
            if (match != null && match.score >= 35) {
                return ParsedMessageAction(candRecipient, match.number, match.displayName, candMsg)
            }
        }

        // 3. Grammatical Connectors in Spanish
        // "que diga", "diciendo", "y dile que", "dile que", "con el texto", "con el mensaje"
        val gramMatch = Regex("(?iu)^(.+?)\\s+(que\\s+diga|diciendo|y\\s+dile(\\s+que)?|dile\\s+que|con\\s+el\\s+texto|con\\s+el\\s+mensaje)\\s+(.+)$").find(cleanRaw)
        if (gramMatch != null) {
            val candRecipient = cleanPunctuation(gramMatch.groupValues[1])
            val candMsg = gramMatch.groupValues[4].trim()
            val match = findBestContactMatch(context, candRecipient)
            if (match != null && match.score >= 35) {
                return ParsedMessageAction(candRecipient, match.number, match.displayName, candMsg)
            } else {
                val resolved = resolveContactPhone(context, candRecipient)
                return ParsedMessageAction(candRecipient, resolved?.first, resolved?.second, candMsg)
            }
        }

        // 4. Comma delimiter ONLY IF prefix matches a known contact
        if (cleanRaw.contains(",")) {
            val commaParts = cleanRaw.split(Regex(",\\s*"), 2)
            val candRecipient = cleanPunctuation(commaParts[0])
            val candMsg = commaParts[1].trim()
            val match = findBestContactMatch(context, candRecipient)
            if (match != null && match.score >= 40) {
                return ParsedMessageAction(candRecipient, match.number, match.displayName, candMsg)
            }
        }

        // 5. Sliding window on tokens (1 to 4 words)
        val tokens = cleanRaw.split(Regex("\\s+"))
        var bestMatch: ContactMatch? = null
        var bestTokenCount = 0

        for (i in 1..minOf(4, tokens.size)) {
            val candidate = cleanPunctuation(tokens.take(i).joinToString(" "))
            val match = findBestContactMatch(context, candidate)
            if (match != null && match.score >= 35) {
                if (bestMatch == null || match.score > bestMatch.score || (match.score == bestMatch.score && i > bestTokenCount)) {
                    bestMatch = match
                    bestTokenCount = i
                }
            }
        }

        if (bestMatch != null && bestTokenCount > 0) {
            val candidate = cleanPunctuation(tokens.take(bestTokenCount).joinToString(" "))
            val rawMsg = tokens.drop(bestTokenCount).joinToString(" ")
                .replace(Regex("(?iu)^(que|que diga|diciendo|dile que)\\s+"), "")
                .trim()
            return ParsedMessageAction(candidate, bestMatch.number, bestMatch.displayName, rawMsg)
        }

        // 6. Fallback: If no match found, first word or entire string
        val fallbackRecipient = cleanPunctuation(if (tokens.size >= 2) tokens[0] else cleanRaw)
        val fallbackMsg = if (tokens.size >= 2) {
            tokens.drop(1).joinToString(" ").replace(Regex("(?iu)^(que|que diga|diciendo|dile que)\\s+"), "").trim()
        } else ""
        val resolved = resolveContactPhone(context, fallbackRecipient)
        return ParsedMessageAction(fallbackRecipient, resolved?.first, resolved?.second, fallbackMsg)
    }

    /**
     * Standardizes phone numbers for WhatsApp / Cellular messaging in Colombia and international.
     */
    fun formatColombianPhone(rawNumber: String): String {
        var clean = rawNumber.replace(Regex("[^0-9]"), "")
        // Si tiene 10 dígitos y empieza por 3 o 6 (móvil Colombia), anteponer 57
        if (clean.length == 10 && (clean.startsWith("3") || clean.startsWith("6"))) {
            clean = "57$clean"
        }
        return clean
    }

    /**
     * Resolves the WhatsApp VoIP Call row data ID from ContactsContract.Data if available.
     */
    fun resolveWhatsAppVoipDataId(context: Context, nameOrPhone: String): Long? {
        if (nameOrPhone.isBlank()) return null
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val resolver = context.contentResolver
        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Data._ID,
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.Data.DATA1
        )
        val selection = "${ContactsContract.Data.MIMETYPE} = ?"
        val selectionArgs = arrayOf("vnd.android.cursor.item/vnd.com.whatsapp.voip.call")

        try {
            resolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Data._ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
                val phoneIdx = cursor.getColumnIndex(ContactsContract.Data.DATA1)

                var bestId: Long? = null
                var bestScore = 0

                val cleanTargetPhone = nameOrPhone.replace(Regex("[^0-9]"), "")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx) ?: ""
                    val phone = cursor.getString(phoneIdx) ?: ""

                    if (cleanTargetPhone.isNotEmpty() && phone.replace(Regex("[^0-9]"), "").endsWith(cleanTargetPhone)) {
                        return id
                    }

                    val score = calculateScore(nameOrPhone, name)
                    if (score > bestScore) {
                        bestScore = score
                        bestId = id
                    }
                }
                if (bestScore >= 35) {
                    return bestId
                }
            }
        } catch (e: Exception) {
            LogBus.warn("ContactHelper: resolveWhatsAppVoipDataId error: ${e.message}")
        }
        return null
    }
}
