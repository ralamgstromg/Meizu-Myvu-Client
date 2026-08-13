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
                .put("stream", false)
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
        String clean = response == null ? "" : response.trim();
        if (clean.startsWith("data:")) {
            StringBuilder sb = new StringBuilder();
            for (String line : clean.split("\n")) {
                line = line.trim();
                if (line.startsWith("data:")) {
                    String jsonStr = line.substring(5).trim();
                    if ("[DONE]".equalsIgnoreCase(jsonStr)) continue;
                    try {
                        JSONObject json = new JSONObject(jsonStr);
                        JSONArray choices = json.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            JSONObject choice = choices.getJSONObject(0);
                            JSONObject delta = choice.optJSONObject("delta");
                            if (delta != null && delta.has("content")) {
                                sb.append(delta.optString("content", ""));
                            } else {
                                JSONObject msg = choice.optJSONObject("message");
                                if (msg != null && msg.has("content")) {
                                    sb.append(msg.optString("content", ""));
                                }
                            }
                        }
                    } catch (JSONException ignored) {}
                }
            }
            if (sb.length() > 0) return sb.toString();
        }

        JSONObject root = new JSONObject(clean);
        JSONArray choices = root.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            Object choiceObj = choices.get(0);
            if (choiceObj instanceof JSONObject) {
                JSONObject choice = (JSONObject) choiceObj;
                Object msgObj = choice.opt("message");
                if (msgObj instanceof JSONObject) {
                    JSONObject msg = (JSONObject) msgObj;
                    return msg.isNull("content") ? "" : msg.optString("content");
                } else if (msgObj instanceof String) {
                    return (String) msgObj;
                }
                JSONObject delta = choice.optJSONObject("delta");
                if (delta != null && delta.has("content")) {
                    return delta.optString("content");
                }
                if (choice.has("text")) {
                    return choice.optString("text");
                }
            } else if (choiceObj instanceof String) {
                return (String) choiceObj;
            }
        }

        if (root.has("response")) {
            return root.optString("response");
        }
        if (root.has("data")) {
            Object dataObj = root.get("data");
            if (dataObj instanceof String) {
                return (String) dataObj;
            } else if (dataObj instanceof JSONObject) {
                JSONObject dataJson = (JSONObject) dataObj;
                if (dataJson.has("content")) return dataJson.optString("content");
            }
        }

        throw new JSONException("Unrecognized response format: " + clean);
    }
}
