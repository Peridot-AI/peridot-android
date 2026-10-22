package ai.peridot.app.ui

import ai.peridot.app.R
import ai.peridot.app.markdown.Block
import ai.peridot.app.markdown.Markdown
import ai.peridot.app.ui.theme.MonoStyle
import ai.peridot.app.ui.theme.Peridot
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Renders a model answer. Parsing is cheap enough to redo for every streamed chunk. */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { Markdown.blocks(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { BlockView(it) }
    }
}

@Composable
private fun BlockView(block: Block) {
    val body = MaterialTheme.typography.bodyLarge
    when (block) {
        is Block.Paragraph -> Text(rich(block.text), style = body)
        is Block.Heading -> Text(
            rich(block.text),
            style = body.copy(
                fontSize = when (block.level) { 1 -> 22.sp; 2 -> 19.sp; else -> 17.sp },
                lineHeight = when (block.level) { 1 -> 29.sp; 2 -> 26.sp; else -> 24.sp },
                fontWeight = FontWeight.SemiBold,
            ),
            modifier = Modifier.padding(top = 4.dp),
        )
        is Block.ListItem -> Row(Modifier.padding(start = (block.depth * 18).dp)) {
            Text(
                block.marker,
                style = body,
                color = Peridot.Muted,
                modifier = Modifier.width(if (block.marker.length > 2) 30.dp else 22.dp),
            )
            Text(rich(block.text), style = body)
        }
        is Block.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(Peridot.Mint.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
            Text(rich(block.text), style = body, color = Peridot.Muted, modifier = Modifier.padding(start = 12.dp))
        }
        is Block.Code -> CodeBlock(block)
        is Block.Table -> TableBlock(block)
        Block.Rule -> HorizontalDivider(color = Peridot.Border, modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun CodeBlock(block: Block.Code) {
    val copyText = rememberCopy()
    Surface(
        color = Peridot.Code,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Peridot.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(block.language, color = Peridot.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { copyText(block.code) }) {
                    Text(stringResource(R.string.copy), color = Peridot.Muted, fontSize = 12.sp)
                }
            }
            Text(
                block.code,
                style = MonoStyle,
                color = Peridot.Text,
                softWrap = false,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            )
        }
    }
}

@Composable
private fun TableBlock(block: Block.Table) {
    val columns = maxOf(block.header.size, block.rows.maxOfOrNull { it.size } ?: 0)
    // A rough width per column from its longest cell, so the columns line up
    val widths = (0 until columns).map { c ->
        val longest = (listOf(block.header) + block.rows).maxOf { it.getOrNull(c)?.length ?: 0 }
        (longest * 8 + 28).coerceIn(72, 280).dp
    }
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Peridot.Border),
    ) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            (listOf(block.header) + block.rows).forEachIndexed { r, row ->
                if (r > 0) HorizontalDivider(color = Peridot.Border)
                Row(if (r == 0) Modifier.background(Peridot.Surface) else Modifier) {
                    for (c in 0 until columns) {
                        Text(
                            rich(row.getOrNull(c) ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (r == 0) FontWeight.SemiBold else null,
                            modifier = Modifier.width(widths[c]).padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rich(source: String): AnnotatedString = remember(source) {
    buildAnnotatedString {
        for (span in Markdown.spans(source)) {
            val style = SpanStyle(
                fontWeight = if (span.bold) FontWeight.SemiBold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                textDecoration = if (span.strike) TextDecoration.LineThrough else null,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                fontSize = if (span.code) 0.88.em else androidx.compose.ui.unit.TextUnit.Unspecified,
                background = if (span.code) Peridot.SurfaceHigh else Color.Unspecified,
            )
            val url = span.url
            if (url != null) {
                val link = TextLinkStyles(SpanStyle(color = Peridot.Mint, textDecoration = TextDecoration.Underline))
                withLink(LinkAnnotation.Url(url, link)) { withStyle(style) { append(span.text) } }
            } else {
                withStyle(style) { append(span.text) }
            }
        }
    }
}
