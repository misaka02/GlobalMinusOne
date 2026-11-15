package com.example.globalminusone

import com.example.globalminusone.system.OemWindowLauncher
import org.junit.Assert.*
import org.junit.Test

class OemWindowPolicyTest {
    @Test fun oemGuidanceNeverClaimsThatPlainLaunchIsAWindowApi() {
        assertTrue(OemWindowLauncher.isColorOsFamily("OPPO"))
        assertTrue(OemWindowLauncher.isColorOsFamily("OnePlus"))
        assertFalse(OemWindowLauncher.isColorOsFamily("Google"))
        val small = OemWindowLauncher.guidance(true, true)
        assertTrue(small.contains("ColorOS 16"))
        assertTrue(small.contains("操作指引"))
        assertTrue(small.contains("尚不支持直接"))
        assertFalse(OemWindowLauncher.guidance(false, false).contains("ColorOS 16"))
    }
    @Test fun manufacturerGuidanceUsesConservativeSystemNames() {
        assertTrue(OemWindowLauncher.guidanceForManufacturer(true, "Redmi").contains("HyperOS"))
        assertTrue(OemWindowLauncher.guidanceForManufacturer(false, "iQOO").contains("OriginOS"))
        val huawei = OemWindowLauncher.guidanceForManufacturer(true, "HUAWEI")
        assertTrue(huawei.contains("安卓兼容版"))
        assertTrue(huawei.contains("不代表支持原生鸿蒙"))
    }

}
