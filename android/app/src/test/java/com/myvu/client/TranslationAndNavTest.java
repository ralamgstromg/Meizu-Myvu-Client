package com.myvu.client;

import com.myvu.client.ai.TranslationSession;
import com.myvu.client.nav.Route;
import com.myvu.client.nav.RouteCache;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class TranslationAndNavTest {

    @Test
    public void testTranslationSessionLifecycle() {
        TranslationSession session = new TranslationSession(null);
        Assert.assertFalse(session.isActive());

        session.start("en");
        Assert.assertTrue(session.isActive());

        final AtomicInteger receivedFrames = new AtomicInteger(0);
        session.setListener(new TranslationSession.TranslationListener() {
            @Override
            public void onTranslationFrame(String translatedText) {
                receivedFrames.incrementAndGet();
            }
        });

        session.processAudioChunk(new byte[0], "Hello world. Testing translation.");
        Assert.assertEquals(2, receivedFrames.get());

        session.stop();
        Assert.assertFalse(session.isActive());
    }

    @Test
    public void testRouteCache() {
        RouteCache cache = new RouteCache();
        String key = cache.buildKey(4.6097, -74.0817, 4.6500, -74.0500);

        Route dummyRoute = new Route(new ArrayList<>(), 1200, 300, new ArrayList<>());
        cache.put(key, dummyRoute);

        Route fetched = cache.get(key);
        Assert.assertNotNull(fetched);
        Assert.assertEquals(1200, fetched.totalDistanceM);
    }
}
