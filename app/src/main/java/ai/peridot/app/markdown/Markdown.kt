package ai.peridot.app.markdown

/**
 * A small Markdown reader for model answers: the block structure plus inline styles, as plain
 * data (rendering lives in the UI). It is forgiving on purpose, because answers are parsed again
 * after every streamed chunk: an unclosed code fence runs to the end, an unclosed `**` stays text.
 */
sealed interface Block {
    data class Paragraph(val text: String) : Block
    data class Heading(val level: Int, val text: String) : Block
    data class ListItem(val marker: String, val text: String, val depth: Int) : Block
    data class Quote(val text: String) : Block
    data class Code(val language: String, val code: String) : Block
    data class Table(val header: List<String>, val rows: List<List<String>>) : Block
    data object Rule : Block
}

data class Span(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val code: Boolean = false,
    val url: String? = null,
)

object Markdown {
    private val heading = Regex("""^ {0,3}(#{1,6})\s+(.*?)(?:\s+#+)?\s*$""")
    private val bullet = Regex("""^(\s*)([-*+])\s+(.*)$""")
    private val numbered = Regex("""^(\s*)(\d{1,9})([.)])\s+(.*)$""")
    private val quote = Regex("""^\s*>\s?(.*)$""")
    private val rule = Regex("""^ {0,3}([-*_])(\s*\1){2,}\s*$""")
    private val fence = Regex("""^\s*(`{3,}|~{3,})\s*([^`\s]*).*$""")
    private val tableSeparator = Regex("""^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?\s*$""")

