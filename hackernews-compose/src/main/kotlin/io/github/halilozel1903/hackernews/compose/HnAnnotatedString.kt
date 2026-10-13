package io.github.halilozel1903.hackernews.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.halilozel1903.hackernews.text.HnHtml
import io.github.halilozel1903.hackernews.text.HnSpan
import io.github.halilozel1903.hackernews.text.HnText

/**
 * How Hacker News text looks as an [AnnotatedString]. Use [HnTextStyle.fromTheme] for colors that follow the
 * Material theme in light and dark mode.
 */
@Immutable
public data class HnTextStyle(
    /** Links: clickable through [LinkAnnotation.Url]. */
    val link: SpanStyle = SpanStyle(color = Color(0xFF1565C0), textDecoration = TextDecoration.Underline),
    /** Links while pressed. */
    val pressedLink: SpanStyle? = null,
    val italic: SpanStyle = SpanStyle(fontStyle = FontStyle.Italic),
    val bold: SpanStyle = SpanStyle(fontWeight = FontWeight.Bold),
    /** Inline `<code>`. */
    val code: SpanStyle = SpanStyle(fontFamily = FontFamily.Monospace),
    /** `<pre>` blocks. */
    val codeBlock: SpanStyle = SpanStyle(fontFamily = FontFamily.Monospace),
    /** Paragraphs starting with `>`. */
    val quote: SpanStyle = SpanStyle(color = Color(0xFF6B6B6B), fontStyle = FontStyle.Italic),
) {
    public companion object {
        /** Colors from the current [MaterialTheme]: primary links, muted quotes, tinted code. */
        @Composable
        public fun fromTheme(): HnTextStyle {
            val colors = MaterialTheme.colorScheme
            return remember(colors.primary, colors.onSurfaceVariant, colors.surfaceVariant) {
                HnTextStyle(
                    link = SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline),
                    pressedLink = SpanStyle(color = colors.primary, background = colors.primary.copy(alpha = 0.12f)),
                    code = SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceVariant),
                    codeBlock = SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceVariant),
                    quote = SpanStyle(color = colors.onSurfaceVariant, fontStyle = FontStyle.Italic),
                )
            }
        }
    }
}

/** This text as an [AnnotatedString] with [style]; links open through the platform's `UriHandler` when tapped. */
public fun HnText.toAnnotatedString(style: HnTextStyle = HnTextStyle()): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    for (span in spans) {
        when (span) {
            is HnSpan.Link -> builder.addLink(
                LinkAnnotation.Url(
                    url = span.url,
                    styles = TextLinkStyles(style = style.link, pressedStyle = style.pressedLink),
                ),
                span.start,
                span.end,
            )
            is HnSpan.Italic -> builder.addStyle(style.italic, span.start, span.end)
            is HnSpan.Bold -> builder.addStyle(style.bold, span.start, span.end)
            is HnSpan.Code -> builder.addStyle(if (span.block) style.codeBlock else style.code, span.start, span.end)
            is HnSpan.Quote -> builder.addStyle(style.quote, span.start, span.end)
        }
    }
    return builder.toAnnotatedString()
}

/** Parses Hacker News [html] (a comment, story text or profile) straight into an [AnnotatedString]. */
public fun hnHtmlToAnnotatedString(html: String, style: HnTextStyle = HnTextStyle()): AnnotatedString =
    HnHtml.parse(html).toAnnotatedString(style)

/** [hnHtmlToAnnotatedString], remembered for [html] and [style]. */
@Composable
public fun rememberHnAnnotatedString(html: String, style: HnTextStyle = HnTextStyle.fromTheme()): AnnotatedString =
    remember(html, style) { hnHtmlToAnnotatedString(html, style) }

/**
 * Shows Hacker News [html] with paragraphs, italics, code, quotes and clickable links.
 *
 * ```kotlin
 * HnHtmlText(comment.text.orEmpty(), style = MaterialTheme.typography.bodyMedium)
 * ```
 */
@Composable
public fun HnHtmlText(
    html: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    textStyle: HnTextStyle = HnTextStyle.fromTheme(),
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = rememberHnAnnotatedString(html, textStyle),
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
    )
}
