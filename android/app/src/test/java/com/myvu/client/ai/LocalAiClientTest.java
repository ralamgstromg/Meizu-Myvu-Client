package com.myvu.client.ai;

import org.junit.Test;
import static org.junit.Assert.*;

public class LocalAiClientTest {

    private final LocalAiClient client = new LocalAiClient(
            "https://omniroute.eticosweb.net:20128/v1/chat/completions",
            "dummy_key",
            "gpt-4o",
            "system"
    );

    @Test
    public void testStandardOpenAiResponse() throws Exception {
        String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Hola humano\"}}]}";
        assertEquals("Hola humano", client.extractText(json));
    }

    @Test
    public void testStringMessageResponse() throws Exception {
        String json = "{\"choices\":[{\"message\":\"data\"}]}";
        assertEquals("data", client.extractText(json));
    }

    @Test
    public void testTopLevelDataResponse() throws Exception {
        String json = "{\"data\":\"Respuesta en data\"}";
        assertEquals("Respuesta en data", client.extractText(json));
    }

    @Test
    public void testSseStreamResponse() throws Exception {
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"Hola \"}}]}\n"
                   + "data: {\"choices\":[{\"delta\":{\"content\":\"mundo\"}}]}\n"
                   + "data: [DONE]";
        assertEquals("Hola mundo", client.extractText(sse));
    }
}
