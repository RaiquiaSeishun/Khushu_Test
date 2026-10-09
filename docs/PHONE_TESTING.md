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
| Icon customization | In Customize, switch Dynamic, Dark, Light, and Green. Return to the launcher and check one working icon, then reopen/restart and verify the selection persists. Turn off launcher themed icons if you want to see the chosen background colors. |
| API failure | Offline, choose uncached parameters. Expect a disclosed approximate fallback rather than an official accuracy claim. |
| Notifications | Grant notification permission, enable a prayer reminder, and verify delivery on the phone. Repeat while locked and under battery restrictions; record delays or failures. Revoke permission and verify no crash. |
| Saved data | Save a canvas/layout, restart, and confirm it remains. The automated 3→5 migration test does not establish every historical upgrade path. |

Official JAKIM mode now uses the selected zone’s published entries. The separate Malaysian AlAdhan method does not reproduce the official JAKIM zone timetable in the recorded samples. See [the comparison](PRAYER_REFERENCE_COMPARISON.md). Phone trials cannot establish official accuracy by themselves. Signed update installation requires two builds signed with the same retained production key and remains a separate release test.


See [the current feature notes](JAKIM_AND_ICONS.md) for the new test identity, cache behavior, validation scope, and signing limitations.
