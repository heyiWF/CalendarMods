[中文](assets.md) | [English](assets.en.md)

# Asset sources

## Application icon

The icon was generated with the built-in image generation tool: midnight-blue metallic surfaces, raised coral-orange accents and upper-left lighting, with two binding tabs and six date cells. Source: `docs/media/app-icon-source.png`; 512 px release image: `docs/media/app-icon-playstore.png`; app artwork: `drawable-nodpi/ic_launcher_artwork.png`. The monochrome layer is a project-drawn vector. Adaptive masking follows [Android guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).

Original generation prompt:

Use case: logo-brand. Generate a new production Android launcher icon for CalendarMods, Square 1024x1024 full-bleed icon artwork. Match the exact deep midnight navy background, dark blue-gray beveled metallic/glass material, subtle rim highlights, soft dimensional shadows, and vibrant coral-orange raised accent of the reference. Subject: a large front-facing gently rounded square calendar page, with two short metallic binding tabs at the top, a raised coral-orange header occupying the top quarter, and a clean restrained 3-by-2 arrangement of rounded square date cells on the dark slate page; one date cell coral-orange to reinforce the focus. No numerals, no text, no letters, no clock hands, no extra symbols. Maintain a bold simple recognizable silhouette at launcher sizes. Center all identifying calendar shape inside the central 64% of the canvas width and height with plenty of continuous dark background beyond it so Android adaptive circle/squircle masking never clips the calendar. Lighting from upper left, polished premium restrained 3D rendering, crisp geometry and carefully balanced proportions. No rounded outer app tile or border: dark background goes to all four square edges. Do not add busy decoration or photorealistic environment. Deliver only the icon.

## Original illustration and photograph

The following generated assets replace physical-calendar reference photographs. Other paper, bindings, strings, cat line art and typography are native drawing.

### winter_bear.png

Path: `app/src/main/res/drawable-nodpi/winter_bear.png`. Original prompt:

Use case: illustration-story. Asset type: image-only header illustration for an Android calendar app. Create a charming tactile miniature winter scene, a friendly caramel teddy bear wearing a small red Santa hat sitting in an ivory ceramic hot chocolate cup, tiny gingerbread house at right, snowy pine branches and simple red ornaments. Soft handmade felt and clay textures, powder blue backdrop, cream and warm brown, nostalgic cozy desktop calendar print. Landscape 3:2 composition, bear centered and fully visible. Full bleed illustration only; absolutely no text, no numbers, no calendar, no spiral, no border, no watermark. Save the generated image asset for use in the app.

### sunset_memory.png

Path: `app/src/main/res/drawable-nodpi/sunset_memory.png`. Original prompt:

Use case: photorealistic-natural. Asset type: full bleed photograph used in a photo desk calendar app. Candid close portrait of two adult friends, a man in a dark knit beanie and a woman with brown shoulder-length hair, smiling naturally together on a seaside walk at sunset. Golden rim light, muted warm analog film palette, tactile grain, ocean and rocky coastline softly out of focus. Spontaneous memory, relaxed expressions, realistic faces and hands. Square 1:1 framing, subjects composed centrally for crop to 4:3 and portrait, faces in upper central half. Photograph only, no calendar, no typography, no frame, no watermark, no brands.

## Style/store icons and daily words

The requested [Material Symbols Outlined style icon](https://fonts.gstatic.com/render/v1/Material+Symbols+Outlined/24dp/style.kt?var=opsz,wght,FILL,GRAD,ROND@24,400,0,0,50) is Google Apache-2.0 artwork. Compose source is `StyleIcon.kt`, with package/property names adjusted; compat converts retained icons to framework drawing resources. License: `assets/licenses/material-symbols-Apache-2.0.txt`.

There are 366 original sayings, at most 14 characters including punctuation, and 366 two-character phrases. They are unsigned everyday editorial copy, distinct from calculated almanac entries. Continuous Gregorian-day indexing does not reset on January 1.

Compose store Add/Back/Favorite/Delete/Drag icons use Material Symbols Outlined, 24 dp, weight 400, FILL 0, Apache-2.0. Files: `AddIcon.kt`, `BackIcon.kt`, `FavoriteIcon.kt`, `DeleteIcon.kt`, `DragIcon.kt`. Source URL pattern is `https://fonts.gstatic.com/render/v1/Material+Symbols+Outlined/24dp/{name}.kt?var=opsz,wght,FILL,GRAD,ROND@24,400,0,0,50`, with names `add`, `arrow_back`, `favorite`, `delete`, `drag_indicator`. Package/visibility/property names were adjusted. `FavoriteFilledIcon.kt` retains the outer favorite contour and removes its hole. compat has no store UI; those Compose filenames document the source implementation.
