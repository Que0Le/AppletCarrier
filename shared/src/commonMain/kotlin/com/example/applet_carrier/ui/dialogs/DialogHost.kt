package com.example.applet_carrier.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.applet_carrier.api.Dialogs

/** Internal description of a pending dialog request. */
internal sealed interface DialogRequest {
    data class Confirm(
        val title: String,
        val message: String,
        val onResult: (Boolean) -> Unit,
    ) : DialogRequest

    data class TextInput(
        val title: String,
        val label: String,
        val initial: String,
        val onResult: (String?) -> Unit,
    ) : DialogRequest
}

/**
 * Concrete [Dialogs] implementation. Applets call the interface methods to request a
 * dialog; the shell calls [Render] once (in the main window) to actually show it.
 * Only one dialog shows at a time, which is sufficient for the current scope.
 */
class DialogHost : Dialogs {

    private var request by mutableStateOf<DialogRequest?>(null)

    override fun confirm(title: String, message: String, onResult: (Boolean) -> Unit) {
        request = DialogRequest.Confirm(title, message, onResult)
    }

    override fun textInput(title: String, label: String, initial: String, onResult: (String?) -> Unit) {
        request = DialogRequest.TextInput(title, label, initial, onResult)
    }

    private fun dismiss() {
        request = null
    }

    @Composable
    fun Render() {
        when (val current = request) {
            is DialogRequest.Confirm -> ConfirmDialog(
                request = current,
                onClose = { dismiss() },
            )
            is DialogRequest.TextInput -> TextInputDialog(
                request = current,
                onClose = { dismiss() },
            )
            null -> Unit
        }
    }
}
