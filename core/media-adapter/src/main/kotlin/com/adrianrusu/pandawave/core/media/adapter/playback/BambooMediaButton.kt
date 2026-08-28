package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton

@UnstableApi
internal fun bambooMediaButton(
    icon: Int,
    command: Int,
    displayName: CharSequence
): CommandButton = CommandButton.Builder(icon)
    .setDisplayName(displayName)
    .setPlayerCommand(command)
    .build()
