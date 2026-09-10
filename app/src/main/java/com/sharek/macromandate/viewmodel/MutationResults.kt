package com.sharek.macromandate.viewmodel

import androidx.annotation.StringRes

/**
 * Result of saving or editing a meal entry.
 */
sealed interface SaveResult {
    data object Success : SaveResult
    data class Failure(@StringRes val messageRes: Int) : SaveResult
}

/**
 * Explicit state machine for committing AI-reviewed meal estimates into Room.
 */
sealed interface AnalysisCommitState {
    data object Idle : AnalysisCommitState
    data object Saving : AnalysisCommitState
    data class Failed(@StringRes val messageRes: Int) : AnalysisCommitState
}

sealed interface LocationTrackingStatus {
    data object Disabled : LocationTrackingStatus
    data object PermissionRequired : LocationTrackingStatus
    data object Enabled : LocationTrackingStatus
}

fun locationTrackingStatus(enabled: Boolean, permissionGranted: Boolean): LocationTrackingStatus = when {
    !enabled -> LocationTrackingStatus.Disabled
    !permissionGranted -> LocationTrackingStatus.PermissionRequired
    else -> LocationTrackingStatus.Enabled
}
