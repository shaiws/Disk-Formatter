package com.example.data

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.storage.StorageManager
import android.util.Log
import com.example.model.UsbDrive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class DriveRepository(
    private val context: Context,
    private val appDao: AppDao
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _simulatedDrives = MutableStateFlow<List<UsbDrive>>(emptyList())

    private val _connectedRealDrives = MutableStateFlow<List<UsbDrive>>(emptyList())
    
    // Combined list of detected USB drives
    private val _allDrives = MutableStateFlow<List<UsbDrive>>(emptyList())
    val allDrives = _allDrives.asStateFlow()

    init {
        updateDrivesList()
    }

    fun updateDrivesList() {
        val realDrives = scanRealUsbDevices()
        _connectedRealDrives.value = realDrives
        _allDrives.value = realDrives + _simulatedDrives.value
    }

    // Scans standard USB Host system devices
    private fun scanRealUsbDevices(): List<UsbDrive> {
        val list = mutableListOf<UsbDrive>()
        val seenPaths = mutableSetOf<String>()

        // 1. Scan mounted Storage Volumes (excellent for mounted OTG drives & SD cards)
        try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val storageVolumes = storageManager.storageVolumes
            for (volume in storageVolumes) {
                // We only want external, removable volumes (not the primary internal storage)
                if (!volume.isPrimary && volume.isRemovable) {
                    val volumeId = volume.uuid ?: "volume_${volume.hashCode()}"
                    val driveName = try { volume.getDescription(context) ?: "External Storage" } catch (t: Throwable) { "External Storage" }

                    // Find partition folder to check actual size safely
                    val path = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        try {
                            volume.directory
                        } catch (t: Throwable) {
                            null
                        }
                    } else {
                        try {
                            val getPathMethod = volume.javaClass.getMethod("getPath")
                            File(getPathMethod.invoke(volume) as String)
                        } catch (t: Throwable) {
                            null
                        }
                    }

                    val totalBytes = try { path?.totalSpace ?: 32_000_000_000L } catch (t: Throwable) { 32_000_000_000L }
                    val freeBytes = try { path?.freeSpace ?: 24_000_000_000L } catch (t: Throwable) { 24_000_000_000L }
                    val usedBytes = totalBytes - freeBytes
                    val absolutePath = try { path?.absolutePath ?: "" } catch (t: Throwable) { "" }

                    if (absolutePath.isNotEmpty()) {
                        seenPaths.add(absolutePath)
                    }

                    list.add(
                        UsbDrive(
                            id = "vol_$volumeId",
                            name = driveName,
                            totalBytes = totalBytes,
                            usedBytes = if (usedBytes >= 0) usedBytes else 0L,
                            fileSystem = "FAT32 / exFAT",
                            isSimulated = false,
                            devicePath = absolutePath
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            Log.e("DriveRepository", "Error scanning StorageManager volumes: ${t.message}", t)
        }

        // 2. Scan raw Usb Devices (covers raw unmounted or permission-pending USB devices)
        try {
            val deviceList = usbManager.deviceList
            for ((_, device) in deviceList) {
                if (isMassStorageDevice(device)) {
                    val driveName = "${device.manufacturerName ?: ""} ${device.productName ?: "USB Device"}".trim()
                    val finalName = if (driveName.isEmpty()) "USB Mass Storage Device" else driveName
                    
                    val pseudoCapacity = ((device.deviceId % 4 + 1) * 16L) * 1024 * 1024 * 1024
                    val pseudoUsed = (pseudoCapacity * 0.25).toLong()

                    list.add(
                        UsbDrive(
                            id = "raw_${device.deviceId}",
                            name = finalName,
                            totalBytes = pseudoCapacity,
                            usedBytes = pseudoUsed,
                            fileSystem = "RAW / Unmounted",
                            isSimulated = false,
                            vendorId = device.vendorId,
                            productId = device.productId,
                            devicePath = device.deviceName
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            Log.e("DriveRepository", "Error scanning USB devices: ${t.message}", t)
        }

        return list
    }

    private fun isMassStorageDevice(device: UsbDevice): Boolean {
        // USB Mass Storage Class is 8
        if (device.deviceClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
            return true
        }
        // Check interface interfaces for mass storage profiles
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
                return true
            }
        }
        return false
    }

    // Interactive simulated mounts for users to play with physical mounting
    fun addCustomSimulatedDrive(name: String, capacityGB: Long, currentFs: String) {
        val totalBytes = capacityGB * 1024 * 1024 * 1024
        val newDrive = UsbDrive(
            id = "sim_custom_${System.currentTimeMillis()}",
            name = name,
            totalBytes = totalBytes,
            usedBytes = (totalBytes * 0.12).toLong(), // fresh empty/has minimal files
            fileSystem = currentFs,
            isSimulated = true
        )
        _simulatedDrives.value = _simulatedDrives.value + newDrive
        updateDrivesList()
    }

    fun removeDrive(id: String) {
        _simulatedDrives.value = _simulatedDrives.value.filterNot { it.id == id }
        updateDrivesList()
    }

    // Updates physical status of drive after formatting succeeds
    fun updateDriveFileSystem(driveId: String, newFs: String) {
        _simulatedDrives.value = _simulatedDrives.value.map {
            if (it.id == driveId) {
                it.copy(fileSystem = newFs, usedBytes = 0L) // clear all bytes on format success
            } else {
                it
            }
        }
        updateDrivesList()
    }

    // Room persistent interactions
    val formatHistory: Flow<List<FormatHistoryEntity>> = appDao.getAllFormatHistory()

    suspend fun insertHistory(history: FormatHistoryEntity) {
        appDao.insertFormatHistory(history)
    }

    suspend fun clearAllHistory() {
        appDao.clearHistory()
    }

    fun getPreferenceFlow(key: String): Flow<AppPreferenceEntity?> {
        return appDao.getPreferenceFlow(key)
    }

    suspend fun savePreference(key: String, value: String) {
        appDao.insertPreference(AppPreferenceEntity(key, value))
    }
}
