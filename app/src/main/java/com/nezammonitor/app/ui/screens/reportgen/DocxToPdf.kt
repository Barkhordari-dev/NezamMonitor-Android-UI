package com.nezammonitor.app.ui.screens.reportgen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import org.apache.poi.xwpf.usermodel.BodyElementType
import org.apache.poi.xwpf.usermodel.LineSpacingRule
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFPicture
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import org.apache.poi.xwpf.usermodel.XWPFTableRow
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Converts a filled .docx report — the Word file the generator builds from
 * the user's template — into a PDF that looks like the template itself, the
 * way Word's own "Save as PDF" does: same paragraphs, tables, sizes and
 * alignment, on A4 pages. The old data-driven PDF (a plain text list) is
 * gone from the export path; it stays only as the print fallback when no
 * template exists.
 *
 * How it works: the .docx is opened with POI and its body elements are
 * walked in order. A paragraph becomes one StaticLayout (its runs keep
 * their own size/bold/italic/underline/color via spans, so mixed runs stay
 * on the same lines), a table becomes ruled cells laid out from the
 * tblGrid widths with the template's own borders, shading and cell
 * margins. Everything is drawn with android.graphics.pdf.PdfDocument, so
 * the PDF is real vector text — sharp at any zoom and small.
 *
 * Template facts this relies on (checked against the Ardakan-Mec 2.docx):
 * A4 (11906x16838 twips), margins 25/30pt, tables with single 0.5pt
 * borders, the checklist table is bidi (first cell at the right edge) with
 * a grey header row, the "توضیحات" row has a 242pt minimum height, and the
 * ☐ checkbox character (U+2610, Segoe UI Symbol in the template) is drawn
 * as a stroked empty square because the Android system font has no glyph
 * for it. Floating (anchored, behind-text) pictures such as the template's
 * two background logos are NOT in POI's embedded-pictures list, so they
 * are skipped; inline pictures are drawn scaled to fit.
 *
 * Text is drawn with the bundled B Nazanin (assets/fonts/) — the
 * template's own font, so the PDF text looks like the Word file; bold
 * runs use the real B Nazanin Bold file, not synthesized bold.
 *
 * [fontScale] (0.5–1.0) scales run font sizes — the size-limit loop uses
 * it to shrink a PDF that is over the chosen maximum. Returns false on
 * any failure and never throws.
 */
fun convertDocxToPdf(context: Context, docxFile: File, pdfFile: File, fontScale: Float = 1f): Boolean {
    if (!docxFile.exists()) return false
    return try {
        FileInputStream(docxFile).use { input ->
            XWPFDocument(input).use { xdoc ->
                val pdf = PdfDocument()
                try {
                    val (regular, bold) = BNazaninFonts.load(context)
                    DocxPdfRenderer(pdf, fontScale.coerceIn(0.5f, 1f), regular, bold).render(xdoc)
                    pdfFile.parentFile?.mkdirs()
                    FileOutputStream(pdfFile).use { pdf.writeTo(it) }
                    true
                } finally {
                    pdf.close()
                }
            }
        }
    } catch (_: Exception) {
        try {
            pdfFile.delete()
        } catch (_: Exception) {
        }
        false
    }
}

private const val DEFAULT_SIZE_PT = 11f

/** The bundled B Nazanin fonts (assets/fonts/): the template's own font,
 *  so the PDF text looks like the Word file. Loaded once and cached;
 *  any failure falls back to the system font (regular) / synthesized
 *  bold (bold). The load runs on the caller's thread — callers invoke
 *  convertDocxToPdf from Dispatchers.IO. */
private object BNazaninFonts {
    @Volatile private var regular: Typeface? = null
    @Volatile private var bold: Typeface? = null
    @Volatile private var loaded = false

    @Synchronized
    fun load(context: Context): Pair<Typeface, Typeface?> {
        if (!loaded) {
            val assets = context.applicationContext.assets
            regular = try { Typeface.createFromAsset(assets, "fonts/BNazanin.ttf") } catch (_: Exception) { null }
            bold = try { Typeface.createFromAsset(assets, "fonts/BNazaninBd.ttf") } catch (_: Exception) { null }
            loaded = true
        }
        return (regular ?: Typeface.DEFAULT) to bold
    }
}

