package com.alhaq.amnishield.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Shared accessibility node search and traversal utilities.
 */
object AccessibilityUtils {

    /**
     * Safely finds an [AccessibilityNodeInfo] by view ID from [node], recycling all candidate matches
     * except the returned first match.
     * Note: Respects the Node Lifecycle Invariant - NEVER recycles the parent [node].
     */
    fun findElementById(node: AccessibilityNodeInfo?, id: String?): AccessibilityNodeInfo? {
        if (node == null || id.isNullOrEmpty()) return null
        return try {
            val matches = node.findAccessibilityNodeInfosByViewId(id)
            if (matches.isNullOrEmpty()) {
                null
            } else {
                for (i in 1 until matches.size) {
                    @Suppress("DEPRECATION")
                    matches[i].recycle()
                }
                matches[0]
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves the default home launcher package name from [PackageManager].
     */
    fun getDefaultLauncherPackageName(packageManager: PackageManager): String? {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }
    }
}
