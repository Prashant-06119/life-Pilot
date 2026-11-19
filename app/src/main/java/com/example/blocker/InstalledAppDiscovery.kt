package com.example.blocker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?
)

object PackageEligibilityFilter {

    private val protectedExactPackages = setOf(
        "android",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.emergency",
        "com.google.android.apps.safetyhub",
        "com.android.settings",
        "com.android.systemui",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.stk",
        "com.android.cellbroadcastreceiver",
        "com.google.android.cellbroadcastreceiver"
    )

    private val protectedKeywords = listOf(
        "dialer",
        "telecom",
        "emergency",
        "cellbroadcast",
        "permissioncontroller",
        "packageinstaller",
        "inputmethod"
    )

    fun isProtectedPackage(
        packageName: String,
        selfPackageName: String,
        dynamicProtectedPackages: Set<String> = emptySet()
    ): Boolean {
        val normalized = packageName.trim()
        if (normalized.isBlank()) return true
        if (normalized == selfPackageName) return true
        if (normalized in protectedExactPackages) return true
        if (normalized in dynamicProtectedPackages) return true
        val lower = normalized.lowercase()
        return protectedKeywords.any { lower.contains(it) }
    }

    fun filterEligiblePackageNames(
        candidatePackages: Collection<String>,
        selfPackageName: String,
        dynamicProtectedPackages: Set<String> = emptySet()
    ): List<String> {
        return candidatePackages
            .map { it.trim() }
            .filter { it.length in 3..160 }
            .distinct()
            .filterNot { isProtectedPackage(it, selfPackageName, dynamicProtectedPackages) }
    }
}

object InstalledAppDiscovery {

    fun discoverDynamicProtectedPackages(context: Context): Set<String> {
        val pm = context.packageManager
        val protected = mutableSetOf<String>()
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_CALL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        )
        intents.forEach { intent ->
            val resolved = queryActivitiesCompat(pm, intent)
            resolved.mapNotNullTo(protected) { it.activityInfo?.packageName }
        }
        return protected
    }

    fun loadEligibleLauncherApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val selfPkg = context.packageName
        val dynamicProtected = discoverDynamicProtectedPackages(context)

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = queryActivitiesCompat(pm, launcherIntent)
        val seenPackages = HashSet<String>(resolveInfos.size)
        val results = ArrayList<InstalledAppInfo>(resolveInfos.size)

        for (info in resolveInfos) {
            val activityInfo = info.activityInfo ?: continue
            val pkg = activityInfo.packageName ?: continue
            if (!seenPackages.add(pkg)) continue
            if (PackageEligibilityFilter.isProtectedPackage(pkg, selfPkg, dynamicProtected)) continue

            val label = try {
                info.loadLabel(pm).toString().trim().ifBlank { pkg }
            } catch (_: Exception) {
                pkg
            }
            val icon = try {
                info.loadIcon(pm)
            } catch (_: Exception) {
                null
            }
            results.add(
                InstalledAppInfo(
                    packageName = pkg,
                    appName = label,
                    icon = icon
                )
            )
        }

        return results.sortedBy { it.appName.lowercase() }
    }

    fun resolveAppMetadata(context: Context, packageName: String): InstalledAppInfo {
        val pm = context.packageManager
        return try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            val label = pm.getApplicationLabel(appInfo).toString().trim().ifBlank { packageName }
            val icon = pm.getApplicationIcon(appInfo)
            InstalledAppInfo(packageName = packageName, appName = label, icon = icon)
        } catch (_: Exception) {
            InstalledAppInfo(packageName = packageName, appName = packageName, icon = null)
        }
    }

    private fun queryActivitiesCompat(pm: PackageManager, intent: Intent): List<ResolveInfo> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
