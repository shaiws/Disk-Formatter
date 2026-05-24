package com.example.ui

import com.example.ui.theme.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.FormatHistoryEntity
import com.example.model.FormatState
import com.example.model.UsbDrive
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    viewModel: DriveViewModel,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val drives by viewModel.allDrives.collectAsStateWithLifecycle()
    val selectedDrive by viewModel.selectedDrive.collectAsStateWithLifecycle()
    val targetFs by viewModel.targetFileSystem.collectAsStateWithLifecycle()
    val formatState by viewModel.formatState.collectAsStateWithLifecycle()
    val history by viewModel.formatHistory.collectAsStateWithLifecycle()
    val simulateFailures by viewModel.simulateHardwareFailures.collectAsStateWithLifecycle()

    val showTutorial by viewModel.showTutorial.collectAsStateWithLifecycle()
    val tutorialStep by viewModel.tutorialStep.collectAsStateWithLifecycle()

    var showConfirmationDialog by remember { mutableStateOf(false) }
    var showAddDriveDialog by remember { mutableStateOf(false) }
    var verifyIntegrity by remember { mutableStateOf(true) }

    // Warning confirmation checkboxes inside dangerous popup
    var confirmDataLossCheckbox by remember { mutableStateOf(false) }
    var confirmEraseStorageCheckbox by remember { mutableStateOf(false) }

    // Track offsets of segments to align interactive tutorial focus indicators
    var drivesListOffset by remember { mutableStateOf(IntOffset(0, 0)) }
    var fsConfigOffset by remember { mutableStateOf(IntOffset(0, 0)) }
    var verifierOffset by remember { mutableStateOf(IntOffset(0, 0)) }
    var actionOffset by remember { mutableStateOf(IntOffset(0, 0)) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // Dashboard Header Block
            HeaderSection(
                onRestartTutorial = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.restartTutorial()
                },
                onRefresh = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.triggerScanAndRefresh()
                },
                isDriveConnected = selectedDrive != null
            )

            // Dynamic Scrollable Body Area
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // TUTORIAL HIGHLIGHT STEP 1: Scanned Device List
                item {
                    Column(
                        modifier = Modifier
                            .testTag("step_drives_container")
                            .fillMaxWidth()
                            .border(
                                width = if (showTutorial && tutorialStep == 1) 2.5.dp else 0.dp,
                                color = if (showTutorial && tutorialStep == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(if (showTutorial && tutorialStep == 1) 6.dp else 0.dp)
                    ) {
                        DrivesListHeader()

                        if (drives.isEmpty()) {
                            EmptyDrivesState()
                        } else {
                            // Horizontal Drives Slider Roll
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                drives.forEach { drive ->
                                    val isSelected = selectedDrive?.id == drive.id
                                    DriveCard(
                                        drive = drive,
                                        isSelected = isSelected,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.selectDrive(drive)
                                        },
                                        onDeleteSimulated = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.deleteDrive(drive.id)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Selected Drive Details + File System Setup Card
                selectedDrive?.let { activeDrive ->
                    // TUTORIAL HIGHLIGHT STEP 2: File System Choices
                    item {
                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            border = BorderStroke(1.5.dp, if (showTutorial && tutorialStep == 2) MaterialTheme.colorScheme.primary else Color(0xFF2A2A2A)),
                            color = Color(0xFF1A1A1A),
                            modifier = Modifier
                                .testTag("step_filesystem_card")
                                .fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text(
                                    text = "FILE SYSTEM CONFIGURATION",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = activeDrive.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                    text = "File System TYPE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    letterSpacing = 1.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // File System Options selector buttons list (Mockup Tailwind look)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val formats = listOf("FAT32", "exFAT", "NTFS")
                                    formats.forEach { curFs ->
                                        val isSelectedFs = targetFs == curFs
                                        val boxBorderColor by animateColorAsState(
                                            targetValue = if (isSelectedFs) MaterialTheme.colorScheme.primary else Color(0xFF2D2D2D),
                                            label = "fsBorder"
                                        )
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("fs_chip_$curFs")
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(if (isSelectedFs) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                                .border(
                                                    width = if (isSelectedFs) 2.dp else 1.5.dp,
                                                    color = boxBorderColor,
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    viewModel.setTargetFileSystem(curFs)
                                                }
                                        ) {
                                            Text(
                                                text = curFs,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelectedFs) Color.White else Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                FileSystemRecommendationLabel(targetFs)
                            }
                        }
                    }

                    // TUTORIAL HIGHLIGHT STEP 3: Integrity Check Configuration
                    item {
                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            border = BorderStroke(1.5.dp, if (showTutorial && tutorialStep == 3) MaterialTheme.colorScheme.primary else Color(0xFF2A2A2A)),
                            color = Color(0xFF1A1A1A),
                            modifier = Modifier
                                .testTag("step_integrity_card")
                                .fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Verify Check",
                                        tint = if (verifyIntegrity) MaterialTheme.colorScheme.primary else Color(0xFF475569),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            text = "Verify Integrity",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Slower, safer raw checksum loop",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                                Switch(
                                    checked = verifyIntegrity,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        verifyIntegrity = it
                                    },
                                    modifier = Modifier.testTag("verification_switch")
                                )
                            }
                        }
                    }

                    // Strict Fatal Data Loss warnings warnings zone
                    item {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.5.dp, WarningRedBorder),
                            color = WarningRedContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Absolute Critical Warning: Data Loss Alert",
                                    tint = WarningRedText,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "CRITICAL WARNING: DATA ERASURE",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Black,
                                        color = WarningRedText,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Formatting will completely purge and erase all data. This action cannot be reversed. Ensure you have backed up all irreplaceable files before executing.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = WarningRedText.copy(alpha = 0.9f),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    // TUTORIAL HIGHLIGHT STEP 4: Format launcher trigger
                    item {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                // Reset warning confirmations before opening
                                confirmDataLossCheckbox = false
                                confirmEraseStorageCheckbox = false
                                showConfirmationDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .testTag("step_format_button")
                                .fillMaxWidth()
                                .height(56.dp)
                                .border(
                                    width = if (showTutorial && tutorialStep == 4) 2.5.dp else 0.dp,
                                    color = if (showTutorial && tutorialStep == 4) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(24.dp)
                                ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(
                                text = "INITIALIZE FORMAT SYSTEM",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("⚡", fontSize = 14.sp)
                        }
                    }
                }

                // Simulated error engine setup
                item {
                    SettingsSection(
                        simulateFailures = simulateFailures,
                        onSimulateCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleHardwareFailureSimulation()
                        }
                    )
                }

                // Saved operation statistics logs history list in offline SQLite database
                item {
                    HistorySectionHeader(
                        hasItems = history.isNotEmpty(),
                        onClearClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.clearAllHistory()
                        }
                    )
                }

                if (history.isEmpty()) {
                    item {
                        EmptyHistoryState()
                    }
                } else {
                    items(items = history, key = { it.id }) { record ->
                        HistoryRecordRow(record)
                    }
                }
            }
        }

        // FORMAT EXECUTION DIALOG OVERLAY (State-bound dynamic feedback consoles)
        AnimatedVisibility(
            visible = formatState !is FormatState.Idle,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            FormatConsoleOverlay(
                state = formatState,
                onDismiss = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.dismissFormatState()
                }
            )
        }

        // DOUBLE-ACCESSIBILITY CONFIRMATION ALERT POPUP FOR DATA ERASURE
        if (showConfirmationDialog) {
            AlertDialog(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning Alert",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = "Double Safety Authorization",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "You are preparing to wipe raw clusters on '${selectedDrive?.name}'. Complete both safety declarations to unlock formatting:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Requirement 1 Checkbox
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    confirmDataLossCheckbox = !confirmDataLossCheckbox
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = confirmDataLossCheckbox,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        confirmDataLossCheckbox = it
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.testTag("confirm_cb_data_loss")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "I explicitly agree that ALL files will be deleted forever.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        // Requirement 2 Checkbox
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    confirmEraseStorageCheckbox = !confirmEraseStorageCheckbox
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = confirmEraseStorageCheckbox,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        confirmEraseStorageCheckbox = it
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.testTag("confirm_cb_wipe")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "I confirm I have made static backups of irreplaceable content.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                },
                onDismissRequest = {
                    showConfirmationDialog = false
                },
                confirmButton = {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showConfirmationDialog = false
                            viewModel.executeFormat(verifyIntegrity)
                        },
                        enabled = confirmDataLossCheckbox && confirmEraseStorageCheckbox,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            disabledContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.testTag("confirm_danger_format_now")
                    ) {
                        Text("Wipe Storage Block")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showConfirmationDialog = false }
                    ) {
                        Text("Cancel Setup")
                    }
                }
            )
        }

        // ADD NEW SIMULATED FLASH DRIVE DIALOG POPUP
        if (showAddDriveDialog) {
            AddSimulatedDriveDialog(
                onDismiss = { showAddDriveDialog = false },
                onAddDrive = { modelName, sizeGB, fs ->
                    viewModel.addNewSimulatedDrive(modelName, sizeGB, fs)
                    showAddDriveDialog = false
                }
            )
        }

        // THE INTERACTIVE FIRST STARTUP TUTORIAL MODAL OVERLAY (Guided wizard steps)
        if (showTutorial) {
            OnboardingTutorialStage(
                step = tutorialStep,
                onBack = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.prevTutorialStep()
                },
                onNext = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.nextTutorialStep()
                },
                onSkip = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.completeTutorial()
                }
            )
        }
    }
}

