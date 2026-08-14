package com.myvu.client.ai

/** Selects which Gemini Android backend may process a request. */
enum class GeminiFallbackPolicy(
    @JvmField val id: String,
    @JvmField val allowsApiFallback: Boolean
) {
    NANO_THEN_API("nano_then_api", true),
    NANO_ONLY("nano_only", false),
    API_ONLY("api_only", false);

    companion object {
        @JvmStatic
        fun fromId(id: String?): GeminiFallbackPolicy {
            return entries.firstOrNull { it.id == id } ?: NANO_THEN_API
        }
    }
}
