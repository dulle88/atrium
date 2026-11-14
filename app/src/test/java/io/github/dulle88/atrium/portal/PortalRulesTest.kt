package io.github.dulle88.atrium.portal

import org.junit.Assert.assertEquals
import org.junit.Test

class PortalRulesTest {
    @Test fun photoQuotaNeedsSpaceAndRoom() {
        val mb = 1024L * 1024
        PortalRules.checkPhotoQuota(usableBytes = 256 * mb, webPhotoCount = 199)
        assertEquals(true, runCatching { PortalRules.checkPhotoQuota(256 * mb - 1, 0) }.exceptionOrNull() is StorageFullException)
        assertEquals(true, runCatching { PortalRules.checkPhotoQuota(10_000 * mb, 200) }.exceptionOrNull() is StorageFullException)
    }
}
