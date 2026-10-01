# Changelog

## v0.1.0-alpha.2

- Refactored the project around reusable SDK modules instead of the sample application.
- Added the `openmrz-android` CameraX scanner AAR.
- Added automatic MRZ region detection.
- Added contrast and binary preprocessing attempts.
- Added line-oriented OCR attempts in accurate mode.
- Added context-aware OCR character correction.
- Added stricter structural validation for MRZ fields.
- Split checksum validity from final OCR trust status.
- Added `VERIFIED`, `CHECKSUM_VALID_LOW_CONFIDENCE`, `NEEDS_REVIEW`, and `NOT_RECOGNIZED`.
- Switched bundled OCR data from `tessdata_fast` to `tessdata_best`.
- Added Maven/JitPack publication verification.
- GitHub releases now contain both SDK AARs plus the test APK.

## v0.1.0-alpha.1

- Initial TD1/TD2/TD3 parser.
- ICAO check-digit validation.
- Tesseract OCR prototype.
- CameraX sample application.
