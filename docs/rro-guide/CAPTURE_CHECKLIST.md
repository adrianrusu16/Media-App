# PandaWave RRO capture checklist

Use the [image filename checklist](../images/README.md) to choose the exact destination filename before capturing.

Use one copy per device/build and scenario. Capture a baseline and an overlay result with the same content, scroll position, theme, font scale and restrictions.

| Metadata | Value |
| --- | --- |
| App package / version / commit | |
| Overlay package / version / APK hash | |
| Device / HU model / display | |
| AAOS build fingerprint / API | |
| App user ID | |
| Resolution / density / font scale | |
| Selected PandaWave theme | |
| Android day/night mode | |
| Parked / restricted state | |
| Scenario and playback state | |
| Baseline file / overlay file | |
| Capture date / tester | |

- [ ] Use a demo account for externally shared captures.
- [ ] Save overlay list and dump for the actual app user.
- [ ] Save resource lookup for each canary.
- [ ] Relaunch PandaWave after changing overlay state.
- [ ] Capture Home and Now Playing baseline/overlay pairs.
- [ ] Check restricted entry points and focus/disabled controls.
- [ ] Capture all additional screens relevant to the changed resource families.
- [ ] Save original PNGs; keep annotations in separate files.
- [ ] Use video for voice/ambient/splash timing evidence.
- [ ] Roll back and confirm the previous effective values.

| Resource / change | Expected result | Actual result | Evidence file | Pass/fail |
| --- | --- | --- | --- | --- |
| | | | | |

Return to the [guide](RRO_GUIDE.md).
