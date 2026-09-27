package co.abaye.mailtice.dev

import co.abaye.mailtice.domain.Account
import co.abaye.mailtice.domain.AccountColor
import co.abaye.mailtice.domain.Attachment
import co.abaye.mailtice.domain.Capabilities
import co.abaye.mailtice.domain.FolderRole
import co.abaye.mailtice.domain.ImapServer
import co.abaye.mailtice.domain.ProviderKind

/*
 * Fixture content for demo mode. Addresses use the reserved example.* domains and every person is
 * fictional. The mail mixes Hebrew and English on purpose, so direction handling (RTL chrome with
 * LTR content and the other way round) is exercised on every screen.
 */

private const val MINUTE = 1L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

/** A folder of a demo mailbox. [key] is the stable part of the folder id. */
internal data class DemoFolder(val key: String, val name: String, val role: FolderRole, val color: String = "")

/** One fixture message. [minutesAgo] is relative to the moment the mailbox is first synced. */
internal data class DemoMail(
    val fromName: String,
    val fromAddress: String,
    val subject: String,
    val text: String,
    val minutesAgo: Long,
    val unread: Boolean = false,
    val flagged: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    /** Also send an HTML part, so "open as HTML" has something to show. */
    val html: Boolean = false,
    /** Keys of the folders holding the message (Gmail: labels). */
    val folders: List<String> = listOf(DemoFolders.INBOX),
)

internal object DemoFolders {
    const val INBOX = "INBOX"
    const val SENT = "SENT"
    const val ARCHIVE = "Archive"
    const val WORK = "work"
    const val RECEIPTS = "receipts"
    const val NEWSLETTERS = "newsletters"
    const val TRASH = "Trash"
    const val SPAM = "Spam"
    const val DRAFTS = "Drafts"

    fun of(kind: ProviderKind): List<DemoFolder> = when (kind) {
        ProviderKind.Gmail -> listOf(
            DemoFolder(INBOX, "Inbox", FolderRole.Inbox),
            DemoFolder(SENT, "Sent", FolderRole.Sent),
            DemoFolder(WORK, "עבודה", FolderRole.Other, color = "#16a765"),
            DemoFolder(RECEIPTS, "קבלות", FolderRole.Other, color = "#ffad47"),
            DemoFolder(DRAFTS, "Drafts", FolderRole.Drafts),
            DemoFolder(SPAM, "Spam", FolderRole.Spam),
            DemoFolder(TRASH, "Trash", FolderRole.Trash),
        )
        else -> listOf(
            DemoFolder(INBOX, "Inbox", FolderRole.Inbox),
            DemoFolder(SENT, "Sent Items", FolderRole.Sent),
            DemoFolder(ARCHIVE, "Archive", FolderRole.Archive),
            DemoFolder(NEWSLETTERS, "Newsletters", FolderRole.Other),
            DemoFolder(DRAFTS, "Drafts", FolderRole.Drafts),
            DemoFolder(SPAM, "Junk Email", FolderRole.Spam),
            DemoFolder(TRASH, "Deleted Items", FolderRole.Trash),
        )
    }
}

/** The accounts the [DemoScenario.Full] scenario starts with. */
internal object DemoAccounts {
    const val PERSONAL_ID = "demo-personal"
    const val WORK_ID = "demo-work"
    const val QUIET_ID = "demo-quiet"

    fun initial(): List<Account> = listOf(
        Account(
            id = PERSONAL_ID,
            kind = ProviderKind.Gmail,
            email = "noa.levi@example.com",
            label = "אישי",
            color = AccountColor.Blue,
            capabilities = Capabilities.Gmail,
        ),
        Account(
            id = WORK_ID,
            kind = ProviderKind.Microsoft,
            email = "dan.cohen@work.example.com",
            label = "Work",
            color = AccountColor.Orange,
            capabilities = imapCapabilities(),
            imap = ImapServer("outlook.office365.com"),
        ),
        Account(
            id = QUIET_ID,
            kind = ProviderKind.Imap,
            email = "hello@quiet.example.org",
            color = AccountColor.Green,
            capabilities = imapCapabilities(),
            imap = ImapServer("imap.quiet.example.org"),
        ),
    )

    fun imapCapabilities() = Capabilities(
        markRead = true, archive = true, incremental = true, idle = true, send = true, trash = true, drafts = true, manageLabels = true,
    )

    /** Mail for an account id. Accounts added from the UI in demo mode get the generic mailbox. */
    fun mailFor(accountId: String): List<DemoMail> = when (accountId) {
        PERSONAL_ID -> personalMail + spamAndDrafts
        WORK_ID -> workMail + spamAndDrafts
        QUIET_ID -> emptyList()
        else -> genericMail
    }
}

