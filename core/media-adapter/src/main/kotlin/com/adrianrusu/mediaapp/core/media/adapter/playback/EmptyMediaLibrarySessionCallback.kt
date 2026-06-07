package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.session.MediaLibraryService.MediaLibrarySession

/**
 * Placeholder Media3 library callback for the platform session foundation.
 *
 * Browse/search results will be backed by Rust-owned catalog state in a later
 * milestone, once Supabase/Jamendo and the local data layer are introduced.
 */
internal object EmptyMediaLibrarySessionCallback : MediaLibrarySession.Callback
