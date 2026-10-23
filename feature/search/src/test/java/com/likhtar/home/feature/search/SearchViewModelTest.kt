package com.likhtar.home.feature.search

import com.likhtar.home.core.catalog.CatalogItem
import com.likhtar.home.core.data.SearchHistory
import com.likhtar.home.core.catalog.TitleKind
import com.likhtar.home.core.catalog.TitleSearch
import com.likhtar.home.core.catalog.UaLookup
import com.likhtar.home.core.tv.ShowSearch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.flowOf
import com.likhtar.home.core.catalog.AskEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `one and two characters update immediately without network search`() = runTest {
        val search = RecordingSearch()
        val viewModel = SearchViewModel(search, ShowSearch { emptyList() }, SearchHistory { }, UaLookup { emptyMap() })
        val collector = collectState(viewModel)

        viewModel.append("А")
        testScheduler.runCurrent()
        assertEquals("А", viewModel.uiState.value.query)
        assertEquals(SearchStatus.TYPING, viewModel.uiState.value.status)
        assertEquals(R.string.search_min_chars, viewModel.uiState.value.message)

        viewModel.append("В")
        testScheduler.advanceTimeBy(1_000)
        assertEquals("АВ", viewModel.uiState.value.query)
        assertTrue(search.queries.isEmpty())
        collector.cancel()
    }

    @Test
    fun `network debounce does not delay query text and then returns results`() = runTest {
        val search = RecordingSearch(listOf(item(1, "Аватар")))
        val viewModel = SearchViewModel(search, ShowSearch { emptyList() }, SearchHistory { }, UaLookup { emptyMap() })
        val collector = collectState(viewModel)

        viewModel.setQuery("АВАТАР")
        testScheduler.runCurrent()
        assertEquals("АВАТАР", viewModel.uiState.value.query)
        assertEquals(SearchStatus.TYPING, viewModel.uiState.value.status)
        assertTrue(search.queries.isEmpty())

        testScheduler.advanceTimeBy(275)
        testScheduler.runCurrent()
        assertEquals(SearchStatus.RESULTS, viewModel.uiState.value.status)
        assertEquals(listOf("АВАТАР"), search.queries)
        collector.cancel()
    }

    @Test
    fun `rapid input only searches the latest query`() = runTest {
        val search = RecordingSearch()
        val viewModel = SearchViewModel(search, ShowSearch { emptyList() }, SearchHistory { }, UaLookup { emptyMap() })
        val collector = collectState(viewModel)

        viewModel.setQuery("АВА")
        testScheduler.advanceTimeBy(100)
        viewModel.setQuery("АВАТ")
        testScheduler.advanceTimeBy(100)
        viewModel.setQuery("АВАТАР")
        testScheduler.advanceTimeBy(275)
        testScheduler.runCurrent()

        assertEquals(listOf("АВАТАР"), search.queries)
        collector.cancel()
    }

    @Test
    fun `new query cancels an in-flight request`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val search = object : TitleSearch {
            val completed = mutableListOf<String>()
            override suspend fun search(query: String): List<CatalogItem> {
                if (query == "ПЕРШИЙ") {
                    firstStarted.complete(Unit)
                    delay(10_000)
                }
                completed += query
                return emptyList()
            }
        }
        val viewModel = SearchViewModel(search, ShowSearch { emptyList() }, SearchHistory { }, UaLookup { emptyMap() })
        val collector = collectState(viewModel)

        viewModel.setQuery("ПЕРШИЙ")
        testScheduler.advanceTimeBy(275)
        firstStarted.await()
        viewModel.setQuery("ДРУГИЙ")
        testScheduler.advanceTimeBy(275)
        testScheduler.runCurrent()

        assertEquals(listOf("ДРУГИЙ"), search.completed)
        assertEquals(SearchStatus.EMPTY, viewModel.uiState.value.status)
        collector.cancel()
    }

    @Test
    fun `loading is visible and failure becomes error rather than empty`() = runTest {
        val search = object : TitleSearch {
            override suspend fun search(query: String): List<CatalogItem> {
                delay(100)
                error("offline")
            }
        }
        val viewModel = SearchViewModel(search, ShowSearch { emptyList() }, SearchHistory { }, UaLookup { emptyMap() })
        val collector = collectState(viewModel)

        viewModel.setQuery("ПОМИЛКА")
        testScheduler.advanceTimeBy(275)
        testScheduler.runCurrent()
        assertEquals(SearchStatus.LOADING, viewModel.uiState.value.status)
        testScheduler.advanceTimeBy(100)
        testScheduler.runCurrent()
        assertEquals(SearchStatus.ERROR, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.results.isEmpty())
        collector.cancel()
    }

    @Test
    fun `valid empty response has empty state`() = runTest {
        val viewModel = SearchViewModel(RecordingSearch(), ShowSearch { emptyList() }, SearchHistory { }, UaLookup { emptyMap() })
        val collector = collectState(viewModel)

        viewModel.setQuery("НЕІСНУЄ")
        testScheduler.advanceTimeBy(275)
        testScheduler.runCurrent()

        assertEquals(SearchStatus.EMPTY, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.best == null)
        collector.cancel()
    }

    @Test
    fun `results give the best match`() = runTest {
        val viewModel = SearchViewModel(RecordingSearch(listOf(item(1, "Мавка"))), ShowSearch { emptyList() }, SearchHistory { }, UaLookup { emptyMap() })
        val collector = collectState(viewModel)
        viewModel.setQuery("МАВКА")
        testScheduler.advanceTimeBy(275)
        testScheduler.runCurrent()
        assertEquals("Мавка", viewModel.uiState.value.best?.title)
        collector.cancel()
    }

    @Test
    fun `description makes Likhtaryk guess the best match`() = runTest {
        val avatar = item(19995, "Аватар")
        val search = object : TitleSearch {
            override suspend fun search(query: String) = listOf(item(1, "Сині"))
            override fun ask(query: String) = flowOf(AskEvent.Thought("Сині на'ві з Пандори"), AskEvent.Found(0, avatar, "той самий"))
        }
        val viewModel = SearchViewModel(search, ShowSearch { emptyList() }, SearchHistory { }, UaLookup { emptyMap() })
        val collector = collectState(viewModel)
        viewModel.setQuery("фільм про синіх інопланетян")
        testScheduler.advanceTimeBy(275)
        testScheduler.runCurrent()
        assertEquals(avatar, viewModel.uiState.value.best)
        assertFalse(viewModel.uiState.value.aiPending)
        assertEquals("Сині на'ві з Пандори", viewModel.uiState.value.aiThought)
        collector.cancel()
    }

    private class RecordingSearch(private val response: List<CatalogItem> = emptyList()) : TitleSearch {
        val queries = mutableListOf<String>()
        override suspend fun search(query: String): List<CatalogItem> {
            queries += query
            return response
        }
    }

    private fun TestScope.collectState(viewModel: SearchViewModel): Job =
        launch(dispatcher) { viewModel.uiState.collect {} }.also { testScheduler.runCurrent() }

    private companion object {
        fun item(id: Int, title: String) =
            CatalogItem(tmdbId = id, kind = TitleKind.MOVIE, title = title, year = "2024")
    }
}
