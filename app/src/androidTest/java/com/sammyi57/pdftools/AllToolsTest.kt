package com.sammyi57.pdftools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.random.Random

// Every tool in the catalog, run through its real engine on PDFs built here, with the output checked: page counts,
// sizes, rotation, order, text and placement. Runs on a phone next to the real app:
// ./gradlew :app:connectedDebugAndroidTest -PsideBySide
@RunWith(AndroidJUnit4::class)
class AllToolsTest {
  @Before
  fun setUp() {
    appContext = InstrumentationRegistry.getInstrumentation().targetContext
    PDFBoxResourceLoader.init(appContext)
  }

  // ── Fixtures ──────────────────────────────────────────────────────────────

  private val a4 = PDRectangle.A4

  /** Pages with the given text; page 3 landscape, the last turned 90°, bookmarks on 1/3/5, a big photo on page 1. */
  private fun sample(texts: List<String> = (1..6).map { "Page $it" + if ((it - 1) % 3 == 0) " Invoice No. $it" else "" }, rich: Boolean = true): ByteArray =
    PDDocument().use { doc ->
      texts.forEachIndexed { i, t ->
        val p = PDPage(if (rich && i == 2) PDRectangle(a4.height, a4.width) else a4)
        doc.addPage(p)
        PDPageContentStream(doc, p).use { cs ->
          if (rich && i == 0) cs.drawImage(JPEGFactory.createFromByteArray(doc, noisyJpeg()), 50f, 50f, 495f, 370f)
          cs.beginText()
          cs.setFont(PDType1Font.HELVETICA, 24f)
          cs.newLineAtOffset(72f, p.mediaBox.height - 120f)
          cs.showText(t)
          cs.endText()
        }
      }
      if (rich) {
        doc.getPage(texts.size - 1).rotation = 90
        val outline = PDDocumentOutline()
        listOf("Chapter A" to 0, "Chapter B" to 2, "Chapter C" to 4).forEach { (title, page) ->
          outline.addLast(PDOutlineItem().apply { this.title = title; destination = PDPageFitDestination().apply { setPage(doc.getPage(page)) } })
        }
        doc.documentCatalog.documentOutline = outline
      }
      doc.bytes()
    }

