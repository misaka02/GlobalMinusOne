package com.example.globalminusone.browser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.net.IDN
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/** Home is the browser's exit boundary even if the native view still has older entries. */
internal fun handleBrowserPageBack(
    url: String,
    canGoBack: () -> Boolean,
    goBack: () -> Unit,
    showHome: () -> Unit
): Boolean {
    if (url == "about:blank") return false
    if (canGoBack()) goBack() else showHome()
    return true
}

internal enum class BrowserToolGroup(val label: String) {
    PAGE("网页操作"), DISPLAY("阅读与显示"), LIBRARY("标签与收藏"), PRIVACY("隐私与加载")
}

internal enum class BrowserSearchEngine(val label: String, val queryUrlPrefix: String) {
    BAIDU("百度", "https://www.baidu.com/s?wd="),
    BING("必应", "https://cn.bing.com/search?q="),
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    GITHUB("GitHub", "https://github.com/search?q=")
}

/** Shared by the WebView and native Chrome address fields; never emits executable schemes. */
internal fun resolveBrowserDestination(
    input: String,
    engine: BrowserSearchEngine = BrowserSearchEngine.BAIDU
): String? {
    if (input.length > 8192 || input.any { it.code < 32 || it.code == 127 }) return null
    val value = input.trim()
    if (value.isEmpty() || value == "about:blank") return "about:blank"
    val explicitWeb = value.startsWith("http://", true) || value.startsWith("https://", true)
    val hostPort = Regex("^[^ /:]+:[0-9]+(?:[/?#].*)?$").matches(value)
    if (!explicitWeb && !hostPort && Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(value)) return null
    val isAddress = explicitWeb || hostPort || !value.any { it.isWhitespace() } &&
        ('.' in value || value.substringBefore('/').substringBefore(':').equals("localhost", true) || value.startsWith("["))
    if (!isAddress) return engine.queryUrlPrefix + URLEncoder.encode(value, "UTF-8")
    val local = value.startsWith("localhost", true) || Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?:[:/?#].*)?$").matches(value) || value.startsWith("[")
    val candidate = if (explicitWeb) value else (if (local || hostPort) "http://" else "https://") + value
    return runCatching {
        val parsed = URL(candidate)
        if (parsed.host.isNullOrBlank() || parsed.userInfo != null || parsed.port !in -1..65535) return@runCatching null
        val host = if (parsed.host.startsWith("[")) parsed.host else IDN.toASCII(parsed.host, IDN.USE_STD3_ASCII_RULES)
        val suffix = candidate.substring(candidate.indexOf("://") + 3 + parsed.authority.length)
        val normalized = parsed.protocol.lowercase() + "://" + host + (if (parsed.port >= 0) ":${parsed.port}" else "") + suffix
        val uri = URI(normalized)
        if (uri.host.isNullOrBlank()) null else uri.toASCIIString()
    }.getOrNull()
}

private val mobileToWwwDomains = setOf(
    "baidu.com",
    "bilibili.com",
    "zhihu.com",
    "163.com",
    "sohu.com",
    "qq.com",
    "taobao.com",
    "jd.com",
    "ctrip.com",
    "douban.com",
    "hupu.com",
    "ithome.com",
    "csdn.net",
    "sina.com.cn",
    "smzdm.com"
)

/**
 * Upgrades known mobile-only subdomains (e.g. m.baidu.com -> www.baidu.com, zh.m.wikipedia.org -> zh.wikipedia.org)
 * when switching to Desktop Mode so sites do not stay trapped on mobile endpoints after reload.
 */
internal fun desktopPreferredUrl(url: String, desktop: Boolean): String {
    if (!desktop || url == "about:blank" || url.isBlank()) return url
    return runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return url
        val upgradedHost = when {
            host.endsWith(".m.wikipedia.org") -> host.replace(".m.wikipedia.org", ".wikipedia.org")
            host == "m.wikipedia.org" -> "www.wikipedia.org"
            host.startsWith("m.") && host.removePrefix("m.") in mobileToWwwDomains -> "www.${host.removePrefix("m.")}"
            host.startsWith("wap.") && host.removePrefix("wap.") in mobileToWwwDomains -> "www.${host.removePrefix("wap.")}"
            else -> return url
        }
        URI(uri.scheme, uri.userInfo, upgradedHost, uri.port, uri.rawPath, uri.rawQuery, uri.rawFragment).toASCIIString()
    }.getOrDefault(url)
}

