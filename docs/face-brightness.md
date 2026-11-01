# Face presence brightness

Each theme has an independent, default-off setting. It requires an available camera and the
CAMERA runtime permission on Android 6+. Capture runs only while the opted-in theme is fullscreen
and the activity is resumed. The front camera is preferred; a rear-only device uses its camera.

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
brightness; leaving fullscreen, pausing or destroying the activity releases capture. Global
brightness settings are never changed. Front-facing, well-lit faces work best; this legacy
detector is not a security or liveness check, and darkness/occlusion can cause misses.

The instrumentation-only `face-fixture.png` is NASA's public-domain photograph of Eileen Collins,
distributed as `skimage.data.astronaut` by scikit-image:
https://scikit-image.org/docs/stable/api/skimage.data#skimage.data.astronaut
https://github.com/scikit-image/scikit-image/blob/v0.24.0/skimage/data/astronaut.png
It is excluded from the application APK and is used solely to test positive/negative frames and
all camera rotations. No endorsement is implied.

Validation: 56 unit tests and lint/build passed. Android 15 instrumentation checks all four
rotations against known-face and blank frames, all eleven per-theme settings, actual camera
frame delivery, pause/resume capture lifecycle, and fullscreen-exit brightness restoration.
The shared 248 numeral checks and framework history regressions also pass on mycompat.
Actual API 14 hardware and physical face/lighting variations still require device acceptance.
