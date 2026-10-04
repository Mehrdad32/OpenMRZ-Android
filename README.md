# OpenMRZ Android

Free, offline-first, open-source MRZ scanning SDK for Android.

> Current stable release: **v0.2.0**

OpenMRZ is SDK-first. The sample APK only demonstrates the same public APIs shipped in the AARs.

## Package identity

The public Kotlin/Java package prefix is:

```text
ir.mehrdad32.openmrz
├── core
├── ocr
└── android
```

JitPack's Maven coordinate still starts with `com.github...`; that is a repository coordinate and is unrelated to Kotlin/Java package names.

## Modules

| Module | Artifact | Purpose |
|---|---|---|
| `openmrz-core` | JVM JAR | TD1 / TD2 / TD3 parser and ICAO validation |
| `openmrz-ocr` | Android AAR | Offline MRZ-trained Tesseract OCR |
| `openmrz-android` | Android AAR | CameraX scanner SDK |
| `sample` | APK | Device/gallery test application |

## Performance modes

```kotlin
MrzRecognizerConfig(
    mode = MrzRecognitionMode.BALANCED // default
)
```

- `FAST`: small MRZ model, one primary pass.
- `BALANCED`: FAST MRZ OCR first; if the result is plausible but not checksum-valid, one targeted generic OCR pass is run only for the second MRZ line. Recommended default.
- `ACCURATE`: exhaustive best-model preprocessing/crops and line OCR when required.

A checksum-valid FAST read stops immediately. BALANCED avoids the expensive BEST model entirely; BEST/exhaustive OCR is reserved for ACCURATE mode. Results expose:

```kotlin
result.processingTimeMs
result.attemptCount
```

for real-device profiling.

## JitPack

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Complete scanner:

```kotlin
implementation(
    "com.github.Mehrdad32.OpenMRZ-Android:openmrz-android:v0.2.0"
)
```

OCR only:

```kotlin
implementation(
    "com.github.Mehrdad32.OpenMRZ-Android:openmrz-ocr:v0.2.0"
)
```

Parser only:

```kotlin
implementation(
    "com.github.Mehrdad32.OpenMRZ-Android:openmrz-core:v0.2.0"
)
```

Imports use the project namespace:

```kotlin
import ir.mehrdad32.openmrz.android.OpenMrzScanner
import ir.mehrdad32.openmrz.ocr.MrzOcrResult
import ir.mehrdad32.openmrz.core.MrzParseResult
```

## Image-only usage (no CameraX)

Applications that already capture or crop documents do not need the CameraX artifact. Depend only on `openmrz-ocr`:

```kotlin
implementation(
    "com.github.Mehrdad32.OpenMRZ-Android:openmrz-ocr:v0.2.0"
)
```

Then pass a host-owned `Bitmap`:

```kotlin
OpenMrzImageRecognizer(context).use { recognizer ->
    // Full camera frame or a cropped passport/ID image.
    // OpenMRZ locates the MRZ region inside the supplied image.
    val result = recognizer.recognize(documentBitmap)

    // If your own pipeline already cropped tightly to the MRZ lines:
    val mrzResult = recognizer.recognizeMrzCrop(mrzOnlyBitmap)
}
```

The recognizer never recycles the caller's input Bitmap.

## Scanner usage

```kotlin
val scanner = OpenMrzScanner(
    context = this,
    lifecycleOwner = this,
    previewView = previewView,
    listener = object : OpenMrzScannerListener {
        override fun onResult(result: MrzOcrResult) {
            if (result.isTrusted) {
                val document =
                    (result.parseResult as MrzParseResult.Success).document
            }
        }
    },
)

scanner.start()
```

The host app owns runtime camera-permission UX. Call `scanner.close()` when the owner is destroyed.

## Trust model

- `validation.checkDigitsValid`: ICAO check digits agree.
- `validation.isValid`: check digits plus structural validation agree, including recognized ISO/ICAO issuing-state and nationality codes.
- `result.isTrusted`: OCR result reached `VERIFIED`.

OpenMRZ reads sex from the MRZ field; it never infers it from a portrait.

## Runtime privacy

Recognition runs locally. There is no API key, license server, analytics requirement, or document upload.

## Direct release assets

Each release ships:

- `openmrz-android-<version>.aar`
- `openmrz-ocr-<version>.aar`
- sample APK
- SHA-256 checksums

## Build

```bash
./gradlew :openmrz-core:test :openmrz-ocr:test
./gradlew :openmrz-android:assembleRelease
./gradlew :sample:assembleDebug
./gradlew publishToMavenLocal
```

Default Maven group for local/standard publication is `ir.mehrdad32.openmrz`.

## License

MIT. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for dependency/model licenses.
