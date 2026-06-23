# PW-PRD-004: Ambient Now Playing

**Status:** Approved
**Owners:** Product, Design, Engineering
**Last updated:** 2026-06-23
**Related:** `PW-PRD-001-adaptive-app-shell.md`, `PW-PRD-002-persistent-preferences-and-rotary-ui.md`, `PW-PRD-003-rro-bambooui-system.md`

## Problem

Now Playing is optimized for direct control, but a parked listener who stops interacting benefits from a calmer, artwork-led presentation that makes the current music visible without leaving transport controls permanently on screen. The experience must never appear while the vehicle is moving or while AAOS distraction restrictions are active, and FFT capture must not continue when the presentation is hidden.

## Outcomes

- Parked listeners can see a full-surface artwork and music-visualization experience after a configurable period of inactivity.
- Any user interaction restores interactive Now Playing without accidentally activating an underlying control.
- Vehicle motion and UX restrictions override every preference and animation.
- FFT capture uses the actual Media3 playback session and consumes resources only while ambient visualization is visible.
- Users can disable ambient mode completely or choose its inactivity delay.

## Non-Goals

- Showing ambient mode outside the Now Playing destination.
- Showing ambient mode while driving, when parked state is unknown, or when UX restriction state is unavailable.
- Making ambient mode a navigation destination or a PandaEngine-owned UI route.
- Running fake amplitude generation as a production fallback.
- Replacing Android's `Visualizer` API with a PCM audio processor in this milestone.
- Synchronizing preferences with Canopy before profile synchronization is implemented.

## Product Requirements

### Eligibility And Exit Policy

- `PR-AMB-01`: Ambient mode shall be eligible only while Now Playing is visible and the application lifecycle is resumed.
- `PR-AMB-02`: Ambient mode shall require a vehicle driving state explicitly equal to parked.
- `PR-AMB-03`: Ambient mode shall require a known AAOS UX restriction state that does not require distraction optimization and has no active restrictions.
- `PR-AMB-04`: Unknown, unavailable, contradictory, or non-parked safety state shall make ambient mode ineligible.
- `PR-AMB-05`: Ambient mode shall require the persisted ambient-mode preference to be enabled.
- `PR-AMB-06`: Ambient mode shall require playback state to be actively playing. Paused, idle, buffering, ended, suppressed, and error states shall be ineligible.
- `PR-AMB-07`: Ambient mode shall require uninterrupted PandaWave inactivity for the configured timeout.
- `PR-AMB-08`: Opening Now Playing or returning PandaWave to the foreground shall reset the inactivity timer.
- `PR-AMB-09`: Touch, rotary, DPAD, keyboard, and accessibility-driven PandaWave interactions shall exit ambient mode and restart the full inactivity timer.
- `PR-AMB-10`: The interaction that exits ambient mode shall not pass through to an underlying navigation or playback control.
- `PR-AMB-11`: Loss of parked state or UX permission shall stop visualization and restore interactive Now Playing immediately without waiting for a transition animation.
- `PR-AMB-12`: Loss of playback, route visibility, lifecycle visibility, or the enabled preference shall stop visualization and restore or remove ambient presentation as appropriate.
- `PR-AMB-13`: An automatic track transition shall remain in ambient mode and update artwork and metadata. A manually initiated skip shall first exit ambient mode and reset inactivity.

### Preferences

- `PR-AMB-14`: Ambient mode shall be enabled by default for new installs and guest users.
- `PR-AMB-15`: The default inactivity timeout shall be 15 seconds.
- `PR-AMB-16`: Profile Preferences shall provide a timeout slider from 5 through 60 seconds in 5-second steps.
- `PR-AMB-17`: Profile Preferences shall provide a toggle that completely disables ambient mode.
- `PR-AMB-18`: Ambient preferences shall load from DataStore first and survive process restart.
- `PR-AMB-19`: A future authenticated profile refresh may replace the locally cached values and persist the replacement back to DataStore.

### Audio Session And Visualization

- `PR-AMB-20`: FFT capture shall attach to the audio-session ID of the ExoPlayer instance owned by `BambooMediaLibraryService`.
- `PR-AMB-21`: The Now Playing feature shall not create or own a second ExoPlayer instance.
- `PR-AMB-22`: Audio-session changes shall stop and release the previous Android `Visualizer` before attaching the new positive session ID.
- `PR-AMB-23`: FFT capture shall start only while ambient visualization is visible and eligible, stop whenever it exits, and release when the owning ViewModel is cleared.
- `PR-AMB-24`: FFT frames shall be captured at a bounded target rate near 30 frames per second and delivered through a conflated state stream.
- `PR-AMB-25`: Production shall never substitute generated amplitudes for unavailable real FFT data. Fake visualization is limited to tests, previews, and explicit debug tooling.
- `PR-AMB-26`: Missing permission, unsupported visualization, invalid sessions, and initialization or runtime failures shall retain static ambient artwork and metadata without animated bars.

