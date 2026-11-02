package com.adblocker.ytmusic.macrobenchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Arranque hasta ver la barra inferior: crear el WebView, cargar las listas de bloqueo y montar
 * la UI de Compose. La pagina de YouTube Music depende de la red y no entra en la medida.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCold() = startup(StartupMode.COLD)

    @Test
    fun startupWarm() = startup(StartupMode.WARM)

    private fun startup(mode: StartupMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = mode,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        // El arranque termina cuando la barra nativa esta en pantalla.
        device.wait(Until.hasObject(By.text("Principal")), UI_TIMEOUT_MS)
    }

    private companion object {
        const val TARGET_PACKAGE = "com.adblocker.ytmusic"
        const val ITERATIONS = 10
        const val UI_TIMEOUT_MS = 5_000L
    }
}