private val personalMail = listOf(
    DemoMail(
        "מיכל אברהם", "michal.a@example.com", "ארוחת שישי אצלנו? 🍲",
        "היי נועה! חשבנו לעשות ארוחת שישי השבוע, תגידי אם אתם מגיעים ואם להביא משהו. יהיו גם סבא וסבתא.",
        minutesAgo = 12 * MINUTE, unread = true,
    ),
    DemoMail(
        "בנק הדמו", "no-reply@bank.example.com", "תדפיס חודשי - ספטמבר",
        "התדפיס החודשי שלך מוכן לצפייה. היתרה בחשבון עודכנה ליום האחרון של החודש. מסמך מצורף.",
        minutesAgo = 47 * MINUTE, unread = true, html = true,
        attachments = listOf(Attachment("statement-09.pdf", 184_320)), folders = listOf(DemoFolders.INBOX, DemoFolders.RECEIPTS),
    ),
    DemoMail(
        "CodeHub", "noreply@codehub.example.com", "[Mailtice] Pull request #42 was merged",
        "Merged #42 into main: \"Empty states with illustrations\". 3 files changed, 214 additions, 12 deletions.",
        minutesAgo = 2 * HOUR, unread = true, folders = listOf(DemoFolders.INBOX, DemoFolders.WORK),
    ),
    DemoMail(
        "יוסי מזרחי", "yossi.m@example.net", "Re: הצעת מחיר לשיפוץ המטבח",
        "שלום נועה, מצרף את הצעת המחיר המעודכנת כולל ארונות עליונים ומשטח קוורץ. ההצעה בתוקף עד סוף החודש. " +
            "אם יש שאלות אשמח לדבר בטלפון, אני זמין בדרך כלל אחרי ארבע.",
        minutesAgo = 3 * HOUR + 20, flagged = true,
        attachments = listOf(Attachment("הצעת מחיר - מטבח.pdf", 512_000), Attachment("הדמיה.jpg", 1_843_200)),
    ),
    DemoMail(
        "SkyFly Airlines", "booking@airline.example.com", "Your booking is confirmed: TLV → LHR",
        "Booking reference XK4P2Q. Flight on 14 Oct, departing 06:10 from Terminal 3. Online check-in opens 24 hours before departure.",
        minutesAgo = 5 * HOUR, html = true,
        attachments = listOf(Attachment("e-ticket.pdf", 96_256)), folders = listOf(DemoFolders.INBOX, DemoFolders.RECEIPTS),
    ),
    DemoMail(
        "ועד הבית", "vaad@building.example.org", "תזכורת: ניקיון חדר המדרגות ביום רביעי",
        "שלום לכולם, ביום רביעי בבוקר תתבצע עבודת ניקיון יסודית בחדר המדרגות. נא לא להשאיר חפצים במבואה.",
        minutesAgo = 9 * HOUR,
    ),
    DemoMail(
        "Tunes", "no-reply@music.example.com", "Your 2026 Wrapped is almost here",
        "Get ready to relive your year in music. We counted every play, skip and repeat.",
        minutesAgo = 1 * DAY + 2 * HOUR,
    ),
    DemoMail(
        "אמא", "mom@example.com", "תמונות מהטיול 📸",
        "הנה התמונות מהטיול בגליל. הילדים יצאו מקסימים! תעבירי גם לאבא.",
        minutesAgo = 1 * DAY + 6 * HOUR, flagged = true,
        attachments = listOf(
            Attachment("IMG_2041.jpg", 2_310_144),
            Attachment("IMG_2042.jpg", 2_150_400),
            Attachment("IMG_2047.jpg", 1_998_848),
        ),
    ),
    DemoMail(
        "ShopNow", "shipment@shop.example.com", "Your package has shipped",
        "Your order #114-2291873 has shipped and should arrive by Thursday. Track your package in the app.",
        minutesAgo = 2 * DAY, folders = listOf(DemoFolders.INBOX, DemoFolders.RECEIPTS),
    ),
    DemoMail(
        "רועי שטרן", "roi.stern@example.net",
        "סיכום פגישה: תכנון הרבעון הבא, חלוקת משימות, לוחות זמנים ומה שנשאר פתוח מהפעם הקודמת",
        "נושא ארוך במיוחד כדי לבדוק חיתוך טקסט בשורה אחת. בגוף ההודעה: 1) לסגור את המפרט עד יום ראשון. " +
            "2) לבדוק מול ספקים. 3) לקבוע פגישת המשך.",
        minutesAgo = 2 * DAY + 5 * HOUR, folders = listOf(DemoFolders.INBOX, DemoFolders.WORK),
    ),
    DemoMail(
        "LingoBird", "hello@lingo.example.com", "🦉 5 minutes a day keeps the streak alive",
        "You're on a 41-day streak. Keep it going with a quick lesson today.",
        minutesAgo = 3 * DAY,
    ),
    DemoMail(
        "אנרגיה פלוס", "billing@power.example.org", "החשבונית שלך לתקופה יולי-אוגוסט",
        "סכום לתשלום: 412.80 ₪. מועד חיוב: 10 לחודש. החשבונית המלאה מצורפת.",
        minutesAgo = 4 * DAY, attachments = listOf(Attachment("invoice-0725.pdf", 88_064)),
        folders = listOf(DemoFolders.INBOX, DemoFolders.RECEIPTS),
    ),
    DemoMail(
        "Tamar Friedman", "tamar.f@example.com", "Mixed direction: שלום and hello in one line",
        "This body starts in English but switches: הטקסט הזה בעברית, then back to English with a link https://example.com/docs.",
        minutesAgo = 5 * DAY,
    ),
    DemoMail(
        "noa.levi@example.com", "noa.levi@example.com", "Re: ארוחת שישי אצלנו?",
        "מגיעים! נביא עוגה.",
        minutesAgo = 6 * DAY, folders = listOf(DemoFolders.SENT),
    ),
    DemoMail(
        "מרפאת השכונה", "clinic@health.example.org", "תזכורת לתור: ד\"ר גולן, יום שלישי 09:30",
        "זוהי תזכורת לתור שנקבע עבורך. לביטול או שינוי ניתן להשיב להודעה זו.",
        minutesAgo = 8 * DAY,
    ),
    DemoMail(
        "WorkNet", "notifications@social.example.com", "You appeared in 9 searches this week",
        "See who's looking at your profile and what they searched for.",
        minutesAgo = 12 * DAY,
    ),
    DemoMail(
        "גלעד בן דוד", "gilad.bd@example.net", "",
        "הודעה בלי נושא, כדי לבדוק איך הרשימה מציגה שורה ריקה.",
        minutesAgo = 16 * DAY,
    ),
    DemoMail(
        "StreamBox", "info@stream.example.com", "New on the watch list this weekend",
        "Three new series and a documentary you might like, based on what you watched.",
        minutesAgo = 21 * DAY, html = true,
    ),
)

