package com.myvu.client.health

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.myvu.client.core.LogBus
import com.myvu.client.service.MirrorNotificationListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Service and repository for health and wellness metrics:
 * - Hardware step counter (Sensor.TYPE_STEP_COUNTER & STEP_DETECTOR)
 * - Daily steps & distance / active calories calculation
 * - Stress level tracking & status evaluation (0-100 score)
 * - Heart rate (BPM) & Sleep stats
 * - Automatic sync from wearable companion apps (Samsung Health, Google Fit, Zepp, Garmin, Huawei)
 */
class HealthService(context: Context) : SensorEventListener {

    private val context = context.applicationContext
    private val prefs: SharedPreferences = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val sensorManager = this.context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private var isSensorRegistered = false

    companion object {
        private const val PREFS_NAME = "myvu_health_prefs"
        private const val KEY_LAST_DATE = "last_date"
        private const val KEY_START_OF_DAY_STEPS = "start_of_day_steps"
        private const val KEY_LAST_KNOWN_TOTAL_STEPS = "last_known_total_steps"
        private const val KEY_CACHED_STEPS_TODAY = "cached_steps_today"
        private const val KEY_STRESS_LEVEL = "stress_level"
        private const val KEY_HEART_RATE = "heart_rate"
        private const val KEY_ACTIVE_CALORIES = "active_calories"
        private const val KEY_SLEEP_HOURS = "sleep_hours"
        private const val KEY_STEP_GOAL = "step_goal"

        @Volatile
        private var instance: HealthService? = null

        fun getInstance(context: Context): HealthService {
            return instance ?: synchronized(this) {
                instance ?: HealthService(context).also { instance = it }
            }
        }
    }

    init {
        checkAndResetDailyBaseline()
        registerHardwareSensor()
    }

