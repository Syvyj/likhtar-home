package com.likhtar.home.core.catalog

import com.likhtar.home.core.model.StreamingService
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingServiceTest {
    @Test
    fun resolvesTmdbNamesAndPackages() {
        assertEquals(StreamingService.PRIME, StreamingService.byProvider("Amazon Prime Video"))
        assertEquals(StreamingService.NETFLIX, StreamingService.byProvider("Netflix basic with Ads"))
        assertEquals(StreamingService.HBO, StreamingService.byPackage("com.hbo.hbonow"))
        assertEquals(null, StreamingService.byProvider("Canal+"))
    }

    @Test
    fun keysAndPackagesAreUnique() {
        val keys = StreamingService.withPage.map { it.key }
        assertEquals(keys.distinct(), keys)
        val packages = StreamingService.entries.flatMap { it.packages }
        assertEquals(packages.distinct(), packages)
    }
}
