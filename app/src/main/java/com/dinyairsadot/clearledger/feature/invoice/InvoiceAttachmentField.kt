package com.dinyairsadot.clearledger.feature.invoice

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dinyairsadot.clearledger.R
import com.dinyairsadot.clearledger.core.util.AttachmentUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** SAF-allowed MIME types: one image or PDF attachment per invoice. */
private val ATTACHMENT_MIME_TYPES = arrayOf("image/*", "application/pdf")

/**
 * Resolves a display-friendly filename for [attachmentUri] off the main thread, re-querying
 * whenever the Uri changes. Returns null while resolving or if the name can't be determined.
 */
@Composable
fun rememberAttachmentDisplayName(attachmentUri: String?): String? {
    val context = LocalContext.current
    var displayName by remember(attachmentUri) { mutableStateOf<String?>(null) }
    LaunchedEffect(attachmentUri) {
        displayName = if (attachmentUri.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) { AttachmentUtil.queryDisplayName(context, attachmentUri) }
        }
    }
    return displayName
}

/**
 * Attach/replace/remove control for the Add/Edit invoice forms. Only picks the file and
 * reports the resulting Uri or removal to the caller — the caller owns the actual form state
 * (copying the picked document into app-private storage, and folding the result into its
 * existing unsaved-changes snapshot).
 *
 * [displayName] is caller-resolved so this composable doesn't need to know whether the current
 * attachment is a managed copy (display name already known, no lookup needed) or a legacy,
 * not-yet-migrated external Uri (looked up on demand via [rememberAttachmentDisplayName]).
 */
@Composable
fun InvoiceAttachmentField(
    hasAttachment: Boolean,
    displayName: String?,
    onAttach: (Uri) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onAttach(uri)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (!hasAttachment) {
            Text(
                text = stringResource(R.string.attachment_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.padding(top = 4.dp))
            OutlinedButton(onClick = { pickerLauncher.launch(ATTACHMENT_MIME_TYPES) }) {
                Icon(
                    imageVector = Icons.Filled.AttachFile,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.attachment_attach_action))
            }
        } else {
            Text(
                text = displayName ?: stringResource(R.string.attachment_unknown_filename),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.padding(top = 4.dp))
            Row {
                OutlinedButton(onClick = { pickerLauncher.launch(ATTACHMENT_MIME_TYPES) }) {
                    Text(stringResource(R.string.attachment_replace_action))
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.attachment_remove_action))
                }
            }
        }
    }
}
