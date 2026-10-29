package com.sammyi57.pdftools

import android.net.Uri
import androidx.pdf.PdfPasswordException
import androidx.pdf.SandboxedPdfLoader
import androidx.pdf.content.PdfPageTextContent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

// The in-app viewer's engine on a real phone (opening, search, selectable text, passwords) and pinned tools.
// ./gradlew :app:connectedDebugAndroidTest -PsideBySide
@RunWith(AndroidJUnit4::class)
class ViewerTest {
  @Before
  fun setUp() {
    appContext = InstrumentationRegistry.getInstrumentation().targetContext
    PDFBoxResourceLoader.init(appContext)
  }

  private fun pdf(name: String, vararg pages: String, password: String? = null): File {
    val bytes = PDDocument().use { doc ->
      for (t in pages) {
        val p = PDPage()
        doc.addPage(p)
        PDPageContentStream(doc, p).use { cs ->
          cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 18f); cs.newLineAtOffset(72f, 700f); cs.showText(t); cs.endText()
        }
      }
      doc.bytes()
    }
    return File(appContext.cacheDir, name).apply { writeBytes(if (password != null) setPdfPassword(bytes, password)!! else bytes) }
  }

  @Test fun supportedOnThisPhone() = assertTrue("Jetpack PDF needs Android 12 with PDF extension 13+", Viewer.supported)

  @Test fun opensSearchesAndSelects() = runBlocking {
    val f = pdf("viewer-test.pdf", "Invoice No. 1 for March", "Notes only", "Invoice No. 2 for April")
    SandboxedPdfLoader(appContext).openDocument(Uri.fromFile(f), null).use { doc ->
      assertEquals(3, doc.pageCount)
      // Search: hits on the two invoice pages, none on the notes page, with somewhere to highlight.
      val hits = doc.searchDocument("invoice", 0 until doc.pageCount)
      assertEquals(listOf(0, 2), (0 until hits.size()).map { hits.keyAt(it) }.sorted())
      assertTrue(hits.get(0).first().bounds.isNotEmpty())
      // Selectable text: what Select all, then Copy, would put on the clipboard.
      val all = doc.getSelectAllSelectionBounds(2)
      val text = all?.selectedContents?.filterIsInstance<PdfPageTextContent>()?.joinToString(" ") { it.text }.orEmpty()
      assertTrue("selected: $text", text.contains("Invoice No. 2 for April"))
    }
  }

  @Test fun passwordProtectedNeedsItsPassword() = runBlocking {
    val f = pdf("viewer-locked.pdf", "Secret page", password = "open-sesame")
    val loader = SandboxedPdfLoader(appContext)
    assertThrows(PdfPasswordException::class.java) { runBlocking { loader.openDocument(Uri.fromFile(f), null).close() } }
    assertThrows(PdfPasswordException::class.java) { runBlocking { loader.openDocument(Uri.fromFile(f), "wrong").close() } }
    loader.openDocument(Uri.fromFile(f), "open-sesame").use { assertEquals(1, it.pageCount) }
  }

  @Test fun pinsKeepTheirOrderAndPersist() {
    val before = Pins.ids
    try {
      before.forEach { Pins.toggle(it) } // start empty
      Pins.toggle("compress"); Pins.toggle("merge")
      assertEquals(listOf("compress", "merge"), Pins.ids)
      assertEquals("compress\nmerge", File(appContext.filesDir, "pinned-tools.txt").readText())
      Pins.toggle("compress")
      assertEquals(listOf("merge"), Pins.ids)
    } finally {
      Pins.ids.forEach { Pins.toggle(it) }
      before.forEach { Pins.toggle(it) }
    }
  }
}
