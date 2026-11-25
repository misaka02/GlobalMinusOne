package com.example.globalminusone.screenassistant

import android.content.Context

/**
 * Local-only API key storage for Screen Assistant OCR & LLM translation.
 * Kept strictly isolated from `GlobalMinusOneState` so config sharing / exports never leak secrets.
 */
internal object ScreenAssistantSecretStore {
    private const val PREFS_NAME = "screen_assistant_secrets"
    private const val KEY_LLM_API = "llm_api_key"
    private const val KEY_VISION_API = "vision_llm_api_key"
    private const val KEY_CUSTOM_OCR_KEY = "custom_ocr_api_key"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLlmApiKey(context: Context): String = prefs(context).getString(KEY_LLM_API, "").orEmpty()
    fun setLlmApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_LLM_API, key.trim()).apply()
    }

    fun getVisionLlmApiKey(context: Context): String = prefs(context).getString(KEY_VISION_API, "").orEmpty()
    fun setVisionLlmApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_VISION_API, key.trim()).apply()
    }

    fun getCustomOcrApiKey(context: Context): String = prefs(context).getString(KEY_CUSTOM_OCR_KEY, "").orEmpty()
    fun setCustomOcrApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_CUSTOM_OCR_KEY, key.trim()).apply()
    }

    fun maskKey(raw: String): String {
        val clean = raw.trim()
        return when {
            clean.isEmpty() -> "未配置"
            clean.length <= 8 -> "已配置 (${clean.length}字符)"
            else -> "${clean.take(3)}••••${clean.takeLast(4)}"
        }
    }
}
