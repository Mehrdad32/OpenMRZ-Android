# OpenMRZ Android

OpenMRZ is a free, offline-first, open-source Android SDK for reading and validating ICAO Machine Readable Zones (MRZ).

The repository is **SDK-first**. The APK under `sample` exists only to exercise the same public SDK that applications consume.

> Current prerelease: **v0.1.0-alpha.3**

## Modules

| Module | Artifact | Purpose |
|---|---|---|
| `openmrz-core` | JVM JAR | TD1 / TD2 / TD3 parsing and ICAO check digits |
| `openmrz-ocr` | Android AAR | Still-image MRZ detection + offline MRZ-trained Tesseract OCR |
| `openmrz-android` | Android AAR | CameraX scanner SDK built on `openmrz-ocr` |
| `sample` | APK | Manual device/gallery test application |

## Features

- TD1 (3×30), TD2 (2×36), and TD3 (2×44)
- ICAO check-digit validation
- Offline OCR; no API key or license server
- Dedicated MRZ-trained Tesseract model
- Automatic MRZ-region detection with conservative fallback crops
- Checksum-guided OCR repair for ambiguous document-number glyphs
- Context-aware OCR correction for numeric/alpha fields
- CameraX scanner controller
- Gallery/still-image recognition
- Android 6.0+ (API 23)
- Maven/JitPack publication metadata
- MIT license

## Install from JitPack

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Complete scanner SDK:

```kotlin
implementation(
    "com.github.Mehrdad32.OpenMRZ-Android:openmrz-android:v0.1.0-alpha.3"
)
```

Still-image OCR only:

```kotlin
implementation(
    "com.github.Mehrdad32.OpenMRZ-Android:openmrz-ocr:v0.1.0-alpha.3"
)
```

Parser only:

```kotlin
implementation(
    "com.github.Mehrdad32.OpenMRZ-Android:openmrz-core:v0.1.0-alpha.3"
)
```

## Result trust

OpenMRZ separates:

- `validation.checkDigitsValid`: ICAO check digits agree.
- `validation.isValid`: check digits and structural field checks agree.
- `result.isTrusted`: the OCR read is `VERIFIED` with acceptable OCR confidence/corrections.

The sex field is read from the MRZ character; it is never inferred from the portrait.

## Important note about specimen images

A scanner should transcribe an MRZ accurately even when the source document is only a specimen, but it must not call a malformed specimen `VERIFIED`.

For example, a TD3 issuing-state field must be three letters and a YYMMDD value such as `230000` is not a valid calendar date. Such a sample can still be useful for OCR testing, but the SDK should return a review/not-recognized status rather than declaring the MRZ structurally valid.

## SDK usage

```kotlin
val scanner = OpenMrzScanner(
    context = this,
    lifecycleOwner = this,
    previewView = previewView,
    listener = object : OpenMrzScannerListener {
        override fun onResult(result: MrzOcrResult) {
            if (result.isTrusted) {
                val document = (result.parseResult as MrzParseResult.Success).document
                // Consume fields.
            }
        }
    },
)

scanner.start()
```

Call `scanner.close()` when the owning component is destroyed.

## Direct release assets

Every prerelease contains:

- `openmrz-android-<version>.aar`
- `openmrz-ocr-<version>.aar`
- sample APK
- SHA-256 checksums

For application projects, Maven/JitPack is recommended so transitive CameraX and Tesseract dependencies are resolved automatically.

## Build

```bash
./gradlew :openmrz-core:test :openmrz-ocr:test
./gradlew :openmrz-android:assembleRelease
./gradlew :sample:assembleDebug
./gradlew publishToMavenLocal
```

On a clean build, `openmrz-ocr` downloads the BSD-3-Clause licensed `mrz.traineddata` model from `DoubangoTelecom/tesseractMRZ` and packages it into the AAR. Installed applications do not download OCR data at runtime.

## Privacy

Recognition runs on-device. The SDK does not require an API key, analytics service, or document upload. The sample does not request Internet permission.

## License

OpenMRZ Android is released under the [MIT License](LICENSE).

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for dependency licenses.
