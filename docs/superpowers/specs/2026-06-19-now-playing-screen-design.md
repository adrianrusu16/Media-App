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

The UI may communicate user-relevant playback availability, safety restrictions, and disabled controls, but it must do so in product language:

- "Controls unavailable"
- "Limited while driving"
- "Playback paused"
- "Ready to play"

It must not expose internal engine status cards.

## Visual Direction

The screen follows the Stitch Forest Tech language:

- Deep forest surfaces with bamboo green primary actions.
- Panda white text and charcoal surfaces for glanceable contrast.
- Large central artwork as the first visual anchor.
- Pebble-like controls with generous touch targets.
- Calm progress scrubber with stable timestamp labels.
- Minimal information density while driving.

The Compose implementation should adapt the spirit of glass panels and glow without relying on unsupported web-only effects like backdrop blur. Use Material surfaces, tokenized colors, shape, elevation, and subtle borders where the current design system supports them.

## Layout

The full Now Playing screen is arranged as a playback cockpit:

1. Artwork and metadata

   A large artwork panel appears at the top or center of the content area. Until real artwork URIs are available, it uses the PandaWave logo or a tokenized placeholder surface. Title and detail text are placed close to the artwork and constrained to avoid overflow.

2. Progress

   The progress row shows elapsed time, a thick progress track, and duration. It uses stable dimensions so ticking progress does not shift layout. Unknown duration displays `--:--`.

3. Primary controls

   Playback controls are large, icon-first, and AAOS-friendly. The play/pause button is the primary action and uses the theme primary color. Previous and next controls sit beside it. Refresh remains available only if it reads as a user action, not as an engine debug command.

4. Optional quick actions

   Quick actions from Stitch, such as library, queue, favorite, shuffle, voice, or nature mode, are not part of this milestone unless already backed by current app state. Avoid fake controls that look functional but cannot dispatch meaningful intents.

5. Restriction messaging

   If driving restrictions disable controls, show a concise product-facing message near the controls or metadata. Do not add a separate internal status section.

## Component Architecture

Add reusable BambooUI pieces only where they remove real duplication:

- A full-size playback progress component that can be shared conceptually with the mini-player.
- A playback controls component for previous, play/pause, next, and optional refresh.
- An artwork placeholder component using the PandaWave logo and tokenized sizing.

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
- Disabled controls
- Driving restriction active
- Long title and subtitle text
- Empty or placeholder artwork

Disabled states must still be understandable to accessibility services through content descriptions and enabled semantics.

## Accessibility

Controls must have content descriptions that describe the action, not the icon.

Touch targets should use the existing design-system sizing tokens and remain suitable for AAOS. Text should use Material typography and avoid viewport-scaled font sizes. Important title text should not be clipped beyond its planned line count.

The screen should avoid decorative text that explains how to use the UI. It should present controls and state directly.

## Testing

Add or update focused unit tests for pure behavior:

- Time label formatting.
- Progress projection behavior if shared or moved.
- Control state projection if new projector logic is introduced.

Avoid brittle screenshot tests for this milestone. Compose screenshot or emulator validation can be added later when the visual system stabilizes further.

## Out Of Scope

- Engine contract changes.
- Real artwork URI loading from PandaEngine.
- Backend or Canopy integration.
- Volume, voice assistant, favorite, shuffle, queue, and nature controls unless already backed by current state.
- Secondary status cards that reveal internal engine state.
- A separate Figma/Stitch import pipeline.

## Self Review

The design has no placeholder requirements. Scope is limited to the Now Playing UI and reusable UI components. Product language is separated from engine internals. The implementation path is compatible with current state and can be tested without changing the engine.