// -------------------------------------------------------------
// COMPOSE SUB-COMPONENTS
// -------------------------------------------------------------

@Composable
fun HeaderSection(
    onRestartTutorial: () -> Unit,
    onRefresh: () -> Unit,
    isDriveConnected: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "FlashFormat",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = (if (isDriveConnected) Color(0xFF10B981) else Color(0xFFF59E0B)).copy(alpha = pulseAlpha),
                            shape = CircleShape
                        )
                )
                Text(
                    text = if (isDriveConnected) "DRIVE CONNECTED" else "NO DRIVE DETECTED",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF1F1F1F), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Scan and refresh connected USB hosts",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onRestartTutorial,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF1F1F1F), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Launch active interactive tutorial",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun DrivesListHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Scanned OTG Drives",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Real OTG-connected USB storage & external SD card",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun EmptyDrivesState() {
    Surface(
        color = Color(0xFF131316),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.5.dp, Color(0xFF2A2A2A)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No USB partitions found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Plug in an OTG-supported USB flash drive or insert an external SD card via standard OTG adapter to begin configuring partition attributes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun DriveCard(
    drive: UsbDrive,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDeleteSimulated: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF2A2A2A),
        label = "borderColorAnim"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.5.dp, borderColor),
        color = if (isSelected) Color(0xFF131316) else Color(0xFF1A1A1A),
        modifier = modifier
            .testTag("drive_card_${drive.id}")
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.padding(18.dp)) {
            // Unmount/Delete icon for mock drives
            if (drive.isSimulated) {
                IconButton(
                    onClick = onDeleteSimulated,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .padding(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Unmount simulated disk",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (drive.isSimulated) Icons.Default.Info else Icons.Default.Settings,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (drive.isSimulated) "SIMULATOR" else "HARDWARE USB",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = drive.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Storage fill bar indicator
                LinearProgressIndicator(
                    progress = { drive.usedPercentage },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = if (drive.usedPercentage > 0.85f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = Color(0xFF2D2D2D)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${drive.formattedFree} free of ${drive.formattedCapacity}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SuggestionChip(
                        onClick = {},
                        label = { Text(drive.fileSystem, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.height(20.dp),
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color(0xFF2D2D2D),
                            labelColor = Color.White
                        ),
                        border = null
                    )
                }
            }
        }
    }
}

@Composable
fun FileSystemRecommendationLabel(fs: String) {
    val description = when (fs) {
        "FAT32" -> "✓ Highly compatible. Recognized directly by legacy car media players, cameras, Smart TVs, Android devices, Mac, and Windows. Limit: 4 GB maximum single file size limit."
        "exFAT" -> "✓ Recommended for modern storage. Infinite file sizes supported, fully read-writable natively across Windows, Mac, Linux, and Android OS. Ideal for high-definition 4K content."
        else -> "✓ Ideal for Microsoft Windows operating systems. Full compression support and journal recovery. On Mac and other devices, it may restrict as a Read-Only system unless extensions are loaded."
    }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp).padding(top = 1.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun SettingsSection(
    simulateFailures: Boolean,
    onSimulateCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.5.dp, Color(0xFF2A2A2A)),
        color = Color(0xFF1A1A1A),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Developer Debug Settings",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Use these switches to test edge cases without physical OTG hardware.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                color = Color(0xFF131316),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (simulateFailures) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Simulate Hardware Failures",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Intentionally triggers abrupt write permissions failures or physical disconnection blocks mid-format to show handling.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = simulateFailures,
                        onCheckedChange = { onSimulateCheckedChange(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.error,
                            checkedTrackColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.testTag("simulate_hardware_switch")
                    )
                }
            }
        }
    }
}

