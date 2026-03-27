package de.mybudgets.app.ui.transfers

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import de.mybudgets.app.util.AppLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "PinTanDialogs"

/** Shows a PIN/password input dialog and suspends until user confirms or cancels. */
suspend fun pinDialog(activity: Activity, prompt: String): String =
    suspendCancellableCoroutine { cont ->
        if (activity.isFinishing || activity.isDestroyed) {
            AppLogger.w(TAG, "pinDialog: Activity nicht verfügbar – Überweisung abgebrochen")
            if (cont.isActive) cont.resume("")
            return@suspendCancellableCoroutine
        }
        val input = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                AppLogger.w(TAG, "pinDialog: Activity nach runOnUiThread nicht mehr verfügbar")
                if (cont.isActive) cont.resume("")
                return@runOnUiThread
            }
            try {
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
            } catch (e: Exception) {
                AppLogger.e(TAG, "pinDialog: Dialog konnte nicht angezeigt werden: ${e.message}", e)
                if (cont.isActive) cont.resume("")
            }
        }
    }

/** Shows a TAN input dialog and suspends until user confirms or cancels. */
suspend fun tanDialog(activity: Activity, challenge: String): String =
    suspendCancellableCoroutine { cont ->
        if (activity.isFinishing || activity.isDestroyed) {
            AppLogger.w(TAG, "tanDialog: Activity nicht verfügbar – TAN-Eingabe abgebrochen")
            if (cont.isActive) cont.resume("")
            return@suspendCancellableCoroutine
        }
        val input = EditText(activity)
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                AppLogger.w(TAG, "tanDialog: Activity nach runOnUiThread nicht mehr verfügbar")
                if (cont.isActive) cont.resume("")
                return@runOnUiThread
            }
            try {
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
            } catch (e: Exception) {
                AppLogger.e(TAG, "tanDialog: Dialog konnte nicht angezeigt werden: ${e.message}", e)
                if (cont.isActive) cont.resume("")
            }
        }
    }
