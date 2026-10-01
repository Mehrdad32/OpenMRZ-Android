# OpenMRZ Android

OpenMRZ is a free, offline-first, open-source Android SDK for reading and validating ICAO Machine Readable Zones (MRZ).

The repository is **SDK-first**. The APK under `sample` exists only to exercise the same public SDK that applications consume.

> Current prerelease: **v0.1.0-alpha.2**

## Modules

| Module | Artifact | Purpose |
|---|---|---|
| `openmrz-core` | JVM JAR | TD1 / TD2 / TD3 parsing and ICAO check digits |
| `openmrz-ocr` | Android AAR | Still-image MRZ detection + offline Tesseract OCR |
| `openmrz-android` | Android AAR | CameraX scanner SDK built on `openmrz-ocr` |
| `sample` | APK | Manual device/gallery test application |

## Features

- TD1 (3×30), TD2 (2×36), and TD3 (2×44)
- ICAO check-digit validation
- Offline OCR; no API key or license server
- Automatic MRZ-region detection
- Multiple preprocessing/OCR attempts
- Context-aware OCR correction for common confusions
- CameraX scanner controller
- Gallery/still-image recognition
- Android 6.0+ (API 23)
- Maven publication metadata and source artifacts
- MIT license

## Install from JitPack

Add JitPack:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

For the complete Android scanner SDK:

```kotlin
dependencies {
    implementation(
        "com.github.Mehrdad32.OpenMRZ-Android:openmrz-android:v0.1.0-alpha.2"
    )
}
```

If you only need OCR from an existing `Bitmap`:

```kotlin
implementation(
    "com.github.Mehrdad32.OpenMRZ-Android:openmrz-ocr:v0.1.0-alpha.2"
)
```

If you only need the parser:

```kotlin
implementation(
    "com.github.Mehrdad32.OpenMRZ-Android:openmrz-core:v0.1.0-alpha.2"
)
```

JitPack publishes the modules independently, so Maven users receive transitive dependencies automatically.

## Still-image OCR

```kotlin
TesseractMrzRecognizer(context).use { recognizer ->
    val result = recognizer.recognize(bitmap)

    when (result.status) {
        MrzScanStatus.VERIFIED -> {
            // Safe to consume automatically.
        }

        MrzScanStatus.CHECKSUM_VALID_LOW_CONFIDENCE,
        MrzScanStatus.NEEDS_REVIEW -> {
            // Ask for a rescan or human review.
        }

        MrzScanStatus.NOT_RECOGNIZED -> {
            // No useful MRZ was found.
        }
    }
}
```

## Camera scanner SDK

The host application owns runtime permission UX; the SDK owns CameraX preview/analysis and MRZ recognition.

```kotlin
val scanner = OpenMrzScanner(
    context = this,
    lifecycleOwner = this,
    previewView = previewView,
    listener = object : OpenMrzScannerListener {
        override fun onResult(result: MrzOcrResult) {
            if (result.isTrusted) {
                val document = (result.parseResult as MrzParseResult.Success).document
                // Use document fields.
            }
        }

        override fun onError(error: Throwable) {
            // Handle camera/OCR errors.
        }
    },
)

scanner.start()
```

Call `scanner.close()` when the owning component is destroyed.

## What “valid” means

OpenMRZ intentionally separates three different ideas:

- `validation.checkDigitsValid`: ICAO check digits agree.
- `validation.isValid`: check digits **and** basic MRZ field structure are valid.
- `result.isTrusted`: the OCR read is `VERIFIED`, including confidence/correction limits.

This prevents a low-confidence OCR read from being presented as trustworthy merely because the checksum-protected fields happen to pass.

The sex field is read from the MRZ character (`M`, `F`, `X`, or filler); OpenMRZ does not infer it from the portrait.

## Direct AAR downloads

Every GitHub prerelease contains:

- `openmrz-android-<version>.aar`
- `openmrz-ocr-<version>.aar`
- a sample APK
- SHA-256 checksums

For application projects, Maven/JitPack is recommended because it resolves CameraX, Tesseract4Android and module dependencies transitively.

## Build

Requirements:

- JDK 17
- Android SDK 36
- Gradle wrapper included

```bash
./gradlew :openmrz-core:test :openmrz-ocr:test
./gradlew :openmrz-android:assembleRelease
./gradlew :sample:assembleDebug
./gradlew publishToMavenLocal
```

On a clean build, `openmrz-ocr` downloads the Apache-2.0 licensed `eng.traineddata` from the official Tesseract `tessdata_best` repository and packages it into the AAR. Installed applications do not download OCR data at runtime.

## Privacy

Recognition runs on-device. The SDK does not require an API key, analytics service, or document upload. The sample does not request Internet permission.

## License

OpenMRZ Android is released under the [MIT License](LICENSE).

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for dependency licenses.
