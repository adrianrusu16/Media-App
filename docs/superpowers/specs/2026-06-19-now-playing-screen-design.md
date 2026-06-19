# Now Playing Screen Design

Date: 2026-06-19

## Goal

Create a production-grade PandaWave Now Playing screen in Compose using the existing `NowPlayingState`. This milestone is UI-system work only. Engine and data contract changes are intentionally deferred.

The screen should feel like the Stitch "Forest Tech" reference while staying compatible with the current Android design-system tokens, AAOS constraints, and BambooUI component architecture.

Reference artifact:

- `E:\AndroidStudioProjects\media_app\stitch_pandawave_media_player.zip`
- `stitch_pandawave_media_player/now_playing_pandawave/code.html`
- `stitch_pandawave_media_player/forest_tech/DESIGN.md`

## Product Principles

The user should experience PandaWave as a calm automotive media player. They should not see implementation details such as "engine", "connection state", reducers, bridges, or native readiness.

The UI may communicate user-relevant playback availability and safety context, but it must do so in product language:

- "Controls unavailable"
- "Playback paused"
- "Ready to play"

It must not expose internal engine status cards.

Drive mode must not disable media controls in PandaWave. The app should be distraction-optimized and safe to operate while moving, but play, pause, previous, next, quick actions, and volume controls should remain available from the Now Playing surface unless a real playback capability is unavailable.

## Visual Direction

The screen follows the Stitch Forest Tech language:

- Deep forest surfaces with bamboo green primary actions.
- Panda white text and charcoal surfaces for glanceable contrast.
- Large central artwork as the first visual anchor.
- Pebble-like controls with generous touch targets, especially the primary green play/pause control from Stitch.
- Calm progress scrubber with stable timestamp labels and a visible leaf/thumb marker.
- Quick actions and volume controls preserved from the Stitch Now Playing design where the current app can support them.
- Minimal information density while driving.

The Compose implementation should adapt the spirit of glass panels and glow without relying on unsupported web-only effects like backdrop blur. Use Material surfaces, tokenized colors, shape, elevation, and subtle borders where the current design system supports them.

## Layout

The full Now Playing screen is arranged as a playback cockpit:

1. Artwork and metadata

   A large artwork panel appears at the top or center of the content area. Until real artwork URIs are available, it uses the PandaWave logo or a tokenized placeholder surface. Title and detail text are placed close to the artwork and constrained to avoid overflow.

2. Progress

   The progress row should closely match Stitch: elapsed time, thick rounded progress track, leaf/thumb marker, and duration. It uses stable dimensions so ticking progress does not shift layout. Unknown duration displays `--:--`. The leaf marker is decorative and should not imply seeking until seek input is wired.

3. Primary controls

   Playback controls are large, icon-first, and AAOS-friendly. The play/pause button is the visual hero: a large circular primary green pebble with a soft glow and the current play or pause icon centered inside. Previous and next controls sit beside it in smaller glass/pebble buttons. Refresh is not shown in the primary cluster because it reads as an implementation/debug action in this design.

4. Quick actions

   Keep the Stitch quick-action row. Actions that already map cleanly to app navigation or current behavior should be enabled. Actions without backing state can appear disabled only if they are visually important to the design, but they must not pretend to complete a feature. Initial candidates:

   - Library: navigate to Library.
   - Settings: navigate to Settings if placed in the app sidebar instead of a top status area.
   - Profile: navigate to Profile if placed in the app sidebar instead of a top status area.
   - Shuffle, favorite, queue, nature, and voice: visual placeholders only if they stay clearly disabled or are omitted from the first implementation.

5. Volume control

   Add a Stitch-inspired volume control to the footer area: rounded glass/pebble container, volume-down and volume-up icons, thick track, and thumb. Because no real volume state is present yet, it can use local UI state for this milestone and should not claim to persist or control system audio until a real audio/vehicle volume integration is added.

6. Settings and profile placement

   Do not fake system status icons such as Wi-Fi, Bluetooth, or battery in app content. If the current app shell does not own a real top status bar, place Settings and Profile as persistent sidebar destinations/actions. This fits the current `NavigationRail` architecture and avoids duplicating vehicle/system chrome.

7. Restriction messaging

   Driving restrictions should not disable playback controls. If a restriction message is still useful, keep it informational and product-facing, but do not add a separate internal status section.

## Component Architecture

Add reusable BambooUI pieces only where they remove real duplication:

- A full-size playback progress component that can be shared conceptually with the mini-player.
- A playback controls component for previous, play/pause, and next.
- An artwork placeholder component using the PandaWave logo and tokenized sizing.
- A quick-action component for large icon-first footer actions.
- A volume control component with local state until a real volume source exists.

Keep feature-specific screen composition in `feature/nowplaying`. Shared generic playback UI belongs in `core/ui` only when it is not tied to Now Playing feature copy or domain state.

The current `BambooMiniPlayer` should remain stable. Any shared helper should avoid changing mini-player behavior unless tests prove parity.

## Data Flow

The screen continues to use:

- `NowPlayingRoute`
- `NowPlayingViewModel`
- `NowPlayingState`
- `NowPlayingIntent`

No PandaEngine, AIDL, JNI, repository, or state-machine contract changes are included in this milestone.

The screen may derive local UI labels from the current state, but any reusable formatting logic should be pure and testable.

## States

The implementation must handle:

- Playing
- Paused
- Loading or unavailable metadata
- Unknown duration
- Disabled controls only when playback capability is genuinely unavailable
- Driving restriction active without disabling media controls
- Long title and subtitle text
- Empty or placeholder artwork
- Local-only volume state

Disabled states must still be understandable to accessibility services through content descriptions and enabled semantics.

## Accessibility

Controls must have content descriptions that describe the action, not the icon.

Touch targets should use the existing design-system sizing tokens and remain suitable for AAOS. Text should use Material typography and avoid viewport-scaled font sizes. Important title text should not be clipped beyond its planned line count.

The screen should avoid decorative text that explains how to use the UI. It should present controls and state directly.

## Testing

Add or update focused unit tests for pure behavior:

- Time label formatting.
- Progress projection behavior if shared or moved.
- Control state projection if new projector logic is introduced, including the rule that drive mode alone does not disable media controls.

Avoid brittle screenshot tests for this milestone. Compose screenshot or emulator validation can be added later when the visual system stabilizes further.

## Out Of Scope

- Engine contract changes.
- Real artwork URI loading from PandaEngine.
- Backend or Canopy integration.
- Real system or vehicle volume integration.
- Real voice assistant, favorite, shuffle, queue, and nature behavior unless already backed by current state.
- Fake system status icons inside app content.
- Secondary status cards that reveal internal engine state.
- A separate Figma/Stitch import pipeline.

## Self Review

The design has no placeholder requirements. Scope is limited to the Now Playing UI and reusable UI components. Product language is separated from engine internals. The implementation path is compatible with current state and can be tested without changing the engine.
