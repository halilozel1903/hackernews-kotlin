package io.github.halilozel1903.hackernews.text

/**
 * Text of a comment, story or profile with its formatting: plain [text] and [spans] over it. Build it with
 * [HnHtml.parse]; the Compose module turns it into an `AnnotatedString`.
 */
public data class HnText(val text: String, val spans: List<HnSpan>) {
    /** Every link, in text order. */
    val links: List<HnSpan.Link> get() = spans.filterIsInstance<HnSpan.Link>().sortedBy { it.start }

    /** `true` when there is no text. */
    public fun isEmpty(): Boolean = text.isEmpty()

    override fun toString(): String = text

    public companion object {
        /** No text. */
        public val Empty: HnText = HnText("", emptyList())
    }
}

/** A formatted range of [HnText.text], from [start] (inclusive) to [end] (exclusive). */
public sealed interface HnSpan {
    public val start: Int
    public val end: Int

    /** An `<a href>` link. */
    public data class Link(override val start: Int, override val end: Int, val url: String) : HnSpan

    /** `<i>` or `<em>`. Hacker News writes `*text*` as italics. */
    public data class Italic(override val start: Int, override val end: Int) : HnSpan

    /** `<b>` or `<strong>`. */
    public data class Bold(override val start: Int, override val end: Int) : HnSpan

    /** `<code>`, or a `<pre>` block (indented text on Hacker News). */
    public data class Code(override val start: Int, override val end: Int, val block: Boolean) : HnSpan

    /** A paragraph that starts with `>`, the Hacker News way of quoting. */
    public data class Quote(override val start: Int, override val end: Int) : HnSpan
}

/**
 * Parses the small HTML subset the Hacker News API uses in `text` and `about`: `<p>` paragraph breaks (usually
 * without closing tags), `<i>`, `<a href>` with `rel="nofollow"`, `<pre><code>` blocks, and entities such as
 * `&#x27;` and `&#x2F;`. Unknown tags are dropped and their text kept.
 */
public object HnHtml {

    /** Parses [html] into text and spans. */
    public fun parse(html: String): HnText {
        if (html.isEmpty()) return HnText.Empty
        val out = StringBuilder()
        val spans = ArrayList<HnSpan>()
        val open = ArrayList<OpenTag>()
        var pendingBreak = false
        var preDepth = 0

        fun trimEnd() {
            var end = out.length
            while (end > 0 && out[end - 1].isWhitespace()) end--
            if (end == out.length) return
            out.setLength(end)
            // Spans that ended in the trimmed whitespace must not grow over the text that follows.
            val kept = spans.mapNotNull { clamp(it, end) }
            spans.clear()
            spans.addAll(kept)
        }

        fun startBlock() {
            if (out.isNotEmpty()) {
                trimEnd()
                out.append("\n\n")
            }
            pendingBreak = false
        }

        fun appendText(text: String) {
            if (text.isEmpty()) return
            if (preDepth == 0) {
                val collapsed = text.replace('\n', ' ')
                if (pendingBreak) {
                    if (collapsed.isBlank()) return
                    startBlock()
                }
                if (out.isEmpty() || out.endsWith("\n")) out.append(collapsed.trimStart()) else out.append(collapsed)
            } else {
                if (pendingBreak) startBlock()
                out.append(text)
            }
        }

        var i = 0
        while (i < html.length) {
            val c = html[i]
            val tagEnd = if (c == '<') tagEnd(html, i) else -1
            if (tagEnd < 0) {
                val next = html.indexOf('<', i + 1).let { if (it < 0) html.length else it }
                appendText(decodeEntities(html.substring(i, next)))
                i = next
                continue
            }
            val raw = html.substring(i + 1, tagEnd).trim()
            i = tagEnd + 1
            val closing = raw.startsWith('/')
            val name = raw.removePrefix("/").takeWhile { it.isLetterOrDigit() }.lowercase()
            when (name) {
                "p" -> pendingBreak = true
                "br" -> {
                    if (preDepth == 0) trimEnd()
                    out.append('\n')
                }
                "pre" -> if (!closing) {
                    startBlock()
                    preDepth++
                    open += OpenTag("pre", out.length, null)
                } else {
                    trimEnd()
                    preDepth = (preDepth - 1).coerceAtLeast(0)
                    close(open, "pre", out, spans)
                    pendingBreak = true
                }
                "a", "i", "em", "b", "strong", "code" -> if (!closing) {
                    if (pendingBreak && preDepth == 0) startBlock()
                    val href = if (name == "a") attribute(raw, "href")?.let { decodeEntities(it) } else null
                    open += OpenTag(name, out.length, href)
                } else {
                    val tag = close(open, name, out, spans)
                    if (tag?.name == "a" && tag.start == out.length && !tag.href.isNullOrEmpty()) {
                        // A link without text shows its url.
                        out.append(tag.href)
                        spans.removeAt(spans.lastIndex)
                        spans += HnSpan.Link(tag.start, out.length, tag.href)
                    }
                }
                else -> Unit
            }
        }
        // Close what the html left open.
        while (open.isNotEmpty()) close(open, open.last().name, out, spans)
        trimEnd()
        val length = out.length
        val text = out.toString()
        val clamped = spans.mapNotNull { span -> clamp(span, length) }
        return HnText(text, (clamped + quotes(text, clamped)).sortedWith(compareBy({ it.start }, { -it.end })))
    }

