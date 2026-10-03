package ru.svatozar.galaxyweather

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import ru.svatozar.galaxyweather.shared.WeatherRepository
import java.time.Instant

class ProfilePayloadTest {
    @Test fun phonePayloadRejectsWrongProfileStaleAndMalformedDataWithoutChangingSelection() {
        val repository = WeatherRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val before = repository.configuredLocation()
        val now = System.currentTimeMillis()
        val root = JSONObject().put("schema", 2).put("name", before.first).put("latitude", before.second)
            .put("longitude", before.third).put("fetched", now).put("source", "MET Norway")
            .put("current", JSONObject().put("t", 11).put("c", 3).put("w", 4).put("h", 70)
                .put("at", Instant.ofEpochMilli(now).toString()).put("day", true))
            .put("daily", org.json.JSONArray().put(JSONObject().put("d", "2026-10-03").put("min", 5).put("max", 12).put("c", 3)))
        assertNull(repository.acceptPhonePayload(root.toString(), "Другой город", before.second + 1, before.third))
        assertEquals(before, repository.configuredLocation())
        assertNull(repository.acceptPhonePayload(root.put("fetched", now - 1_800_001).toString(), before.first, before.second, before.third))
        assertNull(repository.acceptPhonePayload(root.put("fetched", now).put("hourly", org.json.JSONArray().put(JSONObject().put("time", "broken").put("t", 1).put("c", 3).put("day", true))).toString(), before.first, before.second, before.third))
        assertNull(repository.acceptPhonePayload("x".repeat(1_048_577), before.first, before.second, before.third))
        assertEquals(before, repository.configuredLocation())
    }
}
