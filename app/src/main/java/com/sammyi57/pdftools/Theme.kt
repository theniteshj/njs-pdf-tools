package com.sammyi57.pdftools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

// Three colours: Tangerine is the one sharp accent, Black Cherry is the ink (text, primary buttons, the tab bar), and
// Cool Blue is the quiet second surface (the Recent card, the hero's words). Light is warm paper; dark is a
// cherry-tinted near-black. Loud surfaces are a radial glow with film grain, not a glossy linear gradient.
object Brand {
  val tangerine = Color(0xFFFF5C34)
  val cherry = Color(0xFF351E28)
  val cool = Color(0xFFD7EFFF)
}

@Immutable
data class Palette(
  val bg: Color, // screen background
  val surface: Color, // grouped lists, inputs, dialogs
  val sunken: Color, // tiles behind artwork, pressed rows, search, segmented track
  val page: Color, // paper inside illustrations
  val ink: Color, // text and primary buttons
  val sub: Color,
  val faint: Color,
  val line: Color,
  val lineStrong: Color,
  val accent: Color,
  val accentSoft: Color,
  val accentInk: Color,
  val onInk: Color, // text on primary buttons
  val hero: List<Color>, // the loud surface: base, middle, glow
  val panel: Color, // the floating tab bar's glass
  val onPanel: Color,
  val onPanelSub: Color,
  val dark: Boolean,
)

val LightPalette = Palette(
  bg = Color(0xFFF6F2EE), surface = Color(0xFFFFFFFF), sunken = Color(0xFFEFE9E4), page = Color(0xFFFFFFFF),
  ink = Brand.cherry, sub = Color(0xFF7B6870), faint = Color(0xFFB4A6AC), line = Color(0xFFE9E1DC),
  lineStrong = Color(0xFFD9CEC9), accent = Brand.tangerine, accentSoft = Color(0xFFFFE4DB), accentInk = Color(0xFFB83A1C),
  onInk = Color(0xFFFFFFFF),
  hero = listOf(Brand.cherry, Color(0xFF8C2F2A), Brand.tangerine),
  panel = Brand.cherry, onPanel = Color(0xFFFFFFFF), onPanelSub = Color(0xFFCDB9C2),
  dark = false,
)

// Dark is a neutral warm near-black (after Poetica), not a cherry tint: Tangerine is the only colour in it.
val DarkPalette = Palette(
  bg = Color(0xFF111110), surface = Color(0xFF1A1A18), sunken = Color(0xFF232321), page = Color(0xFF262624),
  ink = Color(0xFFEDEDEB), sub = Color(0xFF9C9C99), faint = Color(0xFF6B6A66), line = Color(0xFF2A2A27),
  lineStrong = Color(0xFF383834), accent = Color(0xFFFF6A45), accentSoft = Color(0xFF2B231F), accentInk = Color(0xFFFFB7A2),
  onInk = Color(0xFF111110),
  hero = listOf(Color(0xFF1C1B19), Color(0xFF6B2A1E), Brand.tangerine), // near-black, the glow alone carries the colour
  panel = Color(0xFF1A1A18), onPanel = Color(0xFFEDEDEB), onPanelSub = Color(0xFF9C9C99),
  dark = true,
)

// The scanner is its own dark, camera-style surface in both themes: the dark palette's near-blacks, with Tangerine for
// detection and actions (and Black Cherry on top of it).
object ScanUi {
  val bg = Color(0xFF0B0B0A)
  val panel = Color(0xFF1A1A18)
  val raised = Color(0xFF2A2A27)
  val text = Color(0xFFFFFFFF)
  val dim = Color(0xFF9C9C99)
  val line = Color(0xFF2A2A27)
  val accent = Brand.tangerine
  val accentSoft = Color(0x38FF5C34)
  val onAccent = Brand.cherry
  val scrim = Color(0x99000000)
}

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** The active palette. */
val c: Palette @Composable get() = LocalPalette.current

// Poppins throughout. It runs tall, so line heights are ~1.3x to keep accents and descenders unclipped.
val Poppins = FontFamily(
  Font(R.font.poppins_regular, FontWeight.Normal),
  Font(R.font.poppins_medium, FontWeight.Medium),
  Font(R.font.poppins_semibold, FontWeight.SemiBold),
)

object Type {
  private fun s(size: Float, weight: FontWeight, line: Float? = null, spacing: Float = 0f) = TextStyle(
    fontFamily = Poppins, fontWeight = weight, fontSize = size.sp,
    lineHeight = (line ?: (size * 1.32f)).sp, letterSpacing = spacing.sp,
  )
  val largeTitle = s(32f, FontWeight.SemiBold, 42f, -0.5f)
  val title = s(25f, FontWeight.SemiBold, 33f, -0.3f)
  val hero = s(25f, FontWeight.SemiBold, 33f, -0.4f)
  val body = s(15f, FontWeight.Normal, 22f)
  val small = s(13f, FontWeight.Normal, 19f)
  val item = s(15f, FontWeight.Medium)
  val label = s(13f, FontWeight.SemiBold)
  val button = s(15f, FontWeight.SemiBold)
  val num = s(12.5f, FontWeight.Medium)
  val med = s(13.5f, FontWeight.Medium)
  val bold = s(14f, FontWeight.SemiBold)
  val big = s(26f, FontWeight.SemiBold, 32f, -0.6f) // the reference's big numbers: sizes and counts
  val caption = s(11.5f, FontWeight.Medium, 16f, 1.4f) // uppercase section labels, spaced out
}

// Deliberately not one radius for everything: small things are tighter.
object Radius {
  val tile = 10.dp
  val control = 12.dp
  val group = 16.dp
  val card = 22.dp
  val hero = 30.dp
}

enum class ThemePref { System, Light, Dark }

object ThemeState {
  private val file get() = File(appContext.filesDir, "theme.txt") // same file the Expo app used
  var pref by mutableStateOf(read())
    private set

  private fun read(): ThemePref = try {
    when (file.takeIf { it.exists() }?.readText()?.trim()) {
      "light" -> ThemePref.Light
      "dark" -> ThemePref.Dark
      else -> ThemePref.System
    }
  } catch (_: Exception) {
    ThemePref.System
  }

  fun set(p: ThemePref) {
    pref = p
    try { file.writeText(p.name.lowercase()) } catch (_: Exception) {}
  }
}
