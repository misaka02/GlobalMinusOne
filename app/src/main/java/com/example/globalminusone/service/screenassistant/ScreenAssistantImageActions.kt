package com.example.globalminusone.service.screenassistant

import com.example.globalminusone.screenassistant.ScreenAssistantWorkSession
import kotlinx.coroutines.Job

/** One action path shared by the floating capsule, gallery, and screenshot menu. */
internal class ScreenAssistantImageActions<Image, Share>(
    private val session: ScreenAssistantWorkSession,
    private val port: Port<Image, Share>
) {
    enum class Action { COPY, SAVE, SHARE }

    interface Port<Image, Share> {
        suspend fun copy(image: Image)
        suspend fun save(image: Image): Boolean
        suspend fun prepareShare(image: Image): Share?
        fun discardPreparedShare(prepared: Share) {}
        fun share(image: Image, prepared: Share)
        fun feedback(message: String)
    }

    fun dispatch(action: Action, image: Image, copyFeedback: String = "已复制图片"): Job? {
        if (!session.isActive) return null
        val label = when (action) {
            Action.COPY -> "复制图片"
            Action.SAVE -> "保存提图"
            Action.SHARE -> "分享提图"
        }
        return session.launch(label) {
            when (action) {
                Action.COPY -> {
                    port.copy(image)
                    if (session.isActive) port.feedback(copyFeedback)
                }
                Action.SAVE -> {
                    val saved = port.save(image)
                    if (session.isActive) port.feedback(if (saved) "已保存至相册" else "保存失败")
                }
                Action.SHARE -> {
                    val prepared = port.prepareShare(image)
                    if (prepared != null) {
                        if (session.isActive) port.share(image, prepared) else port.discardPreparedShare(prepared)
                    }
                }
            }
        }
    }
}
