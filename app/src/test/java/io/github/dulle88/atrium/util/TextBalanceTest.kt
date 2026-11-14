package io.github.dulle88.atrium.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TextBalanceTest {
    @Test fun splitsAtTheSentenceNearestTheMiddle() {
        assertEquals("Srećan Božić!\nHristos se rodi!", TextBalance.twoLines("Srećan Božić! Hristos se rodi!"))
        assertEquals(
            "Hristos vaskrse! Srećan Uskrs!\nSrećan Praznik rada!",
            TextBalance.twoLines("Hristos vaskrse! Srećan Uskrs! Srećan Praznik rada!")
        )
    }

    @Test fun splitsMidSentenceWhenThereIsNoPunctuation() {
        assertEquals("Dan primirja u\nPrvom svetskom ratu", TextBalance.twoLines("Dan primirja u Prvom svetskom ratu"))
    }

    @Test fun leavesSingleWordsAlone() = assertEquals("Uskrs!", TextBalance.twoLines("Uskrs!"))
}