    fun hasActivityRecognitionPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun registerHardwareSensor() {
        if (sensorManager == null) return
        if (!hasActivityRecognitionPermission()) {
            LogBus.warn("HealthService -> ACTIVITY_RECOGNITION permission not granted. Cannot register hardware step sensors.")
            return
        }
        if (isSensorRegistered) return

        var registered = false
        try {
            val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            if (stepCounter != null) {
                // Batch steps up to 60s to allow phone CPU to remain in deep sleep (low power)
                val ok = sensorManager.registerListener(
                    this,
                    stepCounter,
                    SensorManager.SENSOR_DELAY_NORMAL,
                    60_000_000
                )
                if (ok) {
                    registered = true
                    LogBus.log("HealthService -> Hardware STEP_COUNTER sensor registered successfully (low-power batched)")
                }
            }

            // Only fallback to STEP_DETECTOR if STEP_COUNTER is absent, avoiding duplicate wakeups/interrupts
            if (!registered) {
                val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
                if (stepDetector != null) {
                    val ok = sensorManager.registerListener(
                        this,
                        stepDetector,
                        SensorManager.SENSOR_DELAY_NORMAL,
                        60_000_000
                    )
                    if (ok) {
                        registered = true
                        LogBus.log("HealthService -> Hardware STEP_DETECTOR sensor registered successfully (fallback)")
                    }
                }
            }

            isSensorRegistered = registered
            if (!registered) {
                LogBus.log("HealthService -> No hardware step sensors available on this device, using companion sync")
            }
        } catch (e: Exception) {
            LogBus.warn("HealthService -> Could not register step sensor: ${e.message}")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values[0].toInt()
            onTotalStepsRecorded(totalSteps)
        } else if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            if (event.values[0] == 1.0f) {
                incrementSingleStep()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun checkAndResetDailyBaseline() {
        val today = getTodayDateString()
        val savedDate = prefs.getString(KEY_LAST_DATE, null)
        if (savedDate != today) {
            val lastTotal = prefs.getInt(KEY_LAST_KNOWN_TOTAL_STEPS, -1)
            prefs.edit()
                .putString(KEY_LAST_DATE, today)
                .putInt(KEY_START_OF_DAY_STEPS, lastTotal)
                .putInt(KEY_CACHED_STEPS_TODAY, 0)
                .apply()
            LogBus.log("HealthService -> Reset daily steps baseline for date: $today")
        }
    }

    @Synchronized
    fun onTotalStepsRecorded(totalHardwareSteps: Int) {
        checkAndResetDailyBaseline()
        val startOfDay = prefs.getInt(KEY_START_OF_DAY_STEPS, -1)
        val todaySteps = if (startOfDay >= 0 && totalHardwareSteps >= startOfDay) {
            totalHardwareSteps - startOfDay
        } else {
            // First run or device rebooted
            prefs.edit().putInt(KEY_START_OF_DAY_STEPS, totalHardwareSteps).apply()
            prefs.getInt(KEY_CACHED_STEPS_TODAY, 0)
        }

        val cached = prefs.getInt(KEY_CACHED_STEPS_TODAY, 0)
        val best = maxOf(todaySteps, cached)

        prefs.edit()
            .putInt(KEY_LAST_KNOWN_TOTAL_STEPS, totalHardwareSteps)
            .putInt(KEY_CACHED_STEPS_TODAY, best)
            .apply()
    }

    @Synchronized
    fun incrementSingleStep() {
        checkAndResetDailyBaseline()
        val current = prefs.getInt(KEY_CACHED_STEPS_TODAY, 0) + 1
        prefs.edit().putInt(KEY_CACHED_STEPS_TODAY, current).apply()
    }

    fun syncFromActiveNotifications() {
        try {
            MirrorNotificationListener.syncActiveHealth(context)
        } catch (e: Exception) {
            LogBus.trace("HealthService -> syncFromActiveNotifications error: ${e.message}")
        }
    }

    fun getTodaySteps(): Int {
        checkAndResetDailyBaseline()
        syncFromActiveNotifications()
        return prefs.getInt(KEY_CACHED_STEPS_TODAY, 0)
    }

    fun getStepGoal(): Int = prefs.getInt(KEY_STEP_GOAL, 10000)

    fun setStepGoal(goal: Int) {
        if (goal > 0) prefs.edit().putInt(KEY_STEP_GOAL, goal).apply()
    }

    fun getStressLevel(): Int {
        val level = prefs.getInt(KEY_STRESS_LEVEL, -1)
        if (level in 0..100) return level
        // Default baseline normal for user (mild / relaxed)
        return 28
    }

    fun getStressStatus(score: Int): Pair<String, String> {
        return when (score) {
            in 0..29 -> Pair("Bajo / Relajado", "Te encuentras en un estado relajado y estable.")
            in 30..59 -> Pair("Moderado / Normal", "Nivel de actividad y alerta en equilibrio.")
            in 60..79 -> Pair("Medio-Alto", "Tensión perceptible. Se aconseja tomar una pausa.")
            else -> Pair("Elevado", "Nivel alto de estrés. Respira profundo durante 2 minutos.")
        }
    }

    fun getHeartRate(): Int {
        val hr = prefs.getInt(KEY_HEART_RATE, -1)
        return if (hr in 40..200) hr else 72
    }

    fun getActiveCalories(): Int {
        val cal = prefs.getInt(KEY_ACTIVE_CALORIES, -1)
        if (cal > 0) return cal
        // Estimation based on steps: ~0.04 kcal per step
        return (getTodaySteps() * 0.04).roundToInt()
    }

    fun getDistanceKm(): Double {
        // Average stride: ~0.75m -> 0.00075 km per step
        val km = getTodaySteps() * 0.00075
        return (km * 100.0).roundToInt() / 100.0
    }

    fun getSleepHours(): Double {
        val sleep = prefs.getFloat(KEY_SLEEP_HOURS, -1.0f)
        return if (sleep > 0f) sleep.toDouble() else 7.5
    }

    // --- Summaries ---

    fun getStepsSummary(): String {
        val steps = getTodaySteps()
        val goal = getStepGoal()
        val percent = if (goal > 0) ((steps.toDouble() / goal) * 100).roundToInt() else 0
        val km = getDistanceKm()
        val kcal = getActiveCalories()

        if (steps == 0 && !hasActivityRecognitionPermission()) {
            return "Llevas 0 pasos hoy. Por favor otorga el permiso de Actividad Física en los Ajustes del móvil para activar el podómetro."
        }

        return "Llevas $steps pasos hoy (aprox. $km km y $kcal kcal). Progreso: $percent% de tu meta diaria ($goal pasos)."
    }

    fun getStressSummary(): String {
        val score = getStressLevel()
        val (label, advice) = getStressStatus(score)
        return "Nivel de estrés actual: $score/100 ($label). $advice"
    }

    fun getHeartRateSummary(): String {
        val hr = getHeartRate()
        val status = when (hr) {
            in 50..85 -> "Rango normal en reposo"
            in 86..115 -> "Ritmo activo moderado"
            else -> "Ritmo elevado"
        }
        return "Frecuencia cardíaca: $hr bpm ($status)."
    }

    fun getFullHealthSummary(): String {
        val steps = getTodaySteps()
        val goal = getStepGoal()
        val percent = if (goal > 0) ((steps.toDouble() / goal) * 100).roundToInt() else 0
        val km = getDistanceKm()
        val kcal = getActiveCalories()
        val stress = getStressLevel()
        val (stressLabel, _) = getStressStatus(stress)
        val hr = getHeartRate()
        val sleep = getSleepHours()

        val stepsNotice = if (steps == 0 && !hasActivityRecognitionPermission()) {
            " (Permiso de Actividad Física requerido)"
        } else ""

        return "Resumen de Salud y Actividad (Hoy): Pasos: $steps$stepsNotice / $goal ($percent% | $km km). Calorías activas: $kcal kcal. Estrés: $stress/100 ($stressLabel). Ritmo cardíaco: $hr bpm. Sueño: ${sleep}h."
    }

    // --- Wearable Sync Parsing ---

    fun recordMetrics(steps: Int? = null, stress: Int? = null, heartRate: Int? = null, calories: Int? = null, sleepHours: Double? = null) {
        checkAndResetDailyBaseline()
        val editor = prefs.edit()
        steps?.let {
            if (it > 0) editor.putInt(KEY_CACHED_STEPS_TODAY, it)
        }
        stress?.let {
            if (it in 0..100) editor.putInt(KEY_STRESS_LEVEL, it)
        }
        heartRate?.let {
            if (it in 30..220) editor.putInt(KEY_HEART_RATE, it)
        }
        calories?.let {
            if (it > 0) editor.putInt(KEY_ACTIVE_CALORIES, it)
        }
        sleepHours?.let {
            if (it > 0.0) editor.putFloat(KEY_SLEEP_HOURS, it.toFloat())
        }
        editor.apply()
    }

    /**
     * Inspects notifications from health & wearable apps (Samsung Health, Google Fit, Zepp, Garmin, Huawei, Xiaomi, etc.)
     * and auto-extracts reported metrics.
     */
    fun parseNotificationForHealthMetrics(packageName: String, title: String?, text: String?) {
        val combined = "${title ?: ""} ${text ?: ""}".lowercase()
        val isHealthApp = packageName.contains("health", ignoreCase = true) ||
                packageName.contains("fit", ignoreCase = true) ||
                packageName.contains("wear", ignoreCase = true) ||
                packageName.contains("shealth", ignoreCase = true) ||
                packageName.contains("garmin", ignoreCase = true) ||
                packageName.contains("huami", ignoreCase = true) ||
                packageName.contains("zepp", ignoreCase = true) ||
                packageName.contains("strava", ignoreCase = true) ||
                packageName.contains("polar", ignoreCase = true) ||
                packageName.contains("suunto", ignoreCase = true) ||
                packageName.contains("withings", ignoreCase = true) ||
                packageName.contains("fitbit", ignoreCase = true) ||
                packageName.contains("heytap", ignoreCase = true) ||
                packageName.contains("oneplus", ignoreCase = true) ||
                packageName.contains("honor", ignoreCase = true)

        if (!isHealthApp && !combined.contains("pasos") && !combined.contains("steps") && !combined.contains("estrés") && !combined.contains("estres")) {
            return
        }

        try {
            // 1. Steps:
            var foundSteps: Int? = null

            // Case A: number followed by steps ("6.520 pasos", "6,520 steps", "6520 de 10000 pasos")
            val stepsAfter = Regex("([0-9]{1,3}([.,\\s][0-9]{3})+|[0-9]{1,6})\\s*(?:de\\s*[0-9.,\\s]+\\s*)?(?:pasos|steps)\\b").find(combined)
            if (stepsAfter != null) {
                val numStr = stepsAfter.groupValues[1].replace(Regex("[^0-9]"), "")
                foundSteps = numStr.toIntOrNull()
            }

            // Case B: word pasos/steps before number ("Pasos: 6.520", "Pasos hoy: 6520", "Steps: 6,520")
            if (foundSteps == null) {
                val stepsBefore = Regex("(?:pasos|steps)(?:\\s+hoy|\\s+de\\s+hoy|\\s+totales)?\\D{1,6}([0-9]{1,3}([.,\\s][0-9]{3})+|[0-9]{1,6})\\b").find(combined)
                if (stepsBefore != null) {
                    val numStr = stepsBefore.groupValues[1].replace(Regex("[^0-9]"), "")
                    foundSteps = numStr.toIntOrNull()
                }
            }

            // Case C: ratio in health app ("6450 / 10000")
            if (foundSteps == null && isHealthApp) {
                val ratioMatch = Regex("([0-9]{1,3}([.,\\s][0-9]{3})+|[0-9]{2,6})\\s*/\\s*([0-9]{1,3}([.,\\s][0-9]{3})+|[0-9]{2,6})").find(combined)
                if (ratioMatch != null) {
                    val numStr = ratioMatch.groupValues[1].replace(Regex("[^0-9]"), "")
                    foundSteps = numStr.toIntOrNull()
                }
            }

            if (foundSteps != null && foundSteps > 0) {
                recordMetrics(steps = foundSteps)
                LogBus.log("HealthService -> Synced $foundSteps steps from notification ($packageName)")
            }

            // 2. Stress: "estrés: 28" or "28 estrés" or "stress: 32"
            val stressMatch = Regex("(?:estres|estrés|stress)\\D{1,6}([0-9]{1,2}|100)\\b").find(combined)
                ?: Regex("([0-9]{1,2}|100)\\s*(?:/\\s*100)?\\s*(?:estres|estrés|stress)\\b").find(combined)
            if (stressMatch != null) {
                val score = (stressMatch.groupValues.getOrNull(1))?.toIntOrNull()
                if (score != null && score in 0..100) {
                    recordMetrics(stress = score)
                    LogBus.log("HealthService -> Synced stress level $score from notification ($packageName)")
                }
            }

            // 3. Heart rate: "72 bpm" or "72 ppm" or "pulso: 72"
            val hrMatch = Regex("([0-9]{2,3})\\s*(?:bpm|ppm|lpm)\\b").find(combined)
                ?: Regex("(?:pulso|frecuencia|ritmo)\\D{1,6}([0-9]{2,3})\\b").find(combined)
            if (hrMatch != null) {
                hrMatch.groupValues[1].toIntOrNull()?.let { hr ->
                    if (hr in 40..220) {
                        recordMetrics(heartRate = hr)
                        LogBus.log("HealthService -> Synced heart rate $hr bpm from notification ($packageName)")
                    }
                }
            }

            // 4. Calories: "450 kcal" or "450 calorías" or "Calorías: 450"
            val calMatch = Regex("([0-9]{2,5})\\s*(?:kcal|calor[ií]as)\\b").find(combined)
                ?: Regex("(?:calor[ií]as|kcal)\\D{1,6}([0-9]{2,5})\\b").find(combined)
            if (calMatch != null) {
                calMatch.groupValues[1].toIntOrNull()?.let { cal ->
                    if (cal in 10..15000) {
                        recordMetrics(calories = cal)
                        LogBus.log("HealthService -> Synced $cal kcal from notification ($packageName)")
                    }
                }
            }

            // 5. Sleep: "7.5h" or "7 horas de sueño" or "sueño: 7h 30m"
            val sleepMatch = Regex("(?:sue[ñn]o|sleep)\\D{1,6}([0-9]+(?:\\.[0-9]+)?)\\s*(?:h|horas)?\\b").find(combined)
                ?: Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(?:h|horas)\\s*(?:de\\s*sue[ñn]o)?\\b").find(combined)
            if (sleepMatch != null) {
                sleepMatch.groupValues[1].toDoubleOrNull()?.let { hours ->
                    if (hours in 1.0..18.0) {
                        recordMetrics(sleepHours = hours)
                        LogBus.log("HealthService -> Synced $hours sleep hours from notification ($packageName)")
                    }
                }
            }
        } catch (e: Exception) {
            LogBus.warn("HealthService -> Error parsing health notification: ${e.message}")
        }
    }
}
