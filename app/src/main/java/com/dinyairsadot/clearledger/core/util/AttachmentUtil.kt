package com.dinyairsadot.clearledger.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Helpers for the single local image/PDF invoice attachment feature.
 *
 * The attachment is never copied into app storage: [Context.getContentResolver] keeps a
 * persisted read-permission grant on the picker-provided `content://` Uri (obtained via
 * `ActivityResultContracts.OpenDocument`), so the document remains accessible across app
 * restarts as long as the underlying document itself is not deleted or moved by the user.
 *
 * Attachment files are not yet included in backup/restore; only the Uri reference is stored
 * on the invoice (see `core/util/backup/BackupMapper.kt`).
 *
 * Two different invoices can legitimately reference the exact same Uri (the user picked the
 * same file twice). Permission release call sites must therefore confirm no other *persisted*
 * invoice still needs a Uri before releasing it — see [releaseIfUnreferenced].
 */
object AttachmentUtil {

    /** Takes a persistable read permission grant so [uri] survives app/device restarts. */
    fun takePersistableReadPermission(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    /**
     * Releases a previously taken persistable read permission for [uriString], if any.
     * Safe to call even if the permission was already released or the Uri is invalid.
     */
    fun releasePersistableReadPermission(context: Context, uriString: String?) {
        if (uriString.isNullOrBlank()) return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    /**
     * Releases the persistable permission on [candidateUri] unless [keepUri] matches it
     * (e.g. the attachment currently saved on the invoice), or [isReferencedElsewhere] reports
     * that some other **persisted** invoice still references the exact same Uri (two invoices
     * may legitimately point at the same attachment file). Used both:
     * - when the user abandons a freshly-picked, unsaved attachment (replace, remove, or
     *   discard) in the add/edit form, and
     * - by [InvoiceListViewModel] after an update/delete that changes/removes a previously
     *   *saved* attachment reference (with `keepUri = null`, since the old Uri is never the
     *   value being kept in that case).
     *
     * [isReferencedElsewhere] should query persisted invoices only, after any DB write the
     * caller is performing has already completed, so the row being changed doesn't count
     * itself in the check.
     */
    suspend fun releaseIfUnreferenced(
        context: Context,
        candidateUri: String?,
        keepUri: String? = null,
        isReferencedElsewhere: suspend (String) -> Boolean
    ) {
        if (candidateUri.isNullOrBlank() || candidateUri == keepUri) return
        if (isReferencedElsewhere(candidateUri)) return
        releasePersistableReadPermission(context, candidateUri)
    }

    /** Returns a human-friendly display name for [uriString], or null if it cannot be resolved. */
    fun queryDisplayName(context: Context, uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        return runCatching {
            val uri = Uri.parse(uriString)
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        }.getOrNull()
    }

    sealed interface OpenResult {
        data object Opened : OpenResult
        /** The document is no longer accessible (deleted, moved, or permission revoked). */
        data object NotAccessible : OpenResult
        /** No installed app can handle this attachment's MIME type. */
        data object NoViewerApp : OpenResult
    }

    /**
     * Opens [uriString] via [Intent.ACTION_VIEW] using an appropriate external viewer,
     * granting temporary read access. Never throws; failures are reported via [OpenResult]
     * so the caller can show an error message instead of crashing.
     */
    fun openAttachment(context: Context, uriString: String?): OpenResult {
        if (uriString.isNullOrBlank()) return OpenResult.NotAccessible
        val uri = runCatching { Uri.parse(uriString) }.getOrNull()
            ?: return OpenResult.NotAccessible

        val isAccessible = runCatching {
            context.contentResolver.openInputStream(uri)?.use { }
            true
        }.getOrDefault(false)
        if (!isAccessible) return OpenResult.NotAccessible

        val mimeType = context.contentResolver.getType(uri) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return try {
            context.startActivity(intent)
            OpenResult.Opened
        } catch (_: ActivityNotFoundException) {
            OpenResult.NoViewerApp
        } catch (_: SecurityException) {
            OpenResult.NotAccessible
        }
    }
}
