package com.likhtar.home.core.data

import java.io.FileNotFoundException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpTest {
    private val url = "https://api.themoviedb.org/3/movie/1?api_key=SECRET&language=uk"

    @Test
    fun errorTextLosesQuery() {
        val clean = withoutQuery(FileNotFoundException(url), url)
        assertTrue(clean is FileNotFoundException)
        assertFalse("SECRET" in clean.message.orEmpty())
        val other = withoutQuery(IOException("Server returned HTTP response code: 500 for URL: $url"), url)
        assertEquals("Server returned HTTP response code: 500 for URL: https://api.themoviedb.org/3/movie/1", other.message)
    }

    @Test
    fun otherErrorsUntouched() {
        val e = IOException("connect timed out")
        assertTrue(withoutQuery(e, url) === e)
    }
}
