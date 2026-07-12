package com.accessible.toolkit.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

class PermissionManager(private val activity: Activity) {

    companion object {
        val REQUIRED_PERMISSIONS = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

        fun hasOverlayPermission(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }

        fun hasAllPermissions(context: Context): Boolean {
            return REQUIRED_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }

        fun hasRecordPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    interface PermissionCallback {
        fun onAllPermissionsGranted()
        fun onPermissionDenied(permission: String)
        fun onOverlayPermissionDenied()
    }

    private var callback: PermissionCallback? = null
    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var overlayLauncher: ActivityResultLauncher<Intent>? = null

    fun setCallback(callback: PermissionCallback) {
        this.callback = callback
    }

    fun registerLaunchers(
        permissionLauncher: ActivityResultLauncher<Array<String>>,
        overlayLauncher: ActivityResultLauncher<Intent>
    ) {
        this.permissionLauncher = permissionLauncher
        this.overlayLauncher = overlayLauncher
    }

    fun checkAndRequestAllPermissions() {
        if (!hasOverlayPermission(activity)) {
            requestOverlayPermission()
            return
        }

        if (hasAllPermissions(activity)) {
            callback?.onAllPermissionsGranted()
            return
        }

        val deniedPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (deniedPermissions.isNotEmpty()) {
            permissionLauncher?.launch(deniedPermissions)
        } else {
            callback?.onAllPermissionsGranted()
        }
    }

    fun checkAndRequestRecordPermission(): Boolean {
        if (hasRecordPermission(activity)) {
            return true
        }

        if (!hasOverlayPermission(activity)) {
            requestOverlayPermission()
            return false
        }

        permissionLauncher?.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        return false
    }

    fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )
        overlayLauncher?.launch(intent)
    }

    fun handlePermissionResult(permissions: Map<String, Boolean>) {
        val denied = permissions.filter { !it.value }.keys
        if (denied.isEmpty()) {
            callback?.onAllPermissionsGranted()
        } else {
            callback?.onPermissionDenied(denied.first())
        }
    }

    fun handleOverlayResult() {
        if (hasOverlayPermission(activity)) {
            checkAndRequestAllPermissions()
        } else {
            callback?.onOverlayPermissionDenied()
        }
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        activity.startActivity(intent)
    }

    fun isAccessibilityServiceEnabled(serviceClass: Class<*>): Boolean {
        val serviceName = "${activity.packageName}/${serviceClass.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            activity.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(serviceName)
    }
}