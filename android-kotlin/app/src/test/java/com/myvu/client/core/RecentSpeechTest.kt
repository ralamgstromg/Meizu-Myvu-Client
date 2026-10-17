package com.myvu.client.core

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecentSpeechTest {

    private var now = 0L

    @Before
    fun setUp() {
        now = 1_000_000L
        RecentSpeech.clock = { now }
    }

    @After
    fun tearDown() {
        RecentSpeech.clock = { System.currentTimeMillis() }
    }

    @Test
    fun matchesSameTextIgnoringPunctuationAndCase() {
        RecentSpeech.remember("Hola, ¿cómo estás?")

        assertTrue(RecentSpeech.wasSpokenRecently("hola cómo estás"))
    }

    @Test
    fun matchesLongerTextThatContainsASpokenAnswer() {
        RecentSpeech.remember("El clima en Barranquilla hoy es soleado con 32 grados y brisa del mar")

        assertTrue(RecentSpeech.wasSpokenRecently("El clima en Barranquilla hoy es soleado con 32 grados y brisa del mar."))
    }

    @Test
    fun shortTextDoesNotMatchInsideLongerSpeech() {
        RecentSpeech.remember("Claro, con gusto te ayudo con eso ahora mismo")

        assertFalse(RecentSpeech.wasSpokenRecently("ok"))
    }

    @Test
    fun forgetsTextAfterTwoMinutes() {
        RecentSpeech.remember("Reunión a las 15:30 con el equipo de diseño")
        now += 121_000L

        assertFalse(RecentSpeech.wasSpokenRecently("Reunión a las 15:30 con el equipo de diseño"))
    }

    @Test
    fun blankTextIsNeverRememberedOrMatched() {
        RecentSpeech.remember("   ")

        assertFalse(RecentSpeech.wasSpokenRecently(" "))
    }
}
