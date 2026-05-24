package com.example.model

sealed interface FormatState {
    object Idle : FormatState
    data class Formatting(
        val progress: Float, // 0.0f to 1.0f
        val currentStep: String,
        val subStep: String
    ) : FormatState
    data class Success(
        val driveName: String,
        val fileSystem: String,
        val isVerified: Boolean
    ) : FormatState
    data class Error(
        val message: String
    ) : FormatState
}
