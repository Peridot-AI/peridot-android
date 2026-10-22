package ai.peridot.app.data

/** One server-sent event. */
data class SseEvent(val event: String, val data: String)

/**
 * Incremental parser for a text/event-stream, fed one line at a time (without the line break).
 * A blank line ends an event; `data:` lines are joined with "\n"; comments and unknown fields
 * are ignored, as the spec says.
 */
class SseParser {
    private var event = ""
    private val data = StringBuilder()
    private var hasData = false

    /** Returns the finished event when [line] ends one, otherwise null. */
    fun feed(line: String): SseEvent? {
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(":")) return null
        val colon = line.indexOf(':')
        val field = if (colon == -1) line else line.substring(0, colon)
        var value = if (colon == -1) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        when (field) {
            "event" -> event = value
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
        }
        return null
    }

    /** Call when the stream ends: returns an event that was not followed by a blank line. */
    fun finish(): SseEvent? = dispatch()

    private fun dispatch(): SseEvent? {
        val result = if (hasData) SseEvent(event.ifEmpty { "message" }, data.toString()) else null
        event = ""
        data.setLength(0)
        hasData = false
        return result
    }
}