### Permission Flow

- `PR-AMB-27`: PandaWave shall request `RECORD_AUDIO` only after an explicit user action on a contextual music-visualization prompt.
- `PR-AMB-28`: The contextual prompt shall appear only on interactive Now Playing while the vehicle is explicitly parked and UX restrictions permit it.
- `PR-AMB-29`: PandaWave shall not display the system permission dialog automatically, during ambient entry, or while driving.
- `PR-AMB-30`: Permission denial shall not trigger repeated prompts. Profile Preferences shall provide a user-initiated retry path.

## UI/UX Requirements

- `UX-AMB-01`: Ambient mode shall occupy the full PandaWave app surface and hide the navigation rail, mini-player, and playback controls.
- `UX-AMB-02`: Ambient mode shall show dominant square artwork, title, artist, and the ambient visualizer when FFT data is available.
- `UX-AMB-03`: Missing artwork shall use the approved PandaWave branded placeholder.
- `UX-AMB-04`: Missing visualization capability shall omit animated bars without replacing them with fabricated production motion.
- `UX-AMB-05`: Normal ambient entry shall use an approximately 350 ms crossfade with a subtle artwork scale-in.
- `UX-AMB-06`: User-triggered exit shall use an approximately 200 ms crossfade while preventing input pass-through.
- `UX-AMB-07`: Safety-triggered exit shall bypass presentation animation.
- `UX-AMB-08`: Automatic track changes in ambient mode shall crossfade artwork and metadata without restarting inactivity.
- `UX-AMB-09`: Transitions shall honor Android animator-duration and reduced-motion behavior.
- `UX-AMB-10`: Interactive Now Playing shall preserve and restore the last meaningful focus target after ambient exit, falling back to play/pause.

## Visualizer Rendering Requirements

- `VIS-AMB-01`: `BambooAmbientVisualizer` shall calculate its bar count from available width, bar width, and gap tokens.
- `VIS-AMB-02`: The renderer shall safely handle empty amplitudes and layouts that resolve to one bar.
- `VIS-AMB-03`: Amplitudes shall be resampled to the calculated bar count and clamped to `0f..1f`.
- `VIS-AMB-04`: Bars shall be centered, vertically balanced, and drawn with rounded ends.
- `VIS-AMB-05`: Quiet bars shall use the configured idle color, with increasing strength blending toward the active bamboo green.
- `VIS-AMB-06`: Android packed FFT data shall be decoded correctly, including the DC and Nyquist bins, before magnitude calculation.
- `VIS-AMB-07`: FFT processing shall apply logarithmic normalization, noise-floor suppression, and fast-attack/slower-decay smoothing.
- `VIS-AMB-08`: Malformed, empty, and partial FFT input shall produce a safe empty or bounded frame without NaN or infinite values.

## Accessibility And Automotive Safety

- The entire ambient surface shall expose one clear accessibility action to show playback controls.
- Visualizer bars shall be decorative and excluded from accessibility traversal.
- The first wake interaction shall restore interactive content and focus without activating a hidden control.
- Focus shall return to the previously focused interactive element when possible and otherwise to play/pause.
- Parked state and UX restriction state are independent mandatory gates; unrestricted UX state shall never be treated as proof that the vehicle is parked.
- Safety state shall fail closed. Platform unavailability shall never select ambient mode.

## Architecture And State Ownership

- Android platform observers shall normalize `CarDrivingStateManager` and `CarUxRestrictionsManager` updates and send them through PandaEngine platform-state contracts.
- PandaEngine and the Bamboo playback projection shall expose normalized playback and vehicle-safety state.
- PandaEngine shall not own route visibility, user inactivity, permission prompts, Compose transitions, or visualizer rendering.
- A pure Kotlin `AmbientModeReducer` shall combine engine-backed safety/playback state with route lifecycle, DataStore preferences, user interaction, permission state, audio-session state, and timer events.
- The reducer shall expose `Hidden`, `Interactive`, `WaitingForInactivity`, `AmbientStatic`, and `AmbientVisualizing` states plus explicit timer and visualizer effects.
- Inactivity deadlines shall use monotonic elapsed time rather than wall-clock time.
- `BambooMediaLibraryService` shall attach `ExoPlayerAudioSessionObserver` to its real ExoPlayer and publish session changes through an app-scoped repository contract.
- A dedicated `core:audio-visualizer` module shall own audio-session and visualization contracts plus the Android `Visualizer` implementation.
- `core:ui` shall own only `BambooAmbientVisualizer` and FFT-independent rendering utilities.
- Now Playing shall report ambient visibility to the app shell so shell chrome can be hidden without introducing a navigation destination.

