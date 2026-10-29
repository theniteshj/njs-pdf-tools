package com.sammyi57.pdftools

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.dnn.Dnn
import org.opencv.dnn.Net
import org.opencv.imgproc.Imgproc

// DocAligner LC100 (DocsaidLab, Apache-2.0): a 1.2M-parameter network that outputs one heatmap per page corner.
// Scores 0.989 IoU on SmartDoc 2015. The bundled ONNX has its BiFPN Einsum weighted sums rewritten as Mul/Add so
// OpenCV's DNN module can run it (output matches the original to 1e-6); no extra ML runtime ships with the app.
object DocModel {
  const val ASSET = "docaligner_lc100.onnx"
  private const val SIZE = 256.0
  private const val THRESHOLD = 0.3f

  /** Where the model bytes come from: the app's assets. */
  @Volatile var source: (() -> ByteArray)? = { appContext.assets.open(ASSET).use { it.readBytes() } }
  private var net: Net? = null
  private var failed = false

  /** Page corners as fractions, ordered TL, TR, BR, BL; null when any corner isn't confidently found. */
  @Synchronized
  fun corners(rgb: Mat): Array<Point>? {
    val n = net ?: load() ?: return null
    val input = Mat()
    Imgproc.resize(rgb, input, Size(SIZE, SIZE), 0.0, 0.0, Imgproc.INTER_AREA)
    n.setInput(Dnn.blobFromImage(input, 1 / 255.0, Size(SIZE, SIZE), Scalar(0.0), false, false))
    val out = n.forward() // 1 x 4 x 128 x 128, sigmoid
    input.release()
    val hw = out.size(2)
    val data = FloatArray(4 * hw * hw)
    out.get(intArrayOf(0, 0, 0, 0), data)
    out.release()
    val pts = Array(4) { c -> peak(data, c * hw * hw, hw) ?: return null }
    val q = PageDetector.order(pts)
    val area = Imgproc.contourArea(MatOfPoint2f(*q))
    if (area < 0.05 || !Imgproc.isContourConvex(MatOfPoint(*q.map { Point(it.x * 1000, it.y * 1000) }.toTypedArray()))) return null
    return q
  }

  private fun load(): Net? {
    if (failed) return null
    return try {
      Dnn.readNetFromONNX(MatOfByte(*source!!.invoke())).also { net = it }
    } catch (e: Exception) {
      failed = true // fall back to the classical detector for this session
      null
    }
  }

  // Confidence-weighted centroid of the largest above-threshold blob in one corner's heatmap, as a fraction.
  private fun peak(data: FloatArray, offset: Int, hw: Int): Point? {
    val mask = Mat(hw, hw, CvType.CV_8U)
    val bytes = ByteArray(hw * hw) { if (data[offset + it] >= THRESHOLD) 1 else 0 }
    mask.put(0, 0, bytes)
    val labels = Mat()
    val stats = Mat()
    val cents = Mat()
    val count = Imgproc.connectedComponentsWithStats(mask, labels, stats, cents)
    var best = -1
    var bestArea = 0.0
    for (i in 1 until count) {
      val a = stats.get(i, Imgproc.CC_STAT_AREA)[0]
      if (a > bestArea) { bestArea = a; best = i }
    }
    var result: Point? = null
    if (best > 0) {
      val lab = IntArray(hw * hw)
      labels.get(0, 0, lab)
      var sx = 0.0; var sy = 0.0; var sw = 0.0
      for (i in lab.indices) if (lab[i] == best) {
        val wgt = data[offset + i].toDouble()
        sx += (i % hw) * wgt; sy += (i / hw) * wgt; sw += wgt
      }
      if (sw > 0) result = Point((sx / sw + 0.5) / hw, (sy / sw + 0.5) / hw)
    }
    listOf(mask, labels, stats, cents).forEach { it.release() }
    return result
  }

}
