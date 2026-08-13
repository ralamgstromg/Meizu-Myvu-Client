package com.myvu.client.ai

enum class TtsProvider(
    @JvmField val id: String,
    @JvmField val label: String
) {
    SYSTEM("system", "Device"),
    HTTP("http", "HTTP API");

    val displayName: String get() = label

    companion object {
        @JvmStatic
        fun fromId(id: String?): TtsProvider {
            for (provider in values()) {
                if (provider.id == id) return provider
            }
            return SYSTEM
        }
    }
}
