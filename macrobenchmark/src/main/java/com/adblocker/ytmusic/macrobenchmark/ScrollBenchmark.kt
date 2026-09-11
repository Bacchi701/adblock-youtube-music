package com.adblocker.ytmusic.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fluidez de la pantalla principal: detecta recomposiciones caras al desplazar. */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollMainScreen() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.textContains("reglas")), UI_TIMEOUT_MS)
        },
    ) {
        val content = device.findObject(By.scrollable(true)) ?: return@measureRepeated
        content.setGestureMargin(device.displayWidth / MARGIN_DIVISOR)
        repeat(SCROLLS) {
            content.scroll(Direction.DOWN, SCROLL_PERCENT)
            device.waitForIdle()
            content.scroll(Direction.UP, SCROLL_PERCENT)
            device.waitForIdle()
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.adblocker.ytmusic"
        const val ITERATIONS = 5
        const val SCROLLS = 3
        const val SCROLL_PERCENT = 0.8f
        const val MARGIN_DIVISOR = 5
        const val UI_TIMEOUT_MS = 5_000L
    }
}
