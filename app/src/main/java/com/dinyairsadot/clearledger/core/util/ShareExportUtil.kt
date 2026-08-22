package com.dinyairsadot.clearledger.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Adds Android Share Sheet support on top of the existing export flow.
 *
 * Export content/format is unchanged: callers write the same bytes they already write to the
 * user-chosen Storage Access Framework destination into a staging file here, and this object
 * turns that staging file into a safe, temporary `content://` Uri for sharing. No `file://` Uri
 * is ever exposed, and no broad storage permission is required since the staging file lives in
 * the app's own cache directory (see res/xml/file_paths.xml).
 */
object ShareExportUtil {

    private const val EXPORT_CACHE_SUBDIR = "exports"
    private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

    /**
     * Returns the cache file to stage [filename] in for sharing, clearing any previously staged
     * export files first so the cache directory doesn't grow unbounded.
     */
    fun prepareCacheFile(context: Context, filename: String): File {
        val dir = File(context.cacheDir, EXPORT_CACHE_SUBDIR)
        if (!dir.exists()) {
            dir.mkdirs()
        } else {
            dir.listFiles()?.forEach { it.delete() }
        }
        return File(dir, filename)
    }

    /** Returns a safe, temporary `content://` Uri for [file] via the app's FileProvider. */
    fun shareUriFor(context: Context, file: File): Uri {
        val authority = context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX
        return FileProvider.getUriForFile(context, authority, file)
    }

    /**
     * Opens the standard Android Share Sheet for [uri], granting the receiving app temporary
     * read permission. Uses [Intent.ACTION_SEND] and [Intent.createChooser] only; no custom
     * sharing UI and no direct integration with any specific app (e.g. Gmail, Drive).
     */
    fun shareFile(context: Context, uri: Uri, mimeType: String, chooserTitle: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
    }
}
