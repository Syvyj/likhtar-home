package com.likhtar.home.feature.cinema

import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.catalog.StreamOption
import com.likhtar.home.core.catalog.TitleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Назва випуску Телепорталу несе в собі назву шоу, і в списку вона повторювалась у кожному
 * рядку. Тест ловить обидві гілки: коли в рядку є «Випуск» і коли його немає.
 */
class EpisodeTitleTest {

    @Test
    fun `назва шоу зрізається до слова Випуск`() {
        assertEquals(
            "Випуск 4", // дата — окремою плашкою на картці
            RealityTitleViewModel.episodeTitle(
                "Україна має талант. 2026. Випуск 4 від 13.09.2026",
                "Україна має талант",
            ),
        )
    }

    @Test
    fun `без слова Випуск лишається власна назва`() {
        assertEquals(
            "Фінал",
            RealityTitleViewModel.episodeTitle("Супермама. Фінал", "Супермама"),
        )
    }

    @Test
    fun `рядок без назви шоу лишається як є`() {
        assertEquals("Спецпроєкт", RealityTitleViewModel.episodeTitle("Спецпроєкт", "Хто зверху?"))
    }

    @Test
    fun `сама лише назва шоу не перетворюється на порожній рядок`() {
        assertEquals("Супермама", RealityTitleViewModel.episodeTitle("Супермама", "Супермама"))
    }
}

// Порядок ряду «Реаліті» перевіряє UaAudioRowTest: правило змінилось із «озвучка
// першим ключем сортування» на «у ряд входить лише те, що можна ввімкнути», і два
// тести, що стерегли старе, зникли разом із ним.

class PlaybackOrderTest {

    private fun stream(lang: String, host: String) = StreamOption(
        label = "1080p",
        url = "https://$host/watch",
        lang = lang,
    )

    @Test
    fun `українська доріжка важливіша за останнє джерело`() {
        val foreignRecent = stream("other", "recent.example")
        val ukrainian = stream("ukr", "ua.example")

        val ordered = prioritizePlaybackOptions(
            options = listOf(foreignRecent, ukrainian),
            recentSources = listOf("recent.example"),
        )

        assertEquals(listOf("ua.example", "recent.example"), ordered.map { it.source })
    }
}

/**
 * Ext-ключ прогресу й наш id проєкту — той самий проєкт під двома іменами. Тест тримає
 * межу: чужі ключі (1+1) не перетворюються на шлях, якого в нас немає.
 */
class ExtKeyTest {

    @Test
    fun `ключ Телепорталу стає id позиції розділу`() {
        assertEquals(
            "show/stb/master-cheff",
            RealityTitleViewModel.idOfExtKey("teleportal:stb/master-cheff"),
        )
    }

    @Test
    fun `ключ 1+1 веде на сторінку проєкту`() {
        // Доки розділу 1+1 не було, цей ключ свідомо не давав шляху — вести картку в
        // екран, якого не існує, гірше, ніж не вести. Відколи ручки 1+1 взяті, шлях є.
        assertEquals("plus1/12345", RealityTitleViewModel.idOfExtKey("1plus1:12345"))
        assertEquals("youtube/nashe-yt-PL1", RealityTitleViewModel.idOfExtKey("nashe:nashe-yt-PL1"))
    }

    @Test
    fun `невідомий префікс шляху не дає`() {
        assertNull(RealityTitleViewModel.idOfExtKey("megogo:777"))
    }

    @Test
    fun `серія серіалу порталу — без назви, сезону й дати`() {
        assertEquals("Серія 24", RealityTitleViewModel.episodeTitle("Лікарі. Сезон 2. Серія 24 від 17.09.2026", "Лікарі"))
    }

    @Test
    fun `серія з YouTube — номером`() {
        assertEquals("Серія 3", RealityTitleViewModel.episodeTitle("Другий шанс на перше кохання 3 серія | Мелодрама", "Другий шанс на перше кохання"))
    }

    @Test
    fun `дві серії одним відео`() {
        assertEquals("Серії 3–4", RealityTitleViewModel.episodeTitle("ВОЄННА ДРАМА НА РЕАЛЬНИХ ПОДІЯХ! Перші дні окупації Херсонщини! Команда 3,4 серії українською", "Команда"))
    }

    @Test
    fun `шоу мовника з YouTube капслоком — номером випуску`() {
        assertEquals("Випуск 1", RealityTitleViewModel.episodeTitle("ПРЕМ'ЄРА! ЛЕГЕНДАРНЕ ШОУ КАРАОКЕ НА МАЙДАНІ | 1 ВИПУСК ВІД 10.05.2026", "Караоке на Майдані"))
        assertEquals("Випуск 7", RealityTitleViewModel.episodeTitle("КАРАОКЕ НА МАЙДАНІ | ВИПУСК 7", "Караоке на Майдані"))
        // Номер сезону перед словом — не номер випуску.
        assertEquals("Випуск 3", RealityTitleViewModel.episodeTitle("Мастер Шеф Професіонали-5 випуск 3: виїзний", "Мастер Шеф"))
    }
}
