# PW-PRD-001: Adaptive App Shell And Driving UX

**Status:** Approved
**Owners:** Product, Design, Engineering
**Last updated:** 2026-06-22
**Related:** `docs/rro-design-tokens.md`, `docs/android-platform-integration.md`

## Problem

PandaWave currently spends limited automotive display space on an app header and a crowded navigation rail. The Now Playing layout exceeds the usable height of the reference AAOS display, and a blanket restriction state can disable interactions that remain appropriate while driving.

## Outcomes

- Media content and playback controls use the available app surface without unnecessary shell chrome.
- Primary destinations remain easy to reach on short, wide automotive displays.
- Now Playing is reachable through media-centric entry points instead of a permanent rail item.
- AAOS restrictions constrain only distracting capabilities.
- OEMs can tune layout dimensions and branded assets through Runtime Resource Overlays.

## Non-Goals

- Pixel-perfect reproduction of the Google Stitch export at every display size.
- A complete account, authentication, or backend-synchronized profile implementation.
- Video playback or free-form keyboard experiences while driving.
- Replacing AAOS platform enforcement with application-defined safety rules.

## Product Requirements

### Navigation

- `PR-NAV-01`: The app shell shall not display the PandaWave name, current page name, or a generic restriction-status header above destination content.
- `PR-NAV-02`: The primary rail destinations shall be Home, Library, Search, and Profile.
- `PR-NAV-03`: Now Playing shall not appear as a rail destination.
- `PR-NAV-04`: Activating the PandaWave rail logo shall open Now Playing.
- `PR-NAV-05`: Activating the mini-player surface outside its playback buttons shall open Now Playing.
- `PR-NAV-06`: Mini-player transport buttons shall perform only their stated playback actions and shall not navigate.
- `PR-NAV-07`: The mini-player shall not be shown while Now Playing is open.
- `PR-NAV-08`: Back navigation from Now Playing shall return to the previously selected primary destination.
- `PR-NAV-09`: Profile shall be the top-level destination for user preferences. Settings shall not be a separate rail destination and may be presented within Profile or as a nested Profile screen.
- `PR-NAV-10`: Back navigation from Profile Preferences shall return to Profile, then Home.
- `PR-NAV-11`: Back navigation from Library, Search, or Profile shall return directly to Home without restoring another primary destination.
- `PR-NAV-12`: Back navigation from Home shall move the PandaWave task to the background without finishing its activity or stopping playback.
- `PR-NAV-13`: Typed destinations and one saveable back stack shall be the source of truth for UI navigation. Playback repositories and PandaEngine shall not own UI navigation history.

### Driving Restrictions

- `PR-UXR-01`: The app shall not display a generic driving-mode or restriction-status badge.
- `PR-UXR-02`: Driving restrictions shall not disable play, pause, previous, next, seek, volume, or other permitted media controls.
- `PR-UXR-03`: Platform limits shall apply to the capabilities they describe, including content depth, cumulative content items, restricted string length, keyboard input, and video.
- `PR-UXR-04`: Restriction decisions shall use explicit capabilities and limits rather than a single blanket `isRestricted` UI gate.
- `PR-UXR-05`: Android shall observe AAOS restriction events and deliver their normalized state to PandaEngine. UI policy shall project the engine-backed state into capability-specific presentation rules.
- `PR-UXR-06`: If restriction state is temporarily unavailable, distracting capabilities shall use a conservative fallback while permitted media transport remains available when the engine is ready.

## UI/UX Requirements

### App Shell

- `UX-SHELL-01`: The rail logo shall communicate that it opens Now Playing through accessibility semantics and shall not change visually when Now Playing is active.
- `UX-SHELL-02`: Home, Library, and Search shall remain labeled primary actions in the rail.
- `UX-SHELL-03`: Profile shall remain directly reachable without exposing a separate Settings rail item.
- `UX-SHELL-04`: A nested Profile settings screen shall keep Profile visually selected.
- `UX-SHELL-05`: Destination content shall own its page-level title only when that title adds useful context; the shell shall not repeat it.
- `UX-SHELL-06`: The selected primary destination shall use the active theme color for its icon and label and shall show one inset vertical indicator with rounded ends on the left of the item.
- `UX-SHELL-07`: Now Playing shall not select a rail destination or add a selection indicator to the logo.

