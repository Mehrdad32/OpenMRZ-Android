# OpenMRZ Android

A modern, offline-first, open-source MRZ toolkit for Android.

OpenMRZ reads and validates Machine Readable Zones (MRZ) on passports, visas, residence permits, and identity documents. Parsing follows ICAO Doc 9303 field layouts and check-digit rules.

> Status: **alpha**. A testable Android scanner app is included.

## Features

- Fully offline recognition at runtime
- No API key, license server, analytics, or document upload
- TD1 (3×30), TD2 (2×36), and TD3 (2×44)
- ICAO check-digit validation
- CameraX live scanner
- Gallery image testing
- Tesseract 5 OCR via Tesseract4Android
- Android 5.0+ (API 21)

## Modules

| Module | Purpose |
|---|---|
| `openmrz-core` | Pure JVM MRZ parsing and ICAO check digits |
| `openmrz-ocr` | Android OCR |
| `sample` | CameraX scanner and Gallery test app |

## Build

```bash
./gradlew :openmrz-core:test
./gradlew :sample:assembleDebug
```

The OCR language model is pulled from the official Tesseract `tessdata_fast` repository at build time and packaged into the APK. The installed application does not download a model.

## Testing

1. Install the APK from GitHub Releases.
2. Grant camera permission.
3. Put the 2 or 3 MRZ lines inside the green frame.
4. Keep the document flat and reduce glare.
5. Or tap **Gallery** to test an existing document photo.
6. A complete read is shown as **Valid MRZ found**.

## Privacy

Recognition runs locally. The sample application does not request Internet permission and does not log MRZ content.

## License

OpenMRZ Android is released under the [MIT License](LICENSE). Third-party components retain their own licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