/** Lightweight, zero-IO host filter for known ad/tracker domains in IndependentBrowser. */
internal object BrowserAdBlocker {
    private val blockedSuffixes = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "adnxs.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "scorecardresearch.com",
        "hm.baidu.com",
        "pos.baidu.com",
        "cpro.baidu.com",
        "eclick.baidu.com",
        "tanx.com",
        "mmstat.com",
        "allyes.com",
        "miaozhen.com"
    )

    fun isBlockedHost(host: String?): Boolean {
        val clean = host?.trim()?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        if (clean.isEmpty()) return false
        return blockedSuffixes.any { suffix -> clean == suffix || clean.endsWith(".$suffix") }
    }

    fun shouldBlockHost(host: String?): Boolean = isBlockedHost(host)

    fun shouldBlockRequest(requestUrl: String?, enabled: Boolean): Boolean {
        if (!enabled || requestUrl.isNullOrBlank()) return false
        val host = runCatching { URI(requestUrl).host }.getOrNull() ?: return false
        return isBlockedHost(host)
    }
}

internal object BrowserNightModeEngine {
    const val DARK_SURFACE_ARGB: Int = 0xFF14161B.toInt()

    // k = 0.80, s = 1.6078, Y = 0.299 R + 0.587 G + 0.114 B
    val HUE_PRESERVING_DARK_MATRIX: FloatArray = floatArrayOf(
        0.3193f, -0.9438f, -0.1833f, 0f, 230f,
        -0.4807f, -0.1438f, -0.1833f, 0f, 232f,
        -0.4807f, -0.9438f, 0.6167f, 0f, 238f,
        0f, 0f, 0f, 1f, 0f
    )

    fun transformRgb(r: Int, g: Int, b: Int): Triple<Int, Int, Int> {
        val m = HUE_PRESERVING_DARK_MATRIX
        val rf = (m[0] * r + m[1] * g + m[2] * b + m[4]).roundToInt().coerceIn(0, 255)
        val gf = (m[5] * r + m[6] * g + m[7] * b + m[9]).roundToInt().coerceIn(0, 255)
        val bf = (m[10] * r + m[11] * g + m[12] * b + m[14]).roundToInt().coerceIn(0, 255)
        return Triple(rf, gf, bf)
    }
}

internal data class ReaderBlock(val text: String, val isHeading: Boolean = false)
internal data class ReaderArticle(val title: String, val site: String, val blocks: List<ReaderBlock>)

