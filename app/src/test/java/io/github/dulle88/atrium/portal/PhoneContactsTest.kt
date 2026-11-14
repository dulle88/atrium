package io.github.dulle88.atrium.portal

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneContactsTest {
    private fun rejection(list: List<PhoneContact?>?): String? =
        runCatching { PhoneContacts.validate(list) }.exceptionOrNull()?.let { (it as InvalidRequestException).message }

    @Test fun trimsAndKeepsOrder() {
        val out = PhoneContacts.validate(listOf(PhoneContact("  Upravnik ", " 064 123-4567 "), PhoneContact("Lift", "+381 (21) 123/456")))
        assertEquals(listOf(PhoneContact("Upravnik", "064 123-4567"), PhoneContact("Lift", "+381 (21) 123/456")), out)
    }

    @Test fun emptyListIsAllowed() = assertEquals(emptyList<PhoneContact>(), PhoneContacts.validate(emptyList()))

    @Test fun atMostEight() {
        val eight = (1..8).map { PhoneContact("N$it", "06$it") }
        assertEquals(eight, PhoneContacts.validate(eight))
        assertEquals("At most 8 contacts", rejection(eight + PhoneContact("N9", "069")))
    }

    @Test fun defaultsAreTheEmergencyNumbers() {
        assertEquals(
            listOf(
                PhoneContact("Policija", "192"),
                PhoneContact("Vatrogasci", "193"),
                PhoneContact("Hitna pomoć", "194")
            ),
            PhoneContacts.DEFAULTS
        )
        assertEquals(PhoneContacts.DEFAULTS, PhoneContacts.validate(PhoneContacts.DEFAULTS))
    }

    @Test fun pagesHoldAtMostFourAndAreBalanced() {
        fun sizes(n: Int) = PhoneContacts.pages((1..n).map { PhoneContact("N$it", "$it") }).map { it.size }
        assertEquals(emptyList<Int>(), sizes(0))
        assertEquals(listOf(1), sizes(1))
        assertEquals(listOf(4), sizes(4))
        assertEquals(listOf(3, 2), sizes(5))
        assertEquals(listOf(3, 3), sizes(6))
        assertEquals(listOf(4, 3), sizes(7))
        assertEquals(listOf(4, 4), sizes(8))
        val seven = (1..7).map { PhoneContact("N$it", "$it") }
        assertEquals(seven, PhoneContacts.pages(seven).flatten())
    }

    @Test fun rejectsBadNames() {
        for (bad in listOf("", "   ", "a".repeat(31), "Up\nravnik", "Tab\there", "Nul\u0000", "\u0085x")) {
            assertEquals(bad, "Each contact needs a name of 1 to 30 characters", rejection(listOf(PhoneContact(bad, "064"))))
        }
        assertEquals(listOf(PhoneContact("Tehnička služba Ђ", "0")), PhoneContacts.validate(listOf(PhoneContact("Tehnička služba Ђ", "0"))))
        assertEquals("a".repeat(30), PhoneContacts.validate(listOf(PhoneContact(" " + "a".repeat(30) + " ", "1"))).single().name)
    }

    @Test fun rejectsBadNumbers() {
        for (bad in listOf("", "  ", "1".repeat(21), "064x123", "064.123", "064#1", "064\n123", "٠٦٤")) {
            assertEquals(bad, "A number must be 1 to 20 characters: digits, spaces and + / - ( )", rejection(listOf(PhoneContact("A", bad))))
        }
        assertEquals("1".repeat(20), PhoneContacts.validate(listOf(PhoneContact("A", "1".repeat(20)))).single().number)
    }

    @Suppress("CAST_NEVER_SUCCEEDS")
    @Test fun rejectsMissingFields() {
        assertEquals("Invalid contacts", rejection(null))
        assertEquals("Invalid contacts", rejection(listOf(null)))
        // Gson leaves missing fields null even though the Kotlin types say otherwise.
        val gson = com.google.gson.Gson()
        val noName = gson.fromJson("""{"number":"064"}""", PhoneContact::class.java)
        val noNumber = gson.fromJson("""{"name":"A"}""", PhoneContact::class.java)
        assertEquals("Each contact needs a name of 1 to 30 characters", rejection(listOf(noName)))
        assertEquals("A number must be 1 to 20 characters: digits, spaces and + / - ( )", rejection(listOf(noNumber)))
    }
}
