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

    val SPANISH_NICKNAMES: Map<String, List<String>> = mapOf(
        "mati" to listOf("matias", "mateo"),
        "matias" to listOf("mati"),
        "mateo" to listOf("mati"),
        "dani" to listOf("daniel", "daniela"),
        "daniel" to listOf("dani"),
        "daniela" to listOf("dani"),
        "sebas" to listOf("sebastian"),
        "sebastian" to listOf("sebas"),
        "santi" to listOf("santiago"),
        "santiago" to listOf("santi"),
        "nico" to listOf("nicolas"),
        "nicolas" to listOf("nico"),
        "alex" to listOf("alejandro", "alejandra"),
        "ale" to listOf("alejandro", "alejandra"),
        "alejandro" to listOf("alex", "ale"),
        "alejandra" to listOf("alex", "ale"),
        "cami" to listOf("camilo", "camila"),
        "camilo" to listOf("cami"),
        "camila" to listOf("cami"),
        "vale" to listOf("valeria", "valentina"),
        "valeria" to listOf("vale"),
        "valentina" to listOf("vale"),
        "juanca" to listOf("juan carlos"),
        "juan carlos" to listOf("juanca"),
        "juanpa" to listOf("juan pablo"),
        "juan pablo" to listOf("juanpa"),
        "pacho" to listOf("francisco", "pancho"),
        "pancho" to listOf("francisco", "pacho"),
        "francisco" to listOf("pacho", "pancho"),
        "pipe" to listOf("felipe"),
        "felipe" to listOf("pipe"),
        "memo" to listOf("guillermo"),
        "guillermo" to listOf("memo"),
        "nacho" to listOf("ignacio"),
        "ignacio" to listOf("nacho"),
        "beto" to listOf("alberto", "roberto"),
        "alberto" to listOf("beto"),
        "roberto" to listOf("beto"),
        "pepe" to listOf("jose"),
        "chepe" to listOf("jose"),
        "jose" to listOf("pepe", "chepe"),
        "toño" to listOf("antonio"),
        "antonio" to listOf("toño"),
        "lalo" to listOf("eduardo"),
        "eduardo" to listOf("lalo"),
        "chucho" to listOf("jesus"),
        "jesus" to listOf("chucho"),
        "gabi" to listOf("gabriel", "gabriela"),
        "gabriel" to listOf("gabi"),
        "gabriela" to listOf("gabi"),
        "sofi" to listOf("sofia"),
        "sofia" to listOf("sofi"),
        "cata" to listOf("catalina"),
        "catalina" to listOf("cata"),
        "caro" to listOf("carolina", "carito"),
        "carito" to listOf("carolina", "caro"),
        "carolina" to listOf("caro", "carito"),
        "mafe" to listOf("maria fernanda"),
        "fer" to listOf("fernando", "fernanda"),
        "fernando" to listOf("fer"),
        "fernanda" to listOf("fer"),
        "rafa" to listOf("rafael"),
        "rafael" to listOf("rafa"),
        "javi" to listOf("javier"),
        "javier" to listOf("javi"),
        "manu" to listOf("manuel", "manuela"),
        "manuel" to listOf("manu"),
        "manuela" to listOf("manu"),
        "cris" to listOf("cristian", "cristina"),
        "cristian" to listOf("cris"),
        "cristina" to listOf("cris"),
        "lau" to listOf("laura"),
        "laura" to listOf("lau"),
        "andy" to listOf("andres"),
        "andres" to listOf("andy")
    )

    val KINSHIP_ALIASES: Map<String, List<String>> = mapOf(
        "papa" to listOf("padre", "papi", "pa"),
        "padre" to listOf("papa", "papi"),
        "papi" to listOf("papa", "padre"),
        "mama" to listOf("madre", "mami", "ma"),
        "madre" to listOf("mama", "mami"),
        "mami" to listOf("mama", "madre"),
        "hijo" to listOf("hijo", "mi hijo"),
        "hija" to listOf("hija", "mi hija"),
        "esposa" to listOf("esposa", "mujer", "amor", "mi amor"),
        "esposo" to listOf("esposo", "marido", "amor", "mi amor"),
        "abuelo" to listOf("abuelo", "abue"),
        "abuela" to listOf("abuela", "abue"),
        "hermano" to listOf("hermano", "mono"),
        "hermana" to listOf("hermana", "mona"),
        "tio" to listOf("tio"),
        "tia" to listOf("tia"),
        "primo" to listOf("primo"),
        "prima" to listOf("prima")
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

    /**
     * Evaluates similarity between two individual tokens (Exact, Prefix, Semantic Nickname/Kinship, or Levenshtein).
     * Returns score between 0 and 100.
     */
    fun tokenMatchScore(sToken: String, cToken: String): Int {
        if (sToken == cToken) return 100
        if (cToken.startsWith(sToken)) return 85
        if (sToken.startsWith(cToken)) return 75

        // Semantic Nickname lookup
        val nickAliases = SPANISH_NICKNAMES[sToken]
        if (nickAliases != null && nickAliases.contains(cToken)) return 80
        val reverseNick = SPANISH_NICKNAMES[cToken]
        if (reverseNick != null && reverseNick.contains(sToken)) return 80

        // Semantic Kinship lookup
        val kinshipAliases = KINSHIP_ALIASES[sToken]
        if (kinshipAliases != null && kinshipAliases.contains(cToken)) return 80
        val reverseKin = KINSHIP_ALIASES[cToken]
        if (reverseKin != null && reverseKin.contains(sToken)) return 80

        // Fuzzy Levenshtein
        val dist = levenshteinDistance(sToken, cToken)
        val maxLen = maxOf(sToken.length, cToken.length)
        if (maxLen >= 4 && dist <= 1) return 70
        if (maxLen >= 6 && dist <= 2) return 50

        return 0
    }

    /**
     * Multi-tiered contact score:
     * 1. Priority 1 (Sequential Order): The contact matches tokens in the exact order entered, anchored on the primary given name.
     * 2. Safety Protection: If the query has multiple tokens (e.g. "Matias Castro"), candidates whose given name completely mismatches
     *    (e.g. "Denis Castro") are strictly rejected with score 0.
     * 3. Priority 2 (Semantic / Unordered Fallback): Only if no strict sequential match exists, semantic aliases and unordered permutations.
     */
    fun calculateScore(searchQuery: String, contactName: String): Int {
        val cleanSearch = cleanText(searchQuery)
        val cleanContact = cleanText(contactName)
        if (cleanSearch.isEmpty() || cleanContact.isEmpty()) return 0

        // 1. Tier 1: Coincidencia idéntica total
        if (cleanSearch == cleanContact) return 3000

        // 2. Tier 2: Coincidencia de Prefijo Continuo Exacto
        if (cleanContact.startsWith(cleanSearch)) {
            val remainder = cleanContact.removePrefix(cleanSearch).trim()
            val brevityBonus = maxOf(0, 100 - remainder.length * 2)
            return 2000 + brevityBonus
        }

        val sTokens = extractTokens(searchQuery)
        val cTokens = extractTokens(contactName)
        if (sTokens.isEmpty() || cTokens.isEmpty()) return 0

        // 3. Regla Crítica de Seguridad para Consultas Compuestas (>= 2 palabras):
        // En consultas como "Matias Castro", el primer token es el nombre de pila ("Matias").
        // Si NINGÚN token del contacto coincide con el primer token pedido, se rechaza de inmediato (0 puntos).
        // Esto evita catastróficamente emparejar "Denis Castro" cuando el usuario pidió "Matias Castro".
        if (sTokens.size >= 2) {
            val primaryTokenMatches = cTokens.any { tokenMatchScore(sTokens[0], it) > 0 }
            if (!primaryTokenMatches) {
                return 0
            }
        }

        // 4. Búsqueda de Coincidencia Secuencial en Orden Estricto (Greedy Forward Alignment)
        var currentCIdx = 0
        var sequentialMatches = 0
        var sequentialScoreSum = 0
        val matchedCIndices = mutableListOf<Int>()

        for (sToken in sTokens) {
            var bestScore = 0
            var bestIdx = -1
            for (cIdx in currentCIdx until cTokens.size) {
                val tScore = tokenMatchScore(sToken, cTokens[cIdx])
                if (tScore > bestScore) {
                    bestScore = tScore
                    bestIdx = cIdx
                }
            }
            if (bestScore > 0 && bestIdx >= 0) {
                sequentialMatches++
                sequentialScoreSum += bestScore
                matchedCIndices.add(bestIdx)
                currentCIdx = bestIdx + 1
            }
        }

        // Si todos los tokens de la consulta coinciden en orden secuencial estricto:
        if (sequentialMatches == sTokens.size) {
            val firstMatchedIdx = matchedCIndices[0]
            val extraTokensPenalty = maxOf(0, cTokens.size - sTokens.size) * 15

            return if (firstMatchedIdx == 0) {
                // Tier 3A: Orden secuencial anclado en el primer token del contacto (ej: "Matías David Castro" para "Matias Castro")
                maxOf(1200, 1500 + sequentialScoreSum - extraTokensPenalty)
            } else {
                // Tier 3B: Orden secuencial con prefijo de relación/título (ej: "Hijo Matías Castro" o "Dr Matías Castro")
                maxOf(1000, 1200 + sequentialScoreSum - (firstMatchedIdx * 30) - extraTokensPenalty)
            }
        }

        // 5. Caso de Consulta de Un Solo Token (ej: "Castro" o "Matias")
        if (sTokens.size == 1) {
            val singleToken = sTokens[0]
            var maxTokenScore = 0
            var matchedIdx = -1

            for ((cIdx, cToken) in cTokens.withIndex()) {
                val tScore = tokenMatchScore(singleToken, cToken)
                if (tScore > maxTokenScore) {
                    maxTokenScore = tScore
                    matchedIdx = cIdx
                }
            }

            if (maxTokenScore > 0) {
                return if (matchedIdx == 0) {
                    800 + maxTokenScore // Primer nombre coincide
                } else {
                    400 + maxTokenScore // Apellido u otro token coincide (ej: "Castro" -> "Denis Castro")
                }
            }
            return 0
        }

        // 6. Fase 2: Fallback Semántico y Coincidencias Desordenadas (cuando no hubo coincidencia secuencial completa)
        var unorderedMatches = 0
        var unorderedScoreSum = 0
        val usedCIndices = mutableSetOf<Int>()

        for (sToken in sTokens) {
            var bestScore = 0
            var bestIdx = -1
            for ((cIdx, cToken) in cTokens.withIndex()) {
                if (cIdx in usedCIndices) continue
                val tScore = tokenMatchScore(sToken, cToken)
                if (tScore > bestScore) {
                    bestScore = tScore
                    bestIdx = cIdx
                }
            }
            if (bestScore > 0 && bestIdx >= 0) {
                unorderedMatches++
                unorderedScoreSum += bestScore
                usedCIndices.add(bestIdx)
            }
        }

        // Si TODOS los tokens están presentes pero en orden invertido (ej: "Castro Matias" -> "Matias Castro")
        if (unorderedMatches == sTokens.size) {
            val extraTokensPenalty = maxOf(0, cTokens.size - sTokens.size) * 15
            return maxOf(700, 850 + unorderedScoreSum - extraTokensPenalty)
        }

        // Coincidencia parcial donde el primer token sí coincidió (ej: "Matias Castro" -> "Matias" o "Matias Ortiz")
        if (unorderedMatches > 0 && tokenMatchScore(sTokens[0], cTokens[0]) > 0) {
            return 250 + (unorderedScoreSum / sTokens.size)
        }

        return 0
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

                        var score = calculateScore(cleanTarget, contactName)

                        // Prefer Colombian numbers (+57) when the query doesn't specify a country.
                        // This avoids picking international duplicates over local contacts as a mild tie-breaker.
                        val cleanDigits = contactNumber.replace(Regex("[^0-9]"), "")
                        val isColombian = cleanDigits.startsWith("57") && cleanDigits.length == 12 ||
                                contactNumber.trimStart().startsWith("+57")
                        if (isColombian && score >= 150) score += 20

                        if (score >= 3000) {
                            LogBus.log("ContactHelper -> Exact match '$contactName' ($contactNumber, score: $score)")
                            return ContactMatch(contactNumber, contactName, score, true)
                        }

                        if (score > bestScore && score >= 150) {
                            bestScore = score
                            bestNumber = contactNumber
                            bestName = contactName
                            bestIsExact = (score >= 2000)
                        }
                    }
                }
            }

            if (bestNumber != null && bestName != null) {
                LogBus.log("ContactHelper -> Best contact match '$cleanTarget' -> '$bestName' ($bestNumber, score: $bestScore)")
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
            if (match != null && match.score >= 150) {
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
            if (match != null && match.score >= 150) {
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
            if (match != null && match.score >= 150) {
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
            if (match != null && match.score >= 150) {
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
                if (bestScore >= 150) {
                    return bestId
                }
            }
        } catch (e: Exception) {
            LogBus.warn("ContactHelper: resolveWhatsAppVoipDataId error: ${e.message}")
        }
        return null
    }

    /**
     * Resolves the WhatsApp chat/profile data row ID from ContactsContract.Data.
     * Uses the "vnd.android.cursor.item/vnd.com.whatsapp.profile" MIME type, which is
     * present for every contact that has WhatsApp installed and is linked in the Android
     * contacts database.
     *
     * Returns the DATA._ID that can be used with the
     * "content://com.whatsapp/data/<id>" content URI to open the exact chat.
     */
    fun resolveWhatsAppChatDataId(context: Context, nameOrPhone: String): Long? {
        if (nameOrPhone.isBlank()) return null
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val resolver = context.contentResolver
        val projection = arrayOf(
            ContactsContract.Data._ID,
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.Data.DATA1
        )
        // WhatsApp registers both a profile and a voip.call row; use profile for messaging
        val mimeTypes = arrayOf(
            "vnd.android.cursor.item/vnd.com.whatsapp.profile",
            "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
        )
        val placeholders = mimeTypes.joinToString(",") { "?" }
        val selection = "${ContactsContract.Data.MIMETYPE} IN ($placeholders)"

        try {
            resolver.query(
                ContactsContract.Data.CONTENT_URI, projection, selection, mimeTypes, null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Data._ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
                val phoneIdx = cursor.getColumnIndex(ContactsContract.Data.DATA1)

                var bestId: Long? = null
                var bestScore = 0
                val cleanTarget = nameOrPhone.replace(Regex("[^0-9]"), "")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx) ?: ""
                    val phone = cursor.getString(phoneIdx) ?: ""

                    // Exact phone tail match
                    if (cleanTarget.isNotEmpty() && cleanTarget.length >= 7 &&
                        phone.replace(Regex("[^0-9]"), "").endsWith(cleanTarget)) {
                        return id
                    }
                    // Fuzzy name match
                    val score = calculateScore(nameOrPhone, name)
                    if (score > bestScore) {
                        bestScore = score
                        bestId = id
                    }
                }
                if (bestScore >= 150) return bestId
            }
        } catch (e: Exception) {
            LogBus.warn("ContactHelper: resolveWhatsAppChatDataId error: ${e.message}")
        }
        return null
    }
}
