package de.mybudgets.app.ui.transfers

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Shows a PIN/password input dialog and suspends until user confirms or cancels. */
suspend fun pinDialog(activity: Activity, prompt: String): String =
    suspendCancellableCoroutine { cont ->
        val input = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setMessage(prompt)
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    if (cont.isActive) cont.resume(input.text.toString())
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    if (cont.isActive) cont.resume("")
                }
                .setCancelable(false)
                .show()
        }
    }

/** Shows a TAN input dialog and suspends until user confirms or cancels. */
suspend fun tanDialog(activity: Activity, challenge: String): String =
    suspendCancellableCoroutine { cont ->
        val input = EditText(activity)
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setMessage(challenge)
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    if (cont.isActive) cont.resume(input.text.toString())
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    if (cont.isActive) cont.resume("")
                }
                .setCancelable(false)
                .show()
        }
    }
