package com.adrianrusu.pandawave.core.rust.bridge.aidl

data class EngineThemePreference(
    val themeId: String,
    val source: String,
    val revision: Long,
    val initialized: Boolean
) {
    companion object {
        const val THEME_SYSTEM_DEFAULT = "system_default"
        const val THEME_BAMBOO_GROVE_LIGHT = "bamboo_grove_light"
        const val THEME_MOONLIT_BAMBOO_DARK = "moonlit_bamboo_dark"
        const val THEME_FOREST_TECH_LIGHT = "forest_tech_light"
        const val THEME_FOREST_TECH_DARK = "forest_tech_dark"

        const val SOURCE_UNINITIALIZED = "uninitialized"
        const val SOURCE_LOCAL_CACHE = "local_cache"
        const val SOURCE_LOCAL_USER = "local_user"
        const val SOURCE_REMOTE_PROFILE = "remote_profile"

        fun uninitialized(): EngineThemePreference = EngineThemePreference(
            themeId = THEME_SYSTEM_DEFAULT,
            source = SOURCE_UNINITIALIZED,
            revision = 0L,
            initialized = false
        )
    }
}
