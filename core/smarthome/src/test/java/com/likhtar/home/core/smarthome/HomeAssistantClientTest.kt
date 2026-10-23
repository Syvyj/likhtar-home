package com.likhtar.home.core.smarthome

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Інтеграційний тест клієнта проти справжнього HTTP-сервера на localhost.
 *
 * Це перевіряє те, чого не покриває мапер: заголовок авторизації, метод і тіло запиту,
 * читабельність помилки 401 і розбір відповіді Assist. Емулятор для цього не потрібен.
 */
class HomeAssistantClientTest {

    private lateinit var server: MockWebServer
    private lateinit var config: SmartHomeConfig
    private val client = HomeAssistantClient(UkTexts)

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        config = SmartHomeConfig(server.url("/").toString(), "secret-token")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `ping returns the message and sends a bearer token`() = runTest {
        server.enqueue(MockResponse().setBody("""{"message":"API running."}"""))

        val result = client.ping(config)

        assertEquals("API running.", result.getOrNull())
        val request = server.takeRequest()
        assertEquals("/api/", request.path)
        assertEquals("Bearer secret-token", request.getHeader("Authorization"))
    }

    @Test
    fun `states are fetched and mapped`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"entity_id":"light.kitchen","state":"on","attributes":{"friendly_name":"Кухня"}}]""",
            ),
        )
        server.enqueue(MockResponse().setBody("""[["light.kitchen","Кухня",false,null]]"""))

        val devices = client.states(config).getOrThrow()

        assertEquals("Кухня", devices.single().name)
        assertEquals("Кухня", devices.single().room)
        assertTrue(devices.single().isOn)
        assertEquals("/api/states", server.takeRequest().path)
        assertEquals("/api/template", server.takeRequest().path)
    }

    @Test
    fun `service call posts the entity id`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))

        val result = client.callService(config, "light", "turn_off", "light.kitchen")

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/services/light/turn_off", request.path)
        assertTrue(request.body.readUtf8().contains("light.kitchen"))
    }

    @Test
    fun `conversation sends the phrase and returns the assistant reply`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"response":{"speech":{"plain":{"speech":"Вимкнено світло кухні"}}}}""",
            ),
        )

        val reply = client.conversation(config, "вимкни світло кухні").getOrThrow()

        assertEquals("Вимкнено світло кухні", reply)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("вимкни світло кухні"))
        assertTrue(body.contains("\"language\":\"uk\""))
    }

    @Test
    fun `conversation without a speech field still reports success`() = runTest {
        server.enqueue(MockResponse().setBody("""{"response":{}}"""))

        assertEquals("Команду виконано", client.conversation(config, "щось").getOrThrow())
    }

    @Test
    fun `rejected token produces a message about the token, not a raw code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = client.states(config)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Токен"))
    }

    @Test
    fun `wrong address is reported as not looking like Home Assistant`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = client.ping(config)

        assertTrue(result.exceptionOrNull()!!.message!!.contains("Home Assistant"))
    }

    @Test
    fun `incomplete config fails without touching the network`() = runTest {
        val result = client.states(SmartHomeConfig("", ""))

        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }
}
