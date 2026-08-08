package com.myvu.client;

import com.myvu.client.ai.ActionCommand;
import com.myvu.client.ai.ActionPolicy;

import org.junit.Assert;
import org.junit.Test;

public class SecurePrefsTest {

    @Test
    public void testSensitiveActionsRequireConfirmation() {
        Assert.assertTrue(ActionPolicy.isSensitive(ActionCommand.Type.CALL));
        Assert.assertTrue(ActionPolicy.isSensitive(ActionCommand.Type.WHATSAPP));
        Assert.assertTrue(ActionPolicy.isSensitive(ActionCommand.Type.TELEGRAM));
        Assert.assertTrue(ActionPolicy.isSensitive(ActionCommand.Type.SUMMARY));
    }

    @Test
    public void testSafeActionsDoNotRequireConfirmation() {
        Assert.assertFalse(ActionPolicy.isSensitive(ActionCommand.Type.VOLUME));
        Assert.assertFalse(ActionPolicy.isSensitive(ActionCommand.Type.MEDIA_PLAY_PAUSE));
        Assert.assertFalse(ActionPolicy.isSensitive(ActionCommand.Type.SEARCH));
        Assert.assertFalse(ActionPolicy.isSensitive(ActionCommand.Type.NOTE));
    }
}
