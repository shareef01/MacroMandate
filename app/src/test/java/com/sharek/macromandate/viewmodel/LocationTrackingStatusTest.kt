package com.sharek.macromandate.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationTrackingStatusTest {
    @Test
    fun photoPicker_locationEnabled_permissionMissing_hasExplicitState() {
        assertEquals(
            LocationTrackingStatus.PermissionRequired,
            locationTrackingStatus(enabled = true, permissionGranted = false)
        )
    }

    @Test
    fun disabledTrackingDoesNotClaimPermissionIsRequired() {
        assertEquals(
            LocationTrackingStatus.Disabled,
            locationTrackingStatus(enabled = false, permissionGranted = false)
        )
    }
}
