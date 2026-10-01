# OpenMRZ Android

A modern, offline-first, open-source MRZ toolkit for Android.

OpenMRZ is being built as a clean Android SDK for reading and validating Machine Readable Zones (MRZ) on passports, visas, residence permits, and identity documents. The parser follows ICAO Doc 9303 field layouts and check-digit rules and is intentionally independent from the OCR and camera layers.

> Status: **early development / 0.1.x**. The pure MRZ parser is the first implemented module. OCR and CameraX modules are being added next.

## Goals

- Fully offline MRZ recognition
- No API key, license server, analytics, or document-data upload
- TD1 (3x30), TD2 (2x36), and TD3 (2x44)
- ICAO check-digit validation
- Modular architecture: parser, OCR, camera, and optional UI
- Kotlin-first API with Java compatibility
- Android 5.0+ target for scanner components
- Reproducible builds, tests, CI, and publishable Maven artifacts

## Modules

| Module | Purpose | Status |
|---|---|---|
| `openmrz-core` | MRZ parsing and ICAO check digits; no Android dependency | ✅ Foundation |
| `openmrz-ocr` | OCR engine abstraction and Tesseract implementation | 🚧 Next |
| `openmrz-camera` | CameraX preview, frame analysis, auto-detection | 🚧 Next |
| `sample` | Reference Android application | 🚧 Next |

## Core usage

```kotlin
val result = MrzParser.parse(
    """
    P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<
    L898902C<3UTO6908061F9406236ZE184226B<<<<<10
    """.trimIndent()
)
```

## Architecture

```text
CameraX
   ↓
MRZ region / frame preparation
   ↓
OCR engine
   ↓
text normalization
   ↓
openmrz-core
   ├─ TD1 / TD2 / TD3 parser
   ├─ ICAO check digits
   └─ structured result + validation
```

The core parser has no Android dependency. Applications that already have OCR output can use it by itself.

## OCR direction

The Android OCR module will use the actively maintained **Tesseract4Android** wrapper rather than the old `tess-two` dependency. MRZ recognition will be constrained to the ICAO character set:

```text
ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<
```

## Privacy

Recognition is designed to run entirely on-device. The library does not require network access and production code must not log MRZ contents by default.

## Build

Requirements:

- JDK 17
- Gradle 9.6+

Run the core tests:

```bash
./gradlew :openmrz-core:test
```

## License

OpenMRZ Android is released under the [MIT License](LICENSE).

Third-party libraries remain under their respective licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
