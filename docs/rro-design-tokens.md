# RRO Design Tokens

PandaWave exposes OEM-customizable design tokens through Android resources in
`:core:designsystem`.

## App Themes

The app owns its default brand themes in `:core:designsystem`:

| Theme | Resource qualifier | Palette direction |
| --- | --- | --- |
| Bamboo Grove Light | Named resources in `values` | Panda ivory surfaces, bamboo greens, bark accents |
| Moonlit Bamboo Dark | Named resources in `values` | Panda charcoal surfaces, moonlit bamboo greens, moss accents |
| Forest Tech Light | Named resources in `values` | Daytime Forest Tech surfaces, bamboo green emphasis, cockpit-neutral grays |
| Forest Tech Dark | Named resources in `values` | Stitch Forest Tech deep charcoal, bright bamboo green, panda-white text |

Every public token uses its final `pandawave_*` name. No legacy resource aliases
remain. Compose loads a specific named palette from the active
`PandaWaveThemeId`, while `values` and `values-night` aliases supply Android
platform themes with the default light/dark pair.

These named themes are the app's canonical experience. Future user-selectable
themes should map settings or profile preferences to app theme ids, then sync
those preferences through the backend when account state exists.

RROs are still useful, but they are a platform/OEM customization layer over the
public token contract rather than the primary user preference system.

## Sample Overlay

`:rro:bamboo-grove-overlay` is a buildable sample overlay APK that targets
`com.adrianrusu.mediaapp` and `PandaWaveDesignTokens`. It mirrors the app-owned
Bamboo Grove palette with a slightly deeper forest treatment, and exists to
prove the overlay contract for AAOS/OEM integration.

The overlay should not be treated as a runtime theme picker. Runtime themes
belong in app state; RRO packages belong in platform or build-variant
customization.

## Runtime Preference Boundary

Runtime theme selection starts with `PandaWaveThemePreference.SystemDefault`,
then can explicitly select `BambooGroveLight`, `MoonlitBambooDark`,
`ForestTechLight`, or `ForestTechDark`. The app observes that preference
through a repository/use-case boundary before passing it into `PandaWaveTheme`.

The app resolves its device-wide cached theme from DataStore first, then hydrates
PandaEngine through a versioned command. An accepted authenticated profile theme
flows back through the same coordinator and is written to DataStore without
changing the design-system token API. Engine or backend availability never blocks
the local preference from becoming usable.

## Overlayable Target

RRO packages should target the app package and the overlayable group:

```xml
<overlay
    android:targetPackage="com.adrianrusu.mediaapp"
    android:targetName="PandaWaveDesignTokens"
    android:isStatic="true" />
```

The token resources are also declared in `public.xml` so OEM overlays can bind
to stable resource names.

## Token Groups

| Group | Resource types | Usage |
| --- | --- | --- |
| Theme and semantic colors | `color` | Four named palettes, Compose color scheme, surfaces, and emphasis roles |
| Component state colors | `color` selectors | Enabled, disabled, focused, pressed, selected, and checked states |
| Spacing | `dimen` | Shared layout rhythm across screens |
| Shape and geometry | `dimen` | Corners, rail, media, preference, mini-player, and playback bounds |
| Typography | `dimen`, `integer` | Semantic text sizes, line heights, and weights |
| Motion and opacity | `integer`, `fraction` | Durations, easing selection, and interaction alpha |
| Sizing | `dimen` | Touch targets, icons, focus treatment, and adaptive thresholds |
| Elevation | `dimen` | Shared surface elevation levels |
| Restrictions | `integer` | UX-restricted browse density and action limits |
| Brand startup | `color`, `drawable`, `integer` | Adaptive icon layers, splash mark, splash surface, exit timing |

Compose reads these resources through `ResourceDesignTokenProvider`, so runtime
resource overlays can affect the app without code changes.

User-facing strings remain internal to `:core:ui` or their owning feature
module. They are intentionally absent from `public.xml` and `overlayable.xml`.

`PandaWaveResourceContractTest` verifies that the public, overlayable, base, and
reference-overlay resource sets agree. `verifyPandaWaveUiContract` runs from
`qualityCheck` and rejects legacy resource names and production UI literals.
