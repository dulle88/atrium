package io.github.dulle88.atrium.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneFormatTest {
    @Test fun mobileTenDigits() = assertEquals("064/123-4567", PhoneFormat.serbian("0641234567"))
    @Test fun nineDigitsNumber() = assertEquals("063/123-456", PhoneFormat.serbian("063123456"))
    @Test fun novisadLandline() = assertEquals("021/123-456", PhoneFormat.serbian("021123456"))
    @Test fun ignoresExistingSeparators() = assertEquals("064/123-4567", PhoneFormat.serbian("064 123-4567"))
    @Test fun alreadyFormattedStaysTheSame() = assertEquals("064/123-4567", PhoneFormat.serbian("064/123-4567"))
    @Test fun shortNumbersUntouched() = assertEquals("192", PhoneFormat.serbian("192"))
    @Test fun internationalLeftAlone() = assertEquals("+381 64 849 5172", PhoneFormat.serbian("+381 64 849 5172"))
    @Test fun unexpectedLengthLeftAlone() = assertEquals("06412345672", PhoneFormat.serbian("06412345672"))
}
