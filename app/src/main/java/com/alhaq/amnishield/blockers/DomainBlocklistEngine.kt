package com.alhaq.amnishield.blockers

import android.content.Context
import com.alhaq.amnishield.data.blockers.PreloadedPacks
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Arrays
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * High-speed domain suffix matcher backed by a sorted primitive [LongArray] of 64-bit FNV-1a
 * domain hashes. Avoids holding tens of thousands of [String] objects in memory or expanding
 * large bundled blocklists into SharedPreferences or Compose UI state.
 */
class DomainBlocklistEngine {

    @Volatile
    private var sortedHashes: LongArray = LongArray(0)

    fun domainCount(): Int = sortedHashes.size

    fun isLoaded(): Boolean = sortedHashes.isNotEmpty()

    /**
     * Streams gzipped domain assets (falling back to bundled in-memory pack items if an asset
     * is unavailable) and builds a sorted primitive hash array off the Main/Binder thread.
     */
    fun loadPacksFromAssets(context: Context, enabledPackIds: Set<String>) {
        if (enabledPackIds.isEmpty()) {
            sortedHashes = LongArray(0)
            return
        }

        val packsById = PreloadedPacks.websitePacks.associateBy { it.id }
        var buffer = LongArray(4096)
        var count = 0

        fun appendDomain(raw: String) {
            val clean = normalizeRawDomain(raw) ?: return
            if (count == buffer.size) {
                buffer = buffer.copyOf(buffer.size * 2)
            }
            buffer[count++] = fnv1a64(clean)
        }

        for (packId in enabledPackIds) {
            val pack = packsById[packId] ?: continue
            val assetPath = pack.assetPath
            var loadedFromAsset = false
            if (!assetPath.isNullOrBlank()) {
                runCatching {
                    context.assets.open(assetPath).use { stream ->
                        readGzipStream(stream, ::appendDomain)
                    }
                    loadedFromAsset = true
                }
            }
            if (!loadedFromAsset) {
                pack.items.forEach { appendDomain(it) }
            }
        }

        publishBuffer(buffer, count)
    }

    /**
     * Streams one or more gzipped domain streams (e.g. bundled blocklist assets) into the sorted hash index.
     */
    internal fun loadGzipStreamsForTest(streams: Iterable<java.io.InputStream>) {
        var buffer = LongArray(4096)
        var count = 0
        for (stream in streams) {
            readGzipStream(stream) { raw ->
                val clean = normalizeRawDomain(raw) ?: return@readGzipStream
                if (count == buffer.size) {
                    buffer = buffer.copyOf(buffer.size * 2)
                }
                buffer[count++] = fnv1a64(clean)
            }
        }
        publishBuffer(buffer, count)
    }

    private inline fun readGzipStream(stream: java.io.InputStream, onLine: (String) -> Unit) {
        GZIPInputStream(stream).use { gz ->
            BufferedReader(InputStreamReader(gz, Charsets.UTF_8)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    onLine(line)
                    line = reader.readLine()
                }
            }
        }
    }

    /**
     * Loads normalized domains directly for unit testing without Android AssetManager.
     */
    fun loadDomainsForTest(domains: Iterable<String>) {
        var buffer = LongArray(256)
        var count = 0
        for (raw in domains) {
            val clean = normalizeRawDomain(raw) ?: continue
            if (count == buffer.size) {
                buffer = buffer.copyOf(buffer.size * 2)
            }
            buffer[count++] = fnv1a64(clean)
        }
        publishBuffer(buffer, count)
    }

    private fun normalizeRawDomain(raw: String): String? {
        val stripped = raw.trimStart('\uFEFF').trim()
        if (stripped.isEmpty() || stripped[0] == '#') return null
        val clean = stripped
            .lowercase(Locale.ROOT)
            .removePrefix("*.")
            .removePrefix("www.")
        return if (clean.length >= 3 && clean.contains('.') && !clean.contains(' ')) clean else null
    }

    private fun publishBuffer(buffer: LongArray, count: Int) {
        if (count == 0) {
            sortedHashes = LongArray(0)
            return
        }
        val trimmed = buffer.copyOf(count)
        Arrays.sort(trimmed)
        sortedHashes = trimmed
    }

    /**
     * Checks [host] and each parent suffix (e.g. `m.sub.sex.com` -> `sub.sex.com` -> `sex.com`)
     * via O(log N) binary search on the primitive hash array. Returns the matched suffix or null.
     */
    fun findMatchingSuffix(host: String): String? {
        val hashes = sortedHashes
        if (hashes.isEmpty()) return null
        val normalized = host.trim().lowercase(Locale.ROOT)
            .removePrefix("www.")
            .removeSuffix(".")
        if (normalized.length < 3 || !normalized.contains('.')) return null

        var current = normalized
        var hops = 0
        while (hops < 8) {
            if (Arrays.binarySearch(hashes, fnv1a64(current)) >= 0) {
                return current
            }
            val dotIndex = current.indexOf('.')
            if (dotIndex < 0 || dotIndex >= current.length - 1) break
            val next = current.substring(dotIndex + 1)
            if (!next.contains('.')) break
            current = next
            hops++
        }
        return null
    }

    companion object {
        private const val FNV_OFFSET_BASIS = -3750763034362895579L // 0xcbf29ce484222325L
        private const val FNV_PRIME = 1099511628211L

        fun fnv1a64(value: String): Long {
            var hash = FNV_OFFSET_BASIS
            for (i in 0 until value.length) {
                hash = hash xor value[i].code.toLong()
                hash *= FNV_PRIME
            }
            return hash
        }
    }
}
