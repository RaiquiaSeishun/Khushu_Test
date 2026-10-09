# GPS selection of Malaysian prayer zones

In Prayer Settings, choose **Official JAKIM / e-Solat**, then turn on **Select prayer zone using GPS**. This enables Device GPS. Grant location access using **GPS Access** and use **Retry** to obtain a fresh fix. Existing installations retain manual mode after updating. Selecting any zone in the dropdown turns automatic selection off; turning Device GPS off also disables it.

The feature selects a Malaysian prayer-zone code, such as KDH05. It does not change Android's clock/time zone. Prayer entries continue to come directly from JAKIM/e-Solat; GPS zone detection uses the separate [Waktu Solat community API](https://api.waktusolat.app/docs). That service is not affiliated with or endorsed by JAKIM. Its district map has assumptions, missing special areas, and manually traced subdivisions, so a detected zone must be verified against the official zone covering the user's location. Automatic detection cannot establish authoritative boundaries or universal accuracy.

## Requests, accuracy and fallback

Only after opting in, while JAKIM and Device GPS are selected, the app sends latitude/longitude to `https://api.waktusolat.app/zones/<latitude>/<longitude>`. It first looks up the fix, then checks four cardinal points at the reported accuracy radius (minimum 30 m). Fixes worse than 1 km are rejected without sending coordinates. Known mismatched state codes, unknown zones, malformed responses, failures and inconsistent neighbouring zones are rejected. Federal-territory KUL/PJY/LBN and Negeri Sembilan NSN aliases are validated against the corresponding official zone codes.

The five samples reduce uncertainty near mapped borders; they do not prove every point inside the error circle or validate the underlying map. Conservative manual-only guards cover known special/subdistrict conflicts: Yan, Larut/Matang/Taiping, Ranau, Bentong/Raub, Rompin, Gua Musang, PRK03/PRK04 and Limbang in their applicable returned zones. These guards are not an exhaustive audit of Malaysian boundaries. Choose the official zone manually when travelling to special or highland areas or when the suggested zone differs from the official local reference.

One successful lookup is stored in a private file with its rounded coordinates, checked radius and timestamp. It is reusable for the same rounded location and equal/better accuracy for 24 hours, including offline. A different position or larger uncertainty needs another lookup. The monthly official timetable cache remains separate. Network calls have individual 12-second timeouts and a 25-second overall limit. Cancelling the lookup cancels HTTP calls. No third-party polygon file or implementation is bundled in Sukun.

A new GPS fix invalidates automatic confirmation before lookup. The previous selection remains visible in the dropdown, but an unconfirmed automatic zone is not used to show official prayer entries or schedule/deliver official reminders. Home/widget use the disclosed approximate local fallback until a zone is confirmed or selected manually. Responses from an earlier fix, a different source, a disabled GPS mode or a manual override cannot overwrite the current choice.

Detection follows foreground GPS refreshes and the chosen refresh interval. The app does not track travel using background GPS. When travelling, open the app and use GPS Retry if the saved fix has not refreshed. A failure can be resolved by obtaining a better fix, retrying online, or selecting the official zone manually. Opting out does not change the Android time zone.

## Validation scope

Local JVM checks cover same-location disk restoration, coordinate/accuracy separation, invalid data, border disagreement, known special-area rejection, provider state aliases, cancellation, and exclusion of an unconfirmed zone from official prayer data/reminders. Android checks exercise persistence, new-fix invalidation, stale response rejection, manual override, GPS disable and source changes. See [validation evidence](VALIDATION.md) for executed outcomes.

On 2026-10-09 the actual compiled client returned KDH05 for a public Kulim point, WLY01 for Kuala Lumpur and PNG01 for George Town, including surrounding-point checks. Each result was then restored using a new client with networking blocked. A public Genting Highlands point required manual selection rather than accepting the district-level PHG04 result. These checks validate those examples and request handling, not all Malaysian locations, future provider updates, physical GPS accuracy or physical-phone reminder delivery.

Provider implementation references used to inspect the protocol and limitations: [current API](https://github.com/mptwaktusolat/api-waktusolat-x) and [community map methodology](https://github.com/mptwaktusolat/malaysia.geojson). Sukun's client is independently written; map polygons are not redistributed.
