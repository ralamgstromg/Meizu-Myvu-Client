package com.myvu.client.nav

object IcMap {
    const val DEFAULT_IC: Int = 1

    private val BY_MODIFIER: Map<String, Int> = mapOf(
        "straight" to 1,
        "right" to 2,
        "left" to 3,
        "slight right" to 4,
        "slight left" to 5,
        "sharp right" to 6,
        "sharp left" to 7,
        "uturn" to 8
    )

    private val BY_TYPE: Map<String, Int> = mapOf(
        "roundabout" to 9,
        "rotary" to 9,
        "roundabout turn" to 9,
        "merge" to 10,
        "on ramp" to 11,
        "off ramp" to 12,
        "fork" to 13,
        "end of road" to 14,
        "arrive" to 15,
        "depart" to 1
    )

    @JvmStatic
    fun forManeuver(type: String?, modifier: String?): Int {
        val byType = if (type != null) BY_TYPE[type] else null
        if (byType != null) return byType
        val byModifier = if (modifier != null) BY_MODIFIER[modifier] else null
        return byModifier ?: DEFAULT_IC
    }
}
