package com.nezammonitor.app.ui.screens.reportgen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Prints an existing PDF file (e.g. a generated report PDF) via the
 *  framework PrintManager. The file's own bytes are the print document,
 *  so nothing is re-rendered and the print has the PDF's own quality.
 *  No new dependency. */
fun printPdfFile(context: Context, file: File, jobName: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    printManager.print(
        jobName,
        PdfFilePrintAdapter(file),
        PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
    )
}

private class PdfFilePrintAdapter(private val file: File) : PrintDocumentAdapter() {
    private var pageCount = 0

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: android.os.Bundle?
    ) {
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer -> pageCount = renderer.pageCount }
            }
        } catch (_: Exception) {
            pageCount = 1
        }
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(file.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pageCount)
            .build()
        callback?.onLayoutFinished(info, newAttributes != oldAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        try {
            FileInputStream(file).use { input ->
                FileOutputStream(destination.fileDescriptor).use { output -> input.copyTo(output) }
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (_: Exception) {
            callback?.onWriteFailed(null)
        }
    }
}

/** Prints an image file (e.g. a downloaded server report photo) as one A4
 *  page, scaled to fit the page while keeping its aspect ratio. */
fun printImageFile(context: Context, file: File, jobName: String) {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    printManager.print(
        jobName,
        ImagePrintAdapter(bitmap, file.name),
        PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
    )
}

private class ImagePrintAdapter(private val bitmap: Bitmap, private val name: String) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: android.os.Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
            .setPageCount(1)
            .build()
        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        try {
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = doc.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            // Fit the image inside the page with margins, keeping aspect ratio.
            val margin = 36f
            val maxW = 595f - 2 * margin
            val maxH = 842f - 2 * margin
            val scale = minOf(maxW / bitmap.width, maxH / bitmap.height)
            val w = bitmap.width * scale
            val h = bitmap.height * scale
            val left = (595f - w) / 2f
            val top = (842f - h) / 2f
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), Paint())
            doc.finishPage(page)
            FileOutputStream(destination.fileDescriptor).use { doc.writeTo(it) }
            doc.close()
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (_: Exception) {
            callback?.onWriteFailed(null)
        }
    }
}