## Telemetry And Privacy

- Ambient events shall use a dedicated `PandaWave:Ambient` telemetry module.
- Telemetry may record ambient entry/exit reason, safety-gate category, permission category, and visualizer failure category/type.
- Telemetry shall not record audio-session IDs, raw FFT data, media IDs, titles, artist names, artwork URIs, or permission-dialog content.
- Repeated frame, timer-tick, and Compose recomposition events shall not be logged.

## Adaptive And OEM Requirements

- Ambient layout shall use the actual Compose content bounds after system insets.
- The preferred reference display remains the PandaEmulator at `1408x792` and `160 dpi`.
- Artwork and visualizer shall remain visible without scrolling on the reference display.
- Artwork bounds, visualizer height, bar width, gap, radius, minimum/maximum bar height, spacing, colors, and transition durations shall use centralized BambooUI tokens.
- Appropriate dimensions, colors, and durations shall be public RRO resources for OEM tuning.
- Runtime layout shall remain responsive; RROs tune supported layouts rather than replace Compose implementations.

## Verification Requirements

- Unit tests shall cover the eligibility matrix with each mandatory gate independently false.
- Unit tests shall prove unknown driving and UX states fail closed.
- Unit tests shall cover every supported interaction source, foreground/route reset, timer scheduling, and stale timer cancellation.
- Unit tests shall distinguish immediate safety exit from animated normal transition.
- Unit tests shall distinguish automatic track transition from manual skip interaction.
- Unit tests shall verify audio-session reattachment and exact attach/start/stop/release calls.
- Unit tests shall verify permission denial, initialization failure, and static fallback.
- FFT tests shall cover packed-byte decoding, normalization, smoothing, malformed input, empty input, and one-bar resampling.
- Compose tests shall cover responsive bar count, artwork fallback, rail removal, input interception, and focus restoration.
- DataStore tests shall cover defaults, toggle persistence, timeout bounds, and process recreation.
- Emulator QA shall cover permission granted/denied, parked/moving transitions, UX restriction changes, rotary/DPAD/touch exit, and automatic track changes.
- Performance validation shall confirm that FFT capture stops outside visible ambient mode and does not introduce sustained frame instability on the reference emulator.

## Acceptance Criteria

- [ ] Ambient mode never appears unless parked and explicitly UX-unrestricted.
- [ ] Unknown or lost safety state exits or blocks ambient mode immediately.
- [ ] Ambient mode enters after 15 seconds by default while all gates remain true.
- [ ] The timeout can be set from 5 through 60 seconds and survives restart.
- [ ] Disabling the preference prevents ambient entry.
- [ ] Every supported interaction wakes interactive Now Playing, resets the timer, and does not pass through.
- [ ] The rail and playback controls are absent while ambient is visible.
- [ ] Ambient uses the service-owned player's audio session and no second ExoPlayer exists.
- [ ] FFT starts only in eligible visible ambient mode and stops on every exit path.
- [ ] Permission denial or visualization failure produces static ambient artwork without fake production amplitudes.
- [ ] Normal transitions are smooth, safety exits are immediate, and reduced-motion settings are honored.
- [ ] Automatic track changes remain ambient and crossfade metadata; manual skips exit ambient.
- [ ] Visualizer math produces bounded finite values and adapts bar count to available width.
- [ ] Emulator and performance checks pass on the reference PandaEmulator configuration.

## Dependencies

- Android Automotive `CarDrivingStateManager` and `CarUxRestrictionsManager` integration.
- PandaEngine platform-event and snapshot contracts for normalized driving and UX state.
- Media3 ExoPlayer ownership in `BambooMediaLibraryService`.
- Android `Visualizer` and runtime `RECORD_AUDIO` permission handling.
- Existing Bamboo playback state, DataStore preference foundation, app shell, Profile Preferences, and BambooUI/RRO token system.
