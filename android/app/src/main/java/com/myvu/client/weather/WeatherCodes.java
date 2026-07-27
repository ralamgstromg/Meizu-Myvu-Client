package com.myvu.client.weather;

/**
 * Translates Open-Meteo's WMO weather codes into the glasses' own icon code and
 * a human-readable condition string.
 *
 * HOW MUCH OF THIS IS VERIFIED: the glasses' icon table lives in their launcher
 * (com.upuphone.star.launcher), NOT in the phone app, so it could not be read
 * out of the decompiled APK. Only three values are directly attested, from the
 * official app's own mock payload:
 *
 *     "1" -> 多云 (cloudy)   "2" -> 阴 (overcast)   "7" -> 小雨 (light rain)
 *
 * Those three land exactly where the standard Chinese/CMA weather-icon numbering
 * puts them, which is strong evidence the glasses use that well-known table --
 * so the rest below follows it. Treat any code other than 1/2/7 as a very good
 * guess rather than a fact. If an icon ever looks wrong on the lens, this is the
 * single place to correct it: only the MEIZU_* constants need to change.
 */
public final class WeatherCodes {
    private WeatherCodes() {}

    // The standard table. 1, 2 and 7 are verified; the others follow its scheme.
    private static final String SUNNY          = "0";
    private static final String CLOUDY         = "1";  // verified
    private static final String OVERCAST       = "2";  // verified
    private static final String SHOWER         = "3";
    private static final String THUNDERSHOWER  = "4";
    private static final String THUNDER_HAIL   = "5";
    private static final String LIGHT_RAIN     = "7";  // verified
    private static final String MODERATE_RAIN  = "8";
    private static final String HEAVY_RAIN     = "9";
    private static final String STORM          = "10";
    private static final String SNOW_FLURRY    = "13";
    private static final String LIGHT_SNOW     = "14";
    private static final String MODERATE_SNOW  = "15";
    private static final String HEAVY_SNOW     = "16";
    private static final String FOG            = "18";
    private static final String FREEZING_RAIN  = "19";

    /** An icon code plus the text shown beside it. */
    public static final class Condition {
        public final String iconCode;
        public final String text;

        Condition(String iconCode, String text) {
            this.iconCode = iconCode;
            this.text = text;
        }
    }

    /**
     * Maps a WMO code (what Open-Meteo returns) to what the glasses expect.
     * Unknown codes fall back to cloudy, which is visually neutral -- better
     * than a blank or a wrong-looking sun.
     */
    public static Condition of(int wmo) {
        switch (wmo) {
            case 0:  return new Condition(SUNNY, "Despejado");
            case 1:  return new Condition(CLOUDY, "Mayormente despejado");
            case 2:  return new Condition(CLOUDY, "Parcialmente nublado");
            case 3:  return new Condition(OVERCAST, "Nublado");

            case 45:
            case 48: return new Condition(FOG, "Niebla");

            case 51: return new Condition(LIGHT_RAIN, "Llovizna ligera");
            case 53: return new Condition(LIGHT_RAIN, "Llovizna");
            case 55: return new Condition(MODERATE_RAIN, "Llovizna fuerte");

            case 56:
            case 57: return new Condition(FREEZING_RAIN, "Llovizna helada");

            case 61: return new Condition(LIGHT_RAIN, "Lluvia ligera");
            case 63: return new Condition(MODERATE_RAIN, "Lluvia");
            case 65: return new Condition(HEAVY_RAIN, "Lluvia fuerte");

            case 66:
            case 67: return new Condition(FREEZING_RAIN, "Lluvia helada");

            case 71: return new Condition(LIGHT_SNOW, "Nieve ligera");
            case 73: return new Condition(MODERATE_SNOW, "Nieve");
            case 75: return new Condition(HEAVY_SNOW, "Nieve fuerte");
            case 77: return new Condition(LIGHT_SNOW, "Granizo fino");

            case 80: return new Condition(SHOWER, "Chubascos ligeros");
            case 81: return new Condition(SHOWER, "Chubascos");
            case 82: return new Condition(STORM, "Tormenta fuerte");

            case 85: return new Condition(SNOW_FLURRY, "Chubascos de nieve");
            case 86: return new Condition(SNOW_FLURRY, "Nieve fuerte");

            case 95: return new Condition(THUNDERSHOWER, "Tormenta eléctrica");
            case 96:
            case 99: return new Condition(THUNDER_HAIL, "Tormenta con granizo");

            default: return new Condition(CLOUDY, "Nublado");
        }
    }
}
