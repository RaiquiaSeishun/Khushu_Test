# Sukun

An unofficial, independently maintained fork of [Khushu by greykaizen](https://github.com/greykaizen/khushu), focused on prayer-time reliability, location accuracy, privacy, and data safety.

Changes are AI-generated and reviewed with automated checks. This fork is not endorsed by the original developer. Original copyright notices and the [GPLv3 license](LICENSE) are preserved. The original project description and attribution remain in [the upstream README](docs/UPSTREAM_README.md).

Sukun uses an independently drawn ring mark and application ID `io.github.raiquiaseishun.sukun`. It can coexist with Khushu; its data is separate. It cannot update the original app signed by another key. The Kotlin namespace remains unchanged to reduce synchronization conflicts.

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

Malaysia/JAKIM selection is an AlAdhan calculation convention, **not a verified official Malaysian prayer-zone timetable**. Do not use the local fallback as proof of official accuracy. Parameter changes require independently verified reference data.

See [development and release notes](docs/FORK_DEVELOPMENT.md) for verified checks, limitations, synchronization, and release prerequisites. Upstream pull requests require user approval.