    /** [parse] without the formatting. */
    public fun toPlainText(html: String): String = parse(html).text

    /** Decodes named (`&amp;`, `&quot;`, ...) and numeric (`&#39;`, `&#x2F;`) character references. */
    public fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '&') {
                val semi = text.indexOf(';', i + 1)
                if (semi in (i + 2)..(i + 10)) {
                    val decoded = entity(text.substring(i + 1, semi))
                    if (decoded != null) {
                        out.append(decoded)
                        i = semi + 1
                        continue
                    }
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    private class OpenTag(val name: String, val start: Int, val href: String?)

    private fun close(open: MutableList<OpenTag>, name: String, out: StringBuilder, spans: MutableList<HnSpan>): OpenTag? {
        val index = open.indexOfLast { it.name == name || (name == "em" && it.name == "i") || (name == "i" && it.name == "em") }
        if (index < 0) return null
        val tag = open.removeAt(index)
        val end = out.length
        val insidePre = open.any { it.name == "pre" }
        val span: HnSpan? = when (tag.name) {
            "a" -> tag.href?.takeIf { it.isNotEmpty() }?.let { HnSpan.Link(tag.start, end, it) }
            "i", "em" -> HnSpan.Italic(tag.start, end)
            "b", "strong" -> HnSpan.Bold(tag.start, end)
            // Code inside <pre> is covered by the block span of the pre.
            "code" -> if (insidePre) null else HnSpan.Code(tag.start, end, block = false)
            "pre" -> HnSpan.Code(tag.start, end, block = true)
            else -> null
        }
        if (span != null && (span.end > span.start || span is HnSpan.Link)) spans += span
        return tag
    }

    private fun clamp(span: HnSpan, length: Int): HnSpan? {
        val start = span.start.coerceAtMost(length)
        val end = span.end.coerceAtMost(length)
        if (end <= start) return null
        if (start == span.start && end == span.end) return span
        return when (span) {
            is HnSpan.Link -> span.copy(start = start, end = end)
            is HnSpan.Italic -> span.copy(start = start, end = end)
            is HnSpan.Bold -> span.copy(start = start, end = end)
            is HnSpan.Code -> span.copy(start = start, end = end)
            is HnSpan.Quote -> span.copy(start = start, end = end)
        }
    }

    /** A [HnSpan.Quote] for every paragraph that starts with `>` and is not code. */
    private fun quotes(text: String, spans: List<HnSpan>): List<HnSpan.Quote> {
        val blocks = spans.filterIsInstance<HnSpan.Code>().filter { it.block }
        val result = ArrayList<HnSpan.Quote>()
        var start = 0
        while (start < text.length) {
            val breakAt = text.indexOf("\n\n", start).let { if (it < 0) text.length else it }
            val paragraph = text.substring(start, breakAt)
            val inCode = blocks.any { start >= it.start && start < it.end }
            if (!inCode && paragraph.trimStart().startsWith('>')) result += HnSpan.Quote(start, breakAt)
            start = breakAt + 2
        }
        return result
    }

    /** The index of the `>` closing a tag that starts at [start], or -1 when `<` does not start a tag. */
    private fun tagEnd(html: String, start: Int): Int {
        val next = html.getOrNull(start + 1) ?: return -1
        if (!next.isLetter() && next != '/') return -1
        var quote: Char? = null
        for (j in start + 1 until html.length) {
            val ch = html[j]
            when {
                quote != null -> if (ch == quote) quote = null
                ch == '"' || ch == '\'' -> quote = ch
                ch == '>' -> return j
                ch == '<' -> return -1
            }
        }
        return -1
    }

    private fun attribute(tag: String, name: String): String? {
        val match = Regex("""\b$name\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE).find(tag) ?: return null
        val groups = match.groupValues
        return groups[2].ifEmpty { groups[3] }.ifEmpty { groups[4] }
    }

    private fun entity(name: String): String? {
        if (name.startsWith('#')) {
            val code = if (name.length > 1 && (name[1] == 'x' || name[1] == 'X')) {
                name.substring(2).toIntOrNull(16)
            } else {
                name.substring(1).toIntOrNull()
            } ?: return null
            if (code <= 0 || code > 0x10FFFF) return null
            return String(Character.toChars(code))
        }
        return when (name) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            "nbsp" -> " "
            "mdash" -> "—"
            "ndash" -> "–"
            "hellip" -> "…"
            else -> null
        }
    }
}
