# Sukun phone test

Use a development APK for these checks. It is debug-signed, not a production release. Android 11 or newer is required. The current build is labelled **Sukun Test** and installs separately from Khushu and the earlier Sukun APK. It starts with separate app data; no data import is implemented.

Download the `sukun-phone-test` artifact from a successful **Sukun checks** run on the repository's [GitHub Actions page](https://github.com/RaiquiaSeishun/Khushu_Test/actions/workflows/checks.yml). GitHub requires you to sign in to download Actions artifacts. On a phone, open the run in your browser and use desktop-site mode if the artifact section is hidden.

New phone downloads require the [one-time retained test-key setup](TEST_SIGNING.md). After the first installation signed with that key, install newer APKs as updates to preserve data. A missing signing secret withholds the artifact instead of publishing another temporary-key APK. Earlier downloads used different temporary keys and cannot be updated by the new key.

The download is a ZIP file. Extract it, then open `sukun-fdroid-debug.apk` and allow installation from your browser or file manager if Android prompts you. The archive also includes a checksum, source commit, and this checklist. Artifacts expire after 30 days; a new successful run produces a fresh download. This is a test build, not an F-Droid listing or production release.

Alternatively, install with a connected computer:

```sh
adb install -r sukun-fdroid-debug.apk
```

If Android reports a signature mismatch with an existing Sukun installation, stop and record it. Do not uninstall an installation containing data just to bypass the mismatch.

Record phone model, Android version, app version, source commit, permission mode, and results. Avoid sharing exact home coordinates or personal screenshots publicly.

| Check | Action and expected result |
| --- | --- |
| Startup | Open Sukun. Verify the independent name/icon and that Home and Settings open without crashing. |
| Permission denied | Deny location, then request a refresh. Expect a clear permission/error status and retention of any prior coordinates. |
| Approximate location | Grant approximate location and retry outdoors. Expect a usable fix or an explicit error; a successful fix reports its age and accuracy. |
| Precise location | Grant precise location and refresh outdoors. Check coordinates against a trusted map, and record fix age/accuracy rather than assuming GPS is always best. |
| Movement | Set refresh to 15 minutes, keep the app foreground, move to a clearly different location, and wait past the interval. Expect the saved fix and prayer calculation to refresh. Record battery use. Do not operate the phone while driving. |
| Background and resume | Background the app during a refresh, then reopen it after the interval. Expect cancellation in the background and a new foreground attempt when eligible. This feature does not promise background location tracking. |
| Revocation | Revoke location permission in Android Settings and reopen Sukun. Expect a permission/error status without a crash or erased prior coordinates. |
| Offline source | Select LOCAL, disable connectivity, and change a supported method. Expect local calculation; an unsupported method must disclose its approximate fallback. |
| API cache | Select API, fetch a date/location while online, then reopen offline with the same parameters within 24 hours. Expect retained cached timings. A date/location/method change must not silently reuse another request's cache entry. |
| Official JAKIM | Select the official source and your verified zone, keep offsets at zero, and compare all five entries with e-Solat. Reopen offline after fetching the month and verify the cached source/retrieval status. Change zone, refresh manually, and test a date without cached data: the approximate fallback must be disclosed and official reminders must be skipped. |
| GPS prayer zone | Enable Select prayer zone using GPS, grant GPS access and Retry. Verify the selected code against the official local zone. Try travel/refresh, approximate permission, offline lookup, a border and a special/highland area: unclear results require manual selection and must not use the previous zone as newly confirmed official data. Pick a manual zone and verify a late GPS response does not override it. This feature sends coordinates to a community service and follows foreground refreshes. |
| Icon customization | In Customize, switch Dynamic, Dark, Light, and Green. Return to the launcher and check one working icon, then reopen/restart and verify the selection persists. Turn off launcher themed icons if you want to see the chosen background colors. |
| API failure | Offline, choose uncached parameters. Expect a disclosed approximate fallback rather than an official accuracy claim. |
| Notifications | Grant notification permission, enable a prayer reminder, and verify delivery on the phone. Repeat while locked and under battery restrictions; record delays or failures. Revoke permission and verify no crash. |
| Saved data | Save a canvas/layout, restart, and confirm it remains. The automated 3→5 migration test does not establish every historical upgrade path. |

Official JAKIM mode now uses the selected zone’s published entries. The separate Malaysian AlAdhan method does not reproduce the official JAKIM zone timetable in the recorded samples. See [the comparison](PRAYER_REFERENCE_COMPARISON.md). Phone trials cannot establish official accuracy by themselves. Signed update installation requires two builds signed with the same retained production key and remains a separate release test.


See [the current feature notes](JAKIM_AND_ICONS.md) for the new test identity, cache behavior, validation scope, and signing limitations.

### Prayer source status card

The Home screen uses a compact source card rather than red error text for every notice. Official JAKIM data uses a neutral card. Approximate local times remain marked in the summary, including while GPS zone selection is pending. Tap the compact source row or **Details** to see the GPS lookup status, source/date/retrieval information, derived-night-time disclosure and any failure or adjustment explanation in a dialog; tap **Close** to return to Home.

On a phone, check both light and dark themes and large text: the summary must remain readable, the Details dialog must be readable without truncation, and a pending GPS lookup must not simultaneously instruct you to select a manual zone. Verify that unavailable official data still says the displayed times are approximate, while valid cached official data is presented as source information.

### Home and Guided Prayer removal

Update over the existing app, including if **Pray** was previously the startup tab. Home should open instead, with no Pray navigation tab, session button, Guided Prayer picker or Pray Screen customization. Existing Tasbih collections, settings and icon choice should remain intact.

Home should retain the original section order: prayer-time card, compact source note, upcoming events and daily prayers, with **Explore** beneath the prayer list. Check that **Qibla** is already shown without waiting for a scroll-triggered animation, and that a healthy JAKIM note and the shortcuts fit the reported portrait-screen proportions. Qibla should open the compass immediately. Mosques and Events shortcuts should be absent; Qibla should retain its original tile size and bottom-left position. The healthy official source note should be a slim row, with its longer explanation shown separately. Check normal portrait layout and large text; shorter landscape screens may still require scrolling.

### Qibla direction correction

Use a known location and keep the phone flat, away from magnets and metal. Kuala Lumpur (3.139, 101.6869) should show about 293° from true north. Compare with a trusted compass or Qibla reference using the same coordinates and true-north convention. Rotate the phone: facing east should put N on the left, facing south should put N below, and facing west should put N on the right. The Qibla arrow should point up when the phone faces the displayed bearing. Repeat in landscape so the screen's top remains the heading reference. Without a heading, the app should show the numeric bearing and unavailable-heading explanation, with no N or directional arrow. Sensor calibration and real-phone accuracy need physical testing; emulator checks cannot establish these.

### Quran formatting and downloaded translations

Open 2:190 with Tajweed on: nested rules should be coloured without visible HTML tags or missing Arabic letters. Turn Tajweed off and check there is one verse-end number, including on 1:1 and 2:255. Test reading mode and verse-by-verse mode. Download two translations and reopen the picker: Downloaded should list both regardless of their source, alongside the bundled editions. Switching between them should work offline. Browse should expose all supported sources. Test a Quran Foundation and QuranEnc download when connected; an unavailable edition should show a retryable failure, keep the previous selection, and never appear as a complete downloaded edition. Interrupt a download and retry it; the previous valid copy must remain readable. Verify your existing Mustafa Khattab Allah edition remains selected after updating, and compare several displayed verses with its published reference.
