[中文](face-brightness.md) | [English](face-brightness.en.md)

> Current mycompat has one daily theme. Eleven-theme counts below refer to the earlier implementation. Sampling twice per second still uses a continuously running camera.

# Face presence brightness

Each theme has an independent, default-off setting, offered in the style sheet on devices that
report a camera. It requires the CAMERA runtime permission on Android 6+. Capture runs only while
the opted-in theme is fullscreen
and the activity is resumed. All cameras exposed by the API are enumerated, ordered by front-facing metadata and then ID.
A failed camera/configuration falls back to the remaining candidates, with retries at a bounded frequency.
Front-facing metadata normally identifies the screen-side camera; incorrect OEM metadata,
external displays and physical lenses not exposed by the legacy API cannot be inferred reliably.

The detector is Android's open-source `android.media.FaceDetector` (AOSP, available since API 1):

- https://android.googlesource.com/platform/frameworks/base/+/nougat-release/media/java/android/media/FaceDetector.java
- https://developer.android.com/reference/android/media/FaceDetector

The framework implementation's source is Apache-2.0 licensed. The application calls that
platform API; it does not redistribute a separate native library or require Google Play services.
This is presence detection, not identity recognition. Frames never leave memory, and no images,
face templates or identities are saved. No network or storage permission is added.

One worker samples NV21 luminance at two frames per second into a reused, orientation-correct,
RGB_565 bitmap with maximum edge 320 pixels. A detected face raises this window to 85% brightness.
After eight seconds without a face it fades to 2%, keeping the calendar displayed. Startup uses
the same eight-second grace. Camera errors or a stalled feed restore the original window
brightness while automatically reconnecting; leaving fullscreen, pausing or destroying the activity releases capture. Global
brightness settings are never changed. Front-facing, well-lit faces work best; this legacy
detector is not a security or liveness check, and darkness/occlusion can cause misses.

The instrumentation-only `face-fixture.png` is NASA's public-domain photograph of Eileen Collins,
distributed as `skimage.data.astronaut` by scikit-image:
https://scikit-image.org/docs/stable/api/skimage.data#skimage.data.astronaut
https://github.com/scikit-image/scikit-image/blob/v0.24.0/skimage/data/astronaut.png
It is excluded from the application APK and is used solely to test positive/negative frames and
all camera rotations. No endorsement is implied.

Validation: 61 unit tests and lint/build passed. Android 15 instrumentation checks all four
rotations against known-face and blank frames, all eleven per-theme settings, actual camera
frame delivery, pause/resume capture lifecycle, and fullscreen-exit brightness restoration.
The shared 248 numeral checks and framework history regressions also pass on mycompat.
Actual API 14 hardware and physical face/lighting variations still require device acceptance.

Camera operations share one process-level worker across Activity recreation, so rotation cannot
open a new camera before the old one has released it. Texture destruction defers release until
the producer stops. Display rotation also updates raw-frame orientation without requiring a
preview restart when Android keeps the Activity alive. Negotiated preview dimensions/format
are read back from the driver; unsupported advertised size combinations fall back to defaults.
Diagnostic `FaceBrightness` logs contain camera IDs, orientation and exception information,
never frames or face data. Tests include non-square frames and real display rotations.

Unattended recovery has no retry-count cutoff. A twelve-second frame watchdog reopens stalled
capture; failed reconnects back off from 350 ms to at most 30 seconds, resetting after thirty
seconds of stable analysed frames. Pending retries stop when leaving fullscreen or pausing.
Fullscreen retains FLAG_KEEP_SCREEN_ON independently of face presence and brightness, so a
missing face dims the calendar without letting the display sleep. A physically failed or
blocked camera driver cannot be guaranteed to recover; diagnostics remain available in logcat.

Follow-up device validation on Android 15: non-square known-face/blank frames pass at all four
orientations; seven actual display rotations retain analysed frames. During a 75-second run
with a 15-second screen timeout and plugged-in wake overrides disabled, three deliberately
stopped camera streams recovered automatically, preferring the previously working front
camera. The window remained awake throughout and the original power settings were restored.
This is fault-recovery coverage, not a long-duration certification of the reported YOGA Tablet
2-1050F / Android 5.0.1 hardware; that device is not connected to this environment.
