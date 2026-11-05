# MovieShadow100 — native Android rebuild

This folder is a clean Kotlin + Jetpack Compose + Media3 Android implementation, separate from the original Flutter application at the repository root.

## Build
Use JDK 17 and Gradle 8.11.1:
`gradle --no-daemon testDebugUnitTest assembleDebug`

## Implemented in this foundation
- Local video import via Android document picker.
- Separate manual English and Chinese subtitle imports.
- SRT / WebVTT and basic ASS/SSA parsing.
- Independent subtitle alignment by temporal overlap.
- Tap a subtitle row to seek to its timestamp.
- Media3 playback, landscape immersive mode, A-B loop and timed pause.

## Not yet a release build
Room persistence, offset adjustment, robust subtitle encoding detection, advanced ASS styling, playback gesture controls, and device QA remain before a daily-use release. The workflow runs parser tests before producing a debug APK.
