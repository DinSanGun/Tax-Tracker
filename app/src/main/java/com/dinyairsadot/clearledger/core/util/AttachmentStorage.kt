package com.dinyairsadot.clearledger.core.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Manages the app-private, on-device copies backing invoice attachments.
 *
 * Once a document is picked (via SAF), its bytes are copied once into
 * `filesDir/invoice_attachments/` under a random, collision-safe name. The invoice then
 * references only that internal file — never the original external `content://` Uri — so the
 * user can freely delete or move the original source file afterwards without breaking the
 * attachment. No broad storage permission or `MediaStore` access is used; only a plain
 * app-private files subdirectory.
 *
 * Attachment files are not yet included in backup/restore (see `core/util/backup/BackupMapper.kt`).
 */
object AttachmentStorage {

    private const val ATTACHMENTS_SUBDIR = "invoice_attachments"
    private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

    /** Result of successfully copying a picked document into managed storage. */
    data class CopiedAttachment(
        val fileName: String,
        val displayName: String?,
        val mimeType: String?
    )

    private fun attachmentsDir(context: Context): File {
        val dir = File(context.filesDir, ATTACHMENTS_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** The managed [File] for [fileName], or null if [fileName] is blank. Does not check existence. */
    fun fileFor(context: Context, fileName: String?): File? {
        if (fileName.isNullOrBlank()) return null
        return File(attachmentsDir(context), fileName)
    }

    /** A safe, temporary `content://` Uri for a managed attachment [file] via the app's FileProvider. */
    fun contentUriFor(context: Context, file: File): Uri {
        val authority = context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX
        return FileProvider.getUriForFile(context, authority, file)
    }

    /**
     * Copies [sourceUri]'s bytes into a new, collision-safe file under the app-private
     * attachments directory, capturing the source's display name and MIME type for later UI
     * use (since those can no longer be queried once the source is gone). Runs off the main
     * thread. Returns null — cleaning up any partial file — if [sourceUri] can't be read
     * (deleted, moved, or permission revoked) or the copy otherwise fails. Never throws.
     */
    suspend fun copyIntoManagedStorage(context: Context, sourceUri: Uri): CopiedAttachment? {
        return withContext(Dispatchers.IO) {
            val displayName = AttachmentUtil.queryDisplayName(context, sourceUri.toString())
            val mimeType = runCatching { context.contentResolver.getType(sourceUri) }.getOrNull()
            val fileName = generateFileName(displayName, mimeType)
            val destFile = File(attachmentsDir(context), fileName)

            val copied = runCatching {
                val input = context.contentResolver.openInputStream(sourceUri) ?: return@runCatching false
                input.use { source ->
                    destFile.outputStream().use { output -> source.copyTo(output) }
                }
                true
            }.getOrDefault(false)

            if (!copied) {
                runCatching { if (destFile.exists()) destFile.delete() }
                return@withContext null
            }
            CopiedAttachment(fileName = fileName, displayName = displayName, mimeType = mimeType)
        }
    }

    /** Deletes the managed attachment file named [fileName], if any. Safe to call repeatedly; never throws. */
    fun deleteManagedFile(context: Context, fileName: String?) {
        if (fileName.isNullOrBlank()) return
        runCatching { fileFor(context, fileName)?.takeIf { it.exists() }?.delete() }
    }

    private fun generateFileName(displayName: String?, mimeType: String?): String {
        val extension = displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() && it.length <= 10 && it.all(Char::isLetterOrDigit) }
            ?: extensionForMimeType(mimeType)
        val base = UUID.randomUUID().toString()
        return if (extension != null) "$base.$extension" else base
    }

    private fun extensionForMimeType(mimeType: String?): String? = when (mimeType) {
        "application/pdf" -> "pdf"
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> null
    }
}
