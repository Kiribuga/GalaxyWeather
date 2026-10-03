package ru.svatozar.galaxyweather

import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.svatozar.galaxyweather.widget.WidgetSettingsActivity
import java.lang.ref.WeakReference
import java.lang.ref.ReferenceQueue

class LifecycleResourceTest {
    @Test fun closedActivitiesAreCollectableAfterRepeatedLaunches() {
        val references = mutableListOf<WeakReference<android.app.Activity>>()
        val collected = ReferenceQueue<android.app.Activity>()
        repeat(4) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { references.add(WeakReference(it, collected)) }
            }
            ActivityScenario.launch(WidgetSettingsActivity::class.java).use { scenario ->
                scenario.onActivity { references.add(WeakReference(it, collected)) }
            }
        }
        val deadline = android.os.SystemClock.elapsedRealtime() + 30_000
        var released = 0
        // Polling WeakReference.get() before GC keeps its last result on the test thread's stack.
        while (released < references.size && android.os.SystemClock.elapsedRealtime() < deadline) {
            Runtime.getRuntime().gc()
            Thread.sleep(500)
            while (collected.poll() != null) released++
        }
        if (released < references.size) {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            android.os.Debug.dumpHprofData(java.io.File(context.filesDir, "lifecycle.hprof").absolutePath)
        }
        assertEquals("Closed Activity instances are still retained", references.size, released)
    }
}
