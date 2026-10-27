package com.neko.neuecode.ui.notice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.neko.neuecode.domain.notice.NoticeMarkdown

@Composable
fun NoticeRichBody(
    markdown: String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
) {
    val blocks = NoticeMarkdown.parse(markdown)
    val columnModifier = if (scrollable) {
        modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
    } else {
        modifier.fillMaxWidth()
    }
    Column(
        modifier = columnModifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is NoticeMarkdown.Block.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(
                        block.text,
                        style = style.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
                is NoticeMarkdown.Block.Paragraph -> {
                    NoticeInlineText(block.spans)
                }
                is NoticeMarkdown.Block.BulletList -> {
                    block.items.forEach { item ->
                        NoticeListRow(marker = "•", spans = item.spans)
                    }
                }
                is NoticeMarkdown.Block.OrderedList -> {
                    block.items.forEachIndexed { index, item ->
                        NoticeListRow(marker = "${index + 1}.", spans = item.spans)
                    }
                }
                is NoticeMarkdown.Block.Image -> {
                    AsyncImage(
                        model = block.url,
                        contentDescription = block.alt.ifBlank { "通知图片" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp, max = 220.dp),
                        contentScale = ContentScale.Fit,
                    )
                    if (block.alt.isNotBlank()) {
                        Text(
                            block.alt,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeListRow(marker: String, spans: List<NoticeMarkdown.Span>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            marker,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NoticeInlineText(spans, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun NoticeInlineText(
    spans: List<NoticeMarkdown.Span>,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        spans.forEach { span ->
            when (span) {
                is NoticeMarkdown.Span.Text -> append(span.text)
                is NoticeMarkdown.Span.Bold -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                    append(span.text)
                    pop()
                }
                is NoticeMarkdown.Span.Italic -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(span.text)
                    pop()
                }
                is NoticeMarkdown.Span.Link -> {
                    val start = length
                    pushStyle(
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                        ),
                    )
                    append(span.text)
                    pop()
                    addStringAnnotation(tag = "URL", annotation = span.url, start = start, end = length)
                }
            }
        }
    }
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { annotation ->
                runCatching { uriHandler.openUri(annotation.item) }
            }
        },
    )
}
