# PDF Tools: notes for coding agents

A native Android app in Kotlin and Jetpack Compose (the Gradle project is the repo root; the app module is `app/`).
Package and application id: `com.sammyi57.pdftools`.

## Build and check

- Compile: `./gradlew :app:compileReleaseKotlin`
- Install on a connected phone: `./gradlew :app:installRelease`
- JVM unit tests: `./gradlew :app:testReleaseUnitTest`
- On-device tests (every tool, the viewer engine, pins), installed next to the real app so its files are never
  touched: `./gradlew :app:connectedDebugAndroidTest -PsideBySide`

Verify with these, not a browser. Release builds are minified (R8); check behaviour in the release build.

## Where things are

- `Theme.kt` – the palette (Tangerine, Black Cherry, Cool Blue; light and dark), type and radii.
- `Kit.kt` – shared components (buttons, chips, lists, dialogs, the glow and grain surfaces).
- `Tools.kt` – the tool catalogue; `Pdf.kt` and `PdfRender.kt` – the PDF engine (PdfBox and Android's renderer).
- `ToolScreen.kt`, `PageEditors.kt` – tool screens and their editors.
- `Scanner.kt`, `Review.kt`, `ScanModel.kt`, `DocCamera.kt`, `DocVision.kt` – the document scanner.
- `Viewer.kt` – the in-app PDF viewer (Jetpack PDF).
- `Storage.kt`, `Files.kt`, `Backup.kt` – saved files and two-way Google Drive sync.

## Conventions

- Change code with small, direct edits; match the surrounding style and comment density.
- Colours come from the palette (`c.*`, `Brand.*`), never new hard-coded values in screens.
- Icons are Material Design Icons code points in `Icons.kt`; check a new one against `app/src/main/res/font/mdi.ttf`.
