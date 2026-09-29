package co.abaye.mailtice.main.html

import androidx.compose.ui.graphics.ImageBitmap
import co.abaye.mailtice.platform.createHttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.io.encoding.Base64

/**
 * Images for the native mail renderer: inline parts (data: URIs, as the reader already holds
 * them for cid: references) decoded on the spot, remote ones fetched - only once the reader has
 * allowed them - and kept in a small memory cache, so scrolling back does not fetch again.
 */
internal object MailImages {
    private const val CACHE_SIZE = 80
    private const val MAX_BYTES = 8 * 1024 * 1024

    private val client by lazy { createHttpClient { } }
    private val lock = Mutex()
    private val cache = LinkedHashMap<String, ImageBitmap>()
    private val failed = mutableSetOf<String>()

    /** A data: URI decoded, or null when it is not an image this platform decodes. */
    fun decodeDataUri(uri: String): ImageBitmap? = runCatching {
        val comma = uri.indexOf(',')
        if (!uri.startsWith("data:", ignoreCase = true) || comma < 0) return null
        val meta = uri.substring(5, comma)
        if (!meta.endsWith(";base64", ignoreCase = true)) return null
        Base64.decode(uri.substring(comma + 1).trim()).decodeToImageBitmap()
    }.getOrNull()

    /** [url] (http or https) as an image; null when it cannot be fetched or decoded (that is not retried). */
    suspend fun load(url: String): ImageBitmap? {
        lock.withLock {
            cache.remove(url)?.let {
                cache[url] = it
                return it
            }
            if (url in failed) return null
        }
        val image = runCatching {
            val response = client.get(url)
            if (!response.status.isSuccess()) return@runCatching null
            val bytes = response.readRawBytes()
            if (bytes.size > MAX_BYTES) null else bytes.decodeToImageBitmap()
        }.getOrNull()
        lock.withLock {
            if (image == null) {
                failed += url
            } else {
                cache[url] = image
                while (cache.size > CACHE_SIZE) cache.remove(cache.keys.first())
            }
        }
        return image
    }
}
