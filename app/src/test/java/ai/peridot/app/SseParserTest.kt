package ai.peridot.app

import ai.peridot.app.data.SseEvent
import ai.peridot.app.data.SseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseParserTest {
    private fun parse(text: String): List<SseEvent> {
        val parser = SseParser()
        val events = text.split("\n").mapNotNull { parser.feed(it) }.toMutableList()
        parser.finish()?.let { events.add(it) }
        return events
    }

    @Test
    fun readsHonoEvents() {
        val events = parse("event: message\ndata: {\"chunk\":\"Hi\"}\n\nevent: done\ndata: {\"done\":true}\n\n")
        assertEquals(listOf(SseEvent("message", "{\"chunk\":\"Hi\"}"), SseEvent("done", "{\"done\":true}")), events)
    }

    @Test
    fun defaultsToMessageAndJoinsDataLines() {
        assertEquals(listOf(SseEvent("message", "a\nb")), parse("data: a\ndata:b\n\n"))
    }

    @Test
    fun ignoresCommentsAndUnknownFields() {
        assertEquals(listOf(SseEvent("message", "x")), parse(": ping\nid: 7\nretry: 100\ndata: x\n\n"))
    }

    @Test
    fun eventWithoutDataIsDropped() {
        assertEquals(emptyList<SseEvent>(), parse("event: message\n\n"))
    }

    @Test
    fun finishReturnsUnterminatedEvent() {
        val parser = SseParser()
        assertNull(parser.feed("data: tail"))
        assertEquals(SseEvent("message", "tail"), parser.finish())
        assertNull(parser.finish())
    }
}
