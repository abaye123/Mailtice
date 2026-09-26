package co.abaye.mailtice.provider.gmail

import co.abaye.mailtice.provider.ProviderException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

private const val BASE = "https://gmail.googleapis.com/gmail/v1/users/me"
private const val MAX_PAGES = 20

/** Thin REST wrapper. Every call takes a ready access token. */
class GmailApi(private val http: HttpClient) {

    suspend fun profile(token: String): GmailProfile = http.get("$BASE/profile") { bearerAuth(token) }.parsed()

    suspend fun labels(token: String): List<GmailLabel> = http.get("$BASE/labels") { bearerAuth(token) }.parsed<LabelList>().labels

    /**
     * Ids in [labelId] (null = any label) received after [afterEpochSeconds] (null = all) and matching
     * the Gmail search [query], newest first, paged.
     */
    suspend fun messageIds(token: String, labelId: String?, afterEpochSeconds: Long?, max: Int = 2000, query: String? = null): List<MessageRef> {
        val out = mutableListOf<MessageRef>()
        var pageToken: String? = null
        var pages = 0
        do {
            val page = http.get("$BASE/messages") {
                bearerAuth(token)
                labelId?.let { parameter("labelIds", it) }
                val q = listOfNotNull(afterEpochSeconds?.let { "after:$it" }, query).joinToString(" ")
                if (q.isNotEmpty()) parameter("q", q)
                parameter("maxResults", 500)
                pageToken?.let { parameter("pageToken", it) }
            }.parsed<MessageList>()
            out += page.messages
            pageToken = page.nextPageToken
            pages++
        } while (pageToken != null && out.size < max && pages < MAX_PAGES)
        return out.take(max)
    }

    suspend fun message(token: String, id: String, full: Boolean): GmailMessage =
        http.get("$BASE/messages/$id") {
            bearerAuth(token)
            if (full) {
                parameter("format", "full")
            } else {
                parameter("format", "metadata")
                listOf("From", "To", "Subject", "Date").forEach { parameter("metadataHeaders", it) }
            }
        }.parsed()

    /** Throws [ProviderException.NotFound] when the id is too old; the caller resyncs. */
    suspend fun history(token: String, startHistoryId: String): HistoryDelta {
        val records = mutableListOf<HistoryRecord>()
        var pageToken: String? = null
        var latest = startHistoryId
        var pages = 0
        do {
            val page = http.get("$BASE/history") {
                bearerAuth(token)
                parameter("startHistoryId", startHistoryId)
                parameter("maxResults", 500)
                pageToken?.let { parameter("pageToken", it) }
            }.parsed<HistoryList>()
            records += page.history
            if (page.historyId.isNotEmpty()) latest = page.historyId
            pageToken = page.nextPageToken
            pages++
        } while (pageToken != null && pages < MAX_PAGES)
        return reduceHistory(records, latest)
    }

    suspend fun modify(token: String, id: String, add: List<String> = emptyList(), remove: List<String> = emptyList()) {
        http.post("$BASE/messages/$id/modify") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(ModifyRequest(addLabelIds = add, removeLabelIds = remove))
        }.parsed<MessageRef>()
    }

    /** Moves to Gmail's trash (recoverable for 30 days there). */
    suspend fun trash(token: String, id: String) {
        http.post("$BASE/messages/$id/trash") { bearerAuth(token) }.parsed<MessageRef>()
    }

    suspend fun send(token: String, raw: String, threadId: String?) {
        http.post("$BASE/messages/send") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(SendRequest(raw = raw, threadId = threadId))
        }.parsed<MessageRef>()
    }

    /** An attachment's bytes, base64url in [AttachmentBody.data]. */
    suspend fun attachment(token: String, messageId: String, attachmentId: String): AttachmentBody =
        http.get("$BASE/messages/$messageId/attachments/$attachmentId") { bearerAuth(token) }.parsed()

    suspend fun raw(token: String, id: String): RawMessage =
        http.get("$BASE/messages/$id") {
            bearerAuth(token)
            parameter("format", "raw")
        }.parsed()

    /**
     * Messages over the JSON body limit (attachments) go through the media upload endpoint, which
     * takes the RFC 5322 bytes as they are, up to Gmail's 35 MB. It cannot carry a threadId; the
     * In-Reply-To / References headers still thread a reply for everyone.
     */
    suspend fun sendLarge(token: String, raw: ByteArray) {
        http.post("https://gmail.googleapis.com/upload/gmail/v1/users/me/messages/send") {
            bearerAuth(token)
            parameter("uploadType", "media")
            contentType(ContentType("message", "rfc822"))
            setBody(raw)
        }.parsed<MessageRef>()
    }

    /** Creates a draft ([id] null) or replaces one; returns the draft id. Big ones go through the upload endpoint. */
    suspend fun saveDraft(token: String, id: String?, raw: ByteArray, rawBase64Url: String, threadId: String?, large: Boolean): String {
        val response = if (large) {
            val url = "https://gmail.googleapis.com/upload/gmail/v1/users/me/drafts" + (id?.let { "/$it" } ?: "")
            val block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
                bearerAuth(token)
                parameter("uploadType", "media")
                contentType(ContentType("message", "rfc822"))
                setBody(raw)
            }
            if (id == null) http.post(url, block) else http.put(url, block)
        } else {
            val block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(DraftRequest(DraftMessage(rawBase64Url, threadId)))
            }
            if (id == null) http.post("$BASE/drafts", block) else http.put("$BASE/drafts/$id", block)
        }
        return response.parsed<DraftRef>().id
    }

    suspend fun deleteDraft(token: String, id: String) {
        val response = http.delete("$BASE/drafts/$id") { bearerAuth(token) }
        // Already gone is fine: it was sent or deleted elsewhere.
        if (!response.status.isSuccess() && response.status.value != 404) throw ProviderException.of(response.status.value, response.bodyAsText())
    }

    /** The draft id that holds message [messageId], searching the drafts list (a few pages at most). */
    suspend fun draftIdOf(token: String, messageId: String): String? {
        var pageToken: String? = null
        var pages = 0
        do {
            val page = http.get("$BASE/drafts") {
                bearerAuth(token)
                parameter("maxResults", 500)
                pageToken?.let { parameter("pageToken", it) }
            }.parsed<DraftList>()
            page.drafts.firstOrNull { it.message?.id == messageId }?.let { return it.id }
            pageToken = page.nextPageToken
            pages++
        } while (pageToken != null && pages < 5)
        return null
    }

    /** Only the headers a reply needs to thread correctly. */
    suspend fun threadHeaders(token: String, id: String): GmailMessage =
        http.get("$BASE/messages/$id") {
            bearerAuth(token)
            parameter("format", "metadata")
            listOf("Message-ID", "References").forEach { parameter("metadataHeaders", it) }
        }.parsed()

    private suspend inline fun <reified T> HttpResponse.parsed(): T {
        if (!status.isSuccess()) throw ProviderException.of(status.value, bodyAsText())
        return body()
    }
}
