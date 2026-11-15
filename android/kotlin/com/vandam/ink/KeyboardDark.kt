package com.vandam.ink

import android.app.Activity
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/** The Titan's light controller leaves the light alone while the focused window's title contains this. */
private const val SETUP_WIZARD = "com.google.android.setupwizard"
/** The keyboard light timeouts the Titan's settings offer, apart from off and always on. */
private val LIGHT_TIMEOUTS_MS = longArrayOf(3_000, 5_000, 10_000, 15_000, 30_000)
/** How long before and after each possible timeout the light is let go, to allow for timing slack. */
private const val EARLY_MS = 1_000L
private const val LATE_MS = 800L

/**
 * Keeps the Titan's keyboard light off while a book is open, without changing it anywhere else.
 *
 * The Titan's light controller (KeyboardLightController in the phone's services.jar) leaves the light alone
 * while the focused window's title contains "com.google.android.setupwizard". So while reading, a one-pixel
 * window with that title holds the focus and passes every key on to the reader, and key presses can't light
 * the keyboard. When the book closes the window goes, and the light works as before.
 *
 * Holding the light keeps it as it was, so it has to go off first. The controller turns it off a set time
 * after it last lit it, 3 to 30 seconds from the Titan's settings, which apps can't read. A text field taking
 * the focus restarts that timer, so the window takes the focus with a hidden text field, then lets go of the
 * light briefly at each possible time for it to go off. A keyboard key pressed while the light may still be on
 * restarts the timer, so the waiting starts again from that key.
 */
internal class KeyboardDark(private val activity: Activity, private val back: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val pending = Any()
    private val backCallback = OnBackInvokedCallback { back() }
    private var panel: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private lateinit var field: EditText
    private lateinit var rest: View
    /** True while the window has stepped aside so the light can go off. */
    private var letGo = false
    /** The light may still be on until then, so a key press before it restarts the waiting. */
    private var mayBeLitUntil = 0L

    fun start() {
        if (panel != null) return
        field = EditText(activity).apply {
            showSoftInputOnFocus = false
            isFocusableInTouchMode = true
        }
        rest = View(activity).apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val view = object : FrameLayout(activity) {
            override fun dispatchKeyEvent(event: KeyEvent) = activity.window.callback.dispatchKeyEvent(event)
            override fun dispatchGenericMotionEvent(event: MotionEvent) = activity.window.callback.dispatchGenericMotionEvent(event)

            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (hasWindowFocus) focused()
                // Something else took the focus, and the light may come on there, so wait for it again on return.
                else if (!letGo) field.requestFocus()
            }

            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, backCallback)
            }

            override fun onDetachedFromWindow() {
                findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(backCallback)
                super.onDetachedFromWindow()
            }
        }
        view.addView(field, FrameLayout.LayoutParams(1, 1))
        view.addView(rest, FrameLayout.LayoutParams(1, 1))
        field.requestFocus()
        val layout = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            token = activity.window.decorView.windowToken
            gravity = Gravity.TOP or Gravity.START
            title = "maia reader $SETUP_WIZARD"
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        }
        activity.windowManager.addView(view, layout)
        panel = view
        params = layout
    }

    fun stop() {
        val view = panel ?: return
        handler.removeCallbacksAndMessages(pending)
        activity.windowManager.removeViewImmediate(view)
        panel = null
        params = null
        letGo = false
        Log.i(TAG, "keyboard light released")
    }

    /** Called for every key the reader sees, held or not. */
    fun keyPressed(event: KeyEvent) {
        if (panel == null || event.action != KeyEvent.ACTION_DOWN || event.deviceId != KeyCharacterMap.BUILT_IN_KEYBOARD) return
        // While held, a key press can't light the keyboard, but it does restart the timer the light is waiting on.
        if (letGo || SystemClock.uptimeMillis() < mayBeLitUntil) waitForLightOff()
    }

    private fun focused() {
        if (field.isFocused) {
            // Taking the focus with a text field restarted the light's timer just now.
            Log.i(TAG, "keyboard light held")
            waitForLightOff()
            handler.post { rest.requestFocus() }
        }
    }

    private fun waitForLightOff() {
        handler.removeCallbacksAndMessages(pending)
        if (letGo) hold()
        val now = SystemClock.uptimeMillis()
        mayBeLitUntil = now + LIGHT_TIMEOUTS_MS.last() + LATE_MS
        for (timeout in LIGHT_TIMEOUTS_MS) {
            handler.postAtTime({ release() }, pending, now + timeout - EARLY_MS)
            handler.postAtTime({ hold() }, pending, now + timeout + LATE_MS)
        }
    }

    /** Steps aside, so the light controller can act again. */
    private fun release() {
        letGo = true
        setFocusable(false)
    }

    private fun hold() {
        letGo = false
        // If the reader lost the focus meanwhile, the light may have come on elsewhere, so restart the wait on return.
        if (activity.hasWindowFocus()) rest.requestFocus() else field.requestFocus()
        setFocusable(true)
    }

    private fun setFocusable(focusable: Boolean) {
        val view = panel ?: return
        val layout = params ?: return
        layout.flags = if (focusable) layout.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        else layout.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        activity.windowManager.updateViewLayout(view, layout)
    }
}
