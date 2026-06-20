package com.adrianrusu.mediaapp.core.model.theme

enum class PandaWaveThemePreference(val wireValue: String) {
    SystemDefault("system_default"),
    BambooGroveLight("bamboo_grove_light"),
    MoonlitBambooDark("moonlit_bamboo_dark"),
    ForestTechLight("forest_tech_light"),
    ForestTechDark("forest_tech_dark");

    companion object {
        fun fromWireOrNull(value: String): PandaWaveThemePreference? = entries.firstOrNull { preference ->
            preference.wireValue == value
        }
    }
}
