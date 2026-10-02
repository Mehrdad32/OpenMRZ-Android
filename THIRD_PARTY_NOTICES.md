# Third-party notices

OpenMRZ Android is MIT licensed. Dependencies retain their own licenses.

- **Tesseract OCR** — Apache License 2.0
- **Tesseract4Android** by Adaptech — Apache License 2.0
- **Leptonica** — BSD-style license
- **DoubangoTelecom tesseractMRZ model/dataset repository** — BSD 3-Clause License
- **Tesseract official `tessdata_fast` English model** — Apache License 2.0
- **AndroidX / CameraX / Activity** — Apache License 2.0

The bundled `mrz.traineddata` is fetched from the BSD-3-Clause licensed
[DoubangoTelecom/tesseractMRZ](https://github.com/DoubangoTelecom/tesseractMRZ)
repository during the build. A generic `eng.traineddata` fallback is fetched from the
official Apache-2.0-licensed `tesseract-ocr/tessdata_fast` repository and is only
used for difficult ambiguous reads.

The project does not redistribute the Xavier SDK/AAR. Xavier can be used as a behavior and compatibility reference while OpenMRZ remains independently implemented.