/** Spam and drafts, shared by the demo accounts so every folder view has something in it. */
private val spamAndDrafts = listOf(
    DemoMail(
        "Prize Center", "winner@lottery.example.net", "Congratulations!!! You have WON 1,000,000",
        "Claim your prize today by replying with your bank details. This offer expires in 24 hours.",
        minutesAgo = 3 * HOUR, unread = true, folders = listOf(DemoFolders.SPAM),
    ),
    DemoMail(
        "מבצע בלעדי", "deals@promo.example.org", "רק היום: 90% הנחה על הכל",
        "לחץ כאן כדי לממש את ההטבה. מספר המקומות מוגבל.",
        minutesAgo = 1 * DAY, unread = true, folders = listOf(DemoFolders.SPAM),
    ),
    DemoMail(
        "(draft)", "me@example.com", "טיוטה: סיכום פגישה עם הספק",
        "נקודות לסיכום: מחיר, לוחות זמנים, אחריות. להשלים לפני ששולחים.",
        minutesAgo = 5 * HOUR, folders = listOf(DemoFolders.DRAFTS),
    ),
)

private val workMail = listOf(
    DemoMail(
        "Sarah Klein", "sarah.klein@work.example.com", "Q4 roadmap review - slides attached",
        "Hi Dan, attaching the deck for tomorrow's review. Please look at slides 7-12 (sync reliability) before the meeting.",
        minutesAgo = 25 * MINUTE, unread = true, flagged = true,
        attachments = listOf(Attachment("Q4-roadmap.pptx", 4_718_592)),
    ),
    DemoMail(
        "Tracker", "jira@work.example.com", "[MAIL-311] Notification sound plays twice on Windows",
        "Status changed from In Progress to In Review by Omer Katz. 2 new comments.",
        minutesAgo = 1 * HOUR + 10, unread = true,
    ),
    DemoMail(
        "עומר כץ", "omer.katz@work.example.com", "שאלה קטנה לגבי ה-IMAP IDLE",
        "דן, ראיתי שהחיבור נסגר אחרי 29 דקות בדיוק. זה מכוון? אולי כדאי לחדש את ה-IDLE קצת לפני.",
        minutesAgo = 2 * HOUR + 40, unread = true,
    ),
    DemoMail(
        "HR Team", "hr@work.example.com", "Reminder: submit your hours by Thursday",
        "A friendly reminder to submit your timesheet for September by Thursday 18:00.",
        minutesAgo = 6 * HOUR, html = true,
    ),
    DemoMail(
        "Calendar", "calendar@work.example.com", "Invitation: Design sync @ Wed 11:00 - 11:30",
        "Organizer: Sarah Klein. Join with the video link in the invitation. Agenda: empty states, onboarding.",
        minutesAgo = 20 * HOUR, attachments = listOf(Attachment("invite.ics", 2_048)),
    ),
    DemoMail(
        "Lior Peretz", "lior.p@partner.example.net", "Contract draft v3",
        "Hi Dan, here is v3 with the changes from our call. Section 4.2 is the only open point on our side.",
        minutesAgo = 1 * DAY + 3 * HOUR, attachments = listOf(Attachment("Contract-v3.docx", 245_760)),
    ),
    DemoMail(
        "IT Helpdesk", "it@work.example.com", "Scheduled maintenance: VPN, Saturday 22:00-02:00",
        "The VPN will be unavailable during the maintenance window. No action is needed on your side.",
        minutesAgo = 2 * DAY + 1 * HOUR,
    ),
    DemoMail(
        "Tech Weekly", "digest@news.example.com", "This week: Kotlin 2.4, Compose hot reload tips",
        "Your weekly digest of engineering reads. Top story: shaving seconds off your inner loop.",
        minutesAgo = 3 * DAY, html = true, folders = listOf(DemoFolders.NEWSLETTERS),
    ),
    DemoMail(
        "dan.cohen@work.example.com", "dan.cohen@work.example.com", "Re: Q3 retro notes",
        "Thanks all. I've added the action items to the board.",
        minutesAgo = 4 * DAY, folders = listOf(DemoFolders.SENT),
    ),
    DemoMail(
        "מיה רוזן", "maya.r@work.example.com", "ברוך הבא לצוות! 🎉",
        "שמחים שהצטרפת. מצרפת את מסמך ה-onboarding ואת רשימת האנשים שכדאי להכיר בשבוע הראשון.",
        minutesAgo = 9 * DAY, flagged = true, attachments = listOf(Attachment("onboarding.pdf", 356_352)),
    ),
    DemoMail(
        "Old thread", "archive-bot@work.example.com", "Archived: design principles",
        "An older message that already lives in the archive folder.",
        minutesAgo = 14 * DAY, folders = listOf(DemoFolders.ARCHIVE),
    ),
)

