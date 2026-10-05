package com.pratul.mmplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization

/**
 * Asks for a name ("Create New Folder", "New playlist", "Rename"). [validate] returns an error
 * message for the current text, or null when it is acceptable; [error] shows a failure reported
 * after confirming (e.g. "A playlist with this name already exists").
 */
@Composable
fun NameDialog(
    title: String,
    label: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    validate: (String) -> String? = { if (it.isBlank()) "Enter a name" else null },
    error: String? = null,
    content: @Composable () -> Unit = {},
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var touched by remember { mutableStateOf(false) }
    val problem = validate(text)
    val shownError = error ?: problem.takeIf { touched }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        touched = true
                    },
                    label = { Text(label) },
                    singleLine = true,
                    isError = shownError != null,
                    supportingText = shownError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (problem == null) onConfirm(text.trim()) else touched = true }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                content()
            }
        },
        confirmButton = {
            TextButton(onClick = { if (problem == null) onConfirm(text.trim()) else touched = true }, enabled = text.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
