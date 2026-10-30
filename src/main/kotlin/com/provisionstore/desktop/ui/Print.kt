package com.provisionstore.desktop.ui

import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.print.PrinterJob

/**
 * Prints plain text lines through the Windows print dialog. Reports are short enough to fit
 * on one page; a longer list is cut at the page edge.
 */
fun printLines(lines: List<String>) {
    val job = PrinterJob.getPrinterJob()
    job.setPrintable(Printable { graphics: Graphics, format: PageFormat, page: Int ->
        if (page > 0) return@Printable Printable.NO_SUCH_PAGE
        val g = graphics as Graphics2D
        g.translate(format.imageableX, format.imageableY)
        g.font = Font(Font.MONOSPACED, Font.PLAIN, 10)
        var y = 12
        lines.forEach { line ->
            g.drawString(line, 0, y)
            y += 14
        }
        Printable.PAGE_EXISTS
    })
    if (job.printDialog()) job.print()
}
