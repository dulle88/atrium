package io.github.dulle88.atrium.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HolidayCalendarTest {
    @Test fun orthodoxEaster2025() = assertEquals(LocalDate.of(2025, 4, 20), HolidayCalendar.orthodoxEaster(2025))
    @Test fun orthodoxEaster2026() = assertEquals(LocalDate.of(2026, 4, 12), HolidayCalendar.orthodoxEaster(2026))
    @Test fun orthodoxEaster2027() = assertEquals(LocalDate.of(2027, 5, 2), HolidayCalendar.orthodoxEaster(2027))

    @Test fun newYearCoversFirstTwoDays() {
        assertEquals("Srećna Nova godina!", HolidayCalendar.greetingFor(LocalDate.of(2026, 1, 1))?.sr)
        assertEquals("Happy New Year!", HolidayCalendar.greetingFor(LocalDate.of(2026, 1, 2))?.en)
        assertNull(HolidayCalendar.greetingFor(LocalDate.of(2026, 1, 3)))
    }

    @Test fun christmasCoversSeventhToNinth() {
        assertEquals("Srećan Božić! Hristos se rodi!", HolidayCalendar.greetingFor(LocalDate.of(2026, 1, 7))?.sr)
        assertEquals("Srećan Božić!", HolidayCalendar.greetingFor(LocalDate.of(2026, 1, 8))?.sr)
        assertEquals("Merry Christmas!", HolidayCalendar.greetingFor(LocalDate.of(2026, 1, 9))?.en)
        assertNull(HolidayCalendar.greetingFor(LocalDate.of(2026, 1, 10)))
        assertNull(HolidayCalendar.greetingFor(LocalDate.of(2026, 1, 6)))
    }

    @Test fun easterWeekend2026() {
        assertEquals("Good Friday", HolidayCalendar.greetingFor(LocalDate.of(2026, 4, 10))?.en)
        assertEquals("Happy Easter!", HolidayCalendar.greetingFor(LocalDate.of(2026, 4, 12))?.en)
        assertEquals("Happy Easter Monday!", HolidayCalendar.greetingFor(LocalDate.of(2026, 4, 13))?.en)
        assertNull(HolidayCalendar.greetingFor(LocalDate.of(2026, 4, 9)))
    }

    @Test fun holySaturday2026() {
        val g = HolidayCalendar.greetingFor(LocalDate.of(2026, 4, 11))
        assertEquals("Holy Saturday", g?.en)
        assertEquals("Velika subota", g?.sr)
    }

    @Test fun holySaturday2027CombinesWithLabourDay() {
        val g = HolidayCalendar.greetingFor(LocalDate.of(2027, 5, 1))
        assertEquals("Holy Saturday · Happy Labour Day!", g?.en)
        assertEquals("Velika subota · Srećan Praznik rada!", g?.sr)
    }

    @Test fun otherFixedHolidays() {
        assertNotNull(HolidayCalendar.greetingFor(LocalDate.of(2026, 2, 15)))
        assertNotNull(HolidayCalendar.greetingFor(LocalDate.of(2026, 2, 16)))
        assertNotNull(HolidayCalendar.greetingFor(LocalDate.of(2026, 5, 1)))
        assertNotNull(HolidayCalendar.greetingFor(LocalDate.of(2026, 5, 2)))
        assertNotNull(HolidayCalendar.greetingFor(LocalDate.of(2026, 11, 11)))
        assertNull(HolidayCalendar.greetingFor(LocalDate.of(2026, 10, 28)))
    }

    @Test fun easterOnLabourDayShowsBoth() {
        val g = HolidayCalendar.greetingFor(LocalDate.of(2027, 5, 2))
        assertEquals("Hristos vaskrse! Srećan Uskrs! Srećan Praznik rada!", g?.sr)
        assertEquals("Happy Easter! Happy Labour Day!", g?.en)
    }

    @Test fun plainLabourDayUnchanged() {
        val g = HolidayCalendar.greetingFor(LocalDate.of(2026, 5, 1))
        assertEquals("Srećan Praznik rada!", g?.sr)
        assertEquals("Happy Labour Day!", g?.en)
    }
}
