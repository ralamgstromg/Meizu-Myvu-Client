package com.myvu.client.health

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HealthServiceTest {

    private lateinit var context: Context
    private lateinit var healthService: HealthService

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        healthService = HealthService.getInstance(context)
    }

    @Test
    fun testStepsCalculationAndSummary() {
        healthService.recordMetrics(steps = 5500)
        assertEquals(5500, healthService.getTodaySteps())

        val summary = healthService.getStepsSummary()
        assertTrue(summary.contains("5500"))
        assertFalse("Should not contain markdown asterisks", summary.contains("*"))
        assertFalse("Should not contain thousands separator commas", summary.contains(","))
        assertTrue(summary.contains("km"))
        assertTrue(summary.contains("kcal"))
    }

    @Test
    fun testStressCategorization() {
        healthService.recordMetrics(stress = 22)
        val (labelLow, _) = healthService.getStressStatus(22)
        assertTrue(labelLow.contains("Bajo", ignoreCase = true))

        val (labelMod, _) = healthService.getStressStatus(45)
        assertTrue(labelMod.contains("Moderado", ignoreCase = true))

        val (labelHigh, _) = healthService.getStressStatus(85)
        assertTrue(labelHigh.contains("Elevado", ignoreCase = true))

        val summary = healthService.getStressSummary()
        assertTrue(summary.contains("22/100"))
        assertFalse("Should not contain markdown asterisks", summary.contains("*"))
    }

    @Test
    fun testFullHealthSummary() {
        healthService.recordMetrics(steps = 7200, stress = 35, heartRate = 68, calories = 310)

        val full = healthService.getFullHealthSummary()
        assertTrue(full.contains("Resumen de Salud"))
        assertTrue(full.contains("7200"))
        assertFalse("Should not contain markdown asterisks", full.contains("*"))
        assertTrue(full.contains("35/100"))
        assertTrue(full.contains("68 bpm"))
    }

    @Test
    fun testNotificationMetricsParsing() {
        // Notification from Samsung Health
        healthService.parseNotificationForHealthMetrics(
            packageName = "com.samsung.android.app.shealth",
            title = "Samsung Health",
            text = "¡Excelente! Llevas 8.450 pasos hoy y tu nivel de estrés es 24."
        )

        assertEquals(8450, healthService.getTodaySteps())
        assertEquals(24, healthService.getStressLevel())

        // Notification with heart rate
        healthService.parseNotificationForHealthMetrics(
            packageName = "com.google.android.apps.fitness",
            title = "Google Fit",
            text = "Frecuencia cardíaca en reposo: 65 bpm"
        )
        assertEquals(65, healthService.getHeartRate())
    }
}
