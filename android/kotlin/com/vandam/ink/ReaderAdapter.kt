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
import android.view.View
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

internal const val TAG = "maia"
private const val HOST = "book.maia"
private const val BASE = "https://$HOST/"
/** The Titan keyboard's touch surface, as Android names its input device. */
private const val KEYBOARD_TOUCHPAD = "touchPad"
/** How far a keyboard swipe must travel to turn a page, in key widths across: the same as Atlas's emoji pages. */
private const val SWIPE_KEYS = .45f
/** The keyboard is ten keys across and four rows down. */
private const val KEYS_ACROSS = 10f
private const val KEY_ROWS = 4f
/** One swipe can arrive as motion and as keys; turns closer together than this are the same swipe. */
private const val SWIPE_GAP_MS = 400L
/** How long a page has to say it is laid out before it is shown anyway. */
private const val READY_TIMEOUT_MS = 4_000L

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

/** Books live in Android/data/dev.maia/files/books, where adb and calibre can put them. */
internal fun booksFolder(activity: MainActivity) = File(activity.getExternalFilesDir(null), "books").apply { mkdirs() }

/**
 * Shows a book full screen in WebViews above the Ink view, one file of the reading order in each.
 * Each file is laid out in CSS columns one screen wide, so a page turn moves one column across.
 *
 * Three WebViews are stacked: the file being read on top, and behind it the next file at its first page and
 * the previous file at its last page, laid out and painted. Turning past the end of a file brings the one
 * behind to the top, so a new chapter shows at once, and the WebView left over loads the file after it.
 */
internal class ReaderAdapter(private val activity: MainActivity) : NativeAdapter {
    /** One stacked WebView and the file it holds. */
    internal inner class Slot(val view: WebView) {
        /** Bumped by each load, so calls from a page that is being replaced are ignored. */
        @Volatile var generation = 0
        /** Where to start in the file being loaded: a fraction, an anchor, or "end". */
        @Volatile var start: Any = 0.0
        var spine = -1
        /** Laid out, shown at its starting page and painted. */
        var ready = false
        var page = 0
        var pages = 1
        var minutesLeft = 0

        val fraction get() = if (pages <= 1) 0.0 else page.toDouble() / pages

        /** Whether this slot holds file [index] at [from], or is loading it. */
        fun holds(index: Int, from: Any) = spine == index && when {
            !ready -> start == from
            from == 0.0 -> page == 0
            from == "end" -> page == pages - 1
            else -> false
        }
    }

    private val slots = mutableListOf<Slot>()
    /** The slot on top, which the reader sees. */
    private var current: Slot? = null
    /** A slot that is still loading and goes on top once it is painted. */
    private var pending: Slot? = null
    /** The WebViews with the footer drawn natively over them, so it stays sharp. */
    private var page: FrameLayout? = null
    private var footer: View? = null
    private var timeLeft: TextView? = null
    private var zip: ZipFile? = null
    private var book: Epub? = null
    private var windowCallback: Window.Callback? = null
    private var options = JSONObject()
    @Volatile private var styles = ""
    private var spine = 0
    private var fraction = 0.0
    private var finished = false
    private var generations = 0
    private val backCallback = OnBackInvokedCallback { close() }

    /** Holds the keyboard light off while reading, if Settings asks for that. */
    private var keyboardDark: KeyboardDark? = null

    override fun execute(requestId: Long, operation: String, payload: String, complete: NativeResultHandler) {
        fun fail(error: Exception) {
            Log.w(TAG, "reader $operation failed", error)
            complete(NativeResult.Failure(NativeErrorKind.PROTOCOL, error.message ?: "The book could not be opened", false))
        }
        when (operation) {
            // The book is read off the main thread, so the library stays smooth while it opens.
            "open" -> Thread {
                val request = JSONObject(payload)
                val file = File(request.getString("file"))
                val opened = try {
                    val archive = ZipFile(file)
                    try {
                        archive to Epub.read(archive, file.nameWithoutExtension)
                    } catch (error: Exception) {
                        archive.close()
                        throw error
                    }
                } catch (error: Exception) {
                    activity.runOnUiThread { fail(error) }
                    return@Thread
                }
                activity.runOnUiThread {
                    try {
                        open(request, opened.first, opened.second)
                        complete(NativeResult.Success("null"))
                    } catch (error: Exception) {
                        opened.first.close()
                        fail(error)
                    }
                }
            }.start()
            "close" -> activity.runOnUiThread {
                close()
                complete(NativeResult.Success("null"))
            }
            else -> complete(NativeResult.Failure(NativeErrorKind.PROTOCOL, "Unknown reader operation $operation", false))
        }
    }

