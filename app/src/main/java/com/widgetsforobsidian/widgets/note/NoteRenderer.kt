package com.widgetsforobsidian.widgets.note

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.QuoteSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import com.widgetsforobsidian.markdown.Block
import com.widgetsforobsidian.markdown.Span
import com.widgetsforobsidian.markdown.Style

/**
 * Turns markdown blocks into styled text for RemoteViews TextViews. Only parcelable spans are
 * used, since the text crosses into the launcher process. Colors are chosen to read on both
 * light and dark widget backgrounds; the base text color comes from the layout.
 */
object NoteRenderer {

    const val ACCENT = 0xFF8B5CF6.toInt()
    private const val MUTED = 0xFF8A8A8A.toInt()
    private const val CODE_BG = 0x33888888
    private const val HIGHLIGHT_BG = 0x66FFD400

    data class Rendered(val text: CharSequence, val sizeSp: Float)

    fun headingSize(level: Int) = when (level) { 1 -> 20f; 2 -> 18f; 3 -> 16f; else -> 15f }

    fun render(block: Block, accent: Int = ACCENT): Rendered? = when (block) {
        is Block.Heading -> Rendered(styled(block.text, accent, bold = true), headingSize(block.level))
        is Block.Paragraph -> Rendered(styled(block.text, accent), 14f)
        is Block.ListItem -> Rendered(
            SpannableStringBuilder(" ".repeat(block.level))
                .append(if (block.marker == "•") "•  " else "${block.marker} ")
                .append(styled(block.text, accent)),
            14f,
        )
        is Block.Task -> Rendered(styled(block.text, accent), 14f)
        is Block.Quote -> Rendered(lines(block.lines, accent).also { quote(it, MUTED) }, 14f)
        is Block.Callout -> {
            val sb = SpannableStringBuilder()
            append(sb, block.title, StyleSpan(Typeface.BOLD), ForegroundColorSpan(calloutColor(block.type)))
            if (block.lines.isNotEmpty()) sb.append("\n").append(lines(block.lines, accent))
            Rendered(sb, 14f)
        }
        is Block.Code -> Rendered(SpannableStringBuilder(block.text).also { mono(it) }, 12f)
        is Block.Table -> {
            val sb = SpannableStringBuilder()
            // Monospace + padding keeps columns aligned.
            val widths = (0 until (block.rows.maxOfOrNull { it.size } ?: 0)).map { c ->
                block.rows.maxOf { it.getOrNull(c)?.length ?: 0 }
            }
            block.rows.forEachIndexed { i, row ->
                if (i > 0) sb.append("\n")
                val line = widths.indices.joinToString(" │ ") { c -> row.getOrElse(c) { "" }.padEnd(widths[c]) }.trimEnd()
                if (i == 0) append(sb, line, StyleSpan(Typeface.BOLD)) else sb.append(line)
            }
            mono(sb)
            Rendered(sb, 12f)
        }
        is Block.Embed -> Rendered(
            SpannableStringBuilder().also {
                val icon = if (Regex("""\.(png|jpe?g|gif|webp|svg|bmp)$""", RegexOption.IGNORE_CASE).containsMatchIn(block.target)) "🖼" else "📄"
                append(it, "$icon ${block.target}", ForegroundColorSpan(accent))
            },
            13f,
        )
        Block.Rule -> null
    }

    /** Obsidian's default callout colors. */
    fun calloutColor(type: String): Int = when (type) {
        "abstract", "summary", "tldr", "tip", "hint", "important" -> 0xFF00BFBC.toInt()
        "success", "check", "done" -> 0xFF08B94E.toInt()
        "question", "help", "faq", "warning", "caution", "attention" -> 0xFFEC7500.toInt()
        "failure", "fail", "missing", "danger", "error", "bug" -> 0xFFE93147.toInt()
        "example" -> 0xFF7852EE.toInt()
        "quote", "cite" -> MUTED
        else -> 0xFF086DDD.toInt() // note, info, todo
    }

    private fun lines(lines: List<List<Span>>, accent: Int): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        lines.forEachIndexed { i, l -> if (i > 0) sb.append("\n"); sb.append(styled(l, accent)) }
        return sb
    }

    fun styled(spans: List<Span>, accent: Int = ACCENT, bold: Boolean = false): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        for (span in spans) {
            val what = buildList {
                if (Style.BOLD in span.styles) add(StyleSpan(Typeface.BOLD))
                if (Style.ITALIC in span.styles) add(StyleSpan(Typeface.ITALIC))
                if (Style.STRIKE in span.styles) add(StrikethroughSpan())
                if (Style.CODE in span.styles) { add(TypefaceSpan("monospace")); add(BackgroundColorSpan(CODE_BG)) }
                if (Style.HIGHLIGHT in span.styles) add(BackgroundColorSpan(HIGHLIGHT_BG))
                if (Style.LINK in span.styles) add(ForegroundColorSpan(accent))
                if (Style.TAG in span.styles) { add(ForegroundColorSpan(accent)); add(BackgroundColorSpan((accent and 0x00FFFFFF) or 0x22000000)) }
            }
            append(sb, span.text, *what.toTypedArray())
        }
        if (bold) sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sb
    }

    private fun append(sb: SpannableStringBuilder, text: String, vararg what: Any) {
        val start = sb.length
        sb.append(text)
        what.forEach { sb.setSpan(it, start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }

    private fun mono(sb: SpannableStringBuilder) =
        sb.setSpan(TypefaceSpan("monospace"), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

    private fun quote(sb: SpannableStringBuilder, color: Int) =
        sb.setSpan(QuoteSpan(color), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}
