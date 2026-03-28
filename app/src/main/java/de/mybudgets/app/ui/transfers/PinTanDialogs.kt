package de.mybudgets.app.ui.transfers

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import de.mybudgets.app.R
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

/**
 * Shows a confirmation dialog for decoupled TAN methods (BBBank Secure Go / BestSign / pushTAN).
 * Tells the user to approve the action in their Secure Go (or other banking) app and suspends
 * until they tap OK. No TAN input is required — the bank confirms via the app.
 */
suspend fun decoupledConfirmDialog(activity: Activity, challenge: String): Unit =
    suspendCancellableCoroutine { cont ->
        if (activity.isFinishing || activity.isDestroyed) {
            AppLogger.w(TAG, "decoupledConfirmDialog: Activity nicht verfügbar")
            if (cont.isActive) cont.resume(Unit)
            return@suspendCancellableCoroutine
        }
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                AppLogger.w(TAG, "decoupledConfirmDialog: Activity nach runOnUiThread nicht mehr verfügbar")
                if (cont.isActive) cont.resume(Unit)
                return@runOnUiThread
            }
            try {
                val message = if (challenge.isNotBlank()) challenge
                    else activity.getString(R.string.decoupled_confirm_message)
                AlertDialog.Builder(activity)
                    .setTitle(R.string.decoupled_confirm_title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        if (cont.isActive) cont.resume(Unit)
                    }
                    .setCancelable(false)
                    .show()
            } catch (e: Exception) {
                AppLogger.e(TAG, "decoupledConfirmDialog: Dialog konnte nicht angezeigt werden: ${e.message}", e)
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }
