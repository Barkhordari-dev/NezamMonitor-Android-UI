package com.nezammonitor.app.ui.screens.reportgen

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream

/** One section of a report PDF: a bold heading and its lines. */
data class PdfSection(val title: String, val lines: List<String>)

/**
 * Renders a Persian (RTL) report as a vector PDF: A4 pages, real text (not
 * images), so it stays sharp at any zoom and small — a few pages of text
 * are tens of kilobytes, far under the 2 MB limit. Uses only the framework
 * PdfDocument; no new dependency. Text shaping (RTL order, ligatures) is
 * done by Android's text layout with the system font, which covers Persian.
 *
 * [fontScale] scales all text sizes (1.0 = full quality). A smaller scale
 * packs more text per page, so fewer pages and a smaller file — used by the
 * size-limit feature to fit a chosen maximum file size without changing
 * the content. 1.0 always means "no quality change".
 */
fun generateReportPdf(
    title: String,
    subtitle: String,
    sections: List<PdfSection>,
    footer: String,
    outputFile: File,
    fontScale: Float = 1f
): File? {
    val doc = PdfDocument()
    val writer = PdfWriter(doc, fontScale.coerceIn(0.5f, 1f))
    return try {
        writer.drawTitle(title)
        if (subtitle.isNotBlank()) writer.drawLine(subtitle, 12f)
        writer.drawDivider()
        for (section in sections) {
            writer.drawSectionTitle(section.title)
            for (line in section.lines) writer.drawLine(line, 12f)
            writer.drawSpacer(6f)
        }
        writer.drawDivider()
        if (footer.isNotBlank()) writer.drawLine(footer, 11f)
        writer.finish()
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { doc.writeTo(it) }
        outputFile
    } catch (_: Exception) {
        null
    } finally {
        doc.close()
    }
}

private class PdfWriter(private val doc: PdfDocument, private val fontScale: Float = 1f) {
    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 42f
    private val contentWidth = (pageWidth - 2 * margin).toInt()
    private var page: PdfDocument.Page? = null
    private lateinit var canvas: Canvas
    private var y = margin
    private var pageNumber = 0

    init {
        newPage()
    }

    private fun newPage() {
        page?.let { doc.finishPage(it) }
        pageNumber++
        val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        page = doc.startPage(info)
        canvas = page!!.canvas
        y = margin
    }

    private fun ensureSpace(height: Float) {
        if (y + height > pageHeight - margin) newPage()
    }

    private fun layout(text: String, size: Float, bold: Boolean, centered: Boolean): StaticLayout {
        val paint = TextPaint().apply {
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textSize = size * fontScale
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
            .setAlignment(if (centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setLineSpacing(0f, 1.2f)
            .setIncludePad(true)
            .build()
    }

    private fun drawLayout(l: StaticLayout) {
        canvas.save()
        canvas.translate(margin, y)
        l.draw(canvas)
        canvas.restore()
        y += l.height + 2f
    }

    fun drawTitle(text: String) {
        val l = layout(text, 20f, bold = true, centered = true)
        ensureSpace(l.height.toFloat())
        drawLayout(l)
        y += 4f
    }

    fun drawSectionTitle(text: String) {
        val l = layout(text, 14f, bold = true, centered = false)
        ensureSpace(l.height + 14f)
        y += 4f
        drawLayout(l)
        y += 2f
        canvas.drawLine(margin, y, pageWidth - margin, y, Paint().apply { color = Color.GRAY; strokeWidth = 1f })
        y += 6f
    }

    fun drawLine(text: String, size: Float, bold: Boolean = false) {
        val l = layout(text, size, bold, centered = false)
        ensureSpace(l.height.toFloat())
        drawLayout(l)
    }

    fun drawDivider() {
        ensureSpace(12f)
        canvas.drawLine(margin, y + 4f, pageWidth - margin, y + 4f, Paint().apply { color = Color.BLACK; strokeWidth = 1.5f })
        y += 12f
    }

    fun drawSpacer(h: Float) {
        y += h
    }

    fun finish() {
        page?.let { doc.finishPage(it) }
        page = null
    }
}
