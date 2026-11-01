package com.hudhud.app.data.document

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.hudhud.app.domain.model.FileParseResult
import org.apache.poi.poifs.filesystem.FileMagic

/**
 * Validates files before processing.
 * Checks file type, extension, and accessibility.
 */
class FileValidator(private val context: Context) {

    // Supported file extensions and MIME types
    private val supportedExtensions = mapOf(
        "xlsx" to listOf(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel"
        ),
        "xls" to listOf(
            "application/vnd.ms-excel",
            "application/vnd.ms-office"
        ),
        "pdf" to listOf(
            "application/pdf"
        ),
    )

    // Maximum file sizes (in bytes)
    private val maxFileSize = 50 * 1024 * 1024L // 50 MB

    /**
     * Validate a file from URI
     * @param uri The file URI
     * @return ValidationResult with status and error message if invalid
     */
    fun validate(uri: Uri): ValidationResult {
        // Check if URI is accessible
        if (!isUriAccessible(uri)) {
            return ValidationResult(
                isValid = false,
                error = "Cannot access the file. Please ensure you have permission.",
                errorCode = ErrorCode.ACCESS_DENIED,
            )
        }

        // Get file info
        val fileInfo = getFileInfo(uri)
        
        // Check if we got file name
        if (fileInfo.name.isBlank()) {
            return ValidationResult(
                isValid = false,
                error = "Unable to read file name",
                errorCode = ErrorCode.INVALID_FILE,
            )
        }

        // Check file extension
        val extension = getExtension(fileInfo.name)
        if (!isExtensionSupported(extension)) {
            return ValidationResult(
                isValid = false,
                error = "Unsupported file format: $extension. Supported formats: XLSX, PDF",
                errorCode = ErrorCode.UNSUPPORTED_FORMAT,
                supportedFormats = supportedExtensions.keys.toList(),
            )
        }

        // Check file size
        if (fileInfo.size > maxFileSize) {
            val sizeMB = fileInfo.size / (1024 * 1024)
            return ValidationResult(
                isValid = false,
                error = "File is too large (${sizeMB}MB). Maximum size is ${maxFileSize / (1024 * 1024)}MB",
                errorCode = ErrorCode.FILE_TOO_LARGE,
                fileSize = fileInfo.size,
            )
        }

        // Verify file content matches extension (basic check)
        if (!verifyFileContent(uri, extension)) {
            return ValidationResult(
                isValid = false,
                error = "File content does not match its extension. The file may be corrupted.",
                errorCode = ErrorCode.INVALID_CONTENT,
            )
        }

        return ValidationResult(
            isValid = true,
            fileName = fileInfo.name,
            fileSize = fileInfo.size,
            extension = extension,
        )
    }

    /**
     * Check if URI is accessible
     */
    private fun isUriAccessible(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get file information from URI
     */
    private fun getFileInfo(uri: Uri): FileInfo {
        var name = ""
        var size = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                    if (nameIndex >= 0) {
                        name = cursor.getString(nameIndex) ?: ""
                    }
                    if (sizeIndex >= 0) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback: try to get name from URI
            name = uri.lastPathSegment ?: ""
        }

        return FileInfo(name, size)
    }

    /**
     * Get file extension
     */
    private fun getExtension(fileName: String): String {
        return fileName.substringAfterLast('.', "").lowercase()
    }

    /**
     * Check if extension is supported
     */
    private fun isExtensionSupported(extension: String): Boolean {
        return supportedExtensions.containsKey(extension)
    }

    /**
     * Verify file content matches extension using FileMagic
     */
    private fun verifyFileContent(uri: Uri, extension: String): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val magic = FileMagic.valueOf(inputStream)
                
                when (extension) {
                    "xlsx", "xls" -> {
                        magic == FileMagic.OOXML || magic == FileMagic.OLE2
                    }
                    "pdf" -> {
                        magic == FileMagic.PDF
                    }
                    else -> false
                }
            } ?: false
        } catch (e: Exception) {
            // If we can't check content, assume it's valid
            // This handles cases where FileMagic is not available
            true
        }
    }

    /**
     * Get MIME type from extension
     */
    fun getMimeType(extension: String): String? {
        return supportedExtensions[extension]?.firstOrNull()
    }

    /**
     * Check if file is supported
     */
    fun isSupported(uri: Uri): Boolean {
        val extension = getExtension(getFileInfo(uri).name)
        return isExtensionSupported(extension)
    }

    /**
     * Get supported extensions as list
     */
    fun getSupportedExtensions(): List<String> = supportedExtensions.keys.toList()

    /**
     * Data class for validation result
     */
    data class ValidationResult(
        val isValid: Boolean,
        val error: String? = null,
        val errorCode: ErrorCode? = null,
        val fileName: String? = null,
        val fileSize: Long? = null,
        val extension: String? = null,
        val supportedFormats: List<String>? = null,
    )

    /**
     * Error codes for validation
     */
    enum class ErrorCode {
        ACCESS_DENIED,
        INVALID_FILE,
        UNSUPPORTED_FORMAT,
        FILE_TOO_LARGE,
        INVALID_CONTENT,
    }

    /**
     * File information
     */
    data class FileInfo(
        val name: String,
        val size: Long,
    )
}