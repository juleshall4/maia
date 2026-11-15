package com.vandam.ink

import org.json.JSONObject

/** The app's version and how much the books folder holds, for Settings. */
internal class AboutAdapter(private val activity: MainActivity) : NativeAdapter {
    override fun execute(requestId: Long, operation: String, payload: String, complete: NativeResultHandler) {
        if (operation != "about") {
            complete(NativeResult.Failure(NativeErrorKind.PROTOCOL, "Unknown app operation $operation", false))
            return
        }
        Thread {
            val books = booksFolder(activity).listFiles { file -> file.isFile && file.extension.equals("epub", true) }.orEmpty()
            val about = JSONObject()
                .put("version", activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "")
                .put("books", books.size)
                .put("bytes", books.sumOf { it.length() })
            activity.runOnUiThread { complete(NativeResult.Success(about.toString())) }
        }.start()
    }

    override fun cancel(requestId: Long) = Unit
}
