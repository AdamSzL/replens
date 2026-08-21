package com.replens.feature.workout.ui.picker

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

internal val Context.isCameraGranted: Boolean
    get() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Null reads as false, which combined with a remembered denial means
 * *permanently denied* — so the caller must pass [Activity] rather than casting
 * a Context, or a failed cast quietly routes the user to Settings.
 */
internal val Activity?.canShowCameraRationale: Boolean
    get() = this?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true

/**
 * [Activity], not [Context]: `startActivity` elsewhere needs `NEW_TASK`, which
 * would put Settings in its own task and stop the return from resuming this
 * screen — which is how the granted permission is noticed.
 */
internal fun Activity.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    )
    startActivity(intent)
}