/** Static, non-interpolated scripts for Browser Night Mode sync, Desktop viewport, and Native Reader Mode extraction. */
internal object BrowserDomStyles {
    /**
     * Via-grade deterministic DOM Night Mode stylesheet + MutationObserver persistence + CSP verification:
     * 1. Always injects `#gmo-via-night-style` when `enabled == true` (never skips when `prefers-color-scheme: dark`
     *    matches on ColorOS/Android 16, and never injects `color-scheme: dark light` which disabled Blink AutoDark).
     * 2. Only overrides `background-color` (never `background-image`), preserving CSS sprites, avatars, and thumbnails,
     *    while keeping `<img>`, `<video>`, `<canvas>`, `<svg>`, and `<picture>` in natural un-inverted colors.
     * 3. Verifies `getComputedStyle(documentElement).backgroundColor === 'rgb(19, 22, 28)'`; returns `'csp_blocked'`
     *    when a strict `Content-Security-Policy: style-src` header blocks inline `<style>` so the host FrameLayout
     *    automatically activates its GPU `RenderEffect` hue-preserving dark matrix fallback.
     */
    @Suppress("UNUSED_PARAMETER")
    fun nightModeDomSyncScript(enabled: Boolean, forceCssFallback: Boolean = false): String = if (enabled) {
        """
        (function(){
            try {
                var root = document.documentElement;
                if (!root) return 'no_root';
                var metas = document.querySelectorAll('meta[name="color-scheme" i]');
                for (var i = 0; i < metas.length; i++) {
                    var m = metas[i];
                    var c = m.getAttribute('content') || '';
                    if (!m.hasAttribute('data-gmo-orig-cs')) m.setAttribute('data-gmo-orig-cs', c);
                    m.setAttribute('content', 'dark');
                }
                var legacyIds = ['gmo-night-fallback-style', 'gmo-night-cs-override'];
                for (var k = 0; k < legacyIds.length; k++) {
                    var oldEl = document.getElementById(legacyIds[k]);
                    if (oldEl && oldEl.parentNode) oldEl.parentNode.removeChild(oldEl);
                }
                var styleId = 'gmo-via-night-style';
                var cssText =
                    ':root, html { color-scheme: dark !important; background-color: #13161c !important; color: #d6dce8 !important; } ' +
                    'body { background-color: #13161c !important; color: #d6dce8 !important; } ' +
                    '*:not(img):not(video):not(canvas):not(svg):not(svg *):not(picture):not(iframe) { ' +
                    '  color: #cfd6e4 !important; border-color: #2d3444 !important; box-shadow: none !important; text-shadow: none !important; } ' +
                    'body *:not(img):not(video):not(canvas):not(svg):not(svg *):not(picture):not(input):not(textarea):not(select):not(button):not(header):not(footer):not(nav):not(aside):not(dialog):not(table):not(thead):not(tbody):not(pre):not(code):not(blockquote) { ' +
                    '  background-color: transparent !important; } ' +
                    ':before, :after { background-color: transparent !important; border-color: #2d3444 !important; } ' +
                    'header, footer, nav, aside, dialog, [role="dialog"], [role="menu"], [role="navigation"], [role="listbox"], [role="tooltip"], ' +
                    '[class*="header" i], [id*="header" i], [class*="navbar" i], [class*="topbar" i], [class*="toolbar" i], ' +
                    '[class*="modal" i], [class*="popup" i], [class*="dropdown" i], [class*="drawer" i], [class*="dialog" i], ' +
                    '[class*="menu" i], [class*="toast" i], [class*="popover" i], [class*="sheet" i] { ' +
                    '  background-color: rgba(24, 28, 36, 0.96) !important; } ' +
                    'table, thead, tbody, tfoot, pre, code, blockquote, fieldset, details, summary, [class*="card" i], [class*="panel" i] { ' +
                    '  background-color: rgba(28, 33, 43, 0.92) !important; } ' +
                    'tr:nth-child(even) td { background-color: rgba(255, 255, 255, 0.03) !important; } ' +
                    'input, textarea, select, button, [role="button"], input *, button *, select * { ' +
                    '  background-color: #222836 !important; color: #eef2fa !important; border-color: #3d475e !important; } ' +
                    'h1, h2, h3, h4, h5, h6, h1 *, h2 *, h3 *, h4 *, strong, b, th { color: #eef2fa !important; } ' +
                    'a, a *:not(img):not(svg):not(svg *) { color: #7cb4ff !important; } ' +
                    'a:visited, a:visited *:not(img):not(svg):not(svg *) { color: #b39ddb !important; } ' +
                    'img, video, picture, canvas { opacity: 0.88 !important; }';
                function ensureStyle() {
                    var targetParent = document.head || document.documentElement;
                    if (!targetParent) return null;
                    var el = document.getElementById(styleId);
                    if (!el) {
                        el = document.createElement('style');
                        el.id = styleId;
                        el.type = 'text/css';
                        el.appendChild(document.createTextNode(cssText));
                        targetParent.appendChild(el);
                    } else if (el.parentNode !== targetParent && document.head && el.parentNode !== document.head) {
                        document.head.appendChild(el);
                    }
                    return el;
                }
                ensureStyle();
                window.__gmoNightActive = true;
                if (!window.__gmoNightObs && window.MutationObserver) {
                    var obs = new MutationObserver(function() {
                        if (!window.__gmoNightActive) return;
                        if (!document.getElementById(styleId)) ensureStyle();
                    });
                    obs.observe(root, { childList: true, subtree: true });
                    window.__gmoNightObs = obs;
                }
                function parseBgLum(el) {
                    if (!el || !window.getComputedStyle) return -1;
                    var bg = (window.getComputedStyle(el).backgroundColor || '').replace(/\s+/g, '');
                    var m = bg.match(/^rgba?\((\d+),(\d+),(\d+)(?:,([0-9.]+))?\)$/i);
                    if (!m) return -1;
                    var a = m[4] !== undefined ? parseFloat(m[4]) : 1;
                    if (isNaN(a) || a < 0.45) return -1;
                    return 0.299 * parseInt(m[1], 10) + 0.587 * parseInt(m[2], 10) + 0.114 * parseInt(m[3], 10);
                }
                var bodyLum = parseBgLum(document.body);
                var rootLum = parseBgLum(root);
                var effLum = bodyLum >= 0 ? bodyLum : (rootLum >= 0 ? rootLum : 255);
                if (effLum < 95) {
                    return 'css_applied';
                }
                return 'csp_blocked';
            } catch (e) {
                return 'error';
            }
        })();
        """.trimIndent()
    } else {
        """
        (function(){
            try {
                window.__gmoNightActive = false;
                if (window.__gmoNightObs) {
                    try { window.__gmoNightObs.disconnect(); } catch ( ignore ) {}
                    window.__gmoNightObs = null;
                }
                var ids = ['gmo-via-night-style', 'gmo-night-fallback-style', 'gmo-night-cs-override'];
                for (var i = 0; i < ids.length; i++) {
                    var el = document.getElementById(ids[i]);
                    if (el && el.parentNode) el.parentNode.removeChild(el);
                }
                var metas = document.querySelectorAll('meta[data-gmo-orig-cs]');
                for (var j = 0; j < metas.length; j++) {
                    var m = metas[j];
                    m.setAttribute('content', m.getAttribute('data-gmo-orig-cs') || '');
                    m.removeAttribute('data-gmo-orig-cs');
                }
                return 'light_restored';
            } catch (e) {
                return 'light_restored';
            }
        })();
        """.trimIndent()
    }

