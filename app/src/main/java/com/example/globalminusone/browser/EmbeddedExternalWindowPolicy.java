package com.example.globalminusone.browser;

import java.util.regex.Pattern;

/** Read-only evidence of an app-owned overlay created on the primary display.
 * A task/SurfaceTexture clip cannot move this separately owned WindowToken. */
final class EmbeddedExternalWindowPolicy {
    private static final Pattern WINDOW = Pattern.compile("(?m)^\\s*Window #\\d+ ");
    private static final Pattern OVERLAY = Pattern.compile(
            "\\bty=(?:2038|2002|2003|2006|2007|2010|APPLICATION_OVERLAY|PHONE|SYSTEM_ALERT|SYSTEM_OVERLAY|PRIORITY_PHONE|SYSTEM_ERROR)\\b");
    private static final Pattern PRIMARY = Pattern.compile("\\bmDisplayId=0(?:\\s|$)");
    private static final Pattern VISIBLE = Pattern.compile("\\bisVisible=true(?:\\s|$)");
    private static final Pattern SURFACE = Pattern.compile("\\bmHasSurface=true(?:\\s|$)");

    static boolean hasPrimaryOverlay(String dump, String packageName, int ownerUid, int ownedDisplayId) {
        if (dump == null || packageName == null || ownerUid < 0 || ownedDisplayId <= 0) return false;
        Pattern owner = Pattern.compile("\\bmOwnerUid=" + ownerUid + "(?:\\s|$)");
        Pattern pkg = Pattern.compile("\\bpackage=" + Pattern.quote(packageName) + "(?:\\s|$)");
        for (String block : WINDOW.split(dump)) {
            if (!block.startsWith("Window{") || !owner.matcher(block).find() || !pkg.matcher(block).find()) continue;
            if (!PRIMARY.matcher(block).find()) continue;
            if (OVERLAY.matcher(block).find() && VISIBLE.matcher(block).find() && SURFACE.matcher(block).find()) return true;
        }
        return false;
    }
}
