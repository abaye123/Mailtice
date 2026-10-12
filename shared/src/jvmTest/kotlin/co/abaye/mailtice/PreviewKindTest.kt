package co.abaye.mailtice

import co.abaye.mailtice.main.PreviewKind
import co.abaye.mailtice.main.fileUrl
import co.abaye.mailtice.main.previewKindOf
import co.abaye.mailtice.main.previewPage
import java.io.File
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** On the desktop (where these tests run) every kind has a viewer. */
class PreviewKindTest {
    @Test
    fun kindsFollowNameAndType() {
        assertEquals(PreviewKind.Image, previewKindOf("photo.JPG", ""))
        assertEquals(PreviewKind.Image, previewKindOf("image", "image/png"))
        assertEquals(PreviewKind.Text, previewKindOf("notes.txt", "application/octet-stream"))
        assertEquals(PreviewKind.Text, previewKindOf("data", "application/json"))
        assertEquals(PreviewKind.Html, previewKindOf("page.htm", ""))
        assertEquals(PreviewKind.Pdf, previewKindOf("invoice.pdf", ""))
        assertEquals(PreviewKind.Audio, previewKindOf("voice.m4a", ""))
        assertEquals(PreviewKind.Video, previewKindOf("clip", "video/mp4"))
        assertEquals(PreviewKind.None, previewKindOf("archive.zip", "application/zip"))
        assertEquals(PreviewKind.None, previewKindOf("logo.svg", "image/svg+xml"))
    }

    @Test
    fun playerPointsAtTheFileNextToIt() {
        val page = previewPage(PreviewKind.Video, "הקלטה 1.mp4").orEmpty()
        assertTrue("<video controls" in page)
        assertTrue("src=\"%D7%94%D7%A7%D7%9C%D7%98%D7%94%201.mp4\"" in page)
    }

    // macOS WebKit takes the URL through NSURL URLWithString, which gives nothing for raw Hebrew.
    @Test
    fun aFileUrlIsPlainAsciiAndLeadsBackToTheFile() {
        val file = File(System.getProperty("java.io.tmpdir"), "Mailtice preview/הו_ר, שמ_ע ושמח_ת, גליון 13 #1 %.pdf")
        val url = fileUrl(file.path)
        assertTrue(url.all { it.code in 33..126 }, url)
        assertTrue(url.startsWith("file:///") && "%D7%94%D7%95_%D7%A8%2C%20" in url, url)
        assertEquals(file.absoluteFile, File(URI(url)))
        assertEquals("file:///C:/Users/a/x%20y.pdf", fileUrl("C:\\Users\\a\\x y.pdf"))
    }
}
