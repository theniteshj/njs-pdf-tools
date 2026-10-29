package com.sammyi57.pdftools

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val FEEDBACK_FORM = "https://forms.gle/Fc3RHMTXAiA8fT5VA"

@Composable
fun SettingsTab() {
  val files = remember(Store.version) { listAllFiles() }
  var clearConfirm by remember { mutableStateOf(false) }
  var clearChoice by remember { mutableStateOf(false) } // files are also in Google Drive
  var driveConfirm by remember { mutableStateOf(false) }
  var wiping by remember { mutableStateOf(false) }
  var terms by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  // Drive goes first: if it fails, the phone keeps its files and the error shows under Google Drive.
  val wipeBoth = {
    wiping = true
    scope.launch {
      try { Backup.deleteEverything() } catch (e: Exception) { Backup.update { it.copy(error = Backup.describe(e)) } }
      wiping = false
    }
    Unit
  }
  val clearAll = { keepDrive: Boolean ->
    if (keepDrive) Backup.keepInDriveOnly(files.map { it.path })
    clearFiles()
    clearChoice = false
  }
  val version = remember { runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }.getOrNull() ?: "" }

  Screen {
    LargeTitle("Settings")

    GroupLabel("Appearance", first = true)
    ThemePicker()

    GroupLabel("Storage")
    Group {
      ListRow("Saved files", first = true, lead = { Lead(I.folderOutline) }, value = files.size.toString())
      ListRow("Space used", lead = { Lead(I.harddisk) }, value = fmtSize(files.sumOf { it.size }))
      ListRow(
        "Delete all saved files", lead = { Lead(I.trashCanOutline, true) }, danger = true, value = if (wiping) "Deleting…" else null,
        onClick = if (files.isEmpty() || wiping) null else ({ if (Backup.hasDriveCopies(files.map { it.path })) clearChoice = true else clearConfirm = true }),
      )
    }

    GroupLabel("Scanning")
    Group {
      ListRow(
        "Make scans searchable", first = true, lead = { Lead(I.textRecognition) },
        subtitle = "Reads the text when you save a scan, so you can search and copy it. Runs on this phone; saving takes a little longer.",
        onClick = { ScanPrefs.save(!ScanPrefs.searchable) },
      ) { Toggle(ScanPrefs.searchable) { ScanPrefs.save(it) } }
    }

    GroupLabel("Google Drive")
    DriveBackup()

    GroupLabel("Privacy")
    Group {
      if (Backup.state.enabled) {
        ListRow("Processed on this phone", first = true, lead = { Lead(I.cellphoneLock) }, subtitle = "Every tool runs on the device. Your PDFs sync only with your own Google Drive, and nobody else sees them.")
      } else {
        ListRow("Files stay on this phone", first = true, lead = { Lead(I.cellphoneLock) }, subtitle = "Every tool runs on the device. PDFs leave it only if you turn on Google Drive sync.")
      }
      ListRow("No account needed, no tracking", lead = { Lead(I.accountOffOutline) }, subtitle = "No sign-up, no analytics, no ads.")
      ListRow("Works offline", lead = { Lead(I.airplane) }, subtitle = "Every tool works in airplane mode. Syncing waits until you're online.")
    }

    GroupLabel("About")
    Group {
      ListRow("Version", first = true, lead = { Lead(I.informationOutline) }, value = version)
      ListRow("PDF engine", lead = { Lead(I.codeTags) }, value = "PdfBox")
      ListRow("Privacy Policy & Terms", lead = { Lead(I.shieldCheckOutline) }, chevron = true, onClick = { terms = true })
      ListRow(
        "Send feedback", lead = { Lead(I.messageTextOutline) }, subtitle = "Tell us what to fix or add. Opens a short form.",
        onClick = { openUrl(FEEDBACK_FORM) },
      ) { Icon(I.openInNew, 18.dp, c.faint) }
    }
  }

  if (terms) FullSheet({ terms = false }) { Consent(onClose = { terms = false }) }
  if (clearConfirm) {
    ConfirmDialog(
      "Delete all saved files?", "${plural(files.size, "file")} made in NJS will be removed. The PDFs you picked as input are not touched.",
      "Delete all", danger = true, requireTyping = "confirm", onDismiss = { clearConfirm = false },
    ) { clearFiles() }
  }
  if (clearChoice) {
    AppDialog({ clearChoice = false }, "Delete all saved files?", "${plural(files.size, "file")} made in NJS. They're also in your Google Drive.") {
      Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Btn("Delete from this phone only", Modifier.fillMaxWidth(), I.cellphoneRemove, BtnKind.Secondary) { clearAll(true) }
        Btn("Delete from phone and Drive", Modifier.fillMaxWidth(), I.deleteOutline, BtnKind.Danger) { clearChoice = false; driveConfirm = true }
        T(
          "Phone only keeps everything in Drive; download files again from Files → Google Drive. Phone and Drive moves the Drive copies to Drive's Trash for 30 days.",
          Type.small, c.sub, align = TextAlign.Center,
        )
      }
    }
  }
  if (driveConfirm) {
    ConfirmDialog(
      "Delete from phone and Drive?", "${plural(files.size, "file")} will be removed from this phone. Everything in the NJS folder in your Drive, folders and Drive-only files included, goes to Drive's Trash for 30 days.",
      "Delete all", danger = true, requireTyping = "confirm", onDismiss = { driveConfirm = false },
    ) { wipeBoth() }
  }
}

