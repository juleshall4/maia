package com.vandam.ink

import kotlin.math.ceil

/** One EPUB file measured with the reader's current fonts, margins and viewport. */
internal data class PageLayout(val pages: Int, val words: Int, val chapters: Map<Int, Int>)

internal data class PageCount(val page: Int, val total: Int, val minutesLeft: Int = 0) {
    val remaining get() = total - page
}

/** Keeps page counts across files, using table-of-contents anchors as chapter boundaries. */
internal class ReaderPagination(private val files: Int, private val chapters: List<Chapter>) {
    private val layouts = mutableMapOf<Int, PageLayout>()
    private data class Boundary(val spine: Int, val chapter: Int? = null)

    fun record(spine: Int, layout: PageLayout) {
        layouts[spine] = layout
    }

    fun contains(spine: Int) = spine in layouts

    fun chapterAt(spine: Int, page: Int) = chapters.indices.indexOfLast { index ->
        val chapter = chapters[index]
        chapter.spine < spine || (chapter.spine == spine &&
            (chapter.anchor == null || (layouts[spine]?.chapters?.get(index) ?: Int.MAX_VALUE) <= page))
    }

    private fun boundary(index: Int): Boundary {
        val chapter = chapters[index]
        return Boundary(chapter.spine, if (chapter.anchor != null) index else null)
    }

    private fun boundaries(spine: Int, page: Int): Pair<Boundary, Boundary> {
        if (chapters.isEmpty()) return Boundary(spine) to Boundary(spine + 1)
        val index = chapterAt(spine, page)
        val start = if (index >= 0) boundary(index) else Boundary(0)
        val end = if (index + 1 < chapters.size) boundary(index + 1) else Boundary(files)
        return start to end
    }

    private fun indices(start: Boundary, end: Boundary) =
        start.spine..(if (end.chapter == null) end.spine - 1 else end.spine).coerceAtMost(files - 1)

    fun needed(spine: Int, page: Int, wholeBook: Boolean): IntRange {
        if (wholeBook) return 0 until files
        val (start, end) = boundaries(spine, page)
        return indices(start, end)
    }

    fun bookPages(spine: Int, page: Int): PageCount? {
        if ((0 until files).any { it !in layouts }) return null
        return PageCount(
            layouts.filterKeys { it < spine }.values.sumOf { it.pages } + page + 1,
            layouts.values.sumOf { it.pages },
        )
    }

    fun chapterPages(spine: Int, page: Int): PageCount? {
        val (start, end) = boundaries(spine, page)
        val indices = indices(start, end)
        if (indices.any { it !in layouts }) return null
        fun offset(boundary: Boundary) = boundary.chapter?.let {
            layouts[boundary.spine]?.chapters?.get(it)
        } ?: 0
        var total = 0
        var before = 0
        var remainingWords = 0.0
        for (index in indices) {
            val layout = layouts.getValue(index)
            val from = if (index == start.spine) offset(start) else 0
            val until = if (index == end.spine) offset(end) else layout.pages
            val count = (until - from).coerceAtLeast(0)
            total += count
            before += when {
                index < spine -> count
                index == spine -> (page - from).coerceIn(0, count)
                else -> 0
            }
            val unread = when {
                index < spine -> 0
                index == spine -> (until - maxOf(from, page + 1)).coerceAtLeast(0)
                else -> count
            }
            remainingWords += layout.words.toDouble() * unread / layout.pages
        }
        total = total.coerceAtLeast(1)
        return PageCount((before + 1).coerceAtMost(total), total, ceil(remainingWords / 250).toInt())
    }
}
