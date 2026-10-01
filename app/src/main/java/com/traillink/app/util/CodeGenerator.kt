package com.traillink.app.util

import java.security.SecureRandom

/**
 * Generates the shared secret that links two phones. It's random enough
 * (~45 bits of entropy — nobody will stumble onto it or brute-force it)
 * while staying short enough to read aloud or type by hand. This code,
 * not any account or phone number, is the only thing that grants access
 * to a location — treat it like a password and only share it with the
 * one person who should see it.
 */
object CodeGenerator {

    // Excludes visually-confusable characters: 0/O, 1/I/L, 8/B, 5/S.
    private const val ALPHABET = "234679ACDEFGHJKMNPQRTUVWXYZ"
    private val random = SecureRandom()

    fun generate(): String {
        val raw = (1..8).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        return raw.substring(0, 4) + "-" + raw.substring(4, 8)
    }

    fun normalize(input: String): String =
        input.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")

    fun isPlausible(code: String): Boolean = normalize(code).length in 6..12
}