    override fun cancel(requestId: Long) = Unit

    private fun open(request: JSONObject, archive: ZipFile, epub: Epub) {
        close()
        zip = archive
        book = epub
        options = request
        styles = readerStyles()
        attach()
        createPage()
        val first = slots[0]
        current = first
        spine = request.optInt("spine", 0).coerceIn(0, epub.spine.lastIndex)
        val anchor = request.optString("anchor").ifEmpty { null }
        load(first, spine, anchor ?: request.optDouble("fraction", 0.0))
        first.view.requestFocus()
    }

    private fun load(slot: Slot, index: Int, from: Any) {
        val epub = book ?: return
        slot.spine = index
        slot.start = from
        slot.ready = false
        slot.page = 0
        slot.pages = 1
        slot.minutesLeft = 0
        slot.generation = ++generations
        Log.i(TAG, "load $index ${epub.spine[index].path} from $from")
        // The generation keeps a reload of the same file from being taken as a jump within it.
        slot.view.loadUrl(BASE + epub.spine[index].path + "?g=" + slot.generation)
        // A page that never says it is ready, such as one the reader script could not be added to, still shows.
        val generation = slot.generation
        slot.view.postDelayed({ ready(slot, generation) }, READY_TIMEOUT_MS)
    }

    /** Shows file [index] at [from]: at once if a slot behind already holds it, or as soon as it is painted. */
    private fun goTo(index: Int, from: Any) {
        val top = current ?: return
        val held = slots.firstOrNull { it !== top && it.holds(index, from) }
        if (held != null) {
            if (held.ready) show(held) else pending = held
            return
        }
        // Keep whichever slot holds a neighbour of the file being opened, and load into another.
        val spare = slots.filter { it !== top }.minBy { if (it.spine == index - 1 || it.spine == index + 1) 1 else 0 }
        load(spare, index, from)
        pending = spare
    }

    /** Brings [slot] to the top, then fills the slots behind it with the files either side. */
    private fun show(slot: Slot) {
        val epub = book ?: return
        pending = null
        current = slot
        slot.view.bringToFront()
        footer?.bringToFront()
        page?.invalidate()
        slot.view.requestFocus()
        spine = slot.spine
        report(slot)
        val behind = slots.filter { it !== slot }.toMutableList()
        val wanted = listOfNotNull(
            (slot.spine + 1).takeIf { it <= epub.spine.lastIndex }?.let { it to 0.0 },
            (slot.spine - 1).takeIf { it >= 0 }?.let { it to "end" },
        )
        val missing = wanted.filter { (index, from) ->
            val holder = behind.firstOrNull { it.holds(index, from) }
            if (holder != null) behind.remove(holder)
            holder == null
        }
        missing.zip(behind).forEach { (want, spare) -> load(spare, want.first, want.second) }
    }

