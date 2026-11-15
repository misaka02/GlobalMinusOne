package com.example.globalminusone.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.globalminusone.MainActivity
import com.example.globalminusone.R

/**
 * 下拉通知栏/控制中心快捷磁贴服务。
 * 支持一键呼出/收起全局负一屏，若未授予悬浮窗权限则自动引导至配置主界面。
 */
class MinusOneTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = if (Settings.canDrawOverlays(this)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (Settings.canDrawOverlays(this)) {
            OverlayService.start(this, OverlayService.ACTION_TOGGLE_PANEL)
        } else {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                startActivityAndCollapseBefore34(intent)
            }
        }
    }

    // PendingIntent overload exists only since 34. The Intent overload remains
    // the platform contract on 26..33; the dedicated lint rule ignores SDK guards.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseBefore34(intent: Intent) {
        check(Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        startActivityAndCollapse(intent)
    }
}
