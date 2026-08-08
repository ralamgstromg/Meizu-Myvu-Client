package com.myvu.client.ai;

/**
 * Represents the current status lifecycle of an AI voice interaction.
 */
public enum ConversationState {
    IDLE,
    LISTENING,
    CALIBRATING_NOISE,
    CAPTURING,
    ENDING_UTTERANCE,
    TRANSCRIBING,
    THINKING,
    SHOWING_RESPONSE,
    SPEAKING,
    WAITING_CONFIRMATION,
    CLOSING,
    ERROR
}
