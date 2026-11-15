package com.example.globalminusone.browser

import org.junit.Assert.*
import org.junit.Test

class EmbeddedExternalWindowPolicyTest {
    private fun window(index: Int = 0, pkg: String = "com.tencent.mobileqq", uid: Int = 10242,
                       display: Int = 0, type: String = "APPLICATION_OVERLAY", visible: Boolean = true,
                       surface: Boolean = true) = """
        Window #$index Window{abc u0 $pkg/floating}:
          mDisplayId=$display mSession=Session{123}
          mOwnerUid=$uid showForAllUsers=false package=$pkg appop=SYSTEM_ALERT_WINDOW
          mAttrs={(0,0)(50x50) ty=$type fmt=TRANSLUCENT}
          mHasSurface=$surface isReadyForDisplay()=$visible
          isVisible=$visible
    """.trimIndent() + "\n"

    private fun detected(dump: String) = EmbeddedExternalWindowPolicy.hasPrimaryOverlay(dump,
        "com.tencent.mobileqq", 10242, 7)

    @Test fun exactPackageUidVisiblePrimaryOverlayIsAttributedWithoutChangingIt() {
        assertTrue(detected(window()))
        assertTrue(detected(window(type = "2038")))
        assertTrue(detected(window(type = "SYSTEM_ALERT")))
    }

    @Test fun remoteDisplayActivityOtherPackageAndOtherUserAreNotExternalQqOverlay() {
        assertFalse(detected(window(display = 7)))
        assertFalse(detected(window(display = 3)))
        assertFalse(detected(window(type = "BASE_APPLICATION")))
        assertFalse(detected(window(pkg = "com.tencent.mobileqq.extra")))
        assertFalse(detected(window(uid = 110242)))
        assertFalse(detected(window(visible = false)))
        assertFalse(detected(window(surface = false)))
    }

    @Test fun partialRomOutputAndFactsFromDifferentWindowsDoNotInventAnAttribution() {
        assertFalse(detected(window().replace("isVisible=true", "visibility=unknown")))
        assertFalse(detected(window().replace("package=com.tencent.mobileqq ", "package=unknown ")))
        val sameUidOtherPackage = window(pkg = "other.app")
        val samePackageOtherUid = window(index = 1, uid = 9999)
        assertFalse(detected(sameUidOtherPackage + samePackageOtherUid))
        assertFalse(detected("Permission Denial: no window snapshot"))
        assertFalse(EmbeddedExternalWindowPolicy.hasPrimaryOverlay(window(), "com.tencent.mobileqq", 10242, 0))
    }
}
