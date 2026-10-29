package com.sammyi57.pdftools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.roundToInt

// The page geometry every tool relies on, with the same cases the Expo app's pdf.check.ts used.
// `./gradlew :app:testDebugUnitTest`
class PdfGeometryTest {
  private fun Placement.r() = listOf(x, y, w, h).map { it.roundToInt() }

  @Test
  fun ranges() {
    assertEquals(listOf(listOf(0, 1), listOf(3), listOf(4, 5)), parseRanges("1-2, 4, 5-", 6))
    assertThrows(UserError::class.java) { parseRanges("9", 6) }
  }

  @Test
  fun placesOnTurnedPages() {
    // The origin is the item's own bottom-left corner, which lands on a different page corner as /Rotate turns it.
    val item = listOf(0.1f, 0.2f, 0.5f, 0.1f)
    assertEquals(listOf(40, 420, 200, 60), placeOnPage(item, 0, 400f, 600f).r())
    assertEquals(listOf(120, 60, 300, 40), placeOnPage(item, 90, 400f, 600f).r())
    assertEquals(listOf(360, 180, 200, 60), placeOnPage(item, 180, 400f, 600f).r())
    assertEquals(listOf(280, 540, 300, 40), placeOnPage(item, 270, 400f, 600f).r())
    // Unturned, placeTurned lands where placeOnPage does on every page rotation.
    for (rot in listOf(0, 90, 180, 270)) {
      val (dw, dh) = if (rot % 180 != 0) 600f to 400f else 400f to 600f
      val t = placeTurned(0.1f, 0.2f, 0.5f * dw, 0.1f * dh, 0f, 0f, rot, 400f, 600f)
      val flat = placeOnPage(item, rot, 400f, 600f)
      assertEquals("rot $rot", listOf(flat.x, flat.y, rot.toFloat()).map { it.roundToInt() }, listOf(t.x, t.y, t.rotate).map { it.roundToInt() })
    }
    // Turned 180° about its centre, the drawing origin moves to the opposite corner.
    val half = placeTurned(0.1f, 0.2f, 100f, 50f, 0f, 180f, 0, 400f, 600f)
    assertEquals(listOf(140, 480, -180), listOf(half.x, half.y, half.rotate).map { it.roundToInt() })
  }

  @Test
  fun cropBoxesTurnBack() {
    // A rect drawn on a page the viewer turns 90° becomes a different rect on the page as stored.
    assertEquals(listOf(0.1f, 0.2f, 0.5f, 0.6f).let { unrotate(it, 0) }, listOf(0.1f, 1 - 0.2f - 0.6f, 0.5f, 0.6f))
    assertEquals(listOf(0.2f, 0.1f, 0.6f, 0.5f), unrotate(listOf(0.1f, 0.2f, 0.5f, 0.6f), 90))
  }

  @Test
  fun scanLayout() {
    val sq = listOf(300 to 300)
    assertEquals(1, layoutScanPage("doc", "fit", sq).size)
    assertEquals(2, layoutScanPage("book", "fit", sq).size) // a book splits into two pages
    assertEquals(Crop(0.5f, 0f, 0.5f, 1f), layoutScanPage("book", "fit", sq)[1].items[0].crop)
    assertEquals(2, layoutScanPage("id", "fit", sq + sq)[0].items.size) // an ID card's front and back share one
    val letter = layoutScanPage("doc", "Letter-portrait", sq)[0]
    assertEquals(612f to 792f, letter.W to letter.H)
  }

  @Test
  fun foldsTextHelveticaCantWrite() {
    assertEquals("Total Rs. 500 - \"ok\" ", winAnsi("Total ₹500 — “ok” ✓"))
  }
}
