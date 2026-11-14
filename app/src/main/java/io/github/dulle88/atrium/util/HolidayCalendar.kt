package io.github.dulle88.atrium.util

import java.time.LocalDate

data class Greeting(val sr: String, val en: String)

object HolidayCalendar {
    private val newYear = Greeting("Srećna Nova godina!", "Happy New Year!")
    private val christmas = Greeting("Srećan Božić! Hristos se rodi!", "Merry Christmas!")
    private val christmasAfter = Greeting("Srećan Božić!", "Merry Christmas!")
    private val statehood = Greeting("Srećan Dan državnosti!", "Happy Statehood Day!")
    private val labour = Greeting("Srećan Praznik rada!", "Happy Labour Day!")
    private val armistice = Greeting("Dan primirja u Prvom svetskom ratu", "Armistice Day")
    private val goodFriday = Greeting("Veliki petak", "Good Friday")
    private val holySaturday = Greeting("Velika subota", "Holy Saturday")
    private val easter = Greeting("Hristos vaskrse! Srećan Uskrs!", "Happy Easter!")
    private val easterMonday = Greeting("Srećan Uskršnji ponedeljak!", "Happy Easter Monday!")

    /** Julian computus plus 13 days (valid 1900-2099). */
    fun orthodoxEaster(year: Int): LocalDate {
        val a = year % 4
        val b = year % 7
        val c = year % 19
        val d = (19 * c + 15) % 30
        val e = (2 * a + 4 * b - d + 34) % 7
        val month = (d + e + 114) / 31
        val day = (d + e + 114) % 31 + 1
        return LocalDate.of(year, month, day).plusDays(13)
    }

    fun greetingFor(date: LocalDate): Greeting? {
        val fixed = when (date.monthValue to date.dayOfMonth) {
            1 to 1, 1 to 2 -> newYear
            1 to 7 -> christmas
            1 to 8, 1 to 9 -> christmasAfter
            2 to 15, 2 to 16 -> statehood
            5 to 1, 5 to 2 -> labour
            11 to 11 -> armistice
            else -> null
        }
        val easterDate = orthodoxEaster(date.year)
        val movable = when (date) {
            easterDate.minusDays(2) -> goodFriday
            easterDate.minusDays(1) -> holySaturday
            easterDate -> easter
            easterDate.plusDays(1) -> easterMonday
            else -> null
        }
        if (movable != null && fixed != null) {
            return Greeting(join(movable.sr, fixed.sr), join(movable.en, fixed.en))
        }
        return movable ?: fixed
    }

    /** "Hristos vaskrse! …" ends a sentence, so a space is enough; "Velika subota" doesn't, so use a separator. */
    private fun join(first: String, second: String): String {
        val sep = if (first.last() in ".!?") " " else " · "
        return first + sep + second
    }
}