@Composable
fun Lead(glyph: Int, danger: Boolean = false) {
  Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { Icon(glyph, 20.dp, if (danger) c.accent else c.sub) }
}

@Composable
private fun DriveBackup() {
  val scope = rememberCoroutineScope()
  val state = Backup.state
  val syncing = Backup.syncing
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf("") }
  var confirmOff by remember { mutableStateOf(false) }
  // Turning sync on and changing accounts both go through the account picker.
  val connect = {
    scope.launch {
      busy = true
      error = ""
      try { Backup.connect() } catch (e: Exception) { error = e.message ?: "Google sign-in failed." }
      busy = false
    }
    Unit
  }
  val status = when {
    !state.enabled -> "Off"
    syncing -> "Syncing…"
    state.error != null -> "Needs attention"
    state.lastSync != null -> "Synced · ${fmtDate(state.lastSync)}"
    else -> "Not synced yet"
  }
  Group {
    ListRow(
      "Sync PDFs with Google Drive", first = true, lead = { Lead(I.googleDrive) },
      subtitle = if (state.enabled) state.email ?: "Signed in" else "Keeps your PDFs and folders the same on this phone and in your Drive",
    ) {
      if (busy) Spinner(c.accent) else Toggle(state.enabled) { on -> if (on) connect() else confirmOff = true }
    }
    if (state.enabled) {
      ListRow("Status", lead = { Lead(I.cloudSyncOutline) }, value = status)
      ListRow("Sync now", lead = { Lead(I.sync) }, onClick = if (syncing) null else ({ Backup.syncNow(); Unit }))
      ListRow("Change account", lead = { Lead(I.accountSwitchOutline) }, subtitle = "Sync with a different Google account", onClick = if (busy) null else connect)
    }
  }
  // The last run's problem is hidden while a new run is trying again.
  val problem = error.ifEmpty { if (state.enabled && !syncing) state.error ?: "" else "" }
  if (problem.isNotEmpty()) T(problem, Type.small, c.accent, Modifier.padding(top = 8.dp))
  if (state.enabled) {
    T(
      "Syncs both ways whenever you save, rename, move or delete a file, and when you open Files. Deleted files go to Drive's Trash for 30 days. Only files made by NJS are visible to the app in your Drive.",
      Type.small, c.sub, Modifier.padding(top = 8.dp),
    )
  }
  if (confirmOff) {
    ConfirmDialog(
      "Turn off Drive sync?", "NJS signs out of Google Drive. Your files stay on this phone and in your Drive, but stop syncing.",
      "Turn off", onDismiss = { confirmOff = false },
    ) { scope.launch { busy = true; Backup.disable(); busy = false } }
  }
}

@Composable
private fun ThemePicker() {
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    listOf(ThemePref.System to "System", ThemePref.Light to "Light", ThemePref.Dark to "Dark").forEach { (p, label) ->
      val on = ThemeState.pref == p
      val border by animateColorAsState(if (on) c.accent else c.lineStrong, tween(220), label = "theme")
      val width by animateDpAsState(if (on) 2.5.dp else 1.dp, tween(220), label = "themeW")
      val shape = RoundedCornerShape(Radius.control)
      Column(Modifier.weight(1f).tap(scaleTo = 0.95f, label = label) { ThemeState.set(p) }, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().height(88.dp).clip(shape).border(width, border, shape)) {
          if (p == ThemePref.System) {
            Mini(LightPalette, Modifier.weight(1f))
            Mini(DarkPalette, Modifier.weight(1f))
          } else Mini(if (p == ThemePref.Light) LightPalette else DarkPalette, Modifier.weight(1f))
        }
        T(label, if (on) Type.bold.copy(fontSize = Type.small.fontSize) else Type.small, if (on) c.ink else c.sub, Modifier.padding(top = 8.dp))
      }
    }
  }
}

// A tiny drawing of the app in that palette: a title, the hero card, the floating bar.
@Composable
private fun Mini(p: Palette, modifier: Modifier) {
  Column(modifier.fillMaxHeight().background(p.bg).padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
    Box(Modifier.height(6.dp).fillMaxWidth(0.65f).background(p.ink, RoundedCornerShape(3.dp)))
    Box(Modifier.weight(1f).fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(p.hero), RoundedCornerShape(5.dp)))
    Box(Modifier.height(7.dp).fillMaxWidth(0.8f).align(Alignment.CenterHorizontally).background(p.panel, RoundedCornerShape(4.dp)))
  }
}
