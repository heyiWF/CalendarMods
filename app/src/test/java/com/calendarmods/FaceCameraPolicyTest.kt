package com.calendarmods

import org.junit.Assert.*
import org.junit.Test

class FaceCameraPolicyTest {
    @Test fun frontCamerasPrecedeEveryRearCandidateRegardlessOfId() {
        val rear = FaceCameraInfo(0, false, 90)
        val front = FaceCameraInfo(4, true, 270)
        val secondFront = FaceCameraInfo(2, true, 90)
        assertEquals(listOf(2, 4, 0, 3), FaceCameraPolicy.ordered(listOf(rear, front,
            FaceCameraInfo(3, false, 0), secondFront)).map { it.id })
        assertEquals(listOf(rear), FaceCameraPolicy.ordered(listOf(rear)))
        assertTrue(FaceCameraPolicy.ordered(emptyList()).isEmpty())
    }

    @Test fun sensorAndDisplayOrientationCoverFrontRearAndNaturalLandscapeDevices() {
        for (sensor in listOf(0, 90, 180, 270)) for (display in listOf(0, 90, 180, 270)) {
            assertEquals((sensor + display) % 360, FaceCameraPolicy.rotation(FaceCameraInfo(9, true, sensor), display))
            assertEquals((sensor - display + 360) % 360, FaceCameraPolicy.rotation(FaceCameraInfo(7, false, sensor), display))
        }
    }
}
