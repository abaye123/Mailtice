package co.abaye.mailtice.provider.gmail

import co.abaye.mailtice.provider.ProviderException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
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

    /** Ids in [labelId] received after [afterEpochSeconds] (null = all), newest first, paged. */
    suspend fun messageIds(token: String, labelId: String, afterEpochSeconds: Long?, max: Int = 2000): List<MessageRef> {
        val out = mutableListOf<MessageRef>()
        var pageToken: String? = null
        var pages = 0
        do {
            val page = http.get("$BASE/messages") {
                bearerAuth(token)
                parameter("labelIds", labelId)
                afterEpochSeconds?.let { parameter("q", "after:$it") }
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