    /** Shows the slot on top's place in the footer and saves it. */
    private fun report(slot: Slot) {
        val epub = book ?: return
        timeLeft?.text = if (slot.minutesLeft > 0) "${slot.minutesLeft} min left in chapter" else ""
        fraction = slot.fraction
        finished = spine == epub.spine.lastIndex && slot.page >= slot.pages - 1
        emit("progress", if (finished) 1.0 else epub.progress(spine, fraction))
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
            // The slots behind the page keep their tiles painted, so bringing one to the top shows it at once.
            offscreenPreRaster = true
        }
        val slot = Slot(view)
        view.addJavascriptInterface(ReaderBridge(this, slot), "MaiaReader")
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host != HOST) return true
                val path = url.path.orEmpty().removePrefix("/")
                val index = book?.spineIndex(path) ?: -1
                if (index >= 0) activity.runOnUiThread { if (current === slot) goTo(index, url.fragment ?: 0.0) }
                return true
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.url.host != HOST) return empty(404)
                return serve(request.url.path.orEmpty().removePrefix("/"))
            }
        }
        slots.add(slot)
        return view
    }

    /**
     * Stacks the WebViews and the footer, behind the Ink view until the first page is painted, so opening a
     * book goes straight from the library to the page. They must be attached and visible to lay out at all.
     */
    private fun createPage() {
        val container = FrameLayout(activity)
        container.setBackgroundColor(Color.BLACK)
        repeat(3) {
            container.addView(createWebView(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        slots[0].view.bringToFront()
        val bar = createFooter()
        footer = bar
        container.addView(bar)
        page = container
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        content.addView(container, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
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
                "reader.js" -> cached("text/javascript", READER_SCRIPT_BYTES)
                "reader.css" -> response("text/css", styles.toByteArray())
                else -> readerAsset(name)?.let { cached(mimeType(name), it) } ?: empty(404)
            }
        }
        val archive = zip ?: return empty(404)
        val entry = archive.getEntry(path) ?: return empty(404)
        val bytes = archive.getInputStream(entry).use { it.readBytes() }
        val type = mimeType(path)
        if (type != "text/html") return response(type, bytes)
        val text = String(bytes, Charsets.UTF_8)
        // XHTML must be parsed as XML: as HTML, a self-closing <a id="x"/> would swallow the rest of the page.
        val xml = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t').startsWith("<?xml") || text.contains("http://www.w3.org/1999/xhtml")
        val injected = injectReader(text)
        return response(if (xml) "application/xhtml+xml" else "text/html", injected.toByteArray())
    }

    /** The reader's fonts, read from the APK once and kept for every page. */
    private fun readerAsset(name: String): ByteArray? = readerAssets[name] ?: try {
        activity.assets.open("reader/$name").use { it.readBytes() }.also { readerAssets[name] = it }
    } catch (_: Exception) {
        null
    }

    private fun injectReader(page: String): String {
        HEAD_END.find(page)?.let { return page.replaceRange(it.range.first, it.range.first, READER_TAGS) }
        HTML_START.find(page)?.let { return page.replaceRange(it.range.last + 1, it.range.last + 1, "<head>$READER_TAGS</head>") }
        return READER_TAGS + page
    }

    /** The reading settings, in CSS pixels from the design's device pixels. */
    private fun readerStyles(): String {
        val density = activity.resources.displayMetrics.density
        fun px(value: Number) = "%.2fpx".format(java.util.Locale.ROOT, value.toFloat() / density)
        val settings = options.optJSONObject("settings") ?: JSONObject()
        val size = when (settings.optString("textSize")) { "small" -> 36; "large" -> 50; else -> 42 }
        val lineHeight = when (settings.optString("lineSpacing")) { "tight" -> 1.4; "loose" -> 1.8; else -> 1.57 }
        val margin = when (settings.optString("margins")) { "normal" -> 128; "wide" -> 168; else -> 92 }
        val font = if (settings.optString("font") == "inter") "\"Maia Inter\", sans-serif" else "\"Maia Literata\", serif"
        return READER_STYLES
            .replace("{font}", font)
            .replace("{size}", px(size))
            .replace("{lineHeight}", lineHeight.toString())
            .replace("{margin}", px(margin))
            .replace("{gap}", px(margin * 2))
            .replace("{top}", px(122))
            .replace("{bottom}", px(128))
    }

    private val pageTurns get() = options.optJSONObject("settings")?.optJSONObject("pageTurns")
    private val keys get() = pageTurns?.optBoolean("keys", true) ?: true
    private val taps get() = pageTurns?.optBoolean("taps", true) ?: true
    private val swipes get() = pageTurns?.optBoolean("swipes", true) ?: true

    // Called from ReaderBridge on the WebView's JavaScript thread.

    fun config(slot: Slot): String {
        val from = slot.start
        return JSONObject()
            .put("generation", slot.generation)
            .put("language", book?.language ?: JSONObject.NULL)
            .put("taps", taps)
            .put("swipes", swipes)
            .put("fraction", (from as? Double) ?: JSONObject.NULL)
            .put("anchor", (from as? String)?.takeIf { it != "end" } ?: JSONObject.NULL)
            .put("end", from == "end")
            .toString()
    }

    fun position(slot: Slot, from: Int, page: Int, pages: Int, minutesLeft: Int) = activity.runOnUiThread {
        if (from != slot.generation) return@runOnUiThread
        slot.page = page
        slot.pages = pages
        slot.minutesLeft = minutesLeft
        if (slot === current && slot.ready) report(slot)
    }

    /** The slot's first page is laid out and painted, so it can go on top without a blank frame. */
    fun ready(slot: Slot, from: Int) = activity.runOnUiThread {
        if (from != slot.generation || slot.ready) return@runOnUiThread
        slot.ready = true
        when {
            slot === pending -> show(slot)
            slot === current -> {
                // The book has just opened: bring the reader above the Ink view.
                page?.bringToFront()
                show(slot)
            }
        }
    }

    fun turn(slot: Slot, from: Int, step: Int) = activity.runOnUiThread {
        val epub = book ?: return@runOnUiThread
        if (slot !== current || from != slot.generation) return@runOnUiThread
        val next = spine + step
        if (next in epub.spine.indices) goTo(next, if (step > 0) 0.0 else "end")
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
        if (options.optJSONObject("settings")?.optBoolean("keyboardLight", true) == false) {
            keyboardDark = KeyboardDark(activity) { close() }.also { it.start() }
        }
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
                // Measured as Atlas does: keys across and rows down, and a swipe that goes further down than across is not sideways.
                val dx = (event.x - x) / padSize(event, MotionEvent.AXIS_X) * KEYS_ACROSS
                val dy = (event.y - y) / padSize(event, MotionEvent.AXIS_Y) * KEY_ROWS
                if (kotlin.math.abs(dx) >= SWIPE_KEYS && kotlin.math.abs(dx) >= kotlin.math.abs(dy)) {
                    swipe(if (dx < 0) "next" else "previous")
                }
            }
            MotionEvent.ACTION_CANCEL -> swipeStart = null
        }
        return true
    }

    private fun swipe(command: String) {
        if (!swipes) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastSwipe < SWIPE_GAP_MS) return
        lastSwipe = now
        command(command)
    }

    private fun command(command: String) {
        current?.view?.evaluateJavascript("window.maia && window.maia.$command()", null)
    }

    /** Arrow keys the firmware sends for a keyboard swipe, in the direction the finger moved. */
    private fun onSwipeKey(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> swipe("next")
            KeyEvent.KEYCODE_DPAD_RIGHT -> swipe("previous")
        }
        return true
    }

    private fun padSize(event: MotionEvent, axis: Int) =
        event.device?.getMotionRange(axis, event.source)?.range?.takeIf { it > 0 } ?: if (axis == MotionEvent.AXIS_X) 1079f else 599f

    private fun isKeyboard(event: MotionEvent) =
        event.isFromSource(InputDevice.SOURCE_TOUCHPAD) || event.device?.name == KEYBOARD_TOUCHPAD

    private fun onKey(event: KeyEvent): Boolean {
        keyboardDark?.keyPressed(event)
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
        if (keys && event.action == KeyEvent.ACTION_DOWN) command(command)
        return true
    }

    private fun emit(event: String, progress: Double) {
        activity.emit(
            JSONObject().put("type", "maia.reader").put("event", event)
                .put("spine", spine).put("fraction", fraction).put("progress", progress).toString(),
        )
    }

    private fun close() {
        if (slots.isEmpty()) return
        val epub = book
        emit("closed", if (epub == null) 0.0 else if (finished) 1.0 else epub.progress(spine, fraction))
        timeLeft = null
        footer = null
        current = null
        pending = null
        activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
        keyboardDark?.stop()
        keyboardDark = null
        windowCallback?.let { activity.window.callback = it }
        windowCallback = null
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        page?.let { (it.parent as? ViewGroup)?.removeView(it) }
        page = null
        slots.forEach { it.view.destroy() }
        slots.clear()
        zip?.close()
        zip = null
        book = null
        Log.i(TAG, "closed at $spine $fraction")
    }

    private fun response(type: String, bytes: ByteArray) =
        WebResourceResponse(type, encoding(type), ByteArrayInputStream(bytes))

    /** The reader's own files never change, so the WebView can keep them between pages. */
    private fun cached(type: String, bytes: ByteArray) =
        WebResourceResponse(type, encoding(type), 200, "OK", CACHE_FOREVER, ByteArrayInputStream(bytes))

    private fun encoding(type: String) = if (type.startsWith("text/") || type.endsWith("xml")) "utf-8" else null

    private fun empty(status: Int) =
        WebResourceResponse("text/plain", "utf-8", status, "Not found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}

/** The methods the reader page can call. They run on a WebView thread. */
internal class ReaderBridge(private val reader: ReaderAdapter, private val slot: ReaderAdapter.Slot) {
    @JavascriptInterface fun config() = reader.config(slot)
    @JavascriptInterface fun position(generation: Int, page: Int, pages: Int, minutesLeft: Int) = reader.position(slot, generation, page, pages, minutesLeft)
    @JavascriptInterface fun ready(generation: Int) = reader.ready(slot, generation)
    @JavascriptInterface fun turn(generation: Int, step: Int) = reader.turn(slot, generation, step)
}

private val READER_SCRIPT_BYTES = READER_SCRIPT.toByteArray()
private val READER_TAGS =
    """<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/><link rel="stylesheet" type="text/css" href="/__reader/reader.css"/><script type="text/javascript" src="/__reader/reader.js"></script>"""
private val HEAD_END = Regex("</head\\s*>", RegexOption.IGNORE_CASE)
private val HTML_START = Regex("<html\\b[^>]*>", RegexOption.IGNORE_CASE)
private val CACHE_FOREVER = mapOf("Cache-Control" to "public, max-age=31536000, immutable")
private val readerAssets = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

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
