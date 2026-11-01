package com.calendarmods

import android.os.Handler
import android.os.HandlerThread

/** One producer queue for the process, including the gap between old/new Activity instances. */
internal object FaceCameraWorker {
    val handler: Handler by lazy {
        Handler(HandlerThread("calendar-face-presence").apply { start() }.looper)
    }
}
