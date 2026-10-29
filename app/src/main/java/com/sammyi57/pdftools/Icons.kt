package com.sammyi57.pdftools

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Material Design Icons, drawn from the same font the Expo app used, so every icon looks exactly as before.
val Mdi = FontFamily(Font(R.font.mdi))

@Composable
fun Icon(glyph: Int, size: Dp = 22.dp, color: Color = c.ink, modifier: Modifier = Modifier, label: String? = null) {
  val px = size.value
  BasicText(
    text = String(Character.toChars(glyph)),
    modifier = if (label != null) modifier.semantics { contentDescription = label } else modifier,
    style = TextStyle(fontFamily = Mdi, fontSize = px.sp, lineHeight = px.sp, color = color, textAlign = TextAlign.Center),
  )
}

/** Codepoints in the MaterialCommunityIcons font, named as in the Expo app. */
object I {
  const val accountOffOutline = 0xF0BE7
  const val accountSwitchOutline = 0xF04CB
  const val airplane = 0xF001D
  const val alertCircleOutline = 0xF05D6
  const val arrowLeft = 0xF004D
  const val arrowRight = 0xF0054
  const val arrowUpRight = 0xF17BF
  const val autoFix = 0xF0068
  const val brush = 0xF00E3
  const val cameraIris = 0xF0104
  const val cameraOffOutline = 0xF19BF
  const val cameraOutline = 0xF0D5D
  const val cameraFlipOutline = 0xF15DA
  const val cameraPlusOutline = 0xF0EDC
  const val cameraRetakeOutline = 0xF0E1A
  const val cellphone = 0xF011C
  const val cellphoneArrowDown = 0xF09D5
  const val cellphoneLock = 0xF094F
  const val cellphoneRemove = 0xF094D
  const val check = 0xF012C
  const val checkboxBlankOutline = 0xF0131
  const val checkboxMarked = 0xF0132
  const val chevronDown = 0xF0140
  const val chevronLeft = 0xF0141
  const val chevronRight = 0xF0142
  const val chevronUp = 0xF0143
  const val close = 0xF0156
  const val closeCircle = 0xF0159
  const val cloudSyncOutline = 0xF12D6
  const val cloudUploadOutline = 0xF0B7E
  const val codeTags = 0xF0174
  const val cogOutline = 0xF08BB
  const val contentSaveOutline = 0xF0818
  const val crop = 0xF019E
  const val deleteOutline = 0xF09E7
  const val deleteSweepOutline = 0xF0C62
  const val dotsVertical = 0xF01D9
  const val downloadOutline = 0xF0B8F
  const val drag = 0xF01DB
  const val draw = 0xF0F49
  const val drawPen = 0xF19B9
  const val ellipseOutline = 0xF0EA1
  const val eraser = 0xF01FE
  const val eyedropper = 0xF020A
  const val imageOutline = 0xF0976
  const val eyeOffOutline = 0xF06D1
  const val eyeOutline = 0xF06D0
  const val fileDocumentEditOutline = 0xF0DC9
  const val fileDocumentOutline = 0xF09EE
  const val fileImageOutline = 0xF0EB0
  const val filePdfBox = 0xF0226
  const val fileWordOutline = 0xF103E
  const val flash = 0xF0241
  const val flashAuto = 0xF0242
  const val flashOff = 0xF0243
  const val folderMoveOutline = 0xF1246
  const val folderOutline = 0xF0256
  const val folderPlusOutline = 0xF0B9D
  const val folderZipOutline = 0xF07B9
  const val formatFontSizeDecrease = 0xF09F3
  const val formatFontSizeIncrease = 0xF09F4
  const val formatText = 0xF0284
  const val textRecognition = 0xF113A
  const val fullscreen = 0xF0293
  const val gestureTap = 0xF0741
  const val googleDrive = 0xF02B6
  const val harddisk = 0xF02CA
  const val history = 0xF02DA
  const val homeOutline = 0xF06A1
  const val imageFilterBlackWhite = 0xF02F0
  const val imageMultipleOutline = 0xF02EF
  const val informationOutline = 0xF02FD
  const val lineScan = 0xF0624
  const val magnify = 0xF0349
  const val menuDown = 0xF035D
  const val messageTextOutline = 0xF036A
  const val openInNew = 0xF03CC
  const val pin = 0xF0403
  const val heart = 0xF08D0
  const val heartOutline = 0xF18A0
  const val star = 0xF04CE
  const val paperclip = 0xF03E2
  const val pinOffOutline = 0xF0930
  const val pinOutline = 0xF0931
  const val pencilOutline = 0xF0CB6
  const val play = 0xF040A
  const val plus = 0xF0415
  const val plusBoxMultipleOutline = 0xF1143
  const val qrcode = 0xF0432
  const val rectangle = 0xF0E5E
  const val rectangleOutline = 0xF0E5F
  const val resize = 0xF0A68
  const val restore = 0xF099B
  const val rotateLeft = 0xF0465
  const val rotateRight = 0xF0467
  const val shareVariantOutline = 0xF1514
  const val shieldCheckOutline = 0xF0CC8
  const val swapHorizontal = 0xF04E1
  const val sync = 0xF04E6
  const val trashCanOutline = 0xF0A7A
  const val tuneVariant = 0xF1542
  const val undo = 0xF054C
  const val viewGridOutline = 0xF11D9
  const val wifiOff = 0xF05AA
}
