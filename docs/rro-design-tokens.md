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
