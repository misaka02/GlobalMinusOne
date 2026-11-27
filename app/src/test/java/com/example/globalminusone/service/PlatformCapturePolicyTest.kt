package com.example.globalminusone.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.globalminusone.service.overlay.extractNotificationVerificationCode
import com.example.globalminusone.service.overlay.screenshotOrientationMatches

class PlatformCapturePolicyTest {
    @Test fun verificationCodeNeedsItsOwnAdjacentLabel() {
        assertEquals("123456", extractNotificationVerificationCode("验证码：123456，10分钟内有效"))
        assertEquals("654321", extractNotificationVerificationCode("订单号123456；验证码：654321"))
        assertEquals("987654", extractNotificationVerificationCode("987654 是您的验证码"))
        assertNull(extractNotificationVerificationCode("订单号123456，验证码稍后发送"))
        assertNull(extractNotificationVerificationCode("手机号13800138000，订单123456"))
        assertNull(extractNotificationVerificationCode("验证码：123456789"))
    }

    @Test fun orientationRejectsRotationAndInvalidDimensions() {
        assertTrue(screenshotOrientationMatches(1080, 2400, 900, 2000))
        assertFalse(screenshotOrientationMatches(1080, 2400, 2000, 900))
        assertFalse(screenshotOrientationMatches(0, 2400, 900, 2000))
    }
}
