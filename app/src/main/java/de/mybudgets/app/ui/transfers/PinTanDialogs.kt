package de.mybudgets.app.ui.transfers

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.mybudgets.app.R
import de.mybudgets.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
                    .setTitle(R.string.transfer_pin_dialog_title)
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
suspend fun decoupledConfirmDialog(activity: Activity, challenge: String, waitTimeSeconds: Int = 30): Unit =
    suspendCancellableCoroutine { cont ->
        AppLogger.i(TAG, "decoupledConfirmDialog: Warte ${waitTimeSeconds}s auf SecureGo-Freigabe...")
        activity.runOnUiThread {
            try {
                val dialog = MaterialAlertDialogBuilder(activity)
                    .setTitle("SecureGo Freigabe")
                    .setMessage("Bitte Transaktion in SecureGo-App bestätigen.\n\nWarte noch ${waitTimeSeconds}s...")
                    .setCancelable(false)
                    .setPositiveButton("Manuell schließen") { _, _ ->
                        if (cont.isActive) {
                            AppLogger.i(TAG, "decoupledConfirmDialog: Manuell geschlossen")
                            cont.resume(Unit)
                        }
                    }
                    .create()
                
                dialog.show()
                
                // Live countdown with message updates
                CoroutineScope(Dispatchers.Main).launch {
                    var remaining = waitTimeSeconds
                    while (remaining > 0 && dialog.isShowing) {
                        delay(1000L)
                        remaining--
                        activity.runOnUiThread {
                            if (dialog.isShowing) {
                                dialog.setMessage("Bitte Transaktion in SecureGo-App bestätigen.\n\nWarte noch ${remaining}s...")
                            }
                        }
                    }
                    
                    activity.runOnUiThread {
                        if (dialog.isShowing) {
                            dialog.dismiss()
                            AppLogger.i(TAG, "decoupledConfirmDialog: Auto-closed nach ${waitTimeSeconds}s")
                        }
                    }
                    if (cont.isActive) {
                        AppLogger.i(TAG, "decoupledConfirmDialog: Resuming nach ${waitTimeSeconds}s delay")
                        cont.resume(Unit)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "decoupledConfirmDialog: Dialog-Fehler: ${e.message}", e)
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }
