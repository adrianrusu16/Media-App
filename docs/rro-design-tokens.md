# RRO Design Tokens

Media App exposes OEM-customizable design tokens through Android resources in
`:core:designsystem`.

## Overlayable Target

RRO packages should target the app package and the overlayable group:

```xml
<overlay
    android:targetPackage="com.adrianrusu.mediaapp"
    android:targetName="MediaAppDesignTokens"
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
| Restrictions | `integer` | UX-restricted browse density and action limits |

Compose reads these resources through `ResourceDesignTokenProvider`, so runtime
resource overlays can affect the app without code changes.
