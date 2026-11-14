package com.vandam.ink

import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/** A chapter from the book's table of contents, pointing at an item in the reading order. */
internal data class Chapter(val title: String, val spine: Int, val anchor: String?)

/** One file in the reading order, with its size so progress can be weighted by length. */
internal data class SpineItem(val path: String, val size: Long)

/** The parts of an EPUB the app uses: metadata, reading order and table of contents. */
internal class Epub(
    val id: String,
    val title: String,
    val authors: List<String>,
    val series: String?,
    val seriesIndex: Double?,
    val language: String?,
    val spine: List<SpineItem>,
    val chapters: List<Chapter>,
) {
    private val totalSize = spine.sumOf { it.size }.coerceAtLeast(1)

    fun spineIndex(path: String) = spine.indexOfFirst { it.path == path }

    /** How far through the book a position is, from 0 to 1, weighting each file by its size. */
    fun progress(index: Int, fraction: Double): Double {
        val before = spine.take(index).sumOf { it.size }
        val size = spine.getOrNull(index)?.size ?: 0
        return ((before + fraction * size) / totalSize).coerceIn(0.0, 1.0)
    }

    fun toJson(file: File): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("authors", JSONArray(authors))
        .put("series", series ?: JSONObject.NULL)
        .put("seriesIndex", seriesIndex ?: JSONObject.NULL)
        .put("file", file.absolutePath)
        .put("chapters", JSONArray(chapters.map { JSONObject().put("title", it.title).put("spine", it.spine).put("anchor", it.anchor) }))

    companion object {
        fun read(zip: ZipFile, fallbackId: String): Epub {
            val container = parse(zip, "META-INF/container.xml")
            val opfPath = container.all("rootfile").firstOrNull()?.getAttribute("full-path")
                ?: error("This EPUB has no package file")
            val opf = parse(zip, opfPath)
            val metadata = opf.all("metadata").firstOrNull() ?: error("This EPUB has no metadata")

            val manifest = opf.all("item").associate { item ->
                item.getAttribute("id") to Item(resolve(opfPath, item.getAttribute("href")), item.getAttribute("media-type"), item.getAttribute("properties"))
            }
            val spineElement = opf.all("spine").firstOrNull()
            val spine = opf.all("itemref")
                .filter { it.getAttribute("linear") != "no" }
                .mapNotNull { manifest[it.getAttribute("idref")]?.path }
                .filter { zip.getEntry(it) != null }
                .map { SpineItem(it, zip.getEntry(it).size.coerceAtLeast(1)) }
            if (spine.isEmpty()) error("This EPUB has nothing to read")

            val metas = metadata.all("meta")
            fun calibre(name: String) = metas.firstOrNull { it.getAttribute("name") == name }?.getAttribute("content")?.takeIf { it.isNotBlank() }
            fun refined(property: String) = metas.firstOrNull { it.getAttribute("property") == property }?.textContent?.trim()?.takeIf { it.isNotEmpty() }

            val identifiers = metadata.all("identifier")
            val uniqueId = opf.documentElement.getAttribute("unique-identifier")
            val id = identifiers.firstOrNull { it.attribute("scheme").equals("calibre", true) || it.attribute("scheme").equals("uuid", true) }?.text()
                ?: identifiers.firstOrNull { it.text().startsWith("urn:uuid:") }?.text()?.removePrefix("urn:uuid:")
                ?: identifiers.firstOrNull { it.getAttribute("id") == uniqueId }?.text()
                ?: fallbackId

            val navPath = manifest.values.firstOrNull { it.properties.split(' ').contains("nav") }?.path
            val ncxPath = spineElement?.getAttribute("toc")?.let { manifest[it]?.path }
                ?: manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }?.path
            val entries = when {
                navPath != null && zip.getEntry(navPath) != null -> navEntries(parse(zip, navPath), navPath)
                ncxPath != null && zip.getEntry(ncxPath) != null -> ncxEntries(parse(zip, ncxPath), ncxPath)
                else -> emptyList()
            }
            val paths = spine.map { it.path }
            val chapters = entries.mapNotNull { (title, target) ->
                val path = target.substringBefore('#')
                val index = paths.indexOf(path)
                if (index < 0 || title.isBlank()) null else Chapter(title, index, target.substringAfter('#', "").ifEmpty { null })
            }

            return Epub(
                id = id,
                title = metadata.all("title").firstOrNull()?.text()?.ifEmpty { null } ?: fallbackId,
                authors = metadata.all("creator").map { it.text() }.filter { it.isNotEmpty() },
                series = calibre("calibre:series") ?: refined("belongs-to-collection"),
                seriesIndex = (calibre("calibre:series_index") ?: refined("group-position"))?.toDoubleOrNull(),
                language = metadata.all("language").firstOrNull()?.text()?.ifEmpty { null },
                spine = spine,
                chapters = chapters,
            )
        }

        /** Resolves an href inside the book against the file that contains it, giving a path in the zip. */
        fun resolve(base: String, href: String): String {
            val target = try {
                URI("https://book/").resolve(URI(null, null, base, null)).resolve(URI(href.substringBefore('#'))).rawPath.removePrefix("/")
            } catch (_: Exception) {
                (base.substringBeforeLast('/', "") + "/" + href.substringBefore('#')).removePrefix("/")
            }
            val fragment = href.substringAfter('#', "")
            return URLDecoder.decode(target.replace("+", "%2B"), "UTF-8") + if (fragment.isEmpty()) "" else "#$fragment"
        }

        private data class Item(val path: String, val mediaType: String, val properties: String)

        private fun parse(zip: ZipFile, path: String): Document {
            val entry = zip.getEntry(path) ?: error("Missing $path")
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
            }
            return zip.getInputStream(entry).use { factory.newDocumentBuilder().parse(it) }
        }

        private fun ncxEntries(ncx: Document, ncxPath: String) = ncx.all("navPoint").map { point ->
            val label = point.children("navLabel").firstOrNull()?.text().orEmpty()
            val src = point.children("content").firstOrNull()?.getAttribute("src").orEmpty()
            label.collapse() to resolve(ncxPath, src)
        }

        private fun navEntries(nav: Document, navPath: String): List<Pair<String, String>> {
            val toc = nav.all("nav").firstOrNull { it.attribute("type") == "toc" } ?: nav.all("nav").firstOrNull() ?: return emptyList()
            return toc.all("a").filter { it.hasAttribute("href") }.map { it.text().collapse() to resolve(navPath, it.getAttribute("href")) }
        }

        private fun String.collapse() = replace(Regex("\\s+"), " ").trim()
    }
}

