# Changelog

## v0.1.0-alpha.3

- Fixed the OCR regression introduced in alpha.2.
- Replaced the generic English Tesseract model with a dedicated MRZ-trained model from the BSD-3-Clause licensed DoubangoTelecom/tesseractMRZ project.
- Whole-region OCR is primary again; line-by-line OCR is now fallback-only.
- Added multiple conservative bottom-region candidates so automatic detection cannot be the only crop.
- Fixed double region detection in the CameraX scanner.
- Added checksum-guided document-number repair for ambiguous OCR glyphs such as Z/2.
- Tightened issuing-state and nationality validation to three alphabetic characters.
- Added semantic YYMMDD validation; impossible dates such as 230000 are no longer structurally valid.
- Strengthened candidate scoring so validation beats raw OCR confidence.

## v0.1.0-alpha.2

- Refactored the project around reusable SDK modules instead of the sample application.
- Added the `openmrz-android` CameraX scanner AAR.
- Added automatic MRZ region detection.
- Added multiple OCR preprocessing attempts and explicit scan trust states.
- Added Maven/JitPack publication verification.

## v0.1.0-alpha.1

- Initial TD1/TD2/TD3 parser.
- ICAO check-digit validation.
- Tesseract OCR prototype.
- CameraX sample application.
