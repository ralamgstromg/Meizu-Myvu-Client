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

    /**
     * Resolves a phone number from either raw digits or contact display name.
     * Returns Pair(phoneNumber, displayName) or null if unresolvable.
     */
    fun resolveContactPhone(context: Context, target: String): Pair<String, String>? {
        val cleanTarget = target.trim()
        if (cleanTarget.isEmpty()) return null

        // 1. If it already looks like a direct phone number
        val digitOnly = cleanTarget.replace(Regex("[^0-9+]"), "")
        if (digitOnly.length >= 7 && (cleanTarget.matches(Regex("^[0-9+#* -]+$")) || cleanTarget.startsWith("+"))) {
            return Pair(cleanTarget, cleanTarget)
        }

        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            LogBus.warn("ContactHelper -> READ_CONTACTS permission not granted, returning raw target")
            return if (digitOnly.isNotEmpty()) Pair(digitOnly, cleanTarget) else null
        }

        try {
            val normalizedSearch = normalize(cleanTarget)
            val searchTokens = normalizedSearch.split(Regex("\\s+")).filter { it.length >= 2 }

            var bestNumber: String? = null
            var bestName: String? = null
            var bestScore = Int.MIN_VALUE

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
                        val normalizedContact = normalize(contactName)
                        val contactTokens = normalizedContact.split(Regex("\\s+")).filter { it.length >= 2 }

                        // Coincidencia Exacta
                        if (normalizedContact == normalizedSearch) {
                            LogBus.log("ContactHelper -> Exact match '$contactName' -> $contactNumber")
                            return Pair(contactNumber, contactName)
                        }

                        var currentScore = 0
                        if (normalizedContact.contains(normalizedSearch)) {
                            currentScore += 100
                        }

                        for (sToken in searchTokens) {
                            for (cToken in contactTokens) {
                                if (sToken == cToken) {
                                    currentScore += 50
                                } else if (cToken.contains(sToken) || sToken.contains(cToken)) {
                                    currentScore += 25
                                } else {
                                    val dist = levenshteinDistance(sToken, cToken)
                                    val maxLen = maxOf(sToken.length, cToken.length)
                                    if (maxLen > 3 && dist <= 2) {
                                        currentScore += (20 - (dist * 5))
                                    }
                                }
                            }
                        }

                        if (currentScore > bestScore && currentScore >= 20) {
                            bestScore = currentScore
                            bestNumber = contactNumber
                            bestName = contactName
                        }
                    }
                }
            }

            if (bestNumber != null && bestName != null) {
                LogBus.log("ContactHelper -> Fuzzy match '$cleanTarget' -> '$bestName' ($bestNumber, score: $bestScore)")
                return Pair(bestNumber, bestName)
            }
        } catch (e: Exception) {
            LogBus.warn("ContactHelper -> Contacts query failed: ${e.message}")
        }

        return if (digitOnly.isNotEmpty()) Pair(digitOnly, cleanTarget) else null
    }

    /**
     * Resolves email address from email string or contact display name.
     */
    fun resolveContactEmail(context: Context, target: String): Pair<String, String>? {
        val cleanTarget = target.trim()
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
}
