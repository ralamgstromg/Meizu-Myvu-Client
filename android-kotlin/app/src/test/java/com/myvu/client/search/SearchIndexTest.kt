package com.myvu.client.search

import com.myvu.client.data.ChatMessage
import com.myvu.client.database.AppDatabase
import com.myvu.client.database.LocalDatabase
import com.myvu.client.database.TodoRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchIndexTest {

    private val ctx get() = RuntimeEnvironment.getApplication()

    private fun note(title: String, body: String, updated: Long = 1) {
        LocalDatabase.getInstance(ctx).writableDatabase.execSQL(
            "INSERT INTO notes (title, body, created_at, updated_at) VALUES (?, ?, 1, ?)", arrayOf<Any>(title, body, updated)
        )
    }

    @Test
    fun findsNotesIgnoringAccentsAndRanksTitleMatchesFirst() = runBlocking {
        note("Compras", "Hablar de la reunión con proveedores el lunes", 2)
        note("Reunión de presupuesto", "Revisar cifras del trimestre", 3)
        note("Viaje", "Reservar hotel en Cartagena", 4)

        val hits = SearchIndex.search(ctx, "reunion")

        assertEquals(listOf("Reunión de presupuesto", "Compras"), hits.map { it.title })
        assertTrue(hits[1].snippet.contains("reunión con proveedores"))
    }

    @Test
    fun allTermsMustMatchWithPrefixes() = runBlocking {
        note("Hotel", "Reservar hotel en Cartagena")
        note("Hotel Medellín", "Comparar precios")

        assertEquals(listOf("Hotel"), SearchIndex.search(ctx, "hotel carta").map { it.title })
    }

    @Test
    fun indexRebuildsWhenContentChangesAndCoversTasksAndChat() = runBlocking {
        assertTrue(SearchIndex.search(ctx, "factura").isEmpty())

        TodoRepository(ctx).createTodo("Pagar factura del agua")
        AppDatabase.getInstance(ctx).chatDao()
            .insertMessage(ChatMessage(sessionId = "s9", direction = "USER", content = "¿Cuándo vence la factura?", mediaType = "TEXT"))

        val kinds = SearchIndex.search(ctx, "factura").map { it.kind }.toSet()
        assertEquals(setOf(SearchIndex.Kind.TODO, SearchIndex.Kind.CHAT), kinds)
    }
}
