# OpenMRZ Android

Free, offline-first, open-source MRZ scanning SDK for Android.

> Current prerelease: **v0.2.0-beta.2**

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
- `BALANCED`: small MRZ model first, then the larger best model only when needed; recommended default.
- `ACCURATE`: exhaustive best-model preprocessing/crops and line OCR when required.

A checksum+structure-valid fast-model read stops immediately. Fallback crops are first probed with the small model; the larger model is used only when needed. Results expose:

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
    "com.github.Mehrdad32.OpenMRZ-Android:openmrz-android:v0.2.0-beta.1"
)
```

OCR only:

```kotlin
implementation(
    "com.github.Mehrdad32.OpenMRZ-Android:openmrz-ocr:v0.2.0-beta.1"
)
```

Parser only:

```kotlin
implementation(
    "com.github.Mehrdad32.OpenMRZ-Android:openmrz-core:v0.2.0-beta.1"
)
```

Imports use the project namespace:

```kotlin
import ir.mehrdad32.openmrz.android.OpenMrzScanner
import ir.mehrdad32.openmrz.ocr.MrzOcrResult
import ir.mehrdad32.openmrz.core.MrzParseResult
```

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
- `validation.isValid`: check digits plus structural validation agree.
- `result.isTrusted`: OCR result reached `VERIFIED`.

OpenMRZ reads sex from the MRZ field; it never infers it from a portrait.

## Runtime privacy

Recognition runs locally. There is no API key, license server, analytics requirement, or document upload.

## Direct release assets

Each prerelease ships:

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
