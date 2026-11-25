package com.example.globalminusone.screenassistant

import org.junit.Assert.*
import org.junit.Test

class BreenoQuickSettingsUiPolicyTest {
    private val policy = BreenoQuickSettingsUiPolicy
    @Test fun refusesOtherAppsNotificationsAndInvisibleQs() {
        assertTrue(policy.isQsContainer("com.android.systemui", "com.android.systemui", "com.android.systemui:id/qs_pager", true))
        assertFalse(policy.isQsContainer("com.android.settings", "com.android.systemui", "com.android.systemui:id/qs_pager", true))
        assertFalse(policy.isQsContainer("com.android.systemui", "com.other", "com.android.systemui:id/qs_pager", true))
        assertFalse(policy.isQsContainer("com.android.systemui", "com.android.systemui", "com.android.systemui:id/notification_stack_scroller", true))
        assertFalse(policy.isQsContainer("com.android.systemui", "com.android.systemui", "com.android.systemui:id/qs_pager", false))
    }
    @Test fun exactRecognitionLabelsNeverMatchVoiceAssistantOrNotificationProse() {
        listOf("小布识屏", "屏幕识别", "Breeno Touch", "小布识屏，已关闭").forEach { assertTrue(policy.isRecognitionLabel(it)) }
        listOf("小布助手", "Breeno", "小布识屏设置", "请添加小布识屏", "小布识屏，点击通知查看", "扫一扫").forEach { assertFalse(policy.isRecognitionLabel(it)) }
    }
    @Test fun clickableAncestorIsBoundedAndCannotBePageContainer() {
        assertTrue(policy.canClick("com.android.systemui", true, 2, false, true, true, true))
        assertFalse(policy.canClick("com.android.systemui", false, 0, false, true, true, true))
        assertFalse(policy.canClick("com.android.systemui", true, 0, true, true, true, true))
        assertFalse(policy.canClick("com.android.systemui", true, 5, false, true, true, true))
        assertFalse(policy.canClick("com.android.systemui", true, 1, false, true, false, true))
    }
    @Test fun onlyConfirmedQsPagerCanReceiveSingleForwardAction() {
        assertTrue(policy.canPage("com.android.systemui:id/qs_pager", "androidx.viewpager.widget.ViewPager", true))
        assertTrue(policy.canPage("com.android.systemui:id/qs_pager", "com.android.systemui.qs.PagedTileLayout", true))
        assertTrue(policy.canPage("com.android.systemui:id/qs_pager", "android.view.ViewGroup", true))
        assertFalse(policy.canPage("com.android.systemui:id/notification_stack_scroller", "androidx.viewpager.widget.ViewPager", true))
        assertFalse(policy.canPage("com.android.systemui:id/qs_pager", "android.widget.ScrollView", true))
        assertFalse(policy.canPage("com.android.systemui:id/qs_pager", "androidx.viewpager.widget.ViewPager", false))
    }
}
