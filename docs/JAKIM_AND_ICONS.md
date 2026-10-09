# Official prayer times and launcher icons

## Select official Malaysian times

In Prayer Settings, select **Official JAKIM / e-Solat (Malaysia)** as the source, then select the official zone covering your location. No zone is selected by default. The 60 codes and descriptions were copied from the official e-Solat selector on 2026-10-09. Manual mode remains the default; optional [GPS zone selection](AUTOMATIC_PRAYER_ZONE.md) uses a separate community service, with explicit opt-in and manual override. Check your zone against the official website, especially when travelling or visiting special/highland areas.

Keep the five manual prayer offsets at zero to match published entries. Calculation convention and madhab controls are hidden for this source because the published timetable determines the times. AlAdhan's Malaysian calculation remains a separate option and retains its previously observed differences.

The app requests an entire calendar month using e-Solat's documented website date-range request: POST `index.php?r=esolatApi/takwimsolat&period=duration&zone=<zone>` with `datestart` and `dateend`. Only zone and dates are sent to JAKIM. Response status, zone, every calendar date, complete month coverage, timing format, and prayer ordering are validated before use. Malaysian dates and times are interpreted in `Asia/Kuala_Lumpur`, including when the device uses another time zone.

Monthly responses and retrieval timestamps are saved in private app files, with a limit of 24 month/zone entries. They survive process restarts and remain usable offline for their covered dates. A revision check runs after 24 hours; Home refresh forces a check immediately. A failed revision check retains valid saved data and labels it as cached, with its retrieval time. Failed checks are throttled for one minute. Uncached dates, wrong zones, invalid responses, or missing zone selection produce a clearly labelled approximate local fallback on Home and the widget. Prayer reminders are skipped for dates without a valid official timetable. Selecting a different zone or manually refreshing also reschedules enabled reminders.

The five main prayer times, imsak, and sunrise come from the published entries. Night fractions are derived from published sunset and next-day Fajr; they are not official timetable entries and are omitted by the repository when the next day's official data is unavailable. Home's existing supplementary displays may still show approximate local night calculations; the source notice identifies these as derived. Displayed clock times follow the phone's time zone, while timetable identity remains the Malaysian calendar date.

Recorded, unmodified e-Solat responses for WLY01 in January and October 2026 and JHR02 in October 2026 are in `app/src/test/resources/jakim`, with request provenance and SHA256 checksums. Automated tests check exact published entries, date/time-zone handling, zone/month separation, persistence, failed revision checks, concurrent callers, invalid data, offsets, missing-zone fallback, and manual refresh. This validates the recorded cases and request handling, not every zone/date or future provider revision.

## Icon customization

The launcher switcher previously constructed alias class names from the installed application ID. Sukun's Kotlin namespace remains `com.kaizen.khushu`, so those names did not match the manifest. This mismatch was introduced by the earlier Sukun application-ID rebrand. It now uses the actual MainActivity namespace and the installed package separately.

Android 13 and later update the alias states as one batch. Android 11–12 enable the replacement before disabling other aliases. Both paths keep the application running. Invalid styles are rejected before changing any component. The saved selection is restored when the app starts, and Settings persists a selection only after a successful switch. Instrumented tests switch all four styles, require one launcher entry, open each actual alias, recreate the activity, check persistence, compare the rendered icons, and ensure invalid input cannot remove the launcher.

Launchers may cache icons briefly. With launcher themed icons enabled, Android can use the common monochrome mark and wallpaper colors instead of the chosen background. Activity tests grant notification permission first on Android 13+ so the operating-system consent dialog does not pause the tested activity. No icon or lifecycle assertions are disabled. Physical launcher behavior remains a phone check.

## Install this test build

Version 0.24.12 / code 86 uses the debug application ID `io.github.raiquiaseishun.sukun.debug` and the label **Sukun Test**. It installs alongside the earlier Sukun test APK, which used the base ID and a temporary GitHub debug signing key that is no longer available for signing updates. The new test app starts with separate settings/data. Do not uninstall an existing app containing data to bypass a signature conflict.

Production keeps `io.github.raiquiaseishun.sukun` and requires the owner's retained production signing key. The earlier downloads used temporary debug keys. Future phone artifacts require a [retained test key](TEST_SIGNING.md) and use increasing CI test version codes; no artifact is published without that key. The owner has configured the key, and run 37946015508 passed signing/verification, artifact upload and actual APK update checks on API 30/35. This establishes the new update mechanism; it does not recover old signing identities. Production upgrades remain separate release requirements. Download the `sukun-phone-test` artifact from a successful configured GitHub Actions run, extract the ZIP, and open the APK.
