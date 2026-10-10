# Sukun

An unofficial, independently maintained fork of [Khushu by greykaizen](https://github.com/greykaizen/khushu), focused on prayer-time reliability, location accuracy, privacy, and data safety.

**All changes made for Sukun—including code and this README—are written entirely by AI and reviewed by AI.** Automated tests, builds, and lint provide additional checks. This disclosure applies to this fork's changes; the inherited Khushu code retains its original authorship and attribution.

This fork is not endorsed by the original developer. Original copyright notices and the [GPLv3 license](LICENSE) are preserved. The original project description and attribution remain in [the upstream README](docs/UPSTREAM_README.md).

Sukun uses an independently drawn ring mark and production application ID `io.github.raiquiaseishun.sukun`. Current debug builds use `io.github.raiquiaseishun.sukun.debug` and the label **Sukun Test**, with separate app data. It can coexist with Khushu; its data is separate. It cannot update the original app signed by another key. The Kotlin namespace remains unchanged to reduce synchronization conflicts.

Phone-test downloads require a [retained test signing key](docs/TEST_SIGNING.md). Once configured, newer APKs update the same test app and preserve data. The old temporary-key APKs cannot transition through a normal update.

## Why this fork exists

I started Sukun after encountering issues with Khushu's Qibla finder and GPS/location behavior. I use AI to investigate and make changes because I don't know how to code. I do not personally review the code; code review is also performed by AI.

I'm maintaining these changes independently rather than submitting them upstream. I don't know the original developer's perspective on AI-generated code, and I don't want to burden the maintainer with reviewing changes that I cannot personally review or assess.

## Install a test build

Sign in to GitHub, open a successful [Sukun checks run](https://github.com/RaiquiaSeishun/Khushu_Test/actions/workflows/checks.yml), and download the **sukun-phone-test** artifact. Extract the ZIP and open `sukun-fdroid-debug.apk` on an Android 11 or newer phone. These are debug-signed test builds, not an F-Droid listing or production release. See [the phone checklist](docs/PHONE_TESTING.md) for installation details and checks.

## Home

The Qibla compass uses the corrected great-circle bearing and a heading corrected for magnetic declination. Its north marker moves with the phone; direction markers are hidden when no heading is available. Physical compass accuracy still depends on the phone sensors and nearby magnetic interference.

Quran reading handles nested Tajweed formatting and shows one verse-end marker. Chapter text is prepared off the UI thread, and verse-by-verse translation display uses the selected saved edition. The translation picker opens with Downloaded, including bundled and validated saved editions across sources; Browse shows the full catalogs. Fawaz, Quran Foundation and QuranEnc downloads are checked for all 6,236 verses before atomic installation. Download errors preserve the current selection and existing saved edition. Translation footnotes are omitted from the text-only reader rather than flattened into sentences.

Home retains its prayer card, source notice, upcoming events and daily prayer list. Explore contains only Qibla; the Mosques and Events shortcuts have been removed. Qibla stays beneath the prayer list and is always shown, without a scroll-triggered reveal. Official JAKIM source information uses a compact row with separate Details. Pull-to-refresh finishes after success or failure and ignores duplicate pulls while a request is active. Guided Prayer and its editor have been removed; daily prayer tracking, Tasbih and Study remain available. A saved Pray startup choice opens Home after updating.

## Development

Use a full JDK 21, Android SDK platform 36, build-tools 36.0.0, and the checksum-pinned Gradle 9.5.1 wrapper. The `full` flavor uses downloadable fonts; `fdroid` uses bundled fonts.

```sh
bash gradlew :app:testFullDebugUnitTest :app:testFdroidDebugUnitTest
bash gradlew :app:assembleFullDebug :app:assembleFdroidDebug
bash gradlew :app:lintFullDebug :app:lintFdroidDebug
bash gradlew :app:connectedFdroidDebugAndroidTest
```

Connected tests require an Android device or emulator (API 30 or newer). Cloud initialization and validation instructions are saved in the environment configuration. Do not create another Git worktree: cloud tasks already provide isolated checkouts.

## Reliability and privacy

Automatic location refresh runs only while the app is visible. Fixes must be recent and have usable accuracy; approximate permission is supported. Previous coordinates survive failures and location status is separate from prayer-data refreshes.

Local prayer mode makes no AlAdhan requests. Unsupported local conventions use a disclosed Muslim World League fallback. Online mode sends coordinates, date, convention, madhab, and time zone to AlAdhan over HTTPS. Successful responses are cached in private app cache for 24 hours by the complete request, including across process restarts; simultaneous identical requests share a fetch. Failed requests can be retried.

**For official Malaysian times, choose Official JAKIM / e-Solat in Prayer Settings and select your official prayer zone.** Complete monthly timetables are cached for offline use, with daily revision checks and a displayed source/retrieval status. Keep manual offsets at zero to match official entries. Optional [GPS zone selection](docs/AUTOMATIC_PRAYER_ZONE.md) uses a community map and sends coordinates for lookup; verify the suggested zone. Manual selection is always available, including for ambiguous/special areas.

AlAdhan’s Malaysian convention remains a separate calculated source and differs from official zone timetables in our recorded samples. If official data is unavailable, Home and the widget disclose an approximate local fallback; reminders are skipped for dates lacking official data. See [JAKIM and icon customization](docs/JAKIM_AND_ICONS.md) for details and validation scope.

See [development and release notes](docs/FORK_DEVELOPMENT.md) for verified checks, limitations, synchronization, and release prerequisites. Upstream pull requests require user approval.
