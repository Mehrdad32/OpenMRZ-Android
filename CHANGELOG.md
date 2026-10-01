# Changelog

## v0.2.0-beta.1

- Promoted OpenMRZ to the first public beta.
- Finalized the public package/namespace prefix as `ir.mehrdad32.openmrz.*`.
- Changed the default OCR strategy to progressive `BALANCED` mode.
- Added early exit: a verified first OCR attempt returns immediately.
- Fallback crops are created lazily only when the primary MRZ region fails.
- Binary preprocessing is no longer run unconditionally in the common path.
- Line-by-line OCR remains an `ACCURATE`-mode fallback only.
- Added `processingTimeMs` while retaining `attemptCount` for real-device profiling.
- The sample APK now displays processing latency and OCR attempt count.
- Retained the dedicated MRZ-trained model and alpha.3 accuracy fixes.

## v0.1.0-alpha.3

- Fixed the OCR regression introduced in alpha.2.
- Replaced the generic English Tesseract model with a dedicated MRZ-trained model.
- Whole-region OCR became primary again; line OCR became fallback-only.
- Fixed CameraX double region detection.
- Added checksum-guided document-number repair and stricter structural validation.

## v0.1.0-alpha.2

- Refactored the repository around reusable SDK modules.
- Added the `openmrz-android` CameraX scanner AAR.
- Added Maven/JitPack publication verification.

## v0.1.0-alpha.1

- Initial TD1/TD2/TD3 parser, OCR prototype and CameraX sample.