### Adaptive Layout

- `UX-ADAPT-01`: Layout decisions shall use the actual Compose content bounds after system insets, not physical display dimensions alone.
- `UX-ADAPT-02`: The preferred reference display is the PandaEmulator at `1408x792` and `160 dpi`, with approximately `620dp` of height remaining between its current top and bottom car system bars.
- `UX-ADAPT-03`: Now Playing shall fit without vertical scrolling within the reference app surface.
- `UX-ADAPT-04`: Smaller or unusually shaped windows may use a scroll fallback when preserving touch-target and readability requirements makes a single-screen layout impossible.
- `UX-ADAPT-05`: The implementation shall preserve the Forest Tech/Stitch visual direction without treating its large fixed canvas dimensions as production layout constants.
- `UX-ADAPT-06`: Artwork, transport controls, progress, quick actions, and volume shall use stable responsive constraints so state changes do not shift the layout.

### Now Playing Branding

- `UX-NP-01`: The primary play action shall use the approved green circular button and panda-paw silhouette from the Stitch reference.
- `UX-NP-02`: The pause state shall use a conventional two-bar pause symbol.
- `UX-NP-03`: The progress control shall retain the leaf thumb and Forest Tech treatment while remaining readable at the reference size.

## Accessibility And Automotive Safety

- Interactive elements shall preserve an OEM-overridable minimum touch target of at least `48dp`; the reference automotive theme may use larger targets where space permits.
- Logo, mini-player surface, transport controls, and nested Profile navigation shall expose distinct roles, labels, and actions.
- Nested clickable controls shall not trigger the mini-player navigation action.
- Focus order shall follow rail navigation, destination content, mini-player content, and mini-player controls in a predictable sequence.
- Restricted keyboard or video experiences shall provide a clear non-blocking alternative such as browse suggestions, voice input when available, audio-only playback, or static artwork.

## Adaptive And OEM Requirements

- Layout dimensions shall be centralized as Android resources consumed by the PandaWave design-token layer.
- Rail width, rail item spacing, content padding, artwork bounds, playback-button size, transport spacing, progress dimensions, and footer height shall be eligible for public RRO overrides where OEM tuning is appropriate.
- The panda-paw drawable shall remain a public, overlayable branded resource.
- Runtime layout selection shall use content constraints; RROs shall tune supported modes rather than define separate OEM-only Compose implementations.

## Acceptance Criteria

- [ ] No PandaWave/page/restriction header is visible on any primary destination.
- [ ] The rail shows Home, Library, Search, and Profile without clipping at the reference display size.
- [ ] The rail has no Now Playing or Settings item.
- [ ] Each selected primary rail destination uses the active color and a vertical indicator; Now Playing leaves the logo and rail visually unchanged.
- [ ] Logo and mini-player surface navigation open Now Playing, while each transport button performs only playback.
- [ ] Back from Now Playing returns to the previous primary destination.
- [ ] Back from Profile Preferences returns to Profile, then Home.
- [ ] Back from Library, Search, or Profile returns directly to Home.
- [ ] Back from Home backgrounds the PandaWave task without finishing its activity or stopping playback.
- [ ] Repeated rail selection does not create duplicate navigation entries.
- [ ] The typed navigation stack survives Android saved-state restoration.
- [ ] Now Playing requires no vertical scrolling on the reference PandaEmulator surface.
- [ ] The play button silhouette visually matches the approved reference and the pause state uses two bars.
- [ ] Media controls remain enabled under a simulated driving restriction when the engine is ready.
- [ ] Browse depth/item limits, keyboard restrictions, and video restrictions can be tested independently.
- [ ] Emulator screenshots confirm that system bars, rail, content, and mini-player do not overlap.
- [ ] Public RRO resources cover the approved OEM-tunable dimensions and paw drawable.

## Dependencies

- PandaEngine platform-event and snapshot contracts for normalized AAOS restriction state.
- Existing Compose app shell, mini-player, Now Playing, Profile, Settings, and design-system modules.
- AAOS `CarUxRestrictionsManager` integration in `core:automotive`.
