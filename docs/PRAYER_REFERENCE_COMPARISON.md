# Malaysian prayer reference comparison

On 2026-10-09, live HTTPS requests compared JAKIM e-Solat zone WLY01 (Kuala Lumpur/Putrajaya) with AlAdhan method 17 at Kuala Lumpur coordinates 3.1390, 101.6869. AlAdhan requests used Shafi (`school=0`), `Asia/Kuala_Lumpur`, and zero user offsets, matching Sukun's API parameters for this configuration.

The complete request URLs, retrieval timestamps, responses, and differences are retained in [prayer-reference-samples.json](prayer-reference-samples.json). These are observed references, not mocked test fixtures or proof of complete Malaysian coverage.

Differences below are **AlAdhan minus JAKIM**, in minutes. Negative values mean AlAdhan is earlier.

| Date | Fajr | Dhuhr | Asr | Maghrib | Isha |
| --- | ---: | ---: | ---: | ---: | ---: |
| 2026-01-01 | -11 | -2 | -2 | -2 | -1 |
| 2026-07-01 | -10 | -3 | -2 | -3 | -2 |
| 2026-10-09 | -10 | -2 | -1 | -2 | -1 |

For example, on 2026-10-09 JAKIM returned Fajr 05:51, Dhuhr 13:03, Asr 16:17, Maghrib 19:04, and Isha 20:13. AlAdhan returned 05:41, 13:01, 16:16, 19:02, and 20:12 respectively.

AlAdhan names its method “Jabatan Kemajuan Islam Malaysia (JAKIM)”, but a coordinate calculation is not an official zone timetable. These observations establish a mismatch in these samples; they do not establish its full cause, a correction valid for other dates/zones, or a safe global adjustment. No calculation angles or offsets were changed to conceal the difference.

Official timetable support remains unresolved. A follow-up implementation should support an explicitly selected official zone, validate date/zone response identity, retain source and retrieval status, cache official data for offline use, and clearly disclose any fallback. Validate independent samples across multiple zones and seasons before making official accuracy claims.

