package io.github.dulle88.atrium.portal

/**
 * A contact in the TV's "Važni telefoni" panel. Stored as JSON in Kv and sent to the portal as is
 * (Gson field names are the format, kept by R8). [number] is stored as typed; the TV formats it.
 */
data class PhoneContact(val name: String, val number: String)

object PhoneContacts {
    const val MAX = 8
    /** The TV shows at most this many contacts at once and pages through the rest. */
    const val PER_PAGE = 4
    const val MAX_NAME = 30
    const val MAX_NUMBER = 20

    /** Shown until the list is first saved from the portal: the emergency services. */
    val DEFAULTS = listOf(
        PhoneContact("Policija", "192"),
        PhoneContact("Vatrogasci", "193"),
        PhoneContact("Hitna pomoć", "194")
    )

    /**
     * Splits [contacts] into as few pages of at most [PER_PAGE] as possible, as even as possible with the
     * larger pages first (5 is 3+2, 7 is 4+3), so a page never holds a lone contact while another is full.
     */
    fun pages(contacts: List<PhoneContact>): List<List<PhoneContact>> {
        if (contacts.isEmpty()) return emptyList()
        val count = (contacts.size + PER_PAGE - 1) / PER_PAGE
        val base = contacts.size / count
        val extra = contacts.size % count
        var start = 0
        return List(count) { i ->
            val size = base + if (i < extra) 1 else 0
            contacts.subList(start, start + size).also { start += size }
        }
    }

    private const val NUMBER_CHARS = "0123456789 +/-()"

    /**
     * Returns the list with names and numbers trimmed, or throws [InvalidRequestException]. Fields are
     * checked for null because Gson leaves missing ones null.
     */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun validate(contacts: List<PhoneContact?>?): List<PhoneContact> {
        if (contacts == null || contacts.any { it == null }) throw InvalidRequestException("Invalid contacts")
        if (contacts.size > MAX) throw InvalidRequestException("At most $MAX contacts")
        return contacts.map { c ->
            val name = (c!!.name ?: "").trim()
            val number = (c.number ?: "").trim()
            if (name.length !in 1..MAX_NAME || name.any { it.isISOControl() }) {
                throw InvalidRequestException("Each contact needs a name of 1 to $MAX_NAME characters")
            }
            if (number.length !in 1..MAX_NUMBER || number.any { it !in NUMBER_CHARS }) {
                throw InvalidRequestException("A number must be 1 to $MAX_NUMBER characters: digits, spaces and + / - ( )")
            }
            PhoneContact(name, number)
        }
    }
}
