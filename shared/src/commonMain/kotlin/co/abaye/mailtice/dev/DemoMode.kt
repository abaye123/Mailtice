package co.abaye.mailtice.dev

/**
 * What the app runs against in demo mode. Demo mode is for development and UI tests only: every
 * account is served by [DemoMailProvider], nothing touches the network, and the data lives in its
 * own directory so a developer's real accounts are never read or changed.
 */
enum class DemoScenario {
    Off,

    /** Three accounts with a realistic mix of Hebrew and English mail, plus one quiet mailbox. */
    Full,

    /** No accounts: the onboarding and empty screens. Accounts added from the UI are demo accounts. */
    Empty,
    ;

    companion object {
        /** `full` / `true` / `1` -> [Full], `empty` -> [Empty], anything else (or nothing) -> [Off]. */
        fun parse(raw: String?): DemoScenario = when (raw?.trim()?.lowercase()) {
            "full", "true", "1", "on", "demo" -> Full
            "empty" -> Empty
            else -> Off
        }
    }
}

/**
 * Process-wide demo switch. Set once by the host before the dependency graph is built (see
 * `enableDemoModeFromEnvironment` on desktop) and read-only afterwards.
 */
object DemoMode {
    var scenario: DemoScenario = DemoScenario.Off
        private set

    val enabled: Boolean get() = scenario != DemoScenario.Off

    fun start(scenario: DemoScenario) {
        this.scenario = scenario
        if (enabled) println("Demo mode: ${scenario.name} (no network, separate data directory)")
    }
}
