package com.likhtar.home.core.smarthome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAssistantMapperTest {

    private val body = """
        [
          {"entity_id":"light.kitchen","state":"on",
           "attributes":{"friendly_name":"Кухня"}},
          {"entity_id":"switch.kettle","state":"off",
           "attributes":{"friendly_name":"Чайник"}},
          {"entity_id":"sensor.living_temp","state":"22.5",
           "attributes":{"friendly_name":"Вітальня","unit_of_measurement":"°C","device_class":"temperature"}},
          {"entity_id":"scene.evening","state":"unknown",
           "attributes":{"friendly_name":"Вечір"}},
          {"entity_id":"light.broken","state":"unavailable",
           "attributes":{"friendly_name":"Зламана"}},
          {"entity_id":"automation.morning","state":"on",
           "attributes":{"friendly_name":"Ранкова автоматизація"}},
          {"entity_id":"light.no_name","state":"off","attributes":{}}
        ]
    """.trimIndent()

    private val devices = HomeAssistantMapper.parseStates(body, texts = UkTexts)

    @Test
    fun `unsupported domains are skipped`() {
        assertFalse(devices.any { it.domain == "automation" })
    }

    @Test
    fun `unavailable entities are skipped rather than shown as off`() {
        assertFalse(devices.any { it.entityId == "light.broken" })
        // scene зі станом unknown теж не показуємо: він не має справжнього стану.
        assertFalse(devices.any { it.entityId == "scene.evening" })
    }

    @Test
    fun `friendly name is used and falls back to entity id`() {
        assertEquals("Кухня", devices.first { it.entityId == "light.kitchen" }.name)
        assertEquals("no name", devices.first { it.entityId == "light.no_name" }.name)
    }

    @Test
    fun `on off state is parsed`() {
        assertTrue(devices.first { it.entityId == "light.kitchen" }.isOn)
        assertFalse(devices.first { it.entityId == "switch.kettle" }.isOn)
    }

    @Test
    fun `sensor shows value with unit and is not controllable`() {
        val sensor = devices.first { it.entityId == "sensor.living_temp" }
        assertEquals("22.5 °C", sensor.readableState)
        assertFalse(sensor.isControllable)
        assertNull(HomeAssistantMapper.toggleService(sensor))
    }

    @Test
    fun `controllable devices come before sensors`() {
        val firstSensorIndex = devices.indexOfFirst { !it.isControllable }
        val lastControllableIndex = devices.indexOfLast { it.isControllable }
        assertTrue(lastControllableIndex < firstSensorIndex)
    }

    @Test
    fun `toggle picks the opposite service for the entity domain`() {
        val light = devices.first { it.entityId == "light.kitchen" }
        assertEquals("light" to "turn_off", HomeAssistantMapper.toggleService(light))

        val kettle = devices.first { it.entityId == "switch.kettle" }
        assertEquals("switch" to "turn_on", HomeAssistantMapper.toggleService(kettle))
    }

    @Test
    fun `scene is always turned on because it cannot be turned off`() {
        val scene = SmartDevice(
            entityId = "scene.evening",
            name = "Вечір",
            kind = SmartDeviceKind.SCENE,
            state = "on",
            isOn = true,
        )
        assertEquals("scene" to "turn_on", HomeAssistantMapper.toggleService(scene))
    }

    @Test
    fun `broken payload yields an empty list instead of crashing`() {
        assertTrue(HomeAssistantMapper.parseStates("not json", texts = UkTexts).isEmpty())
        assertTrue(HomeAssistantMapper.parseStates("", texts = UkTexts).isEmpty())
    }

    @Test
    fun `base url is normalized so a pasted api suffix still works`() {
        assertEquals(
            "http://homeassistant.local:8123",
            SmartHomeConfig("http://homeassistant.local:8123/api/", "t").normalizedBaseUrl(),
        )
    }

    @Test
    fun `службове пристрою й приховане не показуються, кімнати йдуть групами`() {
        val states = """
            [
              {"entity_id":"light.lamp","state":"on","attributes":{"friendly_name":"Підсвітка в спальні"}},
              {"entity_id":"switch.lamp_relay","state":"on","attributes":{"friendly_name":"Підсвітка"}},
              {"entity_id":"switch.lamp_protect","state":"on","attributes":{"friendly_name":"Підсвітка Overcharge protection"}},
              {"entity_id":"switch.purifier","state":"on","attributes":{"friendly_name":"Очисник"}},
              {"entity_id":"switch.purifier_sound","state":"off","attributes":{"friendly_name":"Очисник: звук"}},
              {"entity_id":"sensor.volt","state":"232","attributes":{"friendly_name":"Підсвітка Напруга","device_class":"voltage"}},
              {"entity_id":"input_boolean.tech","state":"off","attributes":{"friendly_name":"[тех] реле"}},
              {"entity_id":"switch.night","state":"on","attributes":{"friendly_name":"Нічний режим"}},
              {"entity_id":"light.kid","state":"off","attributes":{"friendly_name":"Люстра","entity_id":["light.warm","light.cold"]}},
              {"entity_id":"light.warm","state":"off","attributes":{"friendly_name":"Тепле світло"}},
              {"entity_id":"light.cold","state":"off","attributes":{"friendly_name":"Холодне світло"}}
            ]
        """.trimIndent()
        val meta = HomeAssistantMapper.parseMeta(
            """[["light.lamp","Спальня",false,"Підсвітка",2],["switch.lamp_relay","Спальня",true,"Підсвітка",2],
               ["switch.lamp_protect","Спальня",false,"Підсвітка",2],["switch.purifier","Спальня",false,"Очисник",2],
               ["switch.purifier_sound","Спальня",false,"Очисник",2],["switch.night",null,false,null,999],
               ["light.kid","Дитяча",false,null,3]]""",
        )
        val list = HomeAssistantMapper.parseStates(states, meta, texts = UkTexts)
        assertEquals(listOf("Очисник", "Підсвітка в спальні", "Люстра", "Нічний режим"), list.map { it.name })
        assertEquals(listOf("Спальня", "Спальня", "Дитяча", null), list.map { it.room })
    }

    @Test
    fun `принтер — одна картка, фільтр — лише коли час міняти`() {
        val states = """
            [
              {"entity_id":"sensor.p1_print_status","state":"running","attributes":{}},
              {"entity_id":"sensor.p1_print_progress","state":"42","attributes":{"unit_of_measurement":"%"}},
              {"entity_id":"sensor.p1_remaining_time","state":"1.25","attributes":{"unit_of_measurement":"h"}},
              {"entity_id":"sensor.p1_nozzle_temperature","state":"219.6","attributes":{"device_class":"temperature"}},
              {"entity_id":"sensor.p1_bed_temperature","state":"60","attributes":{"device_class":"temperature"}},
              {"entity_id":"binary_sensor.p1_online","state":"on","attributes":{}},
              {"entity_id":"light.p1_chamber_light","state":"off","attributes":{"friendly_name":"Підсвітка принтера"}},
              {"entity_id":"sensor.a_filter_life_level","state":"0","attributes":{"friendly_name":"Стан фільтра","unit_of_measurement":"%"}},
              {"entity_id":"sensor.b_filter_life_level","state":"64","attributes":{"friendly_name":"Інший фільтр","unit_of_measurement":"%"}}
            ]
        """.trimIndent()
        val meta = HomeAssistantMapper.parseMeta("""[["sensor.p1_print_status","Лаба",false,"3D принтер",4]]""")
        val list = HomeAssistantMapper.parseStates(states, meta, texts = UkTexts)
        val printer = list.single { it.kind == SmartDeviceKind.PRINTER }
        assertEquals("3D принтер", printer.name)
        assertEquals("Друкує · 42 % · ще 1 год 15 хв", printer.readableState)
        assertEquals("сопло 219 °C · стіл 60 °C", printer.extra)
        assertEquals(0.42f, printer.progress!!, 0.001f)
        assertEquals(listOf("3D принтер", "Підсвітка принтера", "Стан фільтра").sorted(), list.map { it.name }.sorted())
        assertTrue(list.single { it.name == "Стан фільтра" }.warning)
    }

    @Test
    fun `сцена не ховає свої пристрої, сенсор з назвою пристрою лишається`() {
        val states = """
            [
              {"entity_id":"scene.evening","state":"2026-10-01","attributes":{"friendly_name":"Вечір","entity_id":["light.kitchen"]}},
              {"entity_id":"light.kitchen","state":"off","attributes":{"friendly_name":"Кухня"}},
              {"entity_id":"sensor.t","state":"21","attributes":{"friendly_name":"Датчик кухня Температура","device_class":"temperature","unit_of_measurement":"°C"}},
              {"entity_id":"sensor.p_filter_life_level","state":"3","attributes":{"friendly_name":"Очисник Filter life"}}
            ]
        """.trimIndent()
        val meta = HomeAssistantMapper.parseMeta("""[["sensor.t","Кухня",false,"Датчик кухня",1],["sensor.p_filter_life_level","Кухня",false,"Очисник",1]]""")
        val names = HomeAssistantMapper.parseStates(states, meta, texts = UkTexts).map { it.name }.toSet()
        assertEquals(setOf("Вечір", "Кухня", "Датчик кухня Температура", "Очисник Filter life"), names)
    }
}
