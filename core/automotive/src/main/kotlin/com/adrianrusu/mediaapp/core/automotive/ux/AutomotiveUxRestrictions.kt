package com.adrianrusu.mediaapp.core.automotive.ux

/**
 * App-level projection of AAOS UX restrictions.
 *
 * Kotlin and Compose depend on this model instead of platform `android.car`
 * classes so the UI and Rust bridge can be tested without a car service.
 */
data class AutomotiveUxRestrictions(
    val source: Source,
    val requiresDistractionOptimization: Boolean,
    val activeRestrictions: Int,
    val maxContentDepth: Int,
    val maxCumulativeContentItems: Int,
    val maxRestrictedStringLength: Int,
) {
    val isRestricted: Boolean
        get() = requiresDistractionOptimization || activeRestrictions != NO_RESTRICTIONS

    fun hasRestriction(restriction: Int): Boolean =
        activeRestrictions and restriction == restriction

    enum class Source {
        AutomotivePlatform,
        NotAutomotive,
        Unavailable,
    }

    companion object {
        const val NO_RESTRICTIONS = 0

        fun unrestricted(source: Source): AutomotiveUxRestrictions =
            AutomotiveUxRestrictions(
                source = source,
                requiresDistractionOptimization = false,
                activeRestrictions = NO_RESTRICTIONS,
                maxContentDepth = Int.MAX_VALUE,
                maxCumulativeContentItems = Int.MAX_VALUE,
                maxRestrictedStringLength = Int.MAX_VALUE,
            )
    }
}
