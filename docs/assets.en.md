[中文](assets.md) | [English](assets.en.md)

# Asset sources

## Application icon

The icon was generated with the built-in image generation tool: midnight-blue metallic surfaces, raised coral-orange accents and upper-left lighting, with two binding tabs and six date cells. Source: `docs/media/app-icon-source.png`; 512 px release image: `docs/media/app-icon-playstore.png`; app artwork: `drawable-nodpi/ic_launcher_artwork.png`. The monochrome layer is a project-drawn vector. Adaptive masking follows [Android guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).

Original generation prompt:

Use case: logo-brand. Generate a new production Android launcher icon for CalendarMods, Square 1024x1024 full-bleed icon artwork. Match the exact deep midnight navy background, dark blue-gray beveled metallic/glass material, subtle rim highlights, soft dimensional shadows, and vibrant coral-orange raised accent of the reference. Subject: a large front-facing gently rounded square calendar page, with two short metallic binding tabs at the top, a raised coral-orange header occupying the top quarter, and a clean restrained 3-by-2 arrangement of rounded square date cells on the dark slate page; one date cell coral-orange to reinforce the focus. No numerals, no text, no letters, no clock hands, no extra symbols. Maintain a bold simple recognizable silhouette at launcher sizes. Center all identifying calendar shape inside the central 64% of the canvas width and height with plenty of continuous dark background beyond it so Android adaptive circle/squircle masking never clips the calendar. Lighting from upper left, polished premium restrained 3D rendering, crisp geometry and carefully balanced proportions. No rounded outer app tile or border: dark background goes to all four square edges. Do not add busy decoration or photorealistic environment. Deliver only the icon.

## Daily page

Canvas draws the complete page with system sans-serif and supported weight fallbacks. No fonts are bundled. Lunar dates, terms, festivals and almanac entries are calculated locally by tyme4j; its license ships in `assets/licenses/tyme4j-LICENSE.txt`. Layout and carousel rules are in [daily page](daily-page.en.md). Suitable/avoid text is folk-calendar content.
