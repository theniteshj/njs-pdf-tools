package com.sammyi57.pdftools

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Finds the page in an RGB frame. The DocAligner model (DocModel) goes first and its corners are snapped to the real edge
// pixels; when it isn't confident, the classical detector below takes over. Classical candidate quads come from long straight lines (Hough, so a thumb over an edge or a page
// running off-screen still works) and from edge outlines. Each candidate is scored by whether every side shows a consistent
// brighter/darker step along its whole length, which rejects wood grain, carpet and blank walls. Text is erased first so
// it can't form edges, and the winner is re-fitted to the real edge pixels.
// On a synthetic benchmark (pages on white/gray/dark tables, wood, clutter, carpet; shadow, glare, occlusion, empty scenes):
// classical alone 81/108, model alone 73/108, model + snap + classical fallback 86/108.
object PageDetector {
  private const val WORK = 360.0 // working long side, px
  private const val STEP = 14 // min brightness step across a page edge, 0-255
  private const val SAMPLES = 28 // points tested along each side
  private const val OFF = 3 // px either side of the edge

  private class Line(val rho: Double, val theta: Double)

  fun detect(rgb: Mat): DoubleArray? {
    val k = WORK / max(rgb.cols(), rgb.rows())
    val small = Mat()
    Imgproc.resize(rgb, small, Size(), k, k, Imgproc.INTER_AREA)
    val w = small.cols()
    val h = small.rows()

    // Close away dark text and thin marks, then smooth; page borders survive.
    val clean = Mat()
    Imgproc.morphologyEx(small, clean, Imgproc.MORPH_CLOSE, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0)))
    Imgproc.medianBlur(clean, clean, 5)
    var edges = channelEdges(clean, 20.0, 60.0)
    if (Core.countNonZero(edges) > edges.total() * 0.06) { // busy texture: smooth harder, demand stronger edges
      Imgproc.medianBlur(clean, clean, 9)
      edges.release()
      edges = channelEdges(clean, 40.0, 110.0)
    }
    val thin = edges.clone()
    DocModel.corners(small)?.let { ml ->
      val snapped = refine(thin, Array(4) { Point(ml[it].x * w, ml[it].y * h) }, w, h)
      listOf(small, clean, edges, thin).forEach { it.release() }
      return snapped.flatMap { listOf(it.x / w, it.y / h) }.toDoubleArray()
    }
    Imgproc.dilate(edges, edges, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))

    val px = ByteArray(w * h * 3)
    clean.get(0, 0, px)
    val cands = ArrayList<Array<Point>>()

    // Outline candidates.
    val contours = ArrayList<MatOfPoint>()
    Imgproc.findContours(edges.clone(), contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
    for (c in contours.sortedByDescending { Imgproc.contourArea(it) }.take(6)) {
      val idx = MatOfInt()
      Imgproc.convexHull(c, idx)
      val pts = c.toArray()
      val hull = MatOfPoint2f(*idx.toArray().map { pts[it] }.toTypedArray())
      val peri = Imgproc.arcLength(hull, true)
      for (eps in doubleArrayOf(0.02, 0.04, 0.07)) {
        val approx = MatOfPoint2f()
        Imgproc.approxPolyDP(hull, approx, eps * peri, true)
        if (approx.total() == 4L) {
          cands.add(approx.toArray())
          break
        }
      }
    }

    // Line candidates: the strongest distinct near-horizontal and near-vertical lines, plus the frame border.
    val lines = Mat()
    Imgproc.HoughLines(edges, lines, 1.0, PI / 180, (min(w, h) * 0.22).toInt())
    val hor = ArrayList<Line>()
    val ver = ArrayList<Line>()
    for (i in 0 until min(lines.rows(), 120)) {
      val v = lines.get(i, 0)
      var rho = v[0]
      var theta = v[1]
      if (rho < 0) { rho = -rho; theta -= PI }
      val group = if (abs(sin(theta)) > 0.7) hor else ver
      if (group.size < 9 && group.all { abs(rho - it.rho) > 8 || abs(theta - it.theta) > 0.12 }) group.add(Line(rho, theta))
    }
    hor += listOf(Line(0.0, PI / 2), Line(h - 1.0, PI / 2))
    ver += listOf(Line(0.0, 0.0), Line(w - 1.0, 0.0))
    for (a in hor.indices) for (b in a + 1 until hor.size) for (c in ver.indices) for (d in c + 1 until ver.size) {
      val q = arrayOf(cross(hor[a], ver[c]), cross(hor[a], ver[d]), cross(hor[b], ver[d]), cross(hor[b], ver[c]))
      if (q.any { it == null || it.x < -w * 0.1 || it.x > w * 1.1 || it.y < -h * 0.1 || it.y > h * 1.1 }) continue
      cands.add(Array(4) { Point(q[it]!!.x.coerceIn(0.0, w - 1.0), q[it]!!.y.coerceIn(0.0, h - 1.0)) })
    }

    // Score everything on edge evidence; the paper prior only breaks near-ties among the best few.
    val top = cands.mapNotNull { q -> val o = order(q); score(px, o, w, h).takeIf { it > 0 }?.let { it to o } }
      .sortedByDescending { it.first }.take(6)
    var result: DoubleArray? = null
    if (top.isNotEmpty()) {
      val hsv = Mat()
      Imgproc.cvtColor(clean, hsv, Imgproc.COLOR_RGB2HSV)
      val best = top.maxBy { (s, q) -> s * (0.85 + 0.15 * paperness(hsv, q, w, h)) }.second
      hsv.release()
      result = refine(thin, best, w, h).flatMap { listOf(it.x / w, it.y / h) }.toDoubleArray()
    }
    listOf(small, clean, edges, thin, lines).forEach { it.release() }
    return result
  }

  // Sorts four points into top-left, top-right, bottom-right, bottom-left.
  fun order(p: Array<Point>): Array<Point> =
    arrayOf(p.minBy { it.x + it.y }, p.maxBy { it.x - it.y }, p.maxBy { it.x + it.y }, p.minBy { it.x - it.y })

  private fun channelEdges(img: Mat, lo: Double, hi: Double): Mat {
    val channels = ArrayList<Mat>()
    Core.split(img, channels)
    val out = Mat.zeros(img.size(), CvType.CV_8U)
    val e = Mat()
    for (ch in channels) {
      Imgproc.Canny(ch, e, lo, hi)
      Core.bitwise_or(out, e, out)
      ch.release()
    }
    e.release()
    return out
  }

  private fun cross(a: Line, b: Line): Point? {
    val a1 = cos(a.theta); val b1 = sin(a.theta)
    val a2 = cos(b.theta); val b2 = sin(b.theta)
    val det = a1 * b2 - a2 * b1
    if (abs(det) < 1e-3) return null
    return Point((a.rho * b2 - b.rho * b1) / det, (a1 * b.rho - a2 * a.rho) / det)
  }

  private fun score(px: ByteArray, q: Array<Point>, w: Int, h: Int): Double {
    val area = abs((0 until 4).sumOf { q[it].x * q[(it + 1) % 4].y - q[(it + 1) % 4].x * q[it].y }) / 2 / (w * h)
    if (area < 0.12) return 0.0
    var sign = 0
    for (i in 0 until 4) {
      val p0 = q[(i + 3) % 4]; val p1 = q[i]; val p2 = q[(i + 1) % 4]
      val v1x = p0.x - p1.x; val v1y = p0.y - p1.y
      val v2x = p2.x - p1.x; val v2y = p2.y - p1.y
      val cr = v1x * v2y - v1y * v2x
      val s = if (cr > 0) 1 else -1
      if (sign != 0 && s != sign) return 0.0 // not convex
      sign = s
      val c = (v1x * v2x + v1y * v2y) / (hypot(v1x, v1y) * hypot(v2x, v2y) + 1e-6)
      if (abs(c) > 0.64) return 0.0 // interior angle outside 50..130 degrees
    }
    var real = 0
    var sum = 0.0
    var weakest = 1.0
    for (i in 0 until 4) {
      val s = support(px, q[i], q[(i + 1) % 4], w, h)
      if (s == null) sum += 0.5 else { real++; sum += s; weakest = min(weakest, s) }
    }
    if (real < 2 || weakest < 0.55) return 0.0
    return sum / 4 * (0.6 + 0.4 * sqrt(area)) // a frame-border side counts as half the evidence of a real edge
  }

  // Share of samples along a->b with a same-signed step > STEP across the side, strongest channel. null = frame border.
  private fun support(px: ByteArray, a: Point, b: Point, w: Int, h: Int): Double? {
    val mx = w * 0.02; val my = h * 0.02
    if ((a.x < mx && b.x < mx) || (a.x > w - mx && b.x > w - mx) || (a.y < my && b.y < my) || (a.y > h - my && b.y > h - my)) return null
    val dx = b.x - a.x; val dy = b.y - a.y
    val len = hypot(dx, dy)
    if (len < 10) return 0.0
    val nx = -dy / len; val ny = dx / len
    var valid = 0; var pos = 0; var neg = 0
    for (i in 0 until SAMPLES) {
      val t = 0.08 + 0.84 * i / (SAMPLES - 1)
      val x = a.x + t * dx; val y = a.y + t * dy
      val x1 = (x + nx * OFF).roundToInt(); val y1 = (y + ny * OFF).roundToInt()
      val x2 = (x - nx * OFF).roundToInt(); val y2 = (y - ny * OFF).roundToInt()
      if (x1 !in 0 until w || y1 !in 0 until h || x2 !in 0 until w || y2 !in 0 until h) continue
      valid++
      var best = 0
      for (ch in 0 until 3) {
        val d = (px[(y1 * w + x1) * 3 + ch].toInt() and 255) - (px[(y2 * w + x2) * 3 + ch].toInt() and 255)
        if (abs(d) > abs(best)) best = d
      }
      if (best > STEP) pos++ else if (best < -STEP) neg++
    }
    if (valid < SAMPLES / 2) return null // mostly off-screen: treat like a border side
    return max(pos, neg).toDouble() / valid
  }

  // 0..1: inside brighter and less colorful than just outside, like paper on a table.
  private fun paperness(hsv: Mat, q: Array<Point>, w: Int, h: Int): Double {
    val mask = Mat.zeros(h, w, CvType.CV_8U)
    Imgproc.fillPoly(mask, listOf(MatOfPoint(*q.map { Point(it.x.roundToInt().toDouble(), it.y.roundToInt().toDouble()) }.toTypedArray())), Scalar(255.0))
    val inner = Mat(); val outer = Mat()
    Imgproc.erode(mask, inner, Mat.ones(7, 7, CvType.CV_8U))
    Imgproc.dilate(mask, outer, Mat.ones(15, 15, CvType.CV_8U))
    Core.subtract(outer, mask, outer)
    val r = if (Core.countNonZero(outer) == 0) 0.5 else {
      val i = Core.mean(hsv, inner).`val`; val o = Core.mean(hsv, outer).`val`
      (0.5 + (i[2] - o[2]) / 120 + (o[1] - i[1]) / 240).coerceIn(0.0, 1.0)
    }
    listOf(mask, inner, outer).forEach { it.release() }
    return r
  }

  // Re-fits each side to the thin edge pixels within 5 px of it and re-intersects neighbours.
  private fun refine(thin: Mat, q: Array<Point>, w: Int, h: Int): Array<Point> {
    val nz = MatOfPoint()
    Core.findNonZero(thin, nz)
    val all = if (nz.empty()) emptyArray() else nz.toArray()
    val fits = Array<DoubleArray?>(4) { i ->
      val a = q[i]; val b = q[(i + 1) % 4]
      val len = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(1e-6)
      val ux = (b.x - a.x) / len; val uy = (b.y - a.y) / len
      val near = all.filter {
        val rx = it.x - a.x; val ry = it.y - a.y
        val t = rx * ux + ry * uy
        abs(rx * -uy + ry * ux) < 5 && t > len * 0.1 && t < len * 0.9
      }
      if (near.size < 12) null else {
        val line = Mat()
        Imgproc.fitLine(MatOfPoint2f(*near.toTypedArray()), line, Imgproc.DIST_HUBER, 0.0, 0.01, 0.01)
        DoubleArray(4) { line.get(it, 0)[0] }.also { line.release() } // vx, vy, x0, y0
      }
    }
    nz.release()
    return Array(4) { i ->
      val l1 = fits[(i + 3) % 4]; val l2 = fits[i]
      val p = if (l1 == null || l2 == null) null else {
        val det = l1[0] * -l2[1] - -l2[0] * l1[1]
        if (abs(det) < 1e-3) null else {
          val t = ((l2[2] - l1[2]) * -l2[1] - -l2[0] * (l2[3] - l1[3])) / det
          Point(l1[2] + t * l1[0], l1[3] + t * l1[1])
        }
      }
      if (p != null && hypot(p.x - q[i].x, p.y - q[i].y) < 12) Point(p.x.coerceIn(0.0, w - 1.0), p.y.coerceIn(0.0, h - 1.0)) else q[i]
    }
  }
}
