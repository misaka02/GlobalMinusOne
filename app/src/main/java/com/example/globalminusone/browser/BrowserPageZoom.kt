package com.example.globalminusone.browser

import android.webkit.WebView
import kotlin.math.roundToInt

/** Page scale, independent of WebSettings.textZoom and the display's pixel density. */
internal object BrowserPageZoom {
    fun percent(scale: Float, density: Float): Int =
        if (scale.isFinite() && density.isFinite() && scale > 0 && density > 0)
            (scale / density * 100).roundToInt().coerceAtLeast(1) else 100

    fun factor(current: Int, requested: Int): Float {
        require(current > 0)
        return (requested.coerceIn(25, 500).toFloat() / current).coerceIn(0.01f, 100f)
    }

    // Keep each document's exact viewport attributes for reversible user-controlled zoom.
    // No page text, cookies, or form data crosses this boundary.
    fun viewportScript(force: Boolean): String = """
        (function(){
          var key='__gmoViewportZoomV1', saved=window[key];
          if (${force}) {
            if (!saved) {
              var nodes=Array.from(document.querySelectorAll('meta[name="viewport" i]'));
              saved={nodes:nodes.map(function(n){return [n,n.getAttribute('content')];}),added:null};
              if (!nodes.length) {var n=document.createElement('meta');n.name='viewport';
                (document.head||document.documentElement).appendChild(n);nodes=[n];saved.added=n;}
              window[key]=saved;
              nodes.forEach(function(n){var c=(n.getAttribute('content')||'').split(',').filter(function(v){
                return !/^\s*(user-scalable|minimum-scale|maximum-scale)\s*=/i.test(v);});
                c.push('user-scalable=yes','minimum-scale=0.25','maximum-scale=5');n.setAttribute('content',c.join(','));});
            }
          } else if(saved) {
            saved.nodes.forEach(function(p){if(p[1]===null)p[0].removeAttribute('content');else p[0].setAttribute('content',p[1]);});
            if(saved.added)saved.added.remove();delete window[key];
          }
          return true;
        })();
    """.trimIndent()
}

/** Uses WebView's native page zoom; UI reads the resulting scale, never a presumed target. */
internal fun WebView.applyPageZoom(force: Boolean, requested: Int?, valid: () -> Boolean, failed: (String) -> Unit): () -> Unit {
    var cancelled = false
    var pending: Runnable? = null
    fun active() = !cancelled && valid()
    evaluateJavascript(BrowserPageZoom.viewportScript(force)) { result ->
        if (!active()) return@evaluateJavascript
        if (result != "true") { failed("网页未允许调整缩放设置"); return@evaluateJavascript }
        if (requested != null) postVisualStateCallback(0, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                if (!active()) return
                val current = BrowserPageZoom.percent(scale, resources.displayMetrics.density)
                zoomBy(BrowserPageZoom.factor(current, requested))
                val check = Runnable {
                    if (active() && kotlin.math.abs(BrowserPageZoom.percent(scale, resources.displayMetrics.density) - requested.coerceIn(25, 500)) > 5)
                        failed("网页限制了页面比例，可在缩放工具中开启强制缩放")
                }
                pending = check
                postDelayed(check, 300)
            }
        })
    }
    return { cancelled = true; pending?.let { removeCallbacks(it) }; pending = null }
}