/** A span that switches the paint to a specific Typeface (here the real
 *  B Nazanin Bold file) instead of StyleSpan(BOLD), which would only
 *  synthesize bold on top of the regular font. */
private class TypefaceSwitchSpan(private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateMeasureState(textPaint: TextPaint) { textPaint.typeface = typeface }
    override fun updateDrawState(textPaint: TextPaint) { textPaint.typeface = typeface }
}

private class DocxPdfRenderer(
    private val pdf: PdfDocument,
    private val fontScale: Float,
    private val regularTypeface: Typeface,
    private val boldTypeface: Typeface?
) {

    // Page geometry in points (1 pt = 1/72 inch). Defaults are A4 with
    // Word's 1-inch margins; the document's sectPr overrides them.
    private var pageWidth = 595f
    private var pageHeight = 842f
    private var marginLeft = 72f
    private var marginRight = 72f
    private var marginTop = 72f
    private var marginBottom = 72f

    private val contentLeft get() = marginLeft
    private val contentRight get() = pageWidth - marginRight
    private val contentTop get() = marginTop
    private val contentBottom get() = pageHeight - marginBottom
    private val contentWidth get() = contentRight - contentLeft

    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var y = 0f
    private var pageNumber = 0

    fun render(xdoc: XWPFDocument) {
        readSectPr(xdoc)
        // Absurd margins must not wedge the pagination loop below.
        if (pageHeight - marginTop - marginBottom < 72f) {
            marginTop = 42f
            marginBottom = 42f
        }
        if (pageWidth - marginLeft - marginRight < 72f) {
            marginLeft = 42f
            marginRight = 42f
        }
        newPage()
        for (el in xdoc.bodyElements) {
            when (el.elementType) {
                BodyElementType.PARAGRAPH -> drawFlow(measureParagraph(el as XWPFParagraph, contentLeft, contentWidth))
                BodyElementType.TABLE -> drawTable(el as XWPFTable)
                else -> {
                }
            }
        }
        page?.let { pdf.finishPage(it) }
        page = null
    }

    private fun readSectPr(xdoc: XWPFDocument) {
        try {
            val sectPr = xdoc.document?.body?.sectPr ?: return
            sectPr.pgSz?.let { pgSz ->
                val w = numberValue(pgSz.w)
                val h = numberValue(pgSz.h)
                if (w > 0) pageWidth = w / 20f
                if (h > 0) pageHeight = h / 20f
            }
            sectPr.pgMar?.let { mar ->
                marginTop = numberValue(mar.top, 1440f) / 20f
                marginRight = numberValue(mar.right, 1440f) / 20f
                marginBottom = numberValue(mar.bottom, 1440f) / 20f
                marginLeft = numberValue(mar.left, 1440f) / 20f
            }
        } catch (_: Exception) {
        }
    }

    private fun newPage() {
        page?.let { pdf.finishPage(it) }
        pageNumber++
        val info = PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), pageNumber).create()
        page = pdf.startPage(info)
        canvas = page!!.canvas
        canvas!!.drawColor(Color.WHITE)
        y = contentTop
    }

    // ==================== Content model ====================

    /** One checkbox placeholder inside a text layout: the ☐/☑ character
     *  was replaced by a space in the text, and the square is drawn by hand
     *  at the space's position after the layout is drawn. */
    private data class CheckMark(val offset: Int, val sizePt: Float, val checked: Boolean)

    private sealed interface ContentBlock {
        val height: Float
    }

    private class TextBlock(val layout: StaticLayout, val marks: List<CheckMark>) : ContentBlock {
        override val height: Float get() = layout.height.toFloat()
    }

    private class GapBlock(override val height: Float) : ContentBlock

    private class PictureBlock(val bitmap: Bitmap, val width: Float, override val height: Float) : ContentBlock

    /** A measured paragraph: its blocks, and the x/width they are drawn at
     *  (indents already applied). */
    private class ParaContent(val blocks: List<ContentBlock>, val x: Float, val width: Float) {
        val height: Float get() = blocks.sumOf { it.height.toDouble() }.toFloat()
    }

    // ==================== Paragraphs ====================

    private fun measureParagraph(para: XWPFParagraph, x: Float, width: Float): ParaContent {
        val spacingBefore = spacingPt(para.spacingBefore)
        val spacingAfter = spacingPt(para.spacingAfter)
        val built = buildText(para, x, width)
        val blocks = mutableListOf<ContentBlock>()
        if (spacingBefore > 0) blocks.add(GapBlock(spacingBefore))
        if (built.layout != null) {
            blocks.add(TextBlock(built.layout, built.marks))
        } else if (built.pictures.isEmpty()) {
            // An empty paragraph still takes one line of space, like in Word.
            blocks.add(GapBlock(DEFAULT_SIZE_PT * 1.2f * fontScale))
        }
        blocks.addAll(built.pictures)
        if (spacingAfter > 0) blocks.add(GapBlock(spacingAfter))
        return ParaContent(blocks, built.x, built.width)
    }

    private class BuiltText(
        val layout: StaticLayout?,
        val marks: List<CheckMark>,
        val pictures: List<PictureBlock>,
        val x: Float,
        val width: Float
    )

    private fun buildText(para: XWPFParagraph, x: Float, width: Float): BuiltText {
        val sb = SpannableStringBuilder()
        val marks = mutableListOf<CheckMark>()
        val pictures = mutableListOf<PictureBlock>()
        var maxSize = DEFAULT_SIZE_PT
        val runs = try {
            para.runs
        } catch (_: Exception) {
            emptyList()
        }
        for (run in runs) {
            val sizePt = runSizePt(run)
            if (sizePt > maxSize) maxSize = sizePt
            val text = try {
                run.text()
            } catch (_: Exception) {
                null
            } ?: ""
            val start = sb.length
            val clean = StringBuilder()
            for (ch in text) {
                if (ch == '☐' || ch == '☑') {
                    marks.add(CheckMark(sb.length + clean.length, sizePt, ch == '☑'))
                    clean.append(' ')
                } else {
                    clean.append(ch)
                }
            }
            sb.append(clean)
            val end = sb.length
            if (end > start) {
                val bold = try {
                    run.isBold() || run.isComplexScriptBold()
                } catch (_: Exception) {
                    false
                }
                val italic = try {
                    run.isItalic() || run.isComplexScriptItalic()
                } catch (_: Exception) {
                    false
                }
                if (bold) {
                    // Real B Nazanin Bold when the file loaded; otherwise
                    // synthesized bold on the regular font.
                    if (boldTypeface != null) sb.setSpan(TypefaceSwitchSpan(boldTypeface), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    else sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (italic) sb.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(
                    AbsoluteSizeSpan((sizePt * fontScale).roundToInt(), false),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                try {
                    if (run.underline != null && run.underline != UnderlinePatterns.NONE) {
                        sb.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                } catch (_: Exception) {
                }
                try {
                    val color = run.color
                    if (!color.isNullOrBlank() && !color.equals("auto", ignoreCase = true)) {
                        parseHexColor(color)?.let {
                            sb.setSpan(ForegroundColorSpan(it), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                } catch (_: Exception) {
                }
            }
            // Pictures in this run, best-effort: drawn as blocks under the text.
            try {
                for (pic in run.embeddedPictures) {
                    pictureBlock(pic, width)?.let { pictures.add(it) }
                }
            } catch (_: Exception) {
            }
        }
        if (sb.isEmpty()) return BuiltText(null, marks, pictures, x, width)
        val isRtl = paragraphIsRtl(para, sb)
        val (align, justify) = paragraphAlignment(para)
        // Indents: for RTL paragraphs Word mirrors them — the "left" indent
        // is the indent from the start (right) edge.
        val indLeft = spacingTwips(para.indentationLeft)
        val indRight = spacingTwips(para.indentationRight)
        val adjX = if (isRtl) x + indRight else x + indLeft
        val adjWidth = max(1f, width - indLeft - indRight)
        val (lineMult, lineExtra) = lineSpacing(para)
        val paint = TextPaint().apply {
            typeface = regularTypeface
            textSize = maxSize * fontScale
        }
        val builder = StaticLayout.Builder.obtain(sb, 0, sb.length, paint, adjWidth.toInt())
            .setAlignment(align)
            .setTextDirection(if (isRtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setLineSpacing(lineExtra, lineMult)
            .setIncludePad(true)
        if (justify) builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        return BuiltText(builder.build(), marks, pictures, adjX, adjWidth)
    }

    /** POI returns the size in points (getFontSize() == getFontSizeAsDouble()
     *  for the template's runs — verified with a probe: sz 26 -> 13.0).
     *  The complex-script size covers Persian runs that only set w:szCs. */
    private fun runSizePt(run: XWPFRun): Float {
        return try {
            run.fontSizeAsDouble?.toFloat()
                ?: run.complexScriptFontSizeAsDouble?.toFloat()
                ?: (if (run.fontSize > 0) run.fontSize.toFloat() else DEFAULT_SIZE_PT)
        } catch (_: Exception) {
            DEFAULT_SIZE_PT
        }
    }

    private fun paragraphIsRtl(para: XWPFParagraph, text: CharSequence): Boolean {
        try {
            val pPr = para.ctpPr
            if (pPr?.bidi != null) return true
        } catch (_: Exception) {
        }
        // Fall back to the text itself: Arabic/Persian letters are strong RTL.
        for (i in 0 until text.length) {
            val c = text[i]
            if (c in '\u0590'..'\u08FF' || c in '\uFB50'..'\uFEFF') return true
            if (c.isLetter()) return false
        }
        return true
    }

    private fun paragraphAlignment(para: XWPFParagraph): Pair<Layout.Alignment, Boolean> {
        return when (try {
            para.alignment
        } catch (_: Exception) {
            null
        }) {
            ParagraphAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER to false
            ParagraphAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE to false
            ParagraphAlignment.BOTH, ParagraphAlignment.DISTRIBUTE -> Layout.Alignment.ALIGN_NORMAL to true
            else -> Layout.Alignment.ALIGN_NORMAL to false
        }
    }

    /** (multiplier, extra px per line). POI returns -1.0 for spacingBetween
     *  when the paragraph sets no line spacing; for AUTO the value is a
     *  multiple of single spacing, otherwise a fixed height in twentieths
     *  of a point. */
    private fun lineSpacing(para: XWPFParagraph): Pair<Float, Float> {
        return try {
            val rule = para.spacingLineRule
            val between = para.spacingBetween
            if (rule == null || rule == LineSpacingRule.AUTO) {
                (if (between > 0) between.toFloat() else 1f) to 0f
            } else {
                val fixed = if (between > 0) (between / 20f).toFloat() else 0f
                1f to max(0f, fixed - DEFAULT_SIZE_PT * 1.2f * fontScale)
            }
        } catch (_: Exception) {
            1f to 0f
        }
    }

    /** POI returns -1 when a spacing is not set. Value is in twentieths of
     *  a point. */
    private fun spacingPt(twentieths: Int): Float = if (twentieths > 0) twentieths / 20f * fontScale else 0f

    /** Indents are page geometry, not font size: they do not scale. */
    private fun spacingTwips(twentieths: Int): Float = if (twentieths > 0) twentieths / 20f else 0f

    private fun pictureBlock(pic: XWPFPicture, maxWidth: Float): PictureBlock? {
        return try {
            val data = pic.pictureData?.data ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            val rawW = bounds.outWidth
            val rawH = bounds.outHeight
            if (rawW <= 0 || rawH <= 0) return null
            // Extent from the drawing (EMU -> pt); fall back to the
            // intrinsic size at 96 dpi.
            var wPt = 0f
            var hPt = 0f
            try {
                val ext = pic.ctPicture?.spPr?.xfrm?.ext
                if (ext != null) {
                    wPt = ext.cx / 12700f
                    hPt = ext.cy / 12700f
                }
            } catch (_: Exception) {
            }
            if (wPt <= 0 || hPt <= 0) {
                wPt = rawW * 72f / 96f
                hPt = rawH * 72f / 96f
            }
            val scale = min(1f, maxWidth / wPt)
            val targetW = wPt * scale
            val targetH = hPt * scale
            // Decode at a bounded size so a huge photo cannot OOM the app.
            val sample = max(1, min(rawW / max(1, targetW.toInt()), rawH / max(1, targetH.toInt())))
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options) ?: return null
            PictureBlock(bitmap, targetW, targetH)
        } catch (_: Throwable) {
            null
        }
    }

    // ==================== Drawing: flow + bands ====================

    /** Draws a measured paragraph at the current y, flowing onto new pages
     *  as needed. */
    private fun drawFlow(content: ParaContent) {
        val totalH = content.height
        var consumed = 0f
        while (consumed < totalH - 0.01f) {
            if (contentBottom - y <= 1f) newPage()
            val avail = contentBottom - y
            val slice = min(avail, totalH - consumed)
            drawBlocks(content.blocks, content.x, content.width, y - consumed, y, y + slice)
            y += slice
            consumed += slice
        }
    }

    /** Draws a list of blocks whose virtual column starts at [columnTop]
     *  (which may be above the band when the column continues from a
     *  previous page), painting only the part inside [bandTop, bandBottom].
     *  [x]/[width] is the column the blocks are laid out in. */
    private fun drawBlocks(
        blocks: List<ContentBlock>,
        x: Float,
        width: Float,
        columnTop: Float,
        bandTop: Float,
        bandBottom: Float
    ) {
        var vo = 0f
        val c = canvas ?: return
        for (b in blocks) {
            val absTop = columnTop + vo
            val absBottom = absTop + b.height
            if (absBottom > bandTop && absTop < bandBottom) {
                when (b) {
                    is TextBlock -> drawLayoutBand(b.layout, x, absTop, bandTop, bandBottom, b.marks)
                    is PictureBlock -> {
                        val left = x + (width - b.width) / 2f
                        c.save()
                        c.clipRect(x, bandTop, x + width, bandBottom)
                        c.drawBitmap(b.bitmap, null, RectF(left, absTop, left + b.width, absTop + b.height), null)
                        c.restore()
                    }
                    is GapBlock -> {
                    }
                }
            }
            vo += b.height
        }
    }

    /** Draws a text layout whose top is at [layoutTop], clipped to the
     *  vertical band, plus the hand-drawn checkbox squares for its ☐ marks. */
    private fun drawLayoutBand(
        layout: StaticLayout,
        x: Float,
        layoutTop: Float,
        bandTop: Float,
        bandBottom: Float,
        marks: List<CheckMark>
    ) {
        val c = canvas ?: return
        c.save()
        c.clipRect(x, bandTop, x + layout.width, bandBottom)
        c.translate(x, layoutTop)
        layout.draw(c)
        c.restore()
        for (m in marks) {
            if (m.offset >= layout.text.length) continue
            val line = try {
                layout.getLineForOffset(m.offset)
            } catch (_: Exception) {
                continue
            }
            val lt = layout.getLineTop(line)
            val lb = layout.getLineBottom(line)
            val absTop = layoutTop + lt
            val absBottom = layoutTop + lb
            if (absBottom <= bandTop || absTop >= bandBottom) continue
            val x0 = layout.getPrimaryHorizontal(m.offset)
            val x1 = layout.getPrimaryHorizontal(min(m.offset + 1, layout.text.length))
            val cx = x + (x0 + x1) / 2f
            val side = m.sizePt * fontScale * 0.85f
            val cy = layoutTop + (lt + lb) / 2f
            drawCheckSquare(cx, cy, side, m.checked)
        }
    }

    private fun drawCheckSquare(cx: Float, cy: Float, side: Float, checked: Boolean) {
        val c = canvas ?: return
        val half = side / 2f
        val box = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = max(1f, side * 0.12f)
            isAntiAlias = true
        }
        c.drawRect(cx - half, cy - half, cx + half, cy + half, box)
        if (checked) {
            val check = Paint().apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = max(1.2f, side * 0.14f)
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path().apply {
                moveTo(cx - half * 0.55f, cy)
                lineTo(cx - half * 0.1f, cy + half * 0.45f)
                lineTo(cx + half * 0.6f, cy - half * 0.5f)
            }
            c.drawPath(path, check)
        }
    }

    // ==================== Tables ====================

    private data class Border(val widthPt: Float, val color: Int)

    /** 0 = top, 1 = center, 2 = bottom. */
    private class MeasuredCell(
        val paras: List<ParaContent>,
        val contentHeight: Float,
        val x: Float,
        val width: Float,
        val vAlign: Int,
        val shade: Int?,
        val bTop: Border?,
        val bBottom: Border?,
        val bLeft: Border?,
        val bRight: Border?
    )

    private class MeasuredRow(val cells: List<MeasuredCell>, val height: Float, val minHeight: Float, val vPadTop: Float, val vPadBottom: Float)

    private fun drawTable(table: XWPFTable) {
        val rows = try {
            table.rows
        } catch (_: Exception) {
            return
        }
        if (rows.isEmpty()) return
        val ctTbl = try {
            table.ctTbl
        } catch (_: Exception) {
            return
        }
        val tblPr = try {
            ctTbl.tblPr
        } catch (_: Exception) {
            null
        }
        val colCount = rows.maxOfOrNull { it.tableCells.size } ?: return
        if (colCount == 0) return
        // Table width: tblW in dxa, or the full content width when "auto".
        var tblW = contentWidth
        try {
            val w = tblPr?.tblW
            if (w != null && w.type?.toString() == "dxa") {
                val v = numberValue(w.w) / 20f
                if (v > 0) tblW = v
            }
        } catch (_: Exception) {
        }
        // Column widths from tblGrid (dxa), scaled to tblW; equal split when
        // the document has no grid.
        val grid = try {
            ctTbl.tblGrid?.gridColList?.map { numberValue(it.w) / 20f } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val colWidths: List<Float> = if (grid.isEmpty()) {
            List(colCount) { tblW / colCount }
        } else {
            val totalGrid = grid.sum().takeIf { it > 0 } ?: tblW
            val scale = tblW / totalGrid
            val scaled = grid.map { it * scale }
            if (scaled.size < colCount) scaled + List(colCount - scaled.size) { tblW / colCount } else scaled
        }
        val bidi = try {
            tblPr?.bidiVisual != null
        } catch (_: Exception) {
            false
        }
        val tblInd = try {
            numberValue(tblPr?.tblInd?.w) / 20f
        } catch (_: Exception) {
            0f
        }
        // In an RTL (bidi) table the indent is from the right edge and the
        // first cell sits at the right; otherwise from the left.
        val tableLeft = if (bidi) contentRight - tblInd - tblW else contentLeft + tblInd
        val colLefts = mutableListOf<Float>()
        if (bidi) {
            var x = tableLeft + tblW
            for (w in colWidths) {
                x -= w
                colLefts.add(x)
            }
        } else {
            var x = tableLeft
            for (w in colWidths) {
                colLefts.add(x)
                x += w
            }
        }
        val tblBorders = try {
            tblPr?.tblBorders
        } catch (_: Exception) {
            null
        }
        // Cell margins from tblCellMar (Word's defaults: left/right 108 dxa,
        // top/bottom 0); a minimum 2pt pad keeps text off the ruling.
        val cellMar = try {
            tblPr?.tblCellMar
        } catch (_: Exception) {
            null
        }
        val marL = numberValue(cellMar?.left?.w, 108f) / 20f
        val marR = numberValue(cellMar?.right?.w, 108f) / 20f
        val marT = numberValue(cellMar?.top?.w, 0f) / 20f
        val marB = numberValue(cellMar?.bottom?.w, 0f) / 20f
        for ((ri, row) in rows.withIndex()) {
            val measured = measureRow(
                row, colLefts, colWidths, bidi, tblBorders,
                marL, marR, marT, marB,
                isFirstRow = ri == 0, isLastRow = ri == rows.size - 1
            )
            drawRow(measured)
        }
    }

    private fun measureRow(
        row: XWPFTableRow,
        colLefts: List<Float>,
        colWidths: List<Float>,
        bidi: Boolean,
        tblBorders: CTTblBorders?,
        marL: Float,
        marR: Float,
        marT: Float,
        marB: Float,
        isFirstRow: Boolean,
        isLastRow: Boolean
    ): MeasuredRow {
        val cells = try {
            row.tableCells
        } catch (_: Exception) {
            emptyList()
        }
        val measured = mutableListOf<MeasuredCell>()
        var startCol = 0
        var vPadTop = max(2f, marT)
        var vPadBottom = max(2f, marB)
        for (cell in cells) {
            val tcPr = try {
                cell.ctTc?.tcPr
            } catch (_: Exception) {
                null
            }
            val span = try {
                numberValue(tcPr?.gridSpan?.`val`).toInt().coerceIn(1, max(1, colWidths.size - startCol))
            } catch (_: Exception) {
                1
            }
            val endCol = min(startCol + span, colWidths.size)
            val cellW = colWidths.subList(startCol, endCol).sum()
            val cellLeft = if (bidi) colLefts[endCol - 1] else colLefts[startCol]
            // Physical edges (for the tblBorders fallback): in a bidi table
            // the first cell is at the right edge.
            val atLeftEdge = if (bidi) endCol >= colWidths.size else startCol == 0
            val atRightEdge = if (bidi) startCol == 0 else endCol >= colWidths.size
            startCol += span
            // Per-cell margins override the table's.
            val tcMar = try {
                tcPr?.tcMar
            } catch (_: Exception) {
                null
            }
            val cMarL = if (tcMar?.left != null) numberValue(tcMar.left.w) / 20f else marL
            val cMarR = if (tcMar?.right != null) numberValue(tcMar.right.w) / 20f else marR
            val cMarT = if (tcMar?.top != null) numberValue(tcMar.top.w) / 20f else marT
            val cMarB = if (tcMar?.bottom != null) numberValue(tcMar.bottom.w) / 20f else marB
            val hPadL = max(2f, cMarL)
            val hPadR = max(2f, cMarR)
            vPadTop = max(vPadTop, max(2f, cMarT))
            vPadBottom = max(vPadBottom, max(2f, cMarB))
            val contentX = cellLeft + hPadL
            val contentW = max(1f, cellW - hPadL - hPadR)
            val paras = mutableListOf<ParaContent>()
            try {
                for (para in cell.paragraphs) paras.add(measureParagraph(para, contentX, contentW))
            } catch (_: Exception) {
            }
            if (paras.isEmpty()) {
                paras.add(ParaContent(listOf(GapBlock(DEFAULT_SIZE_PT * 1.2f * fontScale)), contentX, contentW))
            }
            val contentH = paras.sumOf { it.height.toDouble() }.toFloat()
            val tcB = try {
                tcPr?.tcBorders
            } catch (_: Exception) {
                null
            }
            val shade = try {
                val fill = hexColorString(tcPr?.shd?.fill)
                if (fill.isNullOrBlank() || fill.equals("auto", ignoreCase = true)) null else parseHexColor(fill)
            } catch (_: Exception) {
                null
            }
            val vAlign = try {
                when (tcPr?.vAlign?.`val`?.toString()) {
                    "center" -> 1
                    "bottom" -> 2
                    else -> 0
                }
            } catch (_: Exception) {
                0
            }
            measured.add(
                MeasuredCell(
                    paras = paras,
                    contentHeight = contentH,
                    x = cellLeft,
                    width = cellW,
                    vAlign = vAlign,
                    shade = shade,
                    bTop = borderFrom(tcB?.top, if (isFirstRow) tblBorders?.top else tblBorders?.insideH),
                    bBottom = borderFrom(tcB?.bottom, if (isLastRow) tblBorders?.bottom else tblBorders?.insideH),
                    bLeft = borderFrom(tcB?.left, if (atLeftEdge) tblBorders?.left else tblBorders?.insideV),
                    bRight = borderFrom(tcB?.right, if (atRightEdge) tblBorders?.right else tblBorders?.insideV)
                )
            )
        }
        val contentH = measured.maxOfOrNull { it.contentHeight } ?: 0f
        return MeasuredRow(measured, contentH + vPadTop + vPadBottom, rowMinHeight(row), vPadTop, vPadBottom)
    }

    /** w:trHeight w:val is in twips; it is a minimum height (the sample's
     *  "توضیحات" row is 4855 twips = 242pt). */
    private fun rowMinHeight(row: XWPFTableRow): Float {
        return try {
            val heights = row.ctRow?.trPr?.trHeightList ?: return 0f
            if (heights.isEmpty()) 0f else numberValue(heights[0].`val`) / 20f
        } catch (_: Exception) {
            0f
        }
    }

    private fun drawRow(row: MeasuredRow) {
        val h = max(row.height, row.minHeight)
        var consumed = 0f
        var firstSlice = true
        while (consumed < h - 0.01f) {
            if (contentBottom - y <= 1f) newPage()
            val avail = contentBottom - y
            val slice = min(avail, h - consumed)
            val bandTop = y
            val bandBottom = y + slice
            val isLast = consumed + slice >= h - 0.01f
            val c = canvas ?: return
            for (cell in row.cells) {
                cell.shade?.let {
                    c.drawRect(
                        cell.x, bandTop, cell.x + cell.width, bandBottom,
                        Paint().apply { color = it; style = Paint.Style.FILL }
                    )
                }
                cell.bLeft?.let { drawVLine(cell.x, bandTop, bandBottom, it) }
                cell.bRight?.let { drawVLine(cell.x + cell.width, bandTop, bandBottom, it) }
                if (firstSlice) cell.bTop?.let { drawHLine(cell.x, cell.x + cell.width, bandTop, it) }
                if (isLast) cell.bBottom?.let { drawHLine(cell.x, cell.x + cell.width, bandBottom, it) }
                val contentAreaH = h - row.vPadTop - row.vPadBottom
                val vOff = when (cell.vAlign) {
                    1 -> max(0f, (contentAreaH - cell.contentHeight) / 2f)
                    2 -> max(0f, contentAreaH - cell.contentHeight)
                    else -> 0f
                }
                val columnTop = bandTop - consumed + row.vPadTop + vOff
                var paraOffset = 0f
                for (para in cell.paras) {
                    drawBlocks(para.blocks, para.x, para.width, columnTop + paraOffset, bandTop, bandBottom)
                    paraOffset += para.height
                }
            }
            y += slice
            consumed += slice
            firstSlice = false
        }
    }

    private fun drawHLine(x0: Float, x1: Float, yPos: Float, b: Border) {
        val c = canvas ?: return
        c.drawLine(
            x0, yPos, x1, yPos,
            Paint().apply { color = b.color; strokeWidth = b.widthPt; style = Paint.Style.STROKE; isAntiAlias = true }
        )
    }

    private fun drawVLine(xPos: Float, y0: Float, y1: Float, b: Border) {
        val c = canvas ?: return
        c.drawLine(
            xPos, y0, xPos, y1,
            Paint().apply { color = b.color; strokeWidth = b.widthPt; style = Paint.Style.STROKE; isAntiAlias = true }
        )
    }

    /** A cell's own tcBorders win; otherwise the table's outer border on
     *  the table's edge, or its inside border between cells. "nil"/"none"
     *  means no border at all (the header table's cells are borderless
     *  even though the table declares single borders). */
    private fun borderFrom(own: CTBorder?, fallback: CTBorder?): Border? {
        val b = own ?: fallback ?: return null
        val v = try {
            b.`val`?.toString()
        } catch (_: Exception) {
            null
        } ?: return null
        if (v == "nil" || v == "none" || v == "hidden") return null
        val sz = try {
            numberValue(b.sz, 4f)
        } catch (_: Exception) {
            4f
        }
        val widthPt = max(0.5f, sz / 8f)
        val color = try {
            val c = hexColorString(b.color)
            if (c.isNullOrBlank() || c.equals("auto", ignoreCase = true)) Color.BLACK else parseHexColor(c) ?: Color.BLACK
        } catch (_: Exception) {
            Color.BLACK
        }
        return Border(widthPt, color)
    }

    // ==================== XML value helpers ====================

    /** XmlBeans returns these numbers as Object (BigInteger in practice). */
    private fun numberValue(v: Any?, default: Float = 0f): Float {
        return when (v) {
            null -> default
            is Number -> v.toFloat()
            else -> v.toString().toFloatOrNull() ?: default
        }
    }

    /** Hex colors come back as byte[] ("F0F0F0") or the string "auto". */
    private fun hexColorString(v: Any?): String? {
        return when (v) {
            null -> null
            is ByteArray -> String(v, Charsets.UTF_8)
            else -> v.toString()
        }
    }

    private fun parseHexColor(hex: String): Int? {
        return try {
            val h = hex.trim().removePrefix("#")
            when (h.length) {
                6 -> Color.parseColor("#$h")
                3 -> Color.parseColor("#${h[0]}${h[0]}${h[1]}${h[1]}${h[2]}${h[2]}")
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
