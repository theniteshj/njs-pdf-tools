# Third-party notices

PDF Tools is MIT-licensed ([LICENSE](LICENSE)). It is built on the open-source projects and assets below, each used under
its own licence. Thank you to everyone who makes and maintains them.

## Libraries

| Project | Used for | Licence |
|---|---|---|
| [Jetpack Compose, AndroidX (Activity, Navigation, Lifecycle, Core)](https://developer.android.com/jetpack) | UI and app framework | Apache-2.0 |
| [Jetpack PDF (`androidx.pdf`)](https://developer.android.com/jetpack/androidx/releases/pdf) | The in-app PDF viewer | Apache-2.0 |
| [CameraX](https://developer.android.com/media/camera/camerax) | The scanner camera | Apache-2.0 |
| [Kotlin, kotlinx.coroutines, kotlinx.serialization](https://github.com/Kotlin) | Language and runtime libraries | Apache-2.0 |
| [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) (a port of [Apache PDFBox](https://pdfbox.apache.org/)) | Reading and writing PDFs | Apache-2.0 |
| [OpenCV](https://opencv.org/) | Page detection and image filters | Apache-2.0 |
| [ZXing](https://github.com/zxing/zxing) | QR code reading | Apache-2.0 |
| [Haze](https://github.com/chrisbanes/haze) | The frosted tab bar | Apache-2.0 |
| [ML Kit Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2) | On-device OCR | [Google ML Kit terms](https://developers.google.com/ml-kit/terms) |
| [Google Play services (Auth)](https://developers.google.com/android/guides/overview) | Google sign-in for Drive sync | [Google APIs terms](https://developers.google.com/terms) |

## Model

- **DocAligner LC100** by [DocsaidLab](https://github.com/DocsaidLab/DocAligner), a small network that finds the four corners
  of a page. Bundled as `app/src/main/assets/docaligner_lc100.onnx`. Apache-2.0.

## Fonts and icons

- **Poppins** by Indian Type Foundry, [SIL Open Font License 1.1](https://openfontlicense.org/).
- **Caveat** by Impallari Type, [SIL Open Font License 1.1](https://openfontlicense.org/).
- **Material Design Icons** by [Pictogrammers](https://pictogrammers.com/), used under
  [its free licence](https://pictogrammers.com/docs/general/license/).

## Licence texts

- Apache License 2.0: <https://www.apache.org/licenses/LICENSE-2.0>
- SIL Open Font License 1.1: <https://openfontlicense.org/open-font-license-official-text/>

If you maintain one of these projects and something here is wrong or missing, please open an issue.
