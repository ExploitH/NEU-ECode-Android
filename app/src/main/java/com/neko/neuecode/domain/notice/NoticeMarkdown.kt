package com.neko.neuecode.domain.notice

object NoticeMarkdown {
    const val listHint = "点击查看详情"
    const val listShowsTitleOnly = true
    const val detailRendersRichText = true

    sealed class Span {
        abstract val text: String

        data class Text(override val text: String) : Span()
        data class Bold(override val text: String) : Span()
        data class Italic(override val text: String) : Span()
        data class Link(override val text: String, val url: String) : Span()
    }

    data class Inline(val spans: List<Span>) {
        val plainText: String get() = spans.joinToString("") { it.text }
    }

    sealed class Block {
        data class Heading(val level: Int, val text: String) : Block()
        data class Paragraph(val spans: List<Span>) : Block() {
            val plainText: String get() = spans.joinToString("") { it.text }
        }
        data class BulletList(val items: List<Inline>) : Block()
        data class OrderedList(val items: List<Inline>) : Block()
        data class Image(val alt: String, val url: String) : Block()
    }

    fun parse(raw: String): List<Block> {
        val lines = raw.replace("\r\n", "\n").split('\n')
        val blocks = mutableListOf<Block>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank()) {
                index += 1
                continue
            }
            val headingBlock = heading(line)
            if (headingBlock != null) {
                blocks += headingBlock
                index += 1
                continue
            }
            val imageBlock = image(line)
            if (imageBlock != null) {
                blocks += imageBlock
                index += 1
                continue
            }
            if (isBullet(line)) {
                val items = mutableListOf<Inline>()
                while (index < lines.size && isBullet(lines[index])) {
                    items += parseInline(bulletText(lines[index]))
                    index += 1
                }
                blocks += Block.BulletList(items)
                continue
            }
            if (isOrdered(line)) {
                val items = mutableListOf<Inline>()
                while (index < lines.size && isOrdered(lines[index])) {
                    items += parseInline(orderedText(lines[index]))
                    index += 1
                }
                blocks += Block.OrderedList(items)
                continue
            }
            val paragraph = StringBuilder(line)
            index += 1
            while (index < lines.size) {
                val next = lines[index]
                if (next.isBlank() || heading(next) != null || image(next) != null || isBullet(next) || isOrdered(next)) {
                    break
                }
                paragraph.append(' ').append(next.trim())
                index += 1
            }
            blocks += Block.Paragraph(parseInline(paragraph.toString()).spans)
        }
        return blocks
    }

    private fun heading(line: String): Block.Heading? {
        val match = HEADING.matchEntire(line.trim()) ?: return null
        val level = match.groupValues[1].length.coerceIn(1, 3)
        return Block.Heading(level, match.groupValues[2].trim())
    }

    private fun image(line: String): Block.Image? {
        val match = IMAGE.matchEntire(line.trim()) ?: return null
        val url = sanitizeUrl(match.groupValues[2]) ?: return null
        return Block.Image(match.groupValues[1].trim(), url)
    }

    private fun isBullet(line: String): Boolean = BULLET.containsMatchIn(line)
    private fun isOrdered(line: String): Boolean = ORDERED.containsMatchIn(line)
    private fun bulletText(line: String): String = BULLET.replace(line, "").trim()
    private fun orderedText(line: String): String = ORDERED.replace(line, "").trim()

    internal fun parseInline(raw: String): Inline {
        val spans = mutableListOf<Span>()
        var cursor = 0
        val source = raw
        while (cursor < source.length) {
            val remaining = source.substring(cursor)
            val link = LINK.find(remaining)
            val bold = BOLD.find(remaining)
            val italic = ITALIC.find(remaining)
            val next = listOfNotNull(
                link?.let { Candidate(it.range.first, "link", it) },
                bold?.let { Candidate(it.range.first, "bold", it) },
                italic?.let { Candidate(it.range.first, "italic", it) },
            ).minByOrNull { it.start }
            if (next == null) {
                spans += Span.Text(remaining)
                break
            }
            if (next.start > 0) {
                spans += Span.Text(remaining.substring(0, next.start))
            }
            when (next.kind) {
                "link" -> {
                    val url = sanitizeUrl(next.match.groupValues[2])
                    val label = next.match.groupValues[1]
                    spans += if (url != null) Span.Link(label, url) else Span.Text(label)
                }
                "bold" -> spans += Span.Bold(next.match.groupValues[1])
                else -> spans += Span.Italic(next.match.groupValues[1])
            }
            cursor += next.start + next.match.value.length
        }
        return Inline(mergeText(spans))
    }

    private fun mergeText(spans: List<Span>): List<Span> {
        if (spans.isEmpty()) return listOf(Span.Text(""))
        val out = mutableListOf<Span>()
        for (span in spans) {
            val last = out.lastOrNull()
            if (span is Span.Text && last is Span.Text) {
                out[out.lastIndex] = Span.Text(last.text + span.text)
            } else {
                out += span
            }
        }
        return out
    }

    private fun sanitizeUrl(raw: String): String? {
        val url = raw.trim()
        val lower = url.lowercase()
        if (lower.startsWith("https://") || lower.startsWith("http://")) {
            return url
        }
        return null
    }

    private data class Candidate(val start: Int, val kind: String, val match: MatchResult)

    private val HEADING = Regex("""^(#{1,6})\s+(.+)$""")
    private val IMAGE = Regex("""^!\[([^\]]*)]\(([^)]+)\)$""")
    private val BULLET = Regex("""^\s*[-*+]\s+""")
    private val ORDERED = Regex("""^\s*\d+[.)]\s+""")
    private val LINK = Regex("""\[([^]]+)]\(([^)]+)\)""")
    private val BOLD = Regex("""\*\*(.+?)\*\*""")
    private val ITALIC = Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)""")
}
