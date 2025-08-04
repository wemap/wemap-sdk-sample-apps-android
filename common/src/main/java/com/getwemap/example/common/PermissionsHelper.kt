package com.getwemap.example.common

import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

typealias PermissionCallback = (granted: List<String>, denied: List<String>) -> Unit

class PermissionHelper(
    private val fragment: Fragment,
    var defaultPermissions: List<String> = listOf()
) {
    private var callback: PermissionCallback? = null

    private val launcher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.filterValues { it }.keys.toList()
        val denied = result.filterValues { !it }.keys.toList()
        callback?.invoke(granted, denied)
        callback = null
    }

    fun request(permissions: List<String> = defaultPermissions, onResult: PermissionCallback) {
        val notGranted = permissions.filter {
            !isPermissionGranted(it)
        }

        if (notGranted.isEmpty()) {
            onResult(permissions, emptyList()) // already granted
        } else {
            callback = onResult
            launcher.launch(notGranted.toTypedArray())
        }
    }

    fun allGranted(permissions: List<String> = defaultPermissions): Boolean {
        return permissions.all {
            isPermissionGranted(it)
        }
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(fragment.requireContext(), permission) == PackageManager.PERMISSION_GRANTED
    }
}