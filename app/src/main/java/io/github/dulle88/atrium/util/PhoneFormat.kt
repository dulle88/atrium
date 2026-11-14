package io.github.dulle88.atrium.util

object PhoneFormat {
    /**
     * Formats a Serbian national number as 064/123-4567 (10 digits) or 063/123-456 (9 digits).
     * Anything else (short emergency numbers, international +381 numbers, odd lengths) is returned as typed.
     */
    fun serbian(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("+")) return trimmed
        val digits = trimmed.filter { it in '0'..'9' }
        if (digits.length !in 9..10 || !digits.startsWith("0")) return trimmed
        if (trimmed.any { it !in '0'..'9' && it !in " /-" }) return trimmed
        return digits.substring(0, 3) + "/" + digits.substring(3, 6) + "-" + digits.substring(6)
    }
}