@Composable
fun HistorySectionHeader(
    hasItems: Boolean,
    onClearClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Offline Format Records",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Persistent logs stored secure locally on SQLite",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        if (hasItems) {
            TextButton(
                onClick = onClearClick,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear History", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun EmptyHistoryState() {
    Surface(
        color = Color(0xFF131316),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.5.dp, Color(0xFF2A2A2A)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No previous filesystems logs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun HistoryRecordRow(record: FormatHistoryEntity) {
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val timeStr = formatter.format(Date(record.timestamp))

    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
        color = Color(0xFF1A1A1A),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = if (record.isSuccess) Color(0x1A10B981) else Color(0x1AEF4444),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (record.isSuccess) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (record.isSuccess) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = record.driveName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Formatted to ${record.fileSystem}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )

                        if (record.isVerified) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.padding(1.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "VERIFIED",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                if (!record.isSuccess) {
                    Text(
                        text = "I/O Failure",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// LIVE CONSOLE OVERLAY ON FORMAT ACTIONS
// -------------------------------------------------------------

@Composable
fun FormatConsoleOverlay(
    state: FormatState,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = { /* Deny physical dismissal during active formatting */ }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (state) {
                    is FormatState.Formatting -> {
                        Text(
                            text = "Formatting Hardware Drive",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Custom animated sector circles
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
                            CircularSectorProgressIndicator(state.progress)
                            Text(
                                text = "${(state.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.currentStep,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = state.subStep,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center,
                                minLines = 2
                            )
                        }

                        // Fake logger window representing sectors layout
                        Surface(
                            color = Color.Black,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxSize(),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Text(
                                    text = "root@android-shost /dev/block/sda1 $",
                                    color = Color.Green,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = "[ OK ] Mapped sectors clusters 0 to 40960",
                                    color = Color.Green,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = "Writing filesystem structural tables...",
                                    color = Color.Cyan,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = "> ${state.subStep}",
                                    color = Color.Yellow,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    is FormatState.Success -> {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Success",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Text(
                            text = "Format Completed!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )

                        Text(
                            text = "'${state.driveName}' has been successfully partitioned and formatted into the ${state.fileSystem} filesystem.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (state.isVerified) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Verified Seal",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "INTEGRITY GUARANTEED",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Verification block reads and SHA-256 checks completed without packet errors.",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Done")
                        }
                    }

                    is FormatState.Error -> {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error Logo",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Text(
                            text = "Format Failed!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.error
                        )

                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "SUGGESTED ACTIONS:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "1. Ensure OTG connection is physical tight.\n2. Ensure Android system USB debug options allow file transfers.\n3. Make sure hardware lock switches on SD adapter (if used) are not in locked state.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Acknowledge Error")
                        }
                    }

                    FormatState.Idle -> { /* Done */ }
                }
            }
        }
    }
}

// Custom Draw Canvas loader sector layout representing circular platter sectors writing
@Composable
fun CircularSectorProgressIndicator(progress: Float) {
    val animateProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(),
        label = "animatedProgress"
    )

    val color = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Draw track base
        drawCircle(
            color = trackColor,
            radius = size.width / 2f,
            style = Stroke(width = 10.dp.toPx())
        )
        // Draw primary active sweep
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * animateProgress,
            useCenter = false,
            style = Stroke(width = 12.dp.toPx())
        )
    }
}

// -------------------------------------------------------------
// ADD DRIVE POPUP FOR THE SIMULATION ENGINE
// -------------------------------------------------------------

@Composable
fun AddSimulatedDriveDialog(
    onDismiss: () -> Unit,
    onAddDrive: (String, Long, String) -> Unit
) {
    var modelName by remember { mutableStateOf("Lexar JumpDrive v40") }
    var capacityGB by remember { mutableStateOf("32") }
    var selectedFs by remember { mutableStateOf("FAT32") }

    AlertDialog(
        title = {
            Text(
                text = "Mount Simulated USB Storage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configure parameters of simulated USB Mass Storage device:",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Manufacturer / Drive Name") },
                    modifier = Modifier.fillMaxWidth().testTag("add_drive_name_input")
                )

                OutlinedTextField(
                    value = capacityGB,
                    onValueChange = { capacityGB = it },
                    label = { Text("Capacity in GB") },
                    modifier = Modifier.fillMaxWidth().testTag("add_drive_size_input")
                )

                Text(text = "Current Filesystem", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("FAT32", "exFAT", "NTFS", "RAW").forEach { fs ->
                        InputChip(
                            selected = selectedFs == fs,
                            onClick = { selectedFs = fs },
                            label = { Text(fs) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val size = capacityGB.toLongOrNull() ?: 32L
                    onAddDrive(modelName, size, selectedFs)
                },
                modifier = Modifier.testTag("add_drive_submit")
            ) {
                Text("Mount simulated drive")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// -------------------------------------------------------------
// INTERACTIVE TUTORIAL OVERLAY
// -------------------------------------------------------------

@Composable
fun OnboardingTutorialStage(
    step: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(enabled = false) {} // Prevent clickthrough
    ) {
        val header = when (step) {
            1 -> "1. Connected Storage Cards"
            2 -> "2. Target File Systems"
            3 -> "3. Sector Integrity Verification"
            else -> "4. Double-Confirmation Format"
        }

        val description = when (step) {
            1 -> "View all connected USB Mass Storage hardware keys, partitions, and simulators. Dynamic percentage bars display active space metrics."
            2 -> "Choose the target layout block sector. Choose FAT32 for universal multimedia playback, exFAT for large modern files, or NTFS for dedicated Windows networks."
            3 -> "Verify block sector alignment post format. Writing dummy block clusters and matching SHA-256 hashes ensures the drive is completely healthy."
            else -> "Execute raw formatting. Prompts a double-authorization verification check requiring explicit agreement to shield against accidental data wipe."
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF4F46E5),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Top accent pull pill
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(6.dp)
                        .background(Color.White.copy(alpha = 0.4f), CircleShape)
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DRIVE MASTER SETUP TUTORIAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFC7D2FE),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "$step of 4",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = header,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE0E7FF),
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onSkip) {
                        Text("Skip Tutorial", color = Color(0xFFC7D2FE), fontWeight = FontWeight.Bold)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (step > 1) {
                            OutlinedButton(
                                onClick = onBack,
                                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("Back", fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = onNext,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF4F46E5)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("tutorial_next_button")
                        ) {
                            Text(
                                text = if (step == 4) "Finish Guide" else "Next Step",
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}
