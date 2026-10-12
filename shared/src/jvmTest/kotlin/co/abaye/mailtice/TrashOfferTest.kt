package co.abaye.mailtice

import co.abaye.mailtice.app.AppState
import co.abaye.mailtice.app.InboxFilter
import co.abaye.mailtice.app.Reader
import co.abaye.mailtice.domain.Folder
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.domain.MailMessage
import co.abaye.mailtice.domain.MailView
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrashOfferTest {
    private val state = AppState(
        folders = mapOf(
            "acc" to listOf(
                Folder("acc", "INBOX", "Inbox", FolderRole.Inbox, sync = true, notify = true),
                Folder("acc", "TRASH", "Trash", FolderRole.Trash, sync = true, notify = false),
            ),
        ),
    )

    private fun message(id: String, vararg folders: String) =
        MailMessage("acc", id, "t", 0, "", "", "", "", "", 0, false, false, false, 0, folders.toList())

    @Test
    fun deleteIsNotOfferedForWhatIsAlreadyInTheTrash() {
        val inbox = message("a", "INBOX")
        val trashed = message("b", "TRASH")
        assertFalse(state.inTrash(inbox))
        assertTrue(state.inTrash(trashed))
        assertFalse(state.trashable(listOf(trashed)))
        // A conversation with one message still outside the trash can still be deleted.
        assertTrue(state.trashable(listOf(trashed, inbox)))
        assertFalse(state.trashable(Reader(trashed)))
        // A row fetched from the server, not stored, has no folders: the view says where it came from.
        assertTrue(state.trashable(listOf(message("c"))))
        assertFalse(state.copy(filter = InboxFilter(view = MailView.Trash)).trashable(listOf(message("c"))))
    }
}
