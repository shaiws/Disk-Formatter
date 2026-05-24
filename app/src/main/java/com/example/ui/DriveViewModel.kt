package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DriveRepository
import com.example.data.FormatHistoryEntity
import com.example.model.FormatState
import com.example.model.UsbDrive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DriveViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val repository = DriveRepository(application, database.appDao())

    // Drives list from repository
    val allDrives: StateFlow<List<UsbDrive>> = repository.allDrives

    private val _selectedDrive = MutableStateFlow<UsbDrive?>(null)
    val selectedDrive = _selectedDrive.asStateFlow()

    private val _targetFileSystem = MutableStateFlow("FAT32")
    val targetFileSystem = _targetFileSystem.asStateFlow()

    private val _formatState = MutableStateFlow<FormatState>(FormatState.Idle)
    val formatState = _formatState.asStateFlow()

    // Simulate connection issue toggle
    private val _simulateHardwareFailures = MutableStateFlow(false)
    val simulateHardwareFailures = _simulateHardwareFailures.asStateFlow()

    // Persistent format history
    val formatHistory: StateFlow<List<FormatHistoryEntity>> = repository.formatHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Tutorial state persistence
    private val _showTutorial = MutableStateFlow(false)
    val showTutorial = _showTutorial.asStateFlow()

    private val _tutorialStep = MutableStateFlow(1)
    val tutorialStep = _tutorialStep.asStateFlow()

    init {
        // Automatically select the first drive if available
        viewModelScope.launch {
            allDrives.collect { list ->
                if (_selectedDrive.value == null && list.isNotEmpty()) {
                    _selectedDrive.value = list.first()
                }
            }
        }

        // Check preference for tutorial status
        viewModelScope.launch {
            repository.getPreferenceFlow("tutorial_completed").collect { pref ->
                _showTutorial.value = pref == null || pref.value != "true"
            }
        }
    }

    fun selectDrive(drive: UsbDrive) {
        _selectedDrive.value = drive
    }

    fun setTargetFileSystem(fs: String) {
        _targetFileSystem.value = fs
    }

    fun toggleHardwareFailureSimulation() {
        _simulateHardwareFailures.value = !_simulateHardwareFailures.value
    }

    fun triggerScanAndRefresh() {
        repository.updateDrivesList()
    }

    // Interactive custom simulator drive adds custom devices
    fun addNewSimulatedDrive(name: String, capacityGB: Long, currentFs: String) {
        repository.addCustomSimulatedDrive(name, capacityGB, currentFs)
    }

    fun deleteDrive(id: String) {
        repository.removeDrive(id)
        if (_selectedDrive.value?.id == id) {
            _selectedDrive.value = allDrives.value.firstOrNull()
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    // ONBOARDING TUTORIAL ACTIONS
    fun nextTutorialStep() {
        if (_tutorialStep.value < 4) {
            _tutorialStep.value += 1
        } else {
            completeTutorial()
        }
    }

    fun prevTutorialStep() {
        if (_tutorialStep.value > 1) {
            _tutorialStep.value -= 1
        }
    }

    fun completeTutorial() {
        _showTutorial.value = false
        viewModelScope.launch {
            repository.savePreference("tutorial_completed", "true")
        }
    }

    fun restartTutorial() {
        _tutorialStep.value = 1
        _showTutorial.value = true
        viewModelScope.launch {
            repository.savePreference("tutorial_completed", "false")
        }
    }

    // CORE ACTION: Formatting Algorithm with reactive step progression
    fun executeFormat(verifyIntegrity: Boolean) {
        val drive = _selectedDrive.value ?: return
        val targetFs = _targetFileSystem.value

        viewModelScope.launch {
            try {
                // Initialize formatting
                _formatState.value = FormatState.Formatting(
                    progress = 0.05f,
                    currentStep = "Initiating USB Mount Procedure",
                    subStep = "Requesting android.permission.USB_PERMISSION..."
                )
                delay(700)

                if (_simulateHardwareFailures.value) {
                    // Abort early due to simulated hardware error
                    throw Exception("Hardware failure: USB power surge or physical connection interrupted.")
                }

                _formatState.value = FormatState.Formatting(
                    progress = 0.15f,
                    currentStep = "Acquiring Device Lock",
                    subStep = "Dismounting active filesystem descriptors..."
                )
                delay(800)

                _formatState.value = FormatState.Formatting(
                    progress = 0.30f,
                    currentStep = "Clearing Sector Maps",
                    subStep = "Overwriting Master Boot Record sector 0xAA55..."
                )
                delay(900)

                if (_simulateHardwareFailures.value) {
                    throw Exception("Hardware failure: USB sectors write failed (Device Write Protected).")
                }

                _formatState.value = FormatState.Formatting(
                    progress = 0.50f,
                    currentStep = "Creating File Tables",
                    subStep = when (targetFs) {
                        "FAT32" -> "Building File Allocation Tables (FAT) & Root Directory..."
                        "exFAT" -> "Initializing Cluster Bitmap & Allocation Tables..."
                        else -> "Writing NTFS Master File Table (MFT) records..."
                    }
                )
                delay(1200)

                _formatState.value = FormatState.Formatting(
                    progress = 0.70f,
                    currentStep = "Verifying Cluster Alignment",
                    subStep = "Scanning boundary clusters for bad sectors..."
                )
                delay(800)

                var integrityVerified = false
                if (verifyIntegrity) {
                    _formatState.value = FormatState.Formatting(
                        progress = 0.85f,
                        currentStep = "Verifying File Integrity",
                        subStep = "Writing verification blocks block_0 to block_512..."
                    )
                    delay(1000)
                    
                    _formatState.value = FormatState.Formatting(
                        progress = 0.92f,
                        currentStep = "Verifying File Integrity",
                        subStep = "Validating block checksums match (SHA-256 validation successful)."
                    )
                    delay(800)
                    integrityVerified = true
                } else {
                    _formatState.value = FormatState.Formatting(
                        progress = 0.90f,
                        currentStep = "Flushing Cache",
                        subStep = "Syncing system hardware cache buffers..."
                    )
                    delay(600)
                }

                // Format success
                _formatState.value = FormatState.Success(
                    driveName = drive.name,
                    fileSystem = targetFs,
                    isVerified = integrityVerified
                )

                // Update drives data
                repository.updateDriveFileSystem(drive.id, targetFs)
                // Also update local selected drive to represent new state
                _selectedDrive.value = _selectedDrive.value?.copy(fileSystem = targetFs, usedBytes = 0L)

                // Save to Room Database
                repository.insertHistory(
                    FormatHistoryEntity(
                        driveName = drive.name,
                        capacityBytes = drive.totalBytes,
                        fileSystem = targetFs,
                        isSuccess = true,
                        isVerified = integrityVerified
                    )
                )

            } catch (e: Exception) {
                // Log and process format error
                _formatState.value = FormatState.Error(
                    message = e.message ?: "Unknown hardware communication failure."
                )

                // Save failed partition attempt into local SQLite
                repository.insertHistory(
                    FormatHistoryEntity(
                        driveName = drive.name,
                        capacityBytes = drive.totalBytes,
                        fileSystem = targetFs,
                        isSuccess = false,
                        isVerified = false,
                        errorMessage = e.message
                    )
                )
            }
        }
    }

    fun dismissFormatState() {
        _formatState.value = FormatState.Idle
    }
}
