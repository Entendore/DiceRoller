# 🎲 RPG Dice Roller

Offline dice roller: d1–d20 + d100, notation (`2d6+3`, `4d6kh3`), advantage/disadvantage,
crits, weighted dice with a fairness simulator, themes, quick-roll presets, shake-to-roll.

## Architecture
MainActivity (thin ViewBinding binder)
  └─ DiceViewModel (StateFlow state + one-shot events, injectable Random)
       ├─ DiceMath (pure: parse/roll/weights — fully unit-tested)
       ├─ ShakeDetector (pure: threshold + cooldown — fully unit-tested)
       └─ DiceRepository (interface) → DiceDataStore (DataStore + legacy migration)

## Build & test
./gradlew lintDebug testDebugUnitTest verifyPaparazziDebug   # lint + JVM tests + screenshots
./gradlew connectedDebugAndroidTest                          # E2E + leak analysis (emulator)
./gradlew bundleRelease                                      # signed AAB (env-var signing)

## Releasing
1. Bump versionCode/versionName in app/build.gradle.kts
2. Commit, tag `vX.Y.Z`, push — CI builds and uploads the signed AAB + mapping.txt
3. Upload AAB to Play Console internal track; upload mapping.txt for deobfuscation

## Notes
- Zero permissions; all data on-device (honest Play Data Safety: "no collection").
- Debug builds include LeakCanary; instrumented tests fail on any memory leak.
- First record screenshot goldens: `./gradlew recordPaparazziDebug`, commit the PNGs.
