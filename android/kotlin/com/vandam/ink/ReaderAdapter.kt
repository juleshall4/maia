package com.vandam.ink

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile

private const val TAG = "uniread"
private const val HOST = "book.uniread"
private const val BASE = "https://$HOST/"
/** The Titan keyboard's touch surface, as Android names its input device. */
private const val KEYBOARD_TOUCHPAD = "touchPad"
/** How far a keyboard swipe must travel to turn a page, in touchpad units (the pad is 1080 wide). */
private const val SWIPE_DISTANCE = 150f
/** One swipe can arrive as motion and as keys; turns closer together than this are the same swipe. */
private const val SWIPE_GAP_MS = 400L

/** Lists the EPUBs in the app's books folder. */
internal class BooksAdapter(private val activity: MainActivity) : NativeAdapter {
    override fun execute(requestId: Long, operation: String, payload: String, complete: NativeResultHandler) {
        if (operation != "scan") {
            complete(NativeResult.Failure(NativeErrorKind.PROTOCOL, "Unknown books operation $operation", false))
            return
        }
        Thread {
            val folder = booksFolder(activity)
            val books = scanBooks(folder)
            Log.i(TAG, "found ${books.length()} books in $folder")
            activity.runOnUiThread { complete(NativeResult.Success(books.toString())) }
        }.start()
    }

    override fun cancel(requestId: Long) = Unit
}

/** Books live in Android/data/dev.uniread/files/books, where adb and calibre can put them. */
internal fun booksFolder(activity: MainActivity) = File(activity.getExternalFilesDir(null), "books").apply { mkdirs() }

/**
 * Shows a book full screen in a WebView above the Ink view, one file of the reading order at a time.
 * Each file is laid out in CSS columns one screen wide, so a page turn moves one column across.
 */
internal class ReaderAdapter(private val activity: MainActivity) : NativeAdapter {
    private var webView: WebView? = null
    /** The WebView with the footer drawn natively over it, so it stays sharp. */
    private var page: FrameLayout? = null
    private var timeLeft: TextView? = null
    private var zip: ZipFile? = null
    private var book: Epub? = null
    private var windowCallback: Window.Callback? = null
    private var options = JSONObject()
    private var spine = 0
    private var fraction = 0.0
    private var finished = false
    /** Bumped by each load, so calls from a page that is being replaced are ignored. */
    private var generation = 0
    /** Where to start in the file being loaded: a fraction, an anchor, or "end". */
    private var start: Any = 0.0
    private val backCallback = OnBackInvokedCallback { close() }

    override fun execute(requestId: Long, operation: String, payload: String, complete: NativeResultHandler) {
        activity.runOnUiThread {
            try {
                when (operation) {
                    "open" -> open(JSONObject(payload))
                    "close" -> close()
                    else -> {
                        complete(NativeResult.Failure(NativeErrorKind.PROTOCOL, "Unknown reader operation $operation", false))
                        return@runOnUiThread
                    }
                }
                complete(NativeResult.Success("null"))
            } catch (error: Exception) {
                Log.w(TAG, "reader $operation failed", error)
                complete(NativeResult.Failure(NativeErrorKind.PROTOCOL, error.message ?: "The book could not be opened", false))
            }
        }
    }

    override fun cancel(requestId: Long) = Unit

    private fun open(request: JSONObject) {
        close()
        val file = File(request.getString("file"))
        val archive = ZipFile(file)
        val epub = try {
            Epub.read(archive, file.nameWithoutExtension)
        } catch (error: Exception) {
            archive.close()
            throw error
        }
        zip = archive
        book = epub
        options = request
        attach()
        val view = createWebView()
        webView = view
        val anchor = request.optString("anchor").ifEmpty { null }
        load(request.optInt("spine", 0).coerceIn(0, epub.spine.lastIndex), anchor ?: request.optDouble("fraction", 0.0))
        view.requestFocus()
    }

