package com.example.applet_carrier.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.example.applet_carrier.ui.theme.CarrierColors

/** Yes/No confirmation rendered with the dark theme. */
@Composable
internal fun ConfirmDialog(
    request: DialogRequest.Confirm,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            request.onResult(false)
            onClose()
        },
        containerColor = CarrierColors.Surface,
        titleContentColor = CarrierColors.TextPrimary,
        textContentColor = CarrierColors.TextMuted,
        title = { Text(request.title) },
        text = { Text(request.message) },
        confirmButton = {
            TextButton(onClick = {
                request.onResult(true)
                onClose()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = {
                request.onResult(false)
                onClose()
            }) { Text("Cancel") }
        },
    )
}
