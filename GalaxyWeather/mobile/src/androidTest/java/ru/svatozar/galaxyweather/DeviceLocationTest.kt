package ru.svatozar.galaxyweather

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceLocationTest {
    @get:org.junit.Rule val activity = androidx.test.ext.junit.rules.ActivityScenarioRule(MainActivity::class.java)
    @Test fun currentLocationUsesActualPhoneSignal(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (!DeviceLocation.permitted(context) || !DeviceLocation.enabled(context)) {
            assertNull(DeviceLocation.city(context))
            android.util.Log.i("WeatherLocationTest", "Location disabled or denied: fallback verified")
        } else {
            val city = DeviceLocation.city(context)
            assertNotNull("Phone did not provide a location in 15 seconds", city)
            assertTrue(city!!.name.isNotBlank())
            android.util.Log.i("WeatherLocationTest", "Real device location and city lookup received; named=${city.name != "Текущее место"}")
        }
        Unit
    }
}
