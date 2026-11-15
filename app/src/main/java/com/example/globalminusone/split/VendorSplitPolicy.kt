package com.example.globalminusone.split

// A matching method is not proof that the caller has the OEM permission or that pairing works.
internal const val VENDOR_SPLIT_CAPABLE = "GMO_VENDOR_SPLIT_V1:SIGNATURE_PRESENT"
internal const val VENDOR_SPLIT_ACCEPTED = "GMO_VENDOR_SPLIT_V1:ACCEPTED"

/** A window overlay must leave this exact external task resumed. No focus/launch repair is allowed. */
internal fun vendorSplitSourceAllowed(snapshot: SplitTaskSnapshot, taskId: Int, sourcePackage: String,
    targetPackage: String, homePackages: Set<String>): Boolean {
    if (homePackages.isEmpty() || !validSplitPackage(sourcePackage) || !validSplitPackage(targetPackage) ||
        sourcePackage == targetPackage || targetPackage == "com.example.globalminusone" ||
        sourcePackage in homePackages || targetPackage in homePackages || sourcePackage in setOf(
            "com.example.globalminusone", "com.android.systemui", "com.android.settings", "android",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller",
            "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.oplus.permissioncontroller", "com.oplus.packageinstaller", "com.coloros.safecenter")) return false
    val source = snapshot.origin(sourcePackage, 0) ?: return false
    return source.id == taskId && source.bounds?.valid == true && snapshot.leafTasks().none {
        it.id != source.id && it.displayId == 0 && it.visible && it.mode == "fullscreen" && it.type == "standard"
    }
}

/** Only this observed vendor status is acceptance. It is never proof of a composed split pair. */
internal fun vendorSplitRequestAccepted(returnCode: Int) = returnCode == 1

object SplitVendorRootCommand {
    @JvmStatic fun command(request: SplitSystemRequest, apkPath: String): String {
        require(apkPath.matches(Regex("""/data/app/[A-Za-z0-9_./=+@~-]+\.apk"""))) { "受限分屏入口APK路径无效" }
        require(apkPath.endsWith("/base.apk") && apkPath.split('/').none { it == "." || it == ".." }) { "受限分屏入口APK路径无效" }
        SplitSystemProtocol.decode(SplitSystemProtocol.op(request), SplitSystemProtocol.taskId(request),
            SplitSystemProtocol.component(request), SplitSystemProtocol.userId(request))
        val args = when (request) {
            SplitSystemRequest.VendorProbe -> "probe"
            is SplitSystemRequest.VendorEdge -> "edge ${request.sourceTaskId} '${request.sourcePackage}' '${request.targetComponent}'"
            else -> error("非厂商分屏请求")
        }
        return "CLASSPATH='$apkPath' /system/bin/timeout -k 1s 7s /system/bin/app_process /system/bin com.example.globalminusone.split.VendorSplitEntry $args"
    }
}