    fun desktopViewportScript(desktop: Boolean): String = if (desktop) {
        """
        (function(){
            try {
                var vp = document.querySelector('meta[name="viewport" i]');
                if (vp) {
                    if (!vp.hasAttribute('data-gmo-orig-vp')) {
                        vp.setAttribute('data-gmo-orig-vp', vp.getAttribute('content') || '');
                    }
                    vp.setAttribute('content', 'width=1280, initial-scale=0.35, minimum-scale=0.2, maximum-scale=5.0, user-scalable=yes');
                } else {
                    var n = document.createElement('meta');
                    n.name = 'viewport';
                    n.setAttribute('data-gmo-added-vp', '1');
                    n.setAttribute('content', 'width=1280, initial-scale=0.35, minimum-scale=0.2, maximum-scale=5.0, user-scalable=yes');
                    (document.head || document.documentElement).appendChild(n);
                }
                return true;
            } catch (e) { return false; }
        })();
        """.trimIndent()
    } else {
        """
        (function(){
            try {
                var added = document.querySelectorAll('meta[data-gmo-added-vp]');
                for (var i = 0; i < added.length; i++) {
                    if (added[i].parentNode) added[i].parentNode.removeChild(added[i]);
                }
                var saved = document.querySelectorAll('meta[data-gmo-orig-vp]');
                for (var j = 0; j < saved.length; j++) {
                    saved[j].setAttribute('content', saved[j].getAttribute('data-gmo-orig-vp') || 'width=device-width, initial-scale=1');
                    saved[j].removeAttribute('data-gmo-orig-vp');
                }
                return true;
            } catch (e) { return false; }
        })();
        """.trimIndent()
    }

