package com.adrianrusu.pandawave.feature.nowplaying.domain.ambient

sealed interface AmbientModeInput {
    data class EligibilityChanged(val eligibility: AmbientEligibility, val nowMillis: Long) : AmbientModeInput

    data class TimeoutElapsed(val token: Long) : AmbientModeInput

    data class UserInteraction(val nowMillis: Long) : AmbientModeInput
}
