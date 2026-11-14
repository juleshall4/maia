package com.vandam.ink

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import org.json.JSONObject

private const val TAG = "uniread"

/**
 * Keeps the Titan's keyboard light off while uniread is in front, if Settings asks for that.
 *
 * The Titan's keyboard light controller turns the light off for this broadcast and then ignores key presses,
 * as it does when the screen goes off, until the screen next comes on. So the light stays off in other apps
 * until the screen is turned off and on again. Apps can't read or write the light's own settings.
 */
internal class KeyboardLight(private val activity: MainActivity) : NativeAdapter {
    private val preferences = activity.getSharedPreferences("uniread", Context.MODE_PRIVATE)

    init {
        activity.application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            // Turning the screen on lights the keys again, and uniread resumes after that.
            override fun onActivityResumed(resumed: Activity) {
                if (resumed === activity && preferences.getBoolean("keyboardLightOff", false)) turnOff()
            }

            override fun onActivityPaused(paused: Activity) = Unit
            override fun onActivityCreated(created: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(started: Activity) = Unit
            override fun onActivityStopped(stopped: Activity) = Unit
            override fun onActivitySaveInstanceState(saved: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(destroyed: Activity) = Unit
        })
    }

    override fun execute(requestId: Long, operation: String, payload: String, complete: NativeResultHandler) {
        activity.runOnUiThread {
            if (operation != "light") {
                complete(NativeResult.Failure(NativeErrorKind.PROTOCOL, "Unknown keyboard operation $operation", false))
                return@runOnUiThread
            }
            val off = !JSONObject(payload).getBoolean("on")
            preferences.edit().putBoolean("keyboardLightOff", off).apply()
            if (off) turnOff()
            complete(NativeResult.Success("null"))
        }
    }

    override fun cancel(requestId: Long) = Unit

    private fun turnOff() {
        activity.sendBroadcast(Intent("agui.action.CLOSE_KEYBOARD_LIGHT"))
        Log.i(TAG, "keyboard light off")
    }
}