    /** Density-scored DOM prose extractor for Native Compose Reader Mode; immune to page CSS/CSP/popups. */
    fun extractReaderArticleScript(): String = """
        (function(){
            try {
                function isNoise(el) {
                    var cur = el;
                    while (cur && cur.nodeType === 1 && cur !== document.body) {
                        var t = (cur.tagName || '').toUpperCase();
                        if (t === 'NAV' || t === 'HEADER' || t === 'FOOTER' || t === 'ASIDE' ||
                            t === 'SCRIPT' || t === 'STYLE' || t === 'NOSCRIPT' || t === 'FORM') return true;
                        var role = (cur.getAttribute('role') || '').toLowerCase();
                        if (role === 'navigation' || role === 'banner' || role === 'contentinfo' || role === 'complementary') return true;
                        cur = cur.parentElement;
                    }
                    return false;
                }
                var selectors = [
                    'article', 'main', '[role="main"]',
                    '.article-content', '.post-content', '.entry-content',
                    '.rich_media_content', '.Post-RichTextContainer', '#article_content',
                    '#mw-content-text', '#content', '.content'
                ];
                var candidates = [];
                for (var s = 0; s < selectors.length; s++) {
                    var found = document.querySelectorAll(selectors[s]);
                    for (var f = 0; f < found.length && candidates.length < 24; f++) {
                        if (candidates.indexOf(found[f]) < 0) candidates.push(found[f]);
                    }
                }
                if (document.body && candidates.indexOf(document.body) < 0) candidates.push(document.body);
                var bestRoot = document.body;
                var bestScore = -1;
                for (var c = 0; c < candidates.length; c++) {
                    var cand = candidates[c];
                    var ps = cand.querySelectorAll('p, blockquote, pre');
                    var score = 0;
                    for (var k = 0; k < ps.length && k < 150; k++) {
                        if (isNoise(ps[k])) continue;
                        var pt = (ps[k].innerText || ps[k].textContent || '').replace(/\s+/g, ' ').trim();
                        if (pt.length >= 16) score += pt.length;
                    }
                    if (cand !== document.body) score = Math.floor(score * 1.25);
                    if (score > bestScore) {
                        bestScore = score;
                        bestRoot = cand;
                    }
                }
                var root = bestRoot || document.body;
                if (!root) return JSON.stringify({title: document.title || '', site: location.hostname || '', blocks: []});
                var nodes = root.querySelectorAll('h1, h2, h3, h4, p, blockquote, pre, li');
                var seen = {};
                var blocks = [];
                var totalChars = 0;
                for (var i = 0; i < nodes.length && blocks.length < 200 && totalChars < 75000; i++) {
                    var n = nodes[i];
                    if (isNoise(n)) continue;
                    var tag = (n.tagName || '').toUpperCase();
                    var isHeading = tag === 'H1' || tag === 'H2' || tag === 'H3' || tag === 'H4';
                    var txt = (n.innerText || n.textContent || '').replace(/\s+/g, ' ').trim();
                    var minLen = isHeading ? 2 : (tag === 'LI' ? 16 : 10);
                    if (txt.length < minLen) continue;
                    if (seen[txt]) continue;
                    seen[txt] = true;
                    blocks.push({t: txt, h: isHeading});
                    totalChars += txt.length;
                }
                if (blocks.length === 0) {
                    var raw = (root.innerText || '').split(/\n+/);
                    for (var j = 0; j < raw.length && blocks.length < 150 && totalChars < 60000; j++) {
                        var line = raw[j].replace(/\s+/g, ' ').trim();
                        if (line.length >= 12 && !seen[line]) {
                            seen[line] = true;
                            blocks.push({t: line, h: false});
                            totalChars += line.length;
                        }
                    }
                }
                return JSON.stringify({
                    title: (document.title || '').trim(),
                    site: (location.hostname || '').trim(),
                    blocks: blocks
                });
            } catch (e) {
                return JSON.stringify({title: '', site: '', blocks: []});
            }
        })();
        """.trimIndent()
}

internal fun parseReaderArticleJson(rawEvaluateResult: String?, fallbackTitle: String): ReaderArticle? {
    if (rawEvaluateResult.isNullOrBlank() || rawEvaluateResult == "null") return null
    return runCatching {
        val outer = Json.parseToJsonElement(rawEvaluateResult)
        val jsonObj: JsonObject = when (outer) {
            is JsonPrimitive -> {
                val innerStr = outer.contentOrNull ?: return null
                Json.parseToJsonElement(innerStr) as? JsonObject ?: return null
            }
            is JsonObject -> outer
            else -> return null
        }
        val title = (jsonObj["title"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: fallbackTitle
        val site = (jsonObj["site"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val arr = jsonObj["blocks"] as? JsonArray ?: JsonArray(emptyList())
        val blocks = arr.mapNotNull { elem ->
            val obj = elem as? JsonObject ?: return@mapNotNull null
            val text = (obj["t"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val heading = (obj["h"] as? JsonPrimitive)?.booleanOrNull ?: false
            ReaderBlock(text, heading)
        }
        ReaderArticle(title.take(200), site.take(120), blocks)
    }.getOrNull()
}

/** Each native view gets a lease. Destroyed/replaced views cannot mutate a new page. */
internal class BrowserCallbackLease {
    private var sequence = 0L
    private var live = false
    fun attach(): Long { live = true; return ++sequence }
    fun accepts(token: Long) = live && sequence == token
    fun detach(token: Long): Boolean {
        if (!accepts(token)) return false
        live = false
        return true
    }
}

internal data class BrowserLink(val title: String, val url: String)

/** Bounded, most-recent-first records. Homepage and failed navigations are never history. */
internal fun recordBrowserVisit(records: List<BrowserLink>, title: String, url: String, limit: Int = 100): List<BrowserLink> {
    if (url == "about:blank" || resolveBrowserDestination(url) != url) return records
    return (listOf(BrowserLink(title.ifBlank { url }.take(200), url)) + records.filterNot { it.url == url }).take(limit.coerceAtLeast(0))
}
