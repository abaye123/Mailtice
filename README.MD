# Mailtice

תיבת דואר מאוחדת עם התראות לכמה חשבונות: Gmail ו-Google Workspace, Outlook ו-Microsoft 365, Yahoo, וכל ספק IMAP.
רץ על Windows, macOS, Linux ואנדרואיד, עם ממשק בעברית (RTL) ובאנגלית (LTR). בנוי על Compose Multiplatform + [Nucleus](https://github.com/NucleusFramework/Nucleus),
לפי התבנית של [MusicRadio](https://github.com/abaye123/MusicRadio).

המיילים נשמרים ב-SQLite מקומי (`mailtice.db`). לכל חשבון בוחרים אילו תיקיות או תוויות לסנכרן ולשמור, ועל אילו לקבל
התראות, וגם כמה ימים אחורה לשמור. בהגדרות מוצג הנפח הכולל והנפח של כל חשבון. פעולה שחשבון לא תומך בה (למשל ארכוב
בשרת IMAP בלי תיקיית Archive) פשוט לא מוצגת.

## הגדרה ראשונה: OAuth

כל הערכים נכנסים ל-`oauth.local.properties` (העתק מ-`oauth.local.properties.example`, הקובץ ב-`.gitignore`).
ב-CI אותם שמות מוגדרים כ-secrets. ספק שלא הוגדר עבורו client פשוט לא מופיע באפליקציה.
חשבון IMAP רגיל (סיסמה או סיסמת אפליקציה) לא צריך שום הגדרה.

**Google (Gmail API)**

1. ב-[Google Cloud Console](https://console.cloud.google.com/) צור פרויקט והפעל את **Gmail API**.
2. במסך ההסכמה בחר External והוסף את ה-scope `gmail.modify`. במצב **Testing** ה-refresh token פג אחרי 7 ימים.
   לשימוש אישי העבר ל-**In production** בלי להגיש לאימות, ואשר את מסך "אפליקציה לא מאומתת" פעם אחת לכל חשבון.
3. צור שלושה OAuth clients באותו פרויקט:
   - **Desktop app**: הערכים ל-`GOOGLE_CLIENT_ID` ו-`GOOGLE_CLIENT_SECRET`.
   - **Web application**: הערכים ל-`GOOGLE_WEB_CLIENT_ID` ו-`GOOGLE_WEB_CLIENT_SECRET`. אנדרואיד מחליף איתו את הקוד.
   - **Android**: package `co.abaye.mailtice` ו-SHA-1 של כל מפתח חתימה (debug ו-release). אין לו ערכים לקובץ.

**Microsoft (Outlook / 365)**

App registration ב-Entra, עם קהל "כל חשבון ארגוני + חשבונות Microsoft אישיים". בפלטפורמה **Mobile and desktop**
מוסיפים שני redirects: `http://localhost` ו-`co.abaye.mailtice://oauth`. ההרשאות: `IMAP.AccessAsUser.All`
(Office 365 Exchange Online), `offline_access`, `openid`, `email`. זה public client בלי secret, והערך היחיד הוא
`MICROSOFT_CLIENT_ID`.

**Yahoo / AOL**

Yahoo דורשת בקשה ואישור לגישה ל-scope של הדואר, ו-redirect ב-https בלבד. מארחים את `website/oauth-relay.html`
(למשל ב-GitHub Pages), רושמים את הכתובת שלו אצל Yahoo, ומכניסים אותה ל-`YAHOO_REDIRECT_URI`.
עד שיש אישור, מוסיפים חשבון Yahoo כ"ספק אחר (IMAP)" עם סיסמת אפליקציה. באנדרואיד Yahoo OAuth עוד לא מחובר.

## הרצה ובנייה

```bash
./gradlew :desktopApp:run                  # הרצה בדסקטופ
./gradlew :desktopApp:hotRun --auto        # hot reload
./gradlew :androidApp:installDebug         # התקנה על מכשיר / אמולטור
./gradlew :shared:jvmTest                  # בדיקות
./gradlew :desktopApp:packageGraalvmNsis   # מתקין שחרור ל-Windows (GraalVM)
```

מאחורי NetFree: `$env:GRADLE_OPTS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT'` לפני הבנייה. באפליקציה עצמה
IMAP בדסקטופ משתמש במאגר האישורים של מערכת ההפעלה (Nucleus native SSL), ובאנדרואיד ב-`network_security_config`
עם שורשי NetFree.

## שחרור

כמו ב-MusicRadio: תג `v*` מריץ את `release.yaml`, שבונה Windows, Linux x64/ARM64, macOS ARM/Intel ו-Android,
ומפרסם הכל ל-GitHub Release אחד עם קבצי העדכון העצמי. חתימת אנדרואיד ו-Linux: אותם secrets כמו ב-MusicRadio.

## מה עוד פתוח

- **אייקונים:** של MusicRadio, בדסקטופ ובאנדרואיד.
- **סודות בדסקטופ:** `secrets.txt` בתיקיית האפליקציה בלי הצפנה. לפני 1.0 להעביר ל-Credential Manager / Keychain / libsecret.
- **כמה חשבונות Google באנדרואיד:** Play services עלול לבחור את החשבון האחרון. צריך בחירת חשבון דרך Credential Manager (TODO ב-`AndroidAuthorizer`).
- **GraalVM:** Angus Mail ו-sqlite-jdbc צריכים reachability metadata. להריץ את ה-tracing agent לפני שחרור.
- **IMAP IDLE ו-HTML מלא בקורא:** שלב 2.

מבנה הקוד והמוסכמות: `AGENTS.MD`.

## Credits

Temporary app icon (to be removed once Mailtice has its own icon):
<a target="_blank" href="https://icons8.com/icon/td499GRWwrWC/gmail-logo">Gmail Logo</a> icon by <a target="_blank" href="https://icons8.com">Icons8</a>
