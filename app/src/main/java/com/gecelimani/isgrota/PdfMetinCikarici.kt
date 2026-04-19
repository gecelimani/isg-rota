package com.gecelimani.isgrota

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import android.graphics.RectF
import java.io.File

object PdfMetinCikarici {

    fun metniCikar(pdfFile: File, pageIndex: Int): String {
        var document: PDDocument? = null
        try {
            document = PDDocument.load(pdfFile)
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
            
            return stripper.getText(document) ?: ""
        } catch (e: Exception) {
            return ""
        } finally {
            document?.close()
        }
    }

    fun bolgeMetniCikar(pdfFile: File, pageIndex: Int, rect: RectF): String {
        var document: PDDocument? = null
        try {
            document = PDDocument.load(pdfFile)
            val page = document.getPage(pageIndex)
            
            val stripper = PDFTextStripperByArea()
            stripper.sortByPosition = true
            
            // PDF koordinatları (72 DPI) ile Android koordinatları (Bitmap boyutu) arasındaki dönüşüm
            val pdfBox = page.mediaBox
            val pdfWidth = pdfBox.width
            val pdfHeight = pdfBox.height
            
            // Android RectF (0..1) -> PDF Koord
            val left = rect.left * pdfWidth
            val top = rect.top * pdfHeight
            val width = rect.width() * pdfWidth
            val height = rect.height() * pdfHeight
            
            // pdfbox-android'de addRegion genellikle android.graphics.RectF bekler
            val region = android.graphics.RectF(left, top, left + width, top + height)
            
            stripper.addRegion("soru_alani", region)
            stripper.extractRegions(page)
            
            return stripper.getTextForRegion("soru_alani") ?: ""
        } catch (e: Exception) {
            return ""
        } finally {
            document?.close()
        }
    }
}
