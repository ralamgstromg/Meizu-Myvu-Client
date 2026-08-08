package com.myvu.client.app.feature;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Splits and queues long text responses into readable sentence-based HUD frames.
 */
public class HudMessageQueue {
    private static final int MAX_HUD_FRAME_LEN = 80;
    private final Queue<String> pendingFrames = new ArrayDeque<>();

    public void enqueue(String fullText) {
        pendingFrames.clear();
        if (fullText == null || fullText.trim().isEmpty()) return;

        List<String> sentences = splitIntoSentences(fullText.trim());
        for (String sentence : sentences) {
            if (sentence.length() <= MAX_HUD_FRAME_LEN) {
                pendingFrames.add(sentence);
            } else {
                // Chunk long sentences by word boundaries
                String[] words = sentence.split("\\s+");
                StringBuilder sb = new StringBuilder();
                for (String word : words) {
                    if (sb.length() + word.length() + 1 > MAX_HUD_FRAME_LEN) {
                        pendingFrames.add(sb.toString().trim());
                        sb.setLength(0);
                    }
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(word);
                }
                if (sb.length() > 0) {
                    pendingFrames.add(sb.toString().trim());
                }
            }
        }
    }

    public boolean hasNext() {
        return !pendingFrames.isEmpty();
    }

    public String pollNext() {
        return pendingFrames.poll();
    }

    public void clear() {
        pendingFrames.clear();
    }

    private static List<String> splitIntoSentences(String text) {
        List<String> result = new ArrayList<>();
        String[] parts = text.split("(?<=[\\.\\?\\!\\n])\\s+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