/** Reads every EPUB in a folder; books that fail to open are skipped. */
internal fun scanBooks(folder: File): JSONArray {
    val books = JSONArray()
    folder.listFiles { file -> file.isFile && file.extension.equals("epub", true) }?.sortedBy { it.name }?.forEach { file ->
        try {
            ZipFile(file).use { books.put(Epub.read(it, file.nameWithoutExtension).toJson(file)) }
        } catch (error: Exception) {
            android.util.Log.w("uniread", "skipped ${file.name}", error)
        }
    }
    return books
}

private fun Document.all(name: String) = getElementsByTagNameNS("*", name).let { list -> List(list.length) { list.item(it) as Element } }

private fun Element.all(name: String) = getElementsByTagNameNS("*", name).let { list -> List(list.length) { list.item(it) as Element } }

private fun Element.children(name: String) = List(childNodes.length) { childNodes.item(it) }.filterIsInstance<Element>().filter { it.localName == name }

private fun Element.text() = textContent.trim()

/** An attribute in any namespace, like opf:scheme or epub:type. */
private fun Element.attribute(name: String): String {
    if (hasAttribute(name)) return getAttribute(name)
    for (index in 0 until attributes.length) {
        val attribute = attributes.item(index)
        if (attribute.localName == name) return attribute.nodeValue
    }
    return ""
}