    private fun load(index: Int, from: Any) {
        val view = webView ?: return
        val epub = book ?: return
        spine = index
        fraction = (from as? Double) ?: 0.0
        finished = false
        generation++
        start = from
        Log.i(TAG, "load $index ${epub.spine[index].path} from $from")
        view.loadUrl(BASE + epub.spine[index].path)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createWebView(): WebView {
        val view = WebView(activity)
        view.setBackgroundColor(Color.BLACK)
        view.isVerticalScrollBarEnabled = false
        view.isHorizontalScrollBarEnabled = false
        view.overScrollMode = WebView.OVER_SCROLL_NEVER
        view.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            textZoom = 100
            useWideViewPort = false
            loadWithOverviewMode = false
            setSupportZoom(false)
        }
        view.addJavascriptInterface(ReaderBridge(this), "UnireadReader")
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host != HOST) return true
                val path = url.path.orEmpty().removePrefix("/")
                val index = book?.spineIndex(path) ?: -1
                if (index >= 0) activity.runOnUiThread { load(index, url.fragment ?: 0.0) }
                return true
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.url.host != HOST) return empty(404)
                return serve(request.url.path.orEmpty().removePrefix("/"))
            }
        }
        val container = FrameLayout(activity)
        container.setBackgroundColor(Color.BLACK)
        container.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        container.addView(createFooter())
        page = container
        activity.addContentView(container, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return view
    }

    /** Book title on the left and time left in the chapter on the right, as in the design: Inter 30/36, 92 in, 64 up. */
    private fun createFooter(): LinearLayout {
        val inter = Typeface.createFromAsset(activity.assets, "reader/Inter-Regular.otf")
        fun label() = TextView(activity).apply {
            typeface = inter
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 30f)
            setLineHeight(TypedValue.COMPLEX_UNIT_PX, 36f)
            setTextColor(Color.rgb(0x7F, 0x7F, 0x84))
            includeFontPadding = false
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val title = label().apply { text = options.optString("title") }
        val left = label().apply { fontFeatureSettings = "tnum" }
        timeLeft = left
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 46 })
            addView(left, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                setMargins(92, 0, 92, 64)
            }
        }
    }

    /** Serves the book's files from the zip, and the reader's own script, styles and fonts. */
    private fun serve(path: String): WebResourceResponse {
        if (path.startsWith("__reader/")) {
            return when (val name = path.removePrefix("__reader/")) {
                "reader.js" -> response("text/javascript", READER_SCRIPT.toByteArray())
                "reader.css" -> response("text/css", readerStyles().toByteArray())
                else -> try {
                    response(mimeType(name), activity.assets.open("reader/$name").use { it.readBytes() })
                } catch (_: Exception) {
                    empty(404)
                }
            }
        }
        val archive = zip ?: return empty(404)
        val entry = archive.getEntry(path) ?: return empty(404)
        val bytes = archive.getInputStream(entry).use { it.readBytes() }
        val type = mimeType(path)
        if (type != "text/html") return response(type, bytes)
        val text = String(bytes, Charsets.UTF_8)
        // XHTML must be parsed as XML: as HTML, a self-closing <a id="x"/> would swallow the rest of the page.
        val xml = text.trimStart('﻿', ' ', '\n', '\r', '\t').startsWith("<?xml") || text.contains("http://www.w3.org/1999/xhtml")
        val injected = injectReader(text)
        return response(if (xml) "application/xhtml+xml" else "text/html", injected.toByteArray())
    }

    private fun injectReader(page: String): String {
        val tags = """<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/><link rel="stylesheet" type="text/css" href="/__reader/reader.css"/><script type="text/javascript" src="/__reader/reader.js"></script>"""
        val head = Regex("</head\\s*>", RegexOption.IGNORE_CASE).find(page)
        return if (head != null) page.replaceRange(head.range.first, head.range.first, tags) else page
    }

    /** The reading settings, in CSS pixels from the design's device pixels. */
    private fun readerStyles(): String {
        val density = activity.resources.displayMetrics.density
        fun px(value: Number) = "%.2fpx".format(java.util.Locale.ROOT, value.toFloat() / density)
        val settings = options.optJSONObject("settings") ?: JSONObject()
        val size = when (settings.optString("textSize")) { "small" -> 36; "large" -> 50; else -> 42 }
        val lineHeight = when (settings.optString("lineSpacing")) { "tight" -> 1.4; "loose" -> 1.8; else -> 1.57 }
        val margin = when (settings.optString("margins")) { "normal" -> 128; "wide" -> 168; else -> 92 }
        val font = if (settings.optString("font") == "inter") "\"Uniread Inter\", sans-serif" else "\"Uniread Literata\", serif"
        return READER_STYLES
            .replace("{font}", font)
            .replace("{size}", px(size))
            .replace("{lineHeight}", lineHeight.toString())
            .replace("{margin}", px(margin))
            .replace("{gap}", px(margin * 2))
            .replace("{top}", px(122))
            .replace("{bottom}", px(172))
    }

    // Called from ReaderBridge on the WebView's JavaScript thread.

    fun config(): String {
        val from = start
        return JSONObject()
            .put("generation", generation)
            .put("language", book?.language ?: JSONObject.NULL)
            .put("taps", options.optJSONObject("settings")?.optString("pageTurns") != "keys")
            .put("fraction", (from as? Double) ?: JSONObject.NULL)
            .put("anchor", (from as? String)?.takeIf { it != "end" } ?: JSONObject.NULL)
            .put("end", from == "end")
            .toString()
    }

    fun position(from: Int, page: Int, pages: Int, minutesLeft: Int) = activity.runOnUiThread {
        val epub = book ?: return@runOnUiThread
        if (from != generation) return@runOnUiThread
        timeLeft?.text = if (minutesLeft > 0) "$minutesLeft min left in chapter" else ""
        fraction = if (pages <= 1) 0.0 else page.toDouble() / pages
        finished = spine == epub.spine.lastIndex && page >= pages - 1
        emit("progress", if (finished) 1.0 else epub.progress(spine, fraction))
    }

    fun turn(from: Int, step: Int) = activity.runOnUiThread {
        val epub = book ?: return@runOnUiThread
        if (from != generation) return@runOnUiThread
        Log.i(TAG, "turn $step from $spine")
        val next = spine + step
        if (next in epub.spine.indices) load(next, if (step > 0) 0.0 else "end")
    }

    private fun attach() {
        val window = activity.window
        val callback = window.callback
        windowCallback = callback
        window.callback = object : Window.Callback by callback {
            override fun dispatchKeyEvent(event: KeyEvent) = onKey(event) || callback.dispatchKeyEvent(event)
            override fun dispatchGenericMotionEvent(event: MotionEvent) = onKeyboardTouch(event) || callback.dispatchGenericMotionEvent(event)
            override fun dispatchTouchEvent(event: MotionEvent) = onKeyboardTouch(event) || callback.dispatchTouchEvent(event)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, backCallback)
    }

    private var swipeStart: Pair<Float, Float>? = null
    private var lastSwipe = 0L

    /**
     * A sideways swipe on the keyboard turns the page: swipe left for the next page, right for the previous one,
     * as on the screen. Depending on the phone's Scroll assistant mode, the keyboard's touchpad reaches the app
     * as touchpad motion, as screen touches from its own device (which must not reach the page as taps), as
     * sideways scrolling, or as arrow keys alongside the motion. One swipe can arrive several ways, so all of
     * them share one page turn.
     */
    private fun onKeyboardTouch(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val sideways = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
            if (sideways == 0f) return false
            swipe(if (sideways > 0) "next" else "previous")
            return true
        }
        if (!isKeyboard(event)) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> swipeStart = event.x to event.y
            MotionEvent.ACTION_UP -> {
                val (x, y) = swipeStart ?: return true
                swipeStart = null
                val dx = event.x - x
                val dy = event.y - y
                if (kotlin.math.abs(dx) >= SWIPE_DISTANCE && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f) {
                    swipe(if (dx < 0) "next" else "previous")
                }
            }
            MotionEvent.ACTION_CANCEL -> swipeStart = null
        }
        return true
    }

    private fun swipe(command: String) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastSwipe < SWIPE_GAP_MS) return
        lastSwipe = now
        webView?.evaluateJavascript("window.uniread && window.uniread.$command()", null)
    }

    /** Arrow keys the firmware sends for a keyboard swipe, in the direction the finger moved. */
    private fun onSwipeKey(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> swipe("next")
            KeyEvent.KEYCODE_DPAD_RIGHT -> swipe("previous")
        }
        return true
    }

    private fun isKeyboard(event: MotionEvent) =
        event.isFromSource(InputDevice.SOURCE_TOUCHPAD) || event.device?.name == KEYBOARD_TOUCHPAD

    private fun onKey(event: KeyEvent): Boolean {
        if (event.device?.name == KEYBOARD_TOUCHPAD) return onSwipeKey(event)
        val command = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_ENTER -> "next"
            KeyEvent.KEYCODE_SPACE -> if (event.isShiftPressed) "previous" else "next"
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DEL -> "previous"
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> "close"
            // Swallow other keys so app shortcuts don't act on the screens under the reader.
            else -> return !event.isSystem
        }
        if (command == "close") {
            if (event.action == KeyEvent.ACTION_UP) close()
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) webView?.evaluateJavascript("window.uniread && window.uniread.$command()", null)
        return true
    }

    private fun emit(event: String, progress: Double) {
        activity.emit(
            JSONObject().put("type", "uniread.reader").put("event", event)
                .put("spine", spine).put("fraction", fraction).put("progress", progress).toString(),
        )
    }

    private fun close() {
        val view = webView ?: return
        val epub = book
        emit("closed", if (epub == null) 0.0 else if (finished) 1.0 else epub.progress(spine, fraction))
        webView = null
        timeLeft = null
        activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
        windowCallback?.let { activity.window.callback = it }
        windowCallback = null
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        page?.let { (it.parent as? ViewGroup)?.removeView(it) }
        page = null
        view.destroy()
        zip?.close()
        zip = null
        book = null
        Log.i(TAG, "closed at $spine $fraction")
    }

    private fun response(type: String, bytes: ByteArray) =
        WebResourceResponse(type, if (type.startsWith("text/") || type.endsWith("xml")) "utf-8" else null, ByteArrayInputStream(bytes))

    private fun empty(status: Int) =
        WebResourceResponse("text/plain", "utf-8", status, "Not found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}

/** The methods the reader page can call. They run on a WebView thread. */
internal class ReaderBridge(private val reader: ReaderAdapter) {
    @JavascriptInterface fun config() = reader.config()
    @JavascriptInterface fun position(generation: Int, page: Int, pages: Int, minutesLeft: Int) = reader.position(generation, page, pages, minutesLeft)
    @JavascriptInterface fun turn(generation: Int, step: Int) = reader.turn(generation, step)
}

private fun mimeType(path: String) = when (path.substringAfterLast('.').lowercase()) {
    "html", "htm", "xhtml" -> "text/html"
    "css" -> "text/css"
    "js" -> "text/javascript"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "svg" -> "image/svg+xml"
    "webp" -> "image/webp"
    "ttf" -> "font/ttf"
    "otf" -> "font/otf"
    "woff" -> "font/woff"
    "woff2" -> "font/woff2"
    else -> "application/octet-stream"
}
