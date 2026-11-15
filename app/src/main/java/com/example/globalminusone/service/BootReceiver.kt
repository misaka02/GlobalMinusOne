package com.example.globalminusone.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.example.globalminusone.data.MinusOneRepository

/**
 * 监听系统开机（BOOT_COMPLETED）与应用更新（MY_PACKAGE_REPLACED）广播，
 * 在已授予悬浮窗权限且开启了自动启动的情况下自动恢复全局负一屏常驻服务。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val repo = MinusOneRepository.getInstance(context)
            val triggerCfg = repo.state.value.triggerConfig
            if (triggerCfg.serviceEnabled && triggerCfg.autoStartOnBoot && Settings.canDrawOverlays(context)) {
                Log.d("BootReceiver", "Auto-starting OverlayService on $action")
                OverlayService.start(context)
            }
        }
    }
}
