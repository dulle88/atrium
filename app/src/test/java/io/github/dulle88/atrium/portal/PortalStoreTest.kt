package io.github.dulle88.atrium.portal

import io.github.dulle88.atrium.FakeKvStore
import io.github.dulle88.atrium.data.db.KvStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalStoreTest {
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val now = 1_800_000_000_000L
    private var nextId = 0
    private fun store(kv: KvStore = FakeKvStore()) = PortalStore(kv) { "id${nextId++}" }

    private fun rejection(block: suspend () -> Unit): String? =
        runCatching { runBlocking { block() } }.exceptionOrNull()?.let { (it as PortalRejection).message }

    // --- PIN ---

    @Test fun pinIsFourDigitsAndStable() = runBlocking {
        val kv = FakeKvStore()
        val first = PortalStore(kv).pin()
        assertTrue(first.matches(Regex("\\d{4}")))
        assertEquals(first, PortalStore(kv).pin())
    }

    @Test fun setPinPersistsAndRejectsBadValues() = runBlocking {
        val kv = FakeKvStore()
        val store = PortalStore(kv)
        store.pin()
        store.setPin("0901")
        assertEquals("0901", store.pin())
        assertEquals("0901", PortalStore(kv).pin())
        for (bad in listOf("", "12", "12345", "abcd", " 123", "١٢٣٤")) {
            try { store.setPin(bad); throw AssertionError("accepted $bad") } catch (_: IllegalArgumentException) { }
        }
        assertEquals("0901", store.pin())
    }

    @Test fun concurrentFirstPinCallsAgree() = runBlocking {
        val store = PortalStore(FakeKvStore())
        val pins = (1..20).map { async(Dispatchers.Default) { store.pin() } }.awaitAll()
        assertEquals(1, pins.toSet().size)
    }

    // --- Slideshow settings ---

    @Test fun slideshowSecondsDefaultsToTenAndAcceptsOnlyTheAllowedSet() = runBlocking {
        val s = store()
        assertEquals(10, s.slideshowSeconds())
        s.setSlideshowSeconds(30)
        assertEquals(30, s.slideshowSeconds())
        for (bad in listOf(0, 7, 61, -5)) {
            assertEquals("Invalid duration", rejection { s.setSlideshowSeconds(bad) })
        }
        assertEquals(30, s.slideshowSeconds())
    }

    @Test fun slideshowSecondsIgnoresAGarbledStoredValue() = runBlocking {
        val kv = FakeKvStore()
        kv.map["slideshow_seconds"] = "banana"
        assertEquals(10, store(kv).slideshowSeconds())
        kv.map["slideshow_seconds"] = "7"
        assertEquals(10, store(kv).slideshowSeconds())
    }

    @Test fun slideshowOrderRoundTripsAppendsAndRemoves() = runBlocking {
        val s = store()
        assertEquals(emptyList<String>(), s.slideshowOrder())
        s.saveSlideshowOrder(listOf("b", "a", "b"))
        assertEquals(listOf("b", "a"), s.slideshowOrder())
        s.appendToSlideshow("c", listOf("a", "b"))
        s.appendToSlideshow("a", listOf("a", "b", "c"))
        assertEquals(listOf("b", "a", "c"), s.slideshowOrder())
        s.removeFromSlideshow("a")
        assertEquals(listOf("b", "c"), s.slideshowOrder())
    }

    @Test fun aNewPhotoGoesAfterPhotosThatWereNeverOrdered() = runBlocking {
        val s = store()
        s.appendToSlideshow("new", listOf("old2", "old1"))
        assertEquals(listOf("old2", "old1", "new"), s.slideshowOrder())
        s.saveSlideshowOrder(listOf("new", "old1"))
        s.appendToSlideshow("newer", listOf("new", "old1", "old2"))
        assertEquals(listOf("new", "old1", "old2", "newer"), s.slideshowOrder())
    }

    @Test fun slideshowOrderSurvivesGarbage() = runBlocking {
        val kv = FakeKvStore()
        kv.map["slideshow_order"] = "{not json"
        assertEquals(emptyList<String>(), store(kv).slideshowOrder())
    }

    // --- Notices: drafts and publishing ---

    @Test fun createDraftStoresAnUnpublishedNoticeStartingNow() = runBlocking {
        val s = store()
        val n = s.createDraft("  Lift is out  ", null, now + hour, now)
        assertEquals(Notice("id0", "Lift is out", emptyList(), now, now + hour, false, now), n)
        assertEquals(listOf(n), s.notices(now))
    }

    @Test fun blankTextIsStoredAsNull() = runBlocking {
        assertNull(store().createDraft("   ", null, now + hour, now).text)
    }

    @Test fun createDraftValidatesTimesAndText() {
        val s = store()
        assertEquals("The end time has already passed", rejection { s.createDraft("x", null, now, now) })
        assertEquals("The end time is more than a year away", rejection { s.createDraft("x", null, now + 365 * day, now) })
        assertEquals("The start must be before the end", rejection { s.createDraft("x", now + 2 * hour, now + hour, now) })
        assertEquals("The start is more than a year away", rejection { s.createDraft("x", now + 365 * day, now + 365 * day - 1, now) })
        assertEquals("Message is longer than 2000 characters", rejection { s.createDraft("a".repeat(2001), null, now + hour, now) })
        assertNull(rejection { s.createDraft("a".repeat(2000), now - hour, now + 365 * day - 1, now) })
    }

    @Test fun atMostTwentyNotices() {
        val s = store()
        runBlocking { repeat(20) { s.createDraft("n$it", null, now + hour, now) } }
        assertEquals("Too many notices. Remove one first.", rejection { s.createDraft("x", null, now + hour, now) })
    }

    @Test fun publishNeedsTextOrAPhoto() = runBlocking {
        val s = store()
        val empty = s.createDraft(null, null, now + hour, now)
        assertEquals("Add a message or at least one photo first", rejection { s.publish(empty.id, now) })
        val text = s.createDraft("Hi", null, now + hour, now)
        assertTrue(s.publish(text.id, now).published)
        assertEquals(listOf(false, true), s.notices(now).map { it.published })
    }

    @Test fun publishRejectsANoticeWhoseEndPassedDuringUpload() = runBlocking {
        val s = store()
        val n = s.createDraft("Hi", null, now + hour, now)
        assertEquals("The end time has already passed", rejection { s.publish(n.id, now + hour) })
    }

    @Test fun unknownNoticeIs404() {
        val s = store()
        val e = runCatching { runBlocking { s.publish("nope", now) } }.exceptionOrNull() as PortalRejection
        assertEquals(404, e.httpStatus)
    }

    @Test fun photosAreNamedByNoticeAndIndexAndCappedAtTen() = runBlocking {
        val s = store()
        val n = s.createDraft(null, null, now + hour, now)
        val written = mutableListOf<String>()
        repeat(10) { s.addNoticePhoto(n.id, if (it == 0) "png" else "jpg", now) { name -> written += name } }
        assertEquals("id0-0.png", written.first())
        assertEquals("id0-9.jpg", written.last())
        assertEquals(written, s.notices(now).single().photos)
        assertEquals("A notice can have at most 10 photos", rejection { s.addNoticePhoto(n.id, "jpg", now) { } })
        assertEquals(10, s.notices(now).single().photos.size)
        assertTrue(s.publish(n.id, now).published)
    }

    @Test fun failedPhotoWriteLeavesTheNoticeUnchanged() = runBlocking {
        val s = store()
        val n = s.createDraft(null, null, now + hour, now)
        runCatching { s.addNoticePhoto(n.id, "jpg", now) { throw java.io.IOException("disk") } }
        assertEquals(emptyList<String>(), s.notices(now).single().photos)
    }

    @Test fun photosCannotBeAddedAfterPublishing() = runBlocking {
        val s = store()
        val n = s.createDraft("Hi", null, now + hour, now)
        s.publish(n.id, now)
        assertEquals("Notice is already on the TV", rejection { s.addNoticePhoto(n.id, "jpg", now) { } })
    }

    @Test fun deleteNoticeReturnsTheRemovedNotice() = runBlocking {
        val s = store()
        val n = s.createDraft("Hi", null, now + hour, now)
        assertEquals(n, s.deleteNotice(n.id, now))
        assertNull(s.deleteNotice(n.id, now))
        assertEquals(emptyList<Notice>(), s.notices(now))
    }

    // --- Purge ---

    @Test fun purgeRemovesExpiredNoticesAndStaleDraftsOnly() = runBlocking {
        val s = store()
        val expiring = s.publish(s.createDraft("Old", null, now + hour, now).id, now)
        val lasting = s.publish(s.createDraft("Keep", null, now + day, now).id, now)
        val scheduled = s.publish(s.createDraft("Later", now + 2 * hour, now + day, now).id, now)
        val draft = s.createDraft("Draft", null, now + day, now)
        val later = now + hour
        val first = s.purge(later)
        assertEquals(listOf(expiring), first.removed)
        assertEquals(listOf(lasting, scheduled, draft), first.kept)
        val second = s.purge(now + hour + 1)
        assertEquals(listOf(draft), second.removed)
        assertEquals(listOf(lasting, scheduled), s.notices(now + hour + 1))
    }

    @Test fun purgeNeverWipesANoticeCreatedMeanwhile() = runBlocking {
        val kv = SlowKv()
        val s = store(kv)
        s.publish(s.createDraft("Old", null, now + 1_000, now).id, now)
        kv.slowReads = true
        val purge = async { s.purge(now + 5_000) }
        kotlinx.coroutines.yield()
        val fresh = async { s.createDraft("New", null, now + day, now + 5_000) }
        purge.await()
        fresh.await()
        kv.slowReads = false
        assertEquals(listOf("New"), s.notices(now + 5_000).map { it.text })
    }

    // --- Legacy message migration ---

    @Test fun legacyMessageBecomesAPublishedNotice() = runBlocking {
        val kv = FakeKvStore()
        kv.map["portal_message"] = """{"text":"Lift is out","expiresAtMs":${now + hour}}"""
        val s = store(kv)
        val n = s.notices(now).single()
        assertEquals(Notice("id0", "Lift is out", emptyList(), now, now + hour, true, now), n)
        assertFalse(kv.map.containsKey("portal_message"))
        assertEquals(listOf(n), s.notices(now))
    }

    @Test fun legacyMessageWithoutExpiryLastsJustUnderAYear() = runBlocking {
        val kv = FakeKvStore()
        kv.map["portal_message"] = """{"text":"Always","expiresAtMs":null}"""
        val n = store(kv).notices(now).single()
        assertTrue(n.untilMs > now + 364 * day && n.untilMs < now + 365 * day)
    }

    @Test fun expiredOrBlankOrGarbledLegacyMessageIsDropped() = runBlocking {
        for (legacy in listOf(
            """{"text":"Old","expiresAtMs":$now}""",
            """{"text":"   ","expiresAtMs":null}""",
            "{garbage"
        )) {
            val kv = FakeKvStore()
            kv.map["portal_message"] = legacy
            assertEquals(emptyList<Notice>(), store(kv).notices(now))
            assertFalse(kv.map.containsKey("portal_message"))
        }
    }

    @Test fun garbledNoticeListReadsAsEmpty() = runBlocking {
        val kv = FakeKvStore()
        kv.map["notices"] = "[{\"id\":null}, 5"
        assertEquals(emptyList<Notice>(), store(kv).notices(now))
        kv.map["notices"] = """[{"id":"a","fromMs":1,"untilMs":2,"published":true,"createdMs":1}]"""
        assertEquals(listOf(Notice("a", null, emptyList(), 1, 2, true, 1)), store(kv).notices(now))
    }

    // --- Phones ---

    @Test fun phonesDefaultUntilSaved() = runBlocking {
        assertEquals(PhoneContacts.DEFAULTS, store().phones())
    }

    @Test fun phonesRoundTripTrimmedAndEmptyListStays() = runBlocking {
        val kv = FakeKvStore()
        store(kv).setPhones(listOf(PhoneContact(" Lift ", " 021 123 456 ")))
        assertEquals(listOf(PhoneContact("Lift", "021 123 456")), store(kv).phones())
        store(kv).setPhones(emptyList())
        assertEquals(emptyList<PhoneContact>(), store(kv).phones())
    }

    @Test fun invalidPhonesAreRejectedAndNotStored() = runBlocking {
        val kv = FakeKvStore()
        val s = store(kv)
        assertEquals(
            "A number must be 1 to 20 characters: digits, spaces and + / - ( )",
            rejection { s.setPhones(listOf(PhoneContact("A", "064;rm"))) }
        )
        assertEquals(PhoneContacts.DEFAULTS, s.phones())
        assertFalse(kv.map.containsKey("phone_contacts"))
    }

    @Test fun garbledStoredPhonesFallBackToDefaults() = runBlocking {
        val kv = FakeKvStore()
        for (raw in listOf("{garbage", "[{\"name\":null,\"number\":\"1\"}]", "5")) {
            kv.map["phone_contacts"] = raw
            assertEquals(raw, PhoneContacts.DEFAULTS, store(kv).phones())
        }
    }

    private class SlowKv : KvStore {
        val map = mutableMapOf<String, String>()
        @Volatile var slowReads = false
        override suspend fun get(key: String): String? {
            val v = map[key]
            if (slowReads) kotlinx.coroutines.delay(50)
            return v
        }
        override suspend fun put(key: String, value: String) { map[key] = value }
        override suspend fun delete(key: String) { map.remove(key) }
    }
}
