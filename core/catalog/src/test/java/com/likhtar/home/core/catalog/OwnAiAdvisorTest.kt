package com.likhtar.home.core.catalog

import com.likhtar.home.core.data.PersonalAi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnAiAdvisorTest {
    @Test
    fun jsonInsideFencesAndProse() {
        val reply = "Ось що я знайшов:\n```json\n{\"best\":{\"title\":\"Interstellar\",\"year\":2014,\"kind\":\"movie\"},\"thought\":\"Космос.\"}\n```"
        assertEquals("Interstellar", PersonalAi.jsonIn(reply)?.optJSONObject("best")?.optString("title"))
        assertNull(PersonalAi.jsonIn("вибачте, не можу"))
    }

    @Test
    fun titlesMatchIgnoringPunctuation() {
        assertEquals(OwnAiAdvisor.alnumLower("Spider-Man: No Way Home"), OwnAiAdvisor.alnumLower("spider man no way home"))
        assertEquals("амелі", OwnAiAdvisor.alnumLower("Амелі!"))
    }
}
