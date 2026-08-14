package com.myvu.client.ai

enum class AiResponseMode(val id: String) {
    VOICE_ONLY("voice_only"),
    VISUAL_ONLY("visual_only"),
    VOICE_AND_VISUAL("voice_and_visual");

    companion object {
        fun fromId(id: String?): AiResponseMode =
            entries.firstOrNull { it.id == id } ?: VOICE_AND_VISUAL
    }
}
