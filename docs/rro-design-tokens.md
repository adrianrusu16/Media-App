# RRO Design Tokens

PandaWave exposes OEM-customizable design tokens through Android resources in
`:core:designsystem`.

## App Themes

The app owns its default brand themes in `:core:designsystem`:

| Theme | Resource qualifier | Palette direction |
| --- | --- | --- |
| Bamboo Grove Light | `values` | Panda ivory surfaces, bamboo greens, bark accents |
| Moonlit Bamboo Dark | `values-night` | Panda charcoal surfaces, moonlit bamboo greens, moss accents |

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
then can explicitly select `BambooGroveLight` or `MoonlitBambooDark`. The app
observes that preference through a repository/use-case boundary before passing
it into `PandaWaveTheme`.

The current repository is in-memory; future storage or backend profile sync
should replace that repository without changing the design-system token API.

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
| Brand colors | `color` | Compose color scheme, media controls, high-emphasis states |
| Surface colors | `color` | App background, mini-player, list surfaces |
| Spacing | `dimen` | Shared layout rhythm across screens |
| Shape | `dimen` | Small and medium corners, mini-player height |
| Sizing | `dimen` | Touch targets and fixed interactive affordances |
| Elevation | `dimen` | Shared surface elevation levels |
| Restrictions | `integer` | UX-restricted browse density and action limits |

Compose reads these resources through `ResourceDesignTokenProvider`, so runtime
resource overlays can affect the app without code changes.
