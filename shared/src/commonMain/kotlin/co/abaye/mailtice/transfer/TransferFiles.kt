package co.abaye.mailtice.transfer

/** Where transfer files are written and how one is chosen to import. */
internal expect object TransferFiles {
    /** False where there is no "open file" dialog (Android): the import button is not offered. */
    val canImport: Boolean

    /**
     * Writes a new archive named [fileName] under [root] (empty = the default downloads folder;
     * an existing name gets " (2)"...) with [write]. Returns where it went, for
     * [co.abaye.mailtice.platform.Platform.revealDownload]. A failure removes the partial file
     * and throws.
     */
    fun export(root: String, fileName: String, write: (ArchiveSink) -> Unit): String

    /** The OS "open file" dialog for a transfer file; its path, or null when cancelled. */
    suspend fun pick(title: String): String?

    /** The archive at [path], read as a stream. */
    fun open(path: String): ArchiveSource
}
