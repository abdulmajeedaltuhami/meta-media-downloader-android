package com.metamedia.downloader.util

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.metamedia.downloader.R

/** عرض رسالة قصيرة غير حرجة. */
fun Fragment.snack(message: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
    val bar = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
    if (actionLabel != null && action != null) {
        bar.setAction(actionLabel) { action() }
    }
    bar.show()
}

/** حوار خطأ يتطلب انتباه المستخدم. */
fun Context.showAlert(title: String, message: String) {
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(R.string.ok, null)
        .show()
}

fun View.visible() { visibility = View.VISIBLE }
fun View.gone() { visibility = View.GONE }

fun Context.color(id: Int) = ContextCompat.getColor(this, id)
