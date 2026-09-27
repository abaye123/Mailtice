package co.abaye.mailtice.di

import co.abaye.mailtice.sync.NetworkProbe
import co.abaye.mailtice.translate.GtxTranslator
import co.abaye.mailtice.translate.Translator
import co.abaye.mailtice.auth.AuthManager
import co.abaye.mailtice.auth.Authorizer
import co.abaye.mailtice.data.MailRepository
import co.abaye.mailtice.data.SecretStore
import co.abaye.mailtice.data.createSecretStore
import co.abaye.mailtice.data.createSqlDriver
import co.abaye.mailtice.dev.DemoAccounts
import co.abaye.mailtice.dev.DemoMailProvider
import co.abaye.mailtice.dev.DemoMode
import co.abaye.mailtice.dev.DemoScenario
import co.abaye.mailtice.domain.ProviderKind
import co.abaye.mailtice.notify.MailNotifications
import co.abaye.mailtice.notify.Notifier
import co.abaye.mailtice.platform.IoDispatcher
import co.abaye.mailtice.platform.createAuthorizer
import co.abaye.mailtice.platform.createHttpClient
import co.abaye.mailtice.platform.createNotifier
import co.abaye.mailtice.provider.ImapAutoConfig
import co.abaye.mailtice.provider.ImapBackend
import co.abaye.mailtice.provider.MailProviders
import co.abaye.mailtice.provider.createImapBackend
import co.abaye.mailtice.provider.gmail.GmailApi
import co.abaye.mailtice.provider.gmail.GmailProvider
import co.abaye.mailtice.sync.SyncEngine
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

@ContributesTo(AppScope::class)
@BindingContainer
object AppBindings {
    @Provides
    fun provideDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Io
    fun provideIoDispatcher(): CoroutineDispatcher = IoDispatcher

    @Provides
    @SingleIn(AppScope::class)
    fun provideHttpClient(): HttpClient = createHttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
        // Status codes are mapped to ProviderException by the callers.
        expectSuccess = false
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideSecretStore(): SecretStore = createSecretStore()

    @Provides
    @SingleIn(AppScope::class)
    fun provideRepository(@Io io: CoroutineDispatcher): MailRepository = MailRepository(createSqlDriver(), io).also { repo ->
        // Demo data starts empty on every launch; the full scenario fills in its accounts, whose mail
        // then arrives through the normal sync path from DemoMailProvider.
        if (DemoMode.scenario == DemoScenario.Full && repo.accountsNow().isEmpty()) {
            DemoAccounts.initial().forEach(repo::addAccount)
        }
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideAuthManager(http: HttpClient, secrets: SecretStore): AuthManager = AuthManager(http, secrets)

    @Provides
    @SingleIn(AppScope::class)
    fun provideGmailApi(http: HttpClient): GmailApi = GmailApi(http)

    @Provides
    @SingleIn(AppScope::class)
    fun provideTranslator(http: HttpClient): Translator = GtxTranslator(http)

    @Provides
    @SingleIn(AppScope::class)
    fun provideGmailProvider(api: GmailApi, auth: AuthManager): GmailProvider = GmailProvider(api, auth)

    @Provides
    @SingleIn(AppScope::class)
    fun provideImapBackend(auth: AuthManager): ImapBackend = createImapBackend(auth)

    @Provides
    @SingleIn(AppScope::class)
    fun provideMailProviders(gmail: GmailProvider, imap: ImapBackend): MailProviders {
        if (DemoMode.enabled) {
            val demo = DemoMailProvider()
            return MailProviders { demo }
        }
        return MailProviders { account -> if (account.kind == ProviderKind.Gmail) gmail else imap }
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideAutoConfig(http: HttpClient): ImapAutoConfig = ImapAutoConfig(http)

    // One engine per process: the UI loops and the Android worker share its per-account locks.
    @Provides
    @SingleIn(AppScope::class)
    fun provideSyncEngine(repo: MailRepository, providers: MailProviders, http: HttpClient): SyncEngine =
        SyncEngine(repo, providers, NetworkProbe(http))

    @Provides
    @SingleIn(AppScope::class)
    fun provideAuthorizer(): Authorizer = createAuthorizer()

    // One registration per process: the OS notification center keys on the app, not the window.
    @Provides
    @SingleIn(AppScope::class)
    fun provideNotifier(): Notifier = createNotifier()

    @Provides
    @SingleIn(AppScope::class)
    fun provideMailNotifications(notifier: Notifier): MailNotifications = MailNotifications(notifier)
}
