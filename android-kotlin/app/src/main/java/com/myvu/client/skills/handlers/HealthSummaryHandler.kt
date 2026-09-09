package com.myvu.client.skills.handlers

import android.content.Context
import com.myvu.client.health.HealthService
import com.myvu.client.skills.SkillHandler
import com.myvu.client.skills.SkillResult
import org.json.JSONObject

/**
 * Skill Handler for Health & Wellness:
 * Queries and summarizes steps, stress level, heart rate, or full fitness summary.
 */
class HealthSummaryHandler : SkillHandler {

    override suspend fun execute(context: Context, args: JSONObject): SkillResult {
        val metric = args.optString("metric", "all").lowercase().trim()
        val healthService = HealthService.getInstance(context)

        val summary = when {
            metric.contains("step") || metric.contains("paso") -> healthService.getStepsSummary()
            metric.contains("stres") || metric.contains("estres") || metric.contains("tensión") -> healthService.getStressSummary()
            metric.contains("heart") || metric.contains("card") || metric.contains("pulso") -> healthService.getHeartRateSummary()
            else -> healthService.getFullHealthSummary()
        }

        return SkillResult(true, summary)
    }
}