private val genericMail = listOf(
    DemoMail(
        "Mailtice Demo", "demo@example.com", "ברוכים הבאים לחשבון הדמו",
        "החשבון הזה נוצר במצב דמו. שום דבר לא נשלח לרשת, וכל הפעולות נשמרות רק בזיכרון.",
        minutesAgo = 3 * MINUTE, unread = true,
    ),
    DemoMail(
        "Alex Morgan", "alex.m@example.net", "Welcome to your demo mailbox",
        "Everything here is generated locally. Press refresh a few times to receive new mail.",
        minutesAgo = 4 * HOUR,
    ),
    DemoMail(
        "שירה נחום", "shira.n@example.com", "קבלה על תרומה",
        "תודה על תרומתך! הקבלה מצורפת.",
        minutesAgo = 2 * DAY, attachments = listOf(Attachment("receipt.pdf", 40_960)),
    ),
)

/** Mail that "arrives" during the session, one piece every few sync rounds. */
internal val incomingMail = listOf(
    DemoMail("דנה ישראלי", "dana.i@example.com", "הגעתי, מחכה בכניסה", "אני ליד הקופות, תגיד כשאתה פה.", minutesAgo = 0, unread = true),
    DemoMail(
        "Build bot", "ci@work.example.com", "✅ Build #1288 passed",
        "All 214 tests passed in 3m 12s on main.", minutesAgo = 0, unread = true,
    ),
    DemoMail(
        "FoodRun", "orders@food.example.com", "ההזמנה שלך בדרך 🛵",
        "השליח יצא מהמסעדה. זמן הגעה משוער: 18 דקות.", minutesAgo = 0, unread = true,
    ),
    DemoMail(
        "Sarah Klein", "sarah.klein@work.example.com", "Quick question",
        "Do you have five minutes before the design sync? Want to check one thing about the empty states.",
        minutesAgo = 0, unread = true,
    ),
    DemoMail("אבא", "dad@example.com", "מתקשר בערב", "תענה הפעם 😄", minutesAgo = 0, unread = true),
)
