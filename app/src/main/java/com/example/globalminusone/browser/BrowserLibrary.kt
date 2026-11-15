package com.example.globalminusone.browser

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/** This app's bookmarks/history only; never reads Chrome data or requests storage access. */
internal class BrowserLibrary(context: Context) {
    private val preferences = context.getSharedPreferences("independent_browser", Context.MODE_PRIVATE)
    val bookmarks = mutableStateListOf<BrowserLink>()
    val history = mutableStateListOf<BrowserLink>()
    init { reload() }
    fun reload() {
        val savedBookmarks = read("bookmarks")
        val savedHistory = read("history")
        bookmarks.clear(); bookmarks.addAll(savedBookmarks)
        history.clear(); history.addAll(savedHistory)
    }
    private fun read(key: String): List<BrowserLink> = runCatching {
        val array = JSONArray(preferences.getString(key, "[]"))
        (0 until minOf(array.length(), 100)).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("url")
            if (url != "about:blank" && resolveBrowserDestination(url) == url) BrowserLink(item.optString("title").take(200), url) else null
        }
    }.getOrDefault(emptyList())
    private fun save(key: String, records: List<BrowserLink>) {
        val array = JSONArray()
        records.forEach { array.put(JSONObject().put("title", it.title).put("url", it.url)) }
        preferences.edit().putString(key, array.toString()).apply()
    }
    fun visit(title: String, url: String) {
        val next = recordBrowserVisit(history, title, url)
        if (next == history) return
        history.clear(); history.addAll(next); save("history", history)
    }
    fun bookmark(title: String, url: String) {
        val next = recordBrowserVisit(bookmarks, title, url)
        bookmarks.clear(); bookmarks.addAll(next); save("bookmarks", bookmarks)
    }
    fun removeBookmark(url: String) { bookmarks.removeAll { it.url == url }; save("bookmarks", bookmarks) }
    fun clearHistory() { history.clear(); save("history", history) }
}
