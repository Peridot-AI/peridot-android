package ai.peridot.app

import ai.peridot.app.markdown.Block
import ai.peridot.app.markdown.Markdown
import ai.peridot.app.markdown.Span
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTest {
    @Test
    fun blocks() {
        val md = """
            # Title
            Some text
            on two lines

            - one
            - two
              continued
            1. first

            > quoted
            ---
            ```kotlin
            val x = 1
            ```
        """.trimIndent()
        assertEquals(
            listOf(
                Block.Heading(1, "Title"),
                Block.Paragraph("Some text\non two lines"),
                Block.ListItem("•", "one", 0),
                Block.ListItem("•", "two continued", 0),
                Block.ListItem("1.", "first", 0),
                Block.Quote("quoted"),
                Block.Rule,
                Block.Code("kotlin", "val x = 1"),
            ),
            Markdown.blocks(md),
        )
    }

    @Test
    fun unclosedFenceRunsToTheEnd() {
        assertEquals(listOf(Block.Paragraph("Look:"), Block.Code("", "a\nb")), Markdown.blocks("Look:\n```\na\nb"))
    }

    @Test
    fun table() {
        val md = "| A | B |\n|---|:-:|\n| 1 | 2 \\| 3 |\n| x |"
        assertEquals(listOf(Block.Table(listOf("A", "B"), listOf(listOf("1", "2 | 3"), listOf("x")))), Markdown.blocks(md))
    }

    @Test
    fun nestedListDepth() {
        assertEquals(
            listOf(Block.ListItem("•", "a", 0), Block.ListItem("•", "b", 1)),
            Markdown.blocks("- a\n  - b"),
        )
    }

    @Test
    fun inlineStyles() {
        assertEquals(
            listOf(
                Span("a "),
                Span("bold", bold = true),
                Span(" "),
                Span("it", italic = true),
                Span(" "),
                Span("code", code = true),
                Span(" "),
                Span("gone", strike = true),
            ),
            Markdown.spans("a **bold** *it* `code` ~~gone~~"),
        )
    }

    @Test
    fun italicInsideBold() {
        assertEquals(
            listOf(Span("x ", bold = true), Span("y", bold = true, italic = true)),
            Markdown.spans("**x *y***"),
        )
    }

    @Test
    fun unclosedDelimitersStayText() {
        assertEquals(listOf(Span("2 * 3 and **open")), Markdown.spans("2 * 3 and **open"))
    }

    @Test
    fun snakeCaseIsNotItalic() {
        assertEquals(listOf(Span("my_var_name")), Markdown.spans("my_var_name"))
    }

    @Test
    fun links() {
        assertEquals(
            listOf(Span("see "), Span("docs", url = "https://peridot.ai/docs"), Span(" or "), Span("https://x.dev/a", url = "https://x.dev/a"), Span(".")),
            Markdown.spans("see [docs](https://peridot.ai/docs) or https://x.dev/a."),
        )
    }

    @Test
    fun escapes() {
        assertEquals(listOf(Span("*not italic*")), Markdown.spans("\\*not italic\\*"))
    }
}
