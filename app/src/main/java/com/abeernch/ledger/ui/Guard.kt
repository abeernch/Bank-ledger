package com.abeernch.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Protects changes to savings locks. Provided by MainActivity.
 *
 *  require:   fingerprint/face, or the Ledger PIN. Used before releasing or reducing a lock.
 *  ensurePin: makes sure a Ledger PIN exists (asks to create one the first time).
 *  changePin: verify, then set a new PIN.
 */
class Guard(
    val require: (reason: String, onOk: () -> Unit) -> Unit,
    val ensurePin: (onOk: () -> Unit) -> Unit,
    val changePin: () -> Unit,
    val hasPin: () -> Boolean
)

val LocalGuard = staticCompositionLocalOf<Guard> { error("Guard not provided") }

private const val MAX_TRIES = 5
private const val WAIT_MS = 30_000L

/** Asks for the Ledger PIN. [check] returns true when the PIN is right. */
@Composable
fun PinDialog(
    title: String,
    reason: String,
    onDismiss: () -> Unit,
    onForgot: () -> Unit,
    check: (String) -> Boolean,
    onOk: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var tries by remember { mutableIntStateOf(0) }
    var blockedUntil by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (System.currentTimeMillis() < blockedUntil) {
            error = "Too many tries. Wait 30 seconds."
            return
        }
        if (check(pin)) {
            onOk()
        } else {
            tries++
            pin = ""
            if (tries >= MAX_TRIES) {
                tries = 0
                blockedUntil = System.currentTimeMillis() + WAIT_MS
                error = "Too many tries. Wait 30 seconds."
            } else {
                error = "Wrong PIN. ${MAX_TRIES - tries} tries left."
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> if (v.length <= 6 && v.all { it.isDigit() }) pin = v },
                    label = { Text("Ledger PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onForgot) { Text("Forgot PIN?") }
            }
        },
        confirmButton = { TextButton(onClick = { submit() }, enabled = pin.length >= 4) { Text("Unlock") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Creates a new Ledger PIN (4 to 6 digits, typed twice). */
@Composable
fun SetPinDialog(title: String, onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val mismatch = again.length >= 4 && again != pin

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This PIN protects your locked savings. You will need it, or your fingerprint, to release or reduce a lock.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    pin, { v -> if (v.length <= 6 && v.all { it.isDigit() }) pin = v },
                    label = { Text("New PIN (4 to 6 digits)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    again, { v -> if (v.length <= 6 && v.all { it.isDigit() }) again = v },
                    label = { Text("Type it again") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = mismatch,
                    supportingText = { if (mismatch) Text("PINs do not match") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(pin) }, enabled = pin.length >= 4 && pin == again) { Text("Save PIN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
