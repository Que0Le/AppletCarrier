package com.example.applet_carrier.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.applet_carrier.ui.theme.CarrierColors

/** Single-line text input. Returns the text on OK, or null on cancel/dismiss. */
@Composable
internal fun TextInputDialog(
    request: DialogRequest.TextInput,
    onClose: () -> Unit,
) {
    var value by remember { mutableStateOf(request.initial) }

    AlertDialog(
        onDismissRequest = {
            request.onResult(null)
            onClose()
        },
        containerColor = CarrierColors.Surface,
        titleContentColor = CarrierColors.TextPrimary,
        textContentColor = CarrierColors.TextMuted,
        title = { Text(request.title) },
        text = {
            Column {
                Text(request.label)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                request.onResult(value)
                onClose()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = {
                request.onResult(null)
                onClose()
            }) { Text("Cancel") }
        },
    )
}
