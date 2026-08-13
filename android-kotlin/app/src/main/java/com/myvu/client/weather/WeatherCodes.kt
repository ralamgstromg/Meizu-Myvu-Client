package com.myvu.client.weather

object WeatherCodes {
    private const val SUNNY = "0"
    private const val CLOUDY = "1"
    private const val OVERCAST = "2"
    private const val SHOWER = "3"
    private const val THUNDERSHOWER = "4"
    private const val THUNDER_HAIL = "5"
    private const val LIGHT_RAIN = "7"
    private const val MODERATE_RAIN = "8"
    private const val HEAVY_RAIN = "9"
    private const val STORM = "10"
    private const val SNOW_FLURRY = "13"
    private const val LIGHT_SNOW = "14"
    private const val MODERATE_SNOW = "15"
    private const val HEAVY_SNOW = "16"
    private const val FOG = "18"
    private const val FREEZING_RAIN = "19"

    class Condition internal constructor(
        @JvmField val iconCode: String,
        @JvmField val text: String
    )

    @JvmStatic
    fun of(wmo: Int): Condition {
        return when (wmo) {
            0 -> Condition(SUNNY, "Despejado")
            1 -> Condition(CLOUDY, "Mayormente despejado")
            2 -> Condition(CLOUDY, "Parcialmente nublado")
            3 -> Condition(OVERCAST, "Nublado")
            45, 48 -> Condition(FOG, "Niebla")
            51 -> Condition(LIGHT_RAIN, "Llovizna ligera")
            53 -> Condition(LIGHT_RAIN, "Llovizna")
            55 -> Condition(MODERATE_RAIN, "Llovizna fuerte")
            56, 57 -> Condition(FREEZING_RAIN, "Llovizna helada")
            61 -> Condition(LIGHT_RAIN, "Lluvia ligera")
            63 -> Condition(MODERATE_RAIN, "Lluvia")
            65 -> Condition(HEAVY_RAIN, "Lluvia fuerte")
            66, 67 -> Condition(FREEZING_RAIN, "Lluvia helada")
            71 -> Condition(LIGHT_SNOW, "Nieve ligera")
            73 -> Condition(MODERATE_SNOW, "Nieve")
            75 -> Condition(HEAVY_SNOW, "Nieve fuerte")
            77 -> Condition(LIGHT_SNOW, "Granizo fino")
            80 -> Condition(SHOWER, "Chubascos ligeros")
            81 -> Condition(SHOWER, "Chubascos")
            82 -> Condition(STORM, "Tormenta fuerte")
            85 -> Condition(SNOW_FLURRY, "Chubascos de nieve")
            86 -> Condition(SNOW_FLURRY, "Nieve fuerte")
            95 -> Condition(THUNDERSHOWER, "Tormenta eléctrica")
            96, 99 -> Condition(THUNDER_HAIL, "Tormenta con granizo")
            else -> Condition(CLOUDY, "Nublado")
        }
    }
}