  private fun noisyJpeg(): ByteArray {
    val bmp = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
    val r = Random(7)
    val c = Canvas(bmp)
    val paint = Paint()
    for (y in 0 until 1200 step 8) for (x in 0 until 1600 step 8) {
      paint.color = android.graphics.Color.rgb(r.nextInt(256), r.nextInt(256), r.nextInt(256))
      c.drawRect(x.toFloat(), y.toFloat(), x + 8f, y + 8f, paint)
    }
    return ByteArrayOutputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it); it.toByteArray() }
  }

  private fun tool(id: String) = toolById(id) ?: error("no tool $id")
  private fun opts(vararg kv: Pair<String, Any>) = JsonObject(kv.associate { (k, v) ->
    k to when (v) { is Boolean -> JsonPrimitive(v); is Number -> JsonPrimitive(v); else -> JsonPrimitive(v.toString()) }
  })
  private fun run(id: String, o: Opts = NoOpts, vararg files: ByteArray) =
    tool(id).run(files.mapIndexed { i, b -> Named("doc${i + 1}.pdf", b) }, o)

  private fun <T> open(b: ByteArray, fn: (PDDocument) -> T) = PDDocument.load(b).use(fn)
  private fun pages(b: ByteArray) = open(b) { it.numberOfPages }
  private fun text(b: ByteArray, page: Int? = null) = open(b) { d ->
    PDFTextStripper().apply { if (page != null) { startPage = page + 1; endPage = page + 1 } }.getText(d)
  }

  /**
   * Where each glyph of `needle` starts, from the crop box's lower-left corner (PDFBox reports them that way). With
   * `anyOf`, every glyph whose character is in `needle`, for text that extraction splits up (a diagonal stamp).
   */
  private fun glyphs(b: ByteArray, page: Int, needle: String, anyOf: Boolean = false): List<Pair<Float, Float>> = open(b) { d ->
    val out = ArrayList<Pair<Float, Float>>()
    object : PDFTextStripper() {
      override fun writeString(text: String, positions: MutableList<TextPosition>) {
        for (p in positions) {
          if (if (anyOf) p.unicode.isNotEmpty() && p.unicode[0] in needle else needle in text) out += p.textMatrix.translateX to p.textMatrix.translateY
        }
      }
    }.apply { startPage = page + 1; endPage = page + 1 }.getText(d)
    out
  }

  private fun near(expected: Float, actual: Float, tol: Float, what: String) = assertTrue("$what: expected ~$expected, got $actual", abs(expected - actual) <= tol)

  // ── Combine & split ───────────────────────────────────────────────────────

  @Test fun merge() {
    val out = run("merge", NoOpts, sample(), sample(listOf("B1", "B2"), rich = false))
    assertEquals(1, out.size)
    assertEquals(8, pages(out[0].bytes))
    assertTrue(text(out[0].bytes, 6).contains("B1"))
  }

  @Test fun alternate() {
    val a = sample(listOf("A1", "A2", "A3"), rich = false)
    val b = sample(listOf("B1", "B2", "B3"), rich = false)
    val plain = run("alternate", opts("reverse" to false), a, b)[0].bytes
    assertEquals(listOf("A1", "B1", "A2", "B2", "A3", "B3"), (0 until 6).map { text(plain, it).trim() })
    val rev = run("alternate", opts("reverse" to true), a, b)[0].bytes
    assertEquals(listOf("A1", "B3", "A2", "B2", "A3", "B1"), (0 until 6).map { text(rev, it).trim() })
  }

  @Test fun splitEveryPageAndRanges() {
    assertEquals(List(6) { 1 }, run("split", opts("mode" to "every", "ranges" to ""), sample()).map { pages(it.bytes) })
    assertEquals(listOf(2, 1, 2), run("split", opts("mode" to "ranges", "ranges" to "1-2, 4, 5-"), sample()).map { pages(it.bytes) })
    assertThrows(UserError::class.java) { run("split", opts("mode" to "ranges", "ranges" to "9"), sample()) }
  }

  @Test fun splitByText() {
    val parts = run("splitText", opts("phrase" to "invoice no."), sample())
    assertEquals(listOf(3, 3), parts.map { pages(it.bytes) })
    assertThrows(UserError::class.java) { run("splitText", opts("phrase" to "nowhere"), sample()) }
  }

  @Test fun splitByBookmarks() {
    val parts = run("splitBookmarks", NoOpts, sample())
    assertEquals(listOf("Chapter A", "Chapter B", "Chapter C"), parts.map { it.name })
    assertEquals(listOf(2, 2, 2), parts.map { pages(it.bytes) })
    assertThrows(UserError::class.java) { run("splitBookmarks", NoOpts, sample(rich = false)) }
  }

  @Test fun splitInHalf() {
    assertEquals(listOf(3, 3), run("splitHalf", opts("mode" to "file"), sample()).map { pages(it.bytes) })
    val sliced = run("splitHalf", opts("mode" to "vertical"), sample())[0].bytes
    assertEquals(12, pages(sliced))
    open(sliced) { near(a4.width / 2, it.getPage(0).cropBox.width, 0.5f, "left half width") }
    assertThrows(UserError::class.java) { run("splitHalf", opts("mode" to "file"), sample(listOf("one"), rich = false)) }
  }

  @Test fun splitBySize() {
    val src = sample()
    val parts = run("splitSize", opts("maxMB" to "0.05"), src)
    assertTrue("several parts", parts.size >= 2)
    assertEquals(6, parts.sumOf { pages(it.bytes) })
    parts.filter { pages(it.bytes) > 1 }.forEach { assertTrue("multi-page part under the limit", it.bytes.size <= 0.05 * 1024 * 1024) }
    assertThrows(UserError::class.java) { run("splitSize", opts("maxMB" to "0"), src) }
  }

  @Test fun zip() {
    val z = tool("zip").run(listOf(Named("a.pdf", sample()), Named("a.pdf", sample())), NoOpts) // same name twice
    assertEquals("zip", z[0].ext)
    val names = ZipInputStream(z[0].bytes.inputStream()).use { s -> generateSequence { s.nextEntry?.name }.toList() }
    assertEquals(2, names.size)
    assertEquals(2, names.toSet().size)
  }

  // ── Arrange pages ─────────────────────────────────────────────────────────

  @Test fun organize() {
    val order = listOf(PageOrder(5), PageOrder(4, rot = 90), PageOrder(3, del = true), PageOrder(2), PageOrder(1), PageOrder(0))
    val out = run("organize", NoOpts.put("order", order), sample())[0].bytes
    assertEquals(5, pages(out))
    // Sideways pages come out of text extraction split ("P\nage 6"), so compare with whitespace dropped.
    val got = (0 until 5).map { text(out, it).filterNot(Char::isWhitespace) }
    assertEquals(listOf("Page6", "Page5", "Page3", "Page2", "Page1InvoiceNo.1"), got)
    open(out) { assertEquals(90, it.getPage(1).rotation) }
  }

  @Test fun rotate() {
    val out = run("rotate", opts("angle" to "90", "pages" to "1, 3-4"), sample())[0].bytes
    open(out) { d -> assertEquals(listOf(90, 0, 90, 90, 0, 90), d.pages.map { it.rotation }) }
    val all = run("rotate", opts("angle" to "180", "pages" to ""), sample())[0].bytes
    open(all) { d -> assertEquals(listOf(180, 180, 180, 180, 180, 270), d.pages.map { it.rotation }) }
  }

  @Test fun flip() {
    val out = run("flip", opts("axis" to "both"), sample())[0].bytes
    open(out) { d ->
      assertEquals(6, d.numberOfPages)
      assertEquals(90, d.getPage(5).rotation) // keeps its turn
      near(a4.height, d.getPage(2).mediaBox.width, 0.5f, "landscape page stays landscape")
    }
  }

  @Test fun nUp() {
    assertEquals(2, pages(run("nup", opts("perSheet" to "4", "lines" to true), sample())[0].bytes))
    assertEquals(3, pages(run("nup", opts("perSheet" to "2", "lines" to false), sample())[0].bytes))
    assertEquals(1, pages(run("nup", opts("perSheet" to "16", "lines" to false), sample())[0].bytes))
  }

  @Test fun resizeToA5() {
    val (w, h) = SIZES.getValue("A5")
    val out = run("crop", opts("mode" to "resize", "size" to "A5"), sample())[0].bytes
    open(out) { d ->
      d.pages.forEachIndexed { i, p ->
        val landscape = i == 2 || i == 5 // page 3 is landscape; the last is portrait turned 90°
        near(if (landscape) h else w, p.mediaBox.width, 0.5f, "page ${i + 1} width")
        near(if (landscape) w else h, p.mediaBox.height, 0.5f, "page ${i + 1} height")
      }
    }
  }

  @Test fun crop() {
    val out = run("crop", NoOpts.with("mode", "crop").put("boxes", mapOf("0" to listOf(0.1f, 0.1f, 0.5f, 0.5f))), sample())[0].bytes
    open(out) { d ->
      val cb = d.getPage(0).cropBox
      near(a4.width * 0.5f, cb.width, 0.5f, "crop width")
      near(a4.width * 0.1f, cb.lowerLeftX, 0.5f, "crop left")
      near(a4.height * 0.4f, cb.lowerLeftY, 0.5f, "crop bottom (y up)")
      near(a4.width, d.getPage(1).cropBox.width, 0.5f, "uncropped page untouched")
    }
    assertThrows(UserError::class.java) { run("crop", opts("mode" to "crop"), sample()) }
  }

  // ── Fill & mark up ────────────────────────────────────────────────────────

  private fun cropped() = run("crop", NoOpts.with("mode", "crop").put("boxes", mapOf("0" to listOf(0.1f, 0.1f, 0.5f, 0.5f))), sample())[0].bytes

  @Test fun watermarkCentredOnVisiblePage() {
    // Letters found nowhere else on the page, so the stamp's glyphs can be picked out one by one.
    val out = run("watermark", opts("text" to "XQXQXQXQ", "opacity" to "0.2", "scale" to "1"), cropped())[0].bytes
    assertTrue(text(out, 1).filterNot(Char::isWhitespace).contains("XQXQXQXQ"))
    // On a cropped page the stamp sits on what's shown (the crop box), not the full sheet.
    val g = glyphs(out, 0, "XQ", anyOf = true)
    assertTrue(g.isNotEmpty())
    val cx = g.map { it.first }.average().toFloat()
    val cy = g.map { it.second }.average().toFloat()
    // The crop box is half the sheet each way, so its centre is a quarter of the sheet from its corner. Centred on the
    // full sheet instead, it would be half the sheet away.
    near(a4.width * 0.25f, cx, 45f, "watermark centre x")
    near(a4.height * 0.25f, cy, 45f, "watermark centre y")
    assertThrows(UserError::class.java) { run("watermark", opts("text" to "  ", "opacity" to "0.2", "scale" to "1"), sample()) }
  }

  @Test fun pageNumbers() {
    val out = run("pageNumbers", opts("position" to "right", "format" to "of"), sample())[0].bytes
    assertTrue(text(out, 5).contains("Page 6 of 6"))
    assertTrue(text(out, 0).contains("Page 1 of 6"))
  }

  @Test fun fillSignLandsWhereShownOnCroppedPage() {
    val items = listOf(SignItem(page = 0, kind = "text", x = 0.5f, y = 0.5f, size = 0.03f, text = "Signed here"))
    val out = run("fillSign", NoOpts.put("items", items), cropped())[0].bytes
    val g = glyphs(out, 0, "Signed here")
    assertTrue(g.isNotEmpty())
    near(a4.width * 0.25f, g.first().first, 3f, "text starts half-way across the crop box") // the box is half the sheet wide
    assertTrue("text sits in the crop box: ${g.first().second}", g.first().second in 0f..(a4.height * 0.5f))
    assertThrows(UserError::class.java) { run("fillSign", NoOpts, sample()) }
  }

  @Test fun redactRemovesText() {
    val out = run("redact", NoOpts.put("boxes", mapOf("0" to listOf(listOf(0f, 0.05f, 1f, 0.15f)))), sample())[0].bytes
    assertEquals(6, pages(out))
    assertTrue("redacted page has no text left", text(out, 0).isBlank())
    assertTrue("other pages untouched", text(out, 1).contains("Page 2"))
    assertThrows(UserError::class.java) { run("redact", NoOpts, sample()) }
  }

  // ── Protect & shrink ──────────────────────────────────────────────────────

  @Test fun compress() {
    val src = sample()
    val out = run("compress", opts("quality" to "35"), src)[0].bytes
    assertEquals(6, pages(out))
    assertTrue("smaller (${out.size} < ${src.size})", out.size < src.size)
    // Already small: never comes back bigger.
    val tiny = sample(listOf("x"), rich = false)
    assertTrue(run("compress", opts("quality" to "80"), tiny)[0].bytes.size <= tiny.size)
  }

  @Test fun compressToFitASize() {
    val src = sample() // ~1.5 MB, nearly all of it the photo
    val out = run("compress", opts("mode" to "size", "targetMB" to "0.4"), src)[0].bytes
    assertTrue("fits under 0.4 MB (${out.size})", out.size <= 0.4 * 1024 * 1024)
    assertEquals(6, pages(out))
    val roomy = run("compress", opts("mode" to "size", "targetMB" to "50"), src)[0].bytes // already fits: just tidied
    assertTrue(roomy.size <= src.size)
    assertThrows(UserError::class.java) { run("compress", opts("mode" to "size", "targetMB" to ""), src) }
  }

  @Test fun removePassword() {
    val locked = File(appContext.cacheDir, "unlock-test.pdf").apply { writeBytes(setPdfPassword(sample(), "pw")!!) }
    val opened = File(unlockPdf(locked.path, "pw")!!).readBytes() // what the screen does once the password is typed
    val out = run("unlock", NoOpts, opened)[0].bytes
    PDDocument.load(out).use { assertTrue("opens with no password, unencrypted", !it.isEncrypted); assertEquals(6, it.numberOfPages) }
  }

  @Test fun editDetails() {
    val out = run("metadata", opts("title" to "Lease 2026", "author" to "", "subject" to "Flat 4B", "keywords" to "rent, lease"), sample())[0].bytes
    assertEquals(mapOf("title" to "Lease 2026", "author" to "", "subject" to "Flat 4B", "keywords" to "rent, lease"), readDetails(out))
    assertEquals(6, pages(out))
  }

  @Test fun ocrMakesAScanSearchable() {
    // A page that's only a picture of text (redaction rasterises it), then OCR puts the words back as text.
    val scan = run("redact", NoOpts.put("boxes", mapOf("0" to listOf(listOf(0.95f, 0.97f, 0.02f, 0.02f)))), sample(listOf("Quarterly Report"), rich = false))[0].bytes
    assertTrue(text(scan).isBlank())
    val out = run("ocr", NoOpts, scan)[0].bytes
    assertTrue("read back: ${text(out)}", text(out).contains("Quarterly", ignoreCase = true))
    // Pages with real text (50+ characters) are left alone, so a fully searchable PDF has nothing to add.
    val searchable = sample(listOf("This page already has plenty of real, selectable text on it, well past fifty characters."), rich = false)
    assertThrows(UserError::class.java) { run("ocr", NoOpts, searchable) }
  }

  @Test fun password() {
    val out = run("password", opts("password" to "s3cret"), sample())[0].bytes
    assertThrows(InvalidPasswordException::class.java) { PDDocument.load(out).close() }
    val f = File(appContext.cacheDir, "locked-test.pdf").apply { writeBytes(out) }
    assertNull("wrong password", unlockPdf(f.path, "nope"))
    val opened = unlockPdf(f.path, "s3cret")
    assertNotNull(opened)
    assertEquals(6, PDDocument.load(File(opened!!)).use { it.numberOfPages })
    assertThrows(UserError::class.java) { run("password", opts("password" to "again"), out) }
    assertThrows(UserError::class.java) { run("password", opts("password" to ""), sample()) }
  }

  // ── Automate, exports, bad input ──────────────────────────────────────────

  @Test fun workflow() {
    val steps = listOf(
      WorkflowStep("m", "merge", NoOpts),
      WorkflowStep("n", "pageNumbers", opts("position" to "center", "format" to "of")),
      WorkflowStep("c", "compress", opts("quality" to "60")),
    )
    val out = run("workflow", NoOpts.put("steps", steps), sample(), sample(listOf("B1", "B2"), rich = false))
    assertEquals(1, out.size)
    assertEquals(8, pages(out[0].bytes))
    assertTrue(text(out[0].bytes, 7).contains("Page 8 of 8"))
    assertThrows(UserError::class.java) { run("workflow", NoOpts, sample()) }
  }

  @Test fun exportsWordAndJpeg() {
    val f = File(appContext.cacheDir, "export-test.pdf").apply { writeBytes(sample()) }
    val docx = pdfToDocx(f.path)
    val xml = ZipInputStream(docx.inputStream()).use { s ->
      generateSequence { s.nextEntry }.first { it.name == "word/document.xml" }.let { s.readBytes().decodeToString() }
    }
    assertTrue(docx.length() > 0)
    assertTrue("pages come through as text or pictures", xml.contains("<w:p>"))
    val jpgs = pdfToJpeg(f.path)
    assertTrue(jpgs.name.endsWith(".zip"))
    assertEquals(6, ZipInputStream(jpgs.inputStream()).use { s -> generateSequence { s.nextEntry }.count() })
  }

  @Test fun badInputSaysSo() {
    assertThrows(UserError::class.java) { run("merge", NoOpts, "not a pdf".toByteArray(), sample()) }
    assertThrows(UserError::class.java) { run("compress", opts("quality" to "60"), ByteArray(0)) }
    assertThrows(UserError::class.java) { run("scan", NoOpts, sample()) }
  }
}