    fun blocks(source: String): List<Block> {
        val lines = source.replace("\r\n", "\n").split('\n')
        val out = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()
        val quoteLines = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) out.add(Block.Paragraph(paragraph.joinToString("\n")))
            paragraph.clear()
        }
        fun flushQuote() {
            if (quoteLines.isNotEmpty()) out.add(Block.Quote(quoteLines.joinToString("\n")))
            quoteLines.clear()
        }
        fun flush() { flushParagraph(); flushQuote() }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            val f = fence.matchEntire(line)
            if (f != null) {
                flush()
                val marker = f.groupValues[1]
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith(marker)) code.add(lines[i++])
                out.add(Block.Code(f.groupValues[2], code.joinToString("\n")))
                i++ // the closing fence (or past the end)
                continue
            }

            if (line.isBlank()) { flush(); i++; continue }

            val q = quote.matchEntire(line)
            if (q != null) {
                flushParagraph()
                quoteLines.add(q.groupValues[1])
                i++
                continue
            }
            flushQuote()

            if (rule.matches(line)) { flushParagraph(); out.add(Block.Rule); i++; continue }

            val h = heading.matchEntire(line)
            if (h != null) {
                flushParagraph()
                out.add(Block.Heading(h.groupValues[1].length, h.groupValues[2]))
                i++
                continue
            }

            if (line.contains('|') && i + 1 < lines.size && tableSeparator.matches(lines[i + 1]) && lines[i + 1].contains('-')) {
                flushParagraph()
                val header = cells(line)
                val rows = mutableListOf<List<String>>()
                i += 2
                while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) rows.add(cells(lines[i++]))
                out.add(Block.Table(header, rows))
                continue
            }

            val b = bullet.matchEntire(line)
            val n = numbered.matchEntire(line)
            if (b != null || n != null) {
                flushParagraph()
                val (indent, marker, text) =
                    if (b != null) Triple(b.groupValues[1], "•", b.groupValues[3])
                    else Triple(n!!.groupValues[1], n.groupValues[2] + ".", n.groupValues[4])
                out.add(Block.ListItem(marker, text, depth(indent)))
                i++
                continue
            }

            // An indented line right after a list item continues that item
            val last = out.lastOrNull()
            if (paragraph.isEmpty() && last is Block.ListItem && line.first().isWhitespace()) {
                out[out.size - 1] = last.copy(text = last.text + " " + line.trim())
                i++
                continue
            }

            paragraph.add(line.trim())
            i++
        }
        flush()
        return out
    }

    private fun depth(indent: String): Int {
        val width = indent.sumOf { if (it == '\t') 4 else 1 }
        return (width / 2).coerceAtMost(4)
    }

    private fun cells(row: String): List<String> {
        var s = row.trim()
        if (s.startsWith("|")) s = s.substring(1)
        if (s.endsWith("|") && !s.endsWith("\\|")) s = s.dropLast(1)
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var j = 0
        while (j < s.length) {
            val c = s[j]
            if (c == '\\' && j + 1 < s.length && s[j + 1] == '|') { cell.append('|'); j += 2; continue }
            if (c == '|') { cells.add(cell.toString().trim()); cell.clear() } else cell.append(c)
            j++
        }
        cells.add(cell.toString().trim())
        return cells
    }

    // ── Inline ──

    private data class Style(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val strike: Boolean = false,
        val url: String? = null,
    )

    private const val ESCAPABLE = "\\`*_{}[]()#+-.!|~<>"

    fun spans(source: String): List<Span> {
        val out = mutableListOf<Span>()
        parse(source, Style(), out)
        // Neighbours with the same style become one span
        val merged = mutableListOf<Span>()
        for (span in out) {
            val prev = merged.lastOrNull()
            if (prev != null && prev.copy(text = "") == span.copy(text = "")) merged[merged.size - 1] = prev.copy(text = prev.text + span.text)
            else merged.add(span)
        }
        return merged
    }

    private fun parse(s: String, style: Style, out: MutableList<Span>) {
        val buf = StringBuilder()
        fun flush() {
            if (buf.isNotEmpty()) out.add(Span(buf.toString(), style.bold, style.italic, style.strike, false, style.url))
            buf.clear()
        }
        var i = 0
        while (i < s.length) {
            val c = s[i]
            // Backslash escapes
            if (c == '\\' && i + 1 < s.length && s[i + 1] in ESCAPABLE) { buf.append(s[i + 1]); i += 2; continue }

            // `code`
            if (c == '`') {
                var n = 0
                while (i + n < s.length && s[i + n] == '`') n++
                val ticks = "`".repeat(n)
                val close = s.indexOf(ticks, i + n)
                if (close == -1) { buf.append(ticks); i += n; continue }
                flush()
                var code = s.substring(i + n, close)
                if (code.length > 2 && code.startsWith(" ") && code.endsWith(" ")) code = code.substring(1, code.length - 1)
                out.add(Span(code, style.bold, style.italic, style.strike, true, style.url))
                i = close + n
                continue
            }

            // **bold**, __bold__, ~~strike~~
            val double = s.regionMatches(i, "**", 0, 2) || s.regionMatches(i, "__", 0, 2) || s.regionMatches(i, "~~", 0, 2)
            if (double) {
                val delim = s.substring(i, i + 2)
                val close = closeDouble(s, delim, i + 2)
                if (close != -1 && opens(s, i, 2, delim[0]) && closes(s, close, 2, delim[0])) {
                    flush()
                    val inner = if (delim == "~~") style.copy(strike = true) else style.copy(bold = true)
                    parse(s.substring(i + 2, close), inner, out)
                    i = close + 2
                    continue
                }
                buf.append(delim)
                i += 2
                continue
            }

            // *italic*, _italic_
            if (c == '*' || c == '_') {
                val close = closeSingle(s, c, i + 1)
                if (close != -1 && opens(s, i, 1, c) && closes(s, close, 1, c)) {
                    flush()
                    parse(s.substring(i + 1, close), style.copy(italic = true), out)
                    i = close + 1
                    continue
                }
                buf.append(c)
                i++
                continue
            }

            // [text](url)
            if (c == '[' && style.url == null) {
                val textEnd = matching(s, i, '[', ']')
                if (textEnd != -1 && textEnd + 1 < s.length && s[textEnd + 1] == '(') {
                    val urlEnd = matching(s, textEnd + 1, '(', ')')
                    val url = if (urlEnd != -1) s.substring(textEnd + 2, urlEnd).trim().substringBefore(' ') else ""
                    if (urlEnd != -1 && isWebUrl(url)) {
                        flush()
                        parse(s.substring(i + 1, textEnd), style.copy(url = url), out)
                        i = urlEnd + 1
                        continue
                    }
                }
            }

            // Bare links
            if (style.url == null && (s.regionMatches(i, "https://", 0, 8) || s.regionMatches(i, "http://", 0, 7)) &&
                (i == 0 || !s[i - 1].isLetterOrDigit())
            ) {
                var end = i
                while (end < s.length && !s[end].isWhitespace() && s[end] != '<' && s[end] != '>') end++
                while (end > i && s[end - 1] in ".,;:!?'\")]*_") end--
                val url = s.substring(i, end)
                if (url.length > 8) {
                    flush()
                    out.add(Span(url, style.bold, style.italic, style.strike, false, url))
                    i = end
                    continue
                }
            }

            buf.append(c)
            i++
        }
        flush()
    }

    /** Can a delimiter run at [at] open emphasis? Not before a space, and `_` not inside a word. */
    private fun opens(s: String, at: Int, len: Int, ch: Char): Boolean {
        val next = s.getOrNull(at + len) ?: return false
        if (next.isWhitespace()) return false
        if (ch == '_' && at > 0 && s[at - 1].isLetterOrDigit()) return false
        return true
    }

    private fun closes(s: String, at: Int, len: Int, ch: Char): Boolean {
        val prev = s.getOrNull(at - 1) ?: return false
        if (prev.isWhitespace()) return false
        if (ch == '_' && s.getOrNull(at + len)?.isLetterOrDigit() == true) return false
        return true
    }

    /** In a longer run like `***`, the closer is its last two characters (the rest closes inner italics). */
    private fun closeDouble(s: String, delim: String, from: Int): Int {
        var j = s.indexOf(delim, from)
        while (j != -1 && (j == from || s[j - 1].isWhitespace())) j = s.indexOf(delim, j + 1)
        if (j != -1) while (j + 2 < s.length && s[j + 2] == delim[0]) j++
        return j
    }

    /** The next single [ch] that isn't half of a double one (those belong to nested bold). */
    private fun closeSingle(s: String, ch: Char, from: Int): Int {
        var j = from
        while (j < s.length) {
            if (s[j] == '`') {
                val end = s.indexOf('`', j + 1)
                if (end == -1) return -1
                j = end + 1
                continue
            }
            if (s[j] == ch) {
                if (j + 1 < s.length && s[j + 1] == ch) { j += 2; continue }
                if (j > from && !s[j - 1].isWhitespace()) return j
            }
            j++
        }
        return -1
    }

    private fun matching(s: String, open: Int, a: Char, b: Char): Int {
        var depth = 0
        for (j in open until s.length) {
            when (s[j]) {
                a -> depth++
                b -> if (--depth == 0) return j
            }
        }
        return -1
    }

    private fun isWebUrl(url: String) =
        url.startsWith("https://") || url.startsWith("http://") || url.startsWith("mailto:")
}
