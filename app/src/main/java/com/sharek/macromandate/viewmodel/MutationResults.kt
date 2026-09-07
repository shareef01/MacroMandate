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

/**
 * Result of purging all meal data, evidence files, and activity logs.
 */
data class DeleteAllResult(
    val dbSuccess: Boolean,
    val filesCleaned: Boolean
) {
    val isCompleteSuccess: Boolean get() = dbSuccess && filesCleaned
}
