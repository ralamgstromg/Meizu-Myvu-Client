package com.myvu.client;

import com.myvu.client.app.feature.TouchGestureManager;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TouchGestureManagerTest {

    private boolean aiExecuted;
    private boolean weatherExecuted;
    private boolean mirrorExecuted;

    @Before
    public void setUp() {
        aiExecuted = false;
        weatherExecuted = false;
        mirrorExecuted = false;
    }

    @Test
    public void testTouchGestureRouting() {
        TouchGestureManager.ActionExecutor executor = new TouchGestureManager.ActionExecutor() {
            @Override
            public void executeAiAssistant(int code) {
                aiExecuted = true;
            }

            @Override
            public void executeWeatherSync() {
                weatherExecuted = true;
            }

            @Override
            public void executeToggleMirror() {
                mirrorExecuted = true;
            }

            @Override
            public void executeMediaPlayPause() {
            }
        };

        // When context is null (pure JVM unit test), handleTrigger defaults to AI Voice
        TouchGestureManager.handleTrigger(null, 3, executor);
        assertTrue(aiExecuted);
    }
}
