package com.vandam.ink

fun main() {
    val book = ReaderPagination(3, emptyList())
    book.record(0, PageLayout(2, 500, emptyMap()))
    book.record(1, PageLayout(5, 1250, emptyMap()))
    check(book.bookPages(1, 2) == null) { "A partial book must not present a final total" }
    book.record(2, PageLayout(3, 750, emptyMap()))
    check(book.bookPages(1, 2) == PageCount(5, 10))
    check(book.bookPages(2, 2) == PageCount(10, 10))

    val shared = ReaderPagination(1, listOf(Chapter("First", 0, "a"), Chapter("Second", 0, "b")))
    shared.record(0, PageLayout(9, 2250, mapOf(0 to 2, 1 to 5)))
    check(shared.chapterAt(0, 3) == 0)
    check(shared.chapterPages(0, 3) == PageCount(2, 3, 1))
    check(shared.chapterPages(0, 4)?.remaining == 0)
    check(shared.chapterAt(0, 5) == 1)
    check(shared.chapterPages(0, 5) == PageCount(1, 4, 3))
    check(shared.chapterAt(0, 0) == -1)
    check(shared.chapterPages(0, 0) == PageCount(1, 2, 1))

    val spanning = ReaderPagination(3, listOf(Chapter("First", 0, "a"), Chapter("Second", 2, "b")))
    spanning.record(0, PageLayout(5, 1250, mapOf(0 to 2)))
    spanning.record(1, PageLayout(4, 1000, emptyMap()))
    check(spanning.chapterPages(1, 1) == null)
    check(spanning.needed(1, 1, false).toList() == listOf(0, 1, 2))
    spanning.record(2, PageLayout(3, 750, mapOf(1 to 1)))
    check(spanning.chapterPages(1, 1) == PageCount(5, 8, 3))
    check(spanning.chapterPages(2, 0) == PageCount(8, 8))
    check(spanning.chapterPages(2, 2) == PageCount(2, 2))

    val separate = ReaderPagination(3, listOf(Chapter("First", 0, null), Chapter("Second", 2, null)))
    separate.record(0, PageLayout(2, 500, mapOf(0 to 0)))
    separate.record(1, PageLayout(3, 750, emptyMap()))
    check(separate.needed(1, 1, false).toList() == listOf(0, 1))
    check(separate.chapterPages(1, 1) == PageCount(4, 5, 1))

    val singlePage = ReaderPagination(1, emptyList())
    singlePage.record(0, PageLayout(1, 0, emptyMap()))
    check(singlePage.chapterPages(0, 0) == PageCount(1, 1))

    book.record(0, PageLayout(4, 500, emptyMap()))
    check(book.bookPages(1, 2) == PageCount(7, 12)) { "Reflow must update page offsets and totals" }
    shared.record(0, PageLayout(12, 2250, mapOf(0 to 3, 1 to 7)))
    check(shared.chapterPages(0, 5) == PageCount(3, 4, 1))

    val samePage = ReaderPagination(1, listOf(Chapter("Part", 0, "a"), Chapter("Chapter", 0, "b")))
    samePage.record(0, PageLayout(4, 1000, mapOf(0 to 0, 1 to 0)))
    check(samePage.chapterAt(0, 0) == 1)
    check(samePage.chapterPages(0, 0) == PageCount(1, 4, 3))
    println("Pagination checks passed: book totals, shared files, spanning chapters, front matter, reflow and final pages.")
}
