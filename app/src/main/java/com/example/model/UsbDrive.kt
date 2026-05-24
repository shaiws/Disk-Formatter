package com.example.model

data class UsbDrive(
    val id: String,
    val name: String,
    val totalBytes: Long,
    val usedBytes: Long,
    val fileSystem: String, // FAT32, exFAT, NTFS, FAT16, Unknown etc.
    val isSimulated: Boolean,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val devicePath: String? = null
) {
    val formattedCapacity: String
        get() = formatSize(totalBytes)

    val formattedUsed: String
        get() = formatSize(usedBytes)

    val formattedFree: String
        get() = formatSize(totalBytes - usedBytes)

    val usedPercentage: Float
        get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f

    companion object {
        fun formatSize(bytes: Long): String {
            val kb = 1024L
            val mb = kb * 1024
            val gb = mb * 1024
            return when {
                bytes >= gb -> String.format("%.1f GB", bytes.toDouble() / gb)
                bytes >= mb -> String.format("%.1f MB", bytes.toDouble() / mb)
                bytes >= kb -> String.format("%.1f KB", bytes.toDouble() / kb)
                else -> "$bytes Bytes"
            }
        }
    }
}
