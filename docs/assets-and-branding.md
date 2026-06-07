# Assets and Branding

PandaWave should use assets that are easy to audit, safe to ship, and simple to
replace. Prefer sources with explicit Apache-2.0, MIT, ISC, or CC0 licensing.

## Recommended Sources

| Source | Best fit | License posture | Notes |
| --- | --- | --- | --- |
| [Material Symbols](https://developers.google.com/fonts/docs/material_symbols) | Core app and media controls | Apache-2.0 | Best default for Android and AAOS controls. |
| [Lucide](https://lucide.dev/) | Thin-line utility icons | ISC | Good secondary style if Material Symbols feels too platform-generic. |
| [Tabler Icons](https://github.com/tabler/tabler-icons) | Broad SVG icon coverage | MIT | Useful for admin/profile/settings concepts. |
| [Heroicons](https://heroicons.com/) | Simple interface icons | MIT | Smaller set, clean shapes. |
| [LottieFiles](https://help.lottiefiles.com/animation-licensing-basics-) | Lightweight JSON animation | Asset-specific | Verify each animation license before adding it. |
| [Rive Marketplace](https://rive.app/docs/community/marketplace-overview) | Interactive animation prototypes | CC BY for marketplace files | Good for branded motion later, but attribution must be tracked. |
| [unDraw](https://undraw.co/license) | Simple editorial illustrations | Custom free license | Useful for placeholders, but avoid making unDraw art central to the product. |

## Intake Rules

- Store third-party assets under `app/src/main/assets` or a feature-specific
  assets folder only after recording source, author, license, and download date.
- Keep a `NOTICE` or asset attribution file once the first third-party asset is
  committed.
- Avoid assets with unclear "free for use" language, no license page, or license
  terms that prohibit app embedding, redistribution, or commercial use.
- Prefer vector drawables for static Android UI, Lottie for lightweight ambient
  animation, and Rive only when state-machine animation is worth the runtime.
- Keep PandaWave brand assets original or commissioned, especially launcher icon,
  hero artwork, and any recurring mascot or identity element.
