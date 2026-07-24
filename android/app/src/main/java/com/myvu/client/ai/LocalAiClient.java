package com.myvu.client.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;

/** Answers through a user-configured OpenAI-compatible Chat Completions API. */
public final class LocalAiClient extends AiHttpClient {
    private static final int MAX_TOKENS = 1024;

    private final String configuredEndpoint;

    public LocalAiClient(String endpoint, String apiKey, String model, String systemPrompt) {
        this(endpoint, apiKey, model, systemPrompt, false);
    }

    public LocalAiClient(String endpoint, String apiKey, String model, String systemPrompt, boolean ignoreSsl) {
        super(AiProvider.LOCAL, apiKey, model, systemPrompt, ignoreSsl);
        configuredEndpoint = endpoint == null ? "" : endpoint.trim();
    }

    @Override
    public boolean isConfigured() {
        return !configuredEndpoint.isEmpty() && !model.isEmpty();
    }

    @Override
    protected String endpoint() {
        return configuredEndpoint;
    }

    @Override
    protected void authorize(HttpURLConnection conn) {
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            String key = apiKey.trim();
            conn.setRequestProperty("authorization", "Bearer " + key);
            conn.setRequestProperty("api-key", key);
            conn.setRequestProperty("x-api-key", key);
        }
    }

    @Override
    protected String buildBody(String question) throws JSONException {
        return new JSONObject()
                .put("model", model)
                .put("max_tokens", MAX_TOKENS)
                .put("messages", new JSONArray()
                        .put(new JSONObject()
                                .put("role", "system")
                                .put("content", systemPrompt))
                        .put(new JSONObject()
                                .put("role", "user")
                                .put("content", question)))
                .toString();
    }

    @Override
    protected String extractText(String response) throws JSONException {
        JSONObject message = new JSONObject(response)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message");
        return message.isNull("content") ? "" : message.optString("content");
    }
}
