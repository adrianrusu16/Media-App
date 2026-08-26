package com.adrianrusu.pandawave.core.telemetry

private const val LOGCAT_TAG_PREFIX = "PandaWave"

enum class TelemetryModule(tagSuffix: String) {
    App("App"),
    AppShell("AppShell"),
    Home("Home"),
    Nps("Nps"),
    Search("Search"),
    Auth("Auth"),
    Account("Account"),
    Playback("Playback"),
    Media3("Media3"),
    Media("Media"),
    Player("Player"),
    Ambient("Ambient"),
    RustBridge("RustBridge"),
    Library("Library"),
    Preferences("Preferences"),
    Automotive("Automotive");

    val logcatTag: String = "$LOGCAT_TAG_PREFIX:$tagSuffix"
}

object TelemetryAttributeNames {
    const val EXCEPTION_TYPE = "exception_type"
}
