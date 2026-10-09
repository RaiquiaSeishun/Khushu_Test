# Validation evidence

Validation ran in the cloud machine on 2026-10-09 against the final application changes. It does not establish restoration in a fresh task or a signed release.

| Check | Executed result |
| --- | --- |
| JDK / Gradle | Full JDK 21.0.12.1 and checksum-pinned Gradle 9.5.1 activated |
| Setup repeatability | Install script rerun successfully with retained dependencies and caches |
| `testFullDebugUnitTest` | 17 passed, 0 failed/errors/skipped; 15 new regressions and 2 existing placeholder tests |
| `testFdroidDebugUnitTest` | Same 17 passed, 0 failed/errors/skipped |
| `assembleFullDebug` | Passed; debug APKs produced |
| `assembleFdroidDebug` | Passed; debug APKs produced |
| `lintFullDebug` | Passed with 0 errors; existing warnings remain |
| `lintFdroidDebug` | Passed with 0 errors; existing warnings remain |
| `connectedFdroidDebugAndroidTest` | 4 passed, 0 failed/errors/skipped on Android 11 / API 30 x86_64 software emulator |
| Git integrity | Original upstream history and GPLv3 license retained; foundation commits pushed to origin/main; no upstream contribution |
| GitHub / AlAdhan / JAKIM metadata and references | Live requests succeeded on 2026-10-09; repository public, not server-linked fork; three WLY01 samples expose a timetable mismatch |
| Release builds / signing / installation of updates | Not executed; manual draft-release workflow is prepared |
| GitHub CI | First run failed before tests in SDK setup; corrected run in progress, final outcome pending |

The three new Android tests verified that a version-3 canvas database retained its saved layout/custom preset after migration to version 5, that the non-exported provider can read update files but refuses unrelated private cache files, and that MainActivity reaches RESUMED without finishing. The fourth is the existing package-context check, updated to use the fork's application ID.

The final build/JVM/lint command was run through `/workspace/sukun-install.sh`. The final connected run used the same toolchain and:

```sh
bash gradlew -Dddmlib.getprop.timeout.sec=30 \
  -Dorg.gradle.java.installations.paths=/workspace/tools/jdk21 \
  -Dorg.gradle.java.installations.auto-download=false \
  :app:connectedFdroidDebugAndroidTest --console=plain
```

Initial attempts were environment failures: Java lacked javac, tooling tried to use unwritable cache directories, the cold software emulator lost Android services, and DDMLib's two-second property query was too short. These were diagnosed and corrected. UI-test animations were disabled to let ActivityScenario become idle. The successful device run completed all four tests in six minutes; failed installation/discovery attempts executed no application tests and are not counted as passing.

Current raw evidence is outside the checkout in `/workspace/downloads/final-setup-validation.log` and `/workspace/downloads/device-timeout-validation.log`; current test/lint reports are under `app/build`. Live processes and generated reports should not be assumed to survive publication.

These checks validate development capabilities and the described regression behavior. The live [reference samples](PRAYER_REFERENCE_COMPARISON.md) show the current API method differs from the official zone timetable. These checks do not establish official accuracy, physical GPS provider accuracy/movement/battery use, notification delivery under Doze, unknown historical database versions, real signed APK upgrades, or third-party redistribution rights. Do not recommend publishing an application release until those required checks are resolved.
