package io.github.halilozel1903.hackernews

import io.github.halilozel1903.hackernews.text.HnHtml
import io.github.halilozel1903.hackernews.text.HnSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HnHtmlTest {

    private fun HnSpan.cover(text: String) = text.substring(start, end)

    @Test
    fun paragraphsWithoutClosingTags() {
        assertEquals("One.\n\nTwo.\n\nThree.", HnHtml.toPlainText("One.<p>Two.<p>Three."))
        assertEquals("One.\n\nTwo.", HnHtml.toPlainText("<p>One.</p><p>Two.</p>"))
        assertEquals("One.\n\nTwo.", HnHtml.toPlainText("One. <p> Two. "))
    }

    @Test
    fun decodesEntities() {
        assertEquals("I'm \"here\" & <there> / 😀", HnHtml.decodeEntities("I&#x27;m &quot;here&quot; &amp; &lt;there&gt; &#x2F; &#128512;"))
        assertEquals("AT&T &bogus; & ;", HnHtml.decodeEntities("AT&T &bogus; & ;"))
    }

    @Test
    fun links() {
        val html = """See <a href="https:&#x2F;&#x2F;github.com&#x2F;HackerNews&#x2F;API" rel="nofollow">the docs</a> now."""
        val text = HnHtml.parse(html)
        assertEquals("See the docs now.", text.text)
        val link = text.links.single()
        assertEquals("https://github.com/HackerNews/API", link.url)
        assertEquals("the docs", link.cover(text.text))
    }

    @Test
    fun emptyLinkShowsItsUrl() {
        val text = HnHtml.parse("""Go <a href="https://example.com"></a>""")
        assertEquals("Go https://example.com", text.text)
        assertEquals("https://example.com", text.links.single().cover(text.text))
    }

    @Test
    fun italicsAndBold() {
        val text = HnHtml.parse("A <i>very</i> <b>bold</b> claim")
        assertEquals("A very bold claim", text.text)
        assertEquals("very", text.spans.filterIsInstance<HnSpan.Italic>().single().cover(text.text))
        assertEquals("bold", text.spans.filterIsInstance<HnSpan.Bold>().single().cover(text.text))
    }

    @Test
    fun codeBlocksKeepWhitespace() {
        val text = HnHtml.parse("Try this:<p><pre><code>  if (x &lt; 1) {\n    y()\n  }\n</code></pre>\nWorks?")
        assertEquals("Try this:\n\n  if (x < 1) {\n    y()\n  }\n\nWorks?", text.text)
        val code = text.spans.filterIsInstance<HnSpan.Code>().single()
        assertTrue(code.block)
        assertEquals("  if (x < 1) {\n    y()\n  }", code.cover(text.text))
    }

    @Test
    fun inlineCode() {
        val text = HnHtml.parse("Use <code>git bisect</code> for that.")
        val code = text.spans.filterIsInstance<HnSpan.Code>().single()
        assertEquals("git bisect", code.cover(text.text))
        assertEquals(false, code.block)
    }

    @Test
    fun quotedParagraphs() {
        val text = HnHtml.parse("&gt; It never fails.<p>It does.<p>&gt; Always?")
        val quotes = text.spans.filterIsInstance<HnSpan.Quote>()
        assertEquals(listOf("> It never fails.", "> Always?"), quotes.map { it.cover(text.text) })
    }

    @Test
    fun spansDoNotGrowOverTrimmedWhitespace() {
        val text = HnHtml.parse("<i>Note: </i><p>Next")
        assertEquals("Note:\n\nNext", text.text)
        assertEquals("Note:", text.spans.filterIsInstance<HnSpan.Italic>().single().cover(text.text))
    }

    @Test
    fun unclosedAndUnknownTags() {
        val text = HnHtml.parse("<span>Kept</span> and <i>open italic")
        assertEquals("Kept and open italic", text.text)
        assertEquals("open italic", text.spans.single().cover(text.text))
        assertEquals("1 < 2 and 3 > 2", HnHtml.toPlainText("1 < 2 and 3 > 2"))
        assertTrue(HnHtml.parse("").isEmpty())
    }
}
