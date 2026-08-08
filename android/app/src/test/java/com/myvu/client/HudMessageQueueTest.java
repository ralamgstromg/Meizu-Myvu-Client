package com.myvu.client;

import com.myvu.client.app.feature.HudMessageQueue;

import org.junit.Assert;
import org.junit.Test;

public class HudMessageQueueTest {

    @Test
    public void testSentenceSplitting() {
        HudMessageQueue queue = new HudMessageQueue();
        queue.enqueue("Hola. Esta es una prueba de HUD! ¿Funciona bien?");

        Assert.assertTrue(queue.hasNext());
        Assert.assertEquals("Hola.", queue.pollNext());
        Assert.assertEquals("Esta es una prueba de HUD!", queue.pollNext());
        Assert.assertEquals("¿Funciona bien?", queue.pollNext());
        Assert.assertFalse(queue.hasNext());
    }

    @Test
    public void testLongSentenceChunking() {
        HudMessageQueue queue = new HudMessageQueue();
        String longText = "Esta es una frase extremadamente larga diseñada para verificar que la cola de mensajes del HUD divida correctamente el texto en bloques de menos de ochenta caracteres sin cortar palabras a la mitad.";
        queue.enqueue(longText);

        while (queue.hasNext()) {
            String chunk = queue.pollNext();
            Assert.assertTrue(chunk.length() <= 80);
        }
    }
}
