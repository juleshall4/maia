package com.vandam.ink

internal fun createAppNativeAdapters(activity: MainActivity): Map<String, NativeAdapter> =
    mapOf("reader" to ReaderAdapter(activity), "books" to BooksAdapter(activity))
