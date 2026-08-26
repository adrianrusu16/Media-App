package com.adrianrusu.pandawave.core.ui.playback

/**
 * Max wall-clock advance from the last engine progress tick.
 * Matches the 5s checkpoint interval plus slack so a stale command clock
 * cannot run the bar to 100% while audio is still in the middle of the track.
 */
const val MAX_PROGRESS_INTERPOLATION_GAP_MILLIS = 12_000L
