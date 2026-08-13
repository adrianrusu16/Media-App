package com.adrianrusu.pandawave.core.rust.bridge.aidl

import android.os.Parcel
import android.os.Parcelable

data class EngineCommand(val type: String, val payload: String?) : Parcelable {
    constructor(parcel: Parcel) : this(
        type = parcel.readString().orEmpty(),
        payload = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(type)
        parcel.writeString(payload)
    }

    override fun describeContents(): Int = 0

    companion object {
        const val TYPE_BOOTSTRAP = "bootstrap"
        const val TYPE_PLAY = "play"
        const val TYPE_PAUSE = "pause"
        const val TYPE_SKIP_PREVIOUS = "skip_previous"
        const val TYPE_SKIP_NEXT = "skip_next"
        const val TYPE_START_SESSION = "start_session"
        const val TYPE_END_SESSION = "end_session"
        const val TYPE_SEARCH = "search"
        const val TYPE_BROWSE = "browse"
        const val TYPE_LOAD_NEXT_CATALOG_PAGE = "load_next_catalog_page"
        const val TYPE_SET_SPEED = "set_speed"
        const val TYPE_SEEK = "seek"
        const val TYPE_PLAY_MEDIA_BY_ID = "play_media_by_id"
        const val TYPE_HYDRATE_THEME_PREFERENCE = "hydrate_theme_preference"
        const val TYPE_SET_THEME_PREFERENCE = "set_theme_preference"
        const val TYPE_APPLY_REMOTE_THEME_PREFERENCE = "apply_remote_theme_preference"
        const val TYPE_REFRESH_BACKEND_STATUS = "refresh_backend_status"
        const val TYPE_UPSERT_PROFILE = "upsert_profile"
        const val TYPE_GET_PROFILE = "get_profile"
        const val TYPE_UPDATE_PROFILE = "update_profile"
        const val TYPE_DELETE_PROFILE = "delete_profile"
        const val TYPE_LOAD_PROFILE_PREFERENCES = "load_profile_preferences"
        const val TYPE_UPDATE_PROFILE_PREFERENCES = "update_profile_preferences"
        const val TYPE_LOAD_HISTORY_SETTINGS = "load_history_settings"
        const val TYPE_UPDATE_HISTORY_SETTINGS = "update_history_settings"
        const val TYPE_LIST_HISTORY = "list_history"
        const val TYPE_LOAD_NEXT_HISTORY_PAGE = "load_next_history_page"
        const val TYPE_DELETE_HISTORY_ENTRY = "delete_history_entry"
        const val TYPE_CLEAR_HISTORY = "clear_history"
        const val TYPE_SAVE_TRACK = "save_track"
        const val TYPE_REMOVE_SAVED_TRACK = "remove_saved_track"
        const val TYPE_LIST_SAVED_TRACKS = "list_saved_tracks"
        const val TYPE_LOAD_NEXT_SAVED_TRACKS_PAGE = "load_next_saved_tracks_page"
        const val TYPE_LIKE_TRACK = "like_track"
        const val TYPE_UNLIKE_TRACK = "unlike_track"
        const val TYPE_LIST_LIKED_TRACKS = "list_liked_tracks"
        const val TYPE_LOAD_NEXT_LIKED_TRACKS_PAGE = "load_next_liked_tracks_page"
        const val TYPE_CREATE_PLAYLIST = "create_playlist"
        const val TYPE_UPDATE_PLAYLIST = "update_playlist"
        const val TYPE_DELETE_PLAYLIST = "delete_playlist"
        const val TYPE_LIST_PLAYLISTS = "list_playlists"
        const val TYPE_LOAD_NEXT_PLAYLISTS_PAGE = "load_next_playlists_page"
        const val TYPE_ADD_PLAYLIST_TRACK = "add_playlist_track"
        const val TYPE_REMOVE_PLAYLIST_TRACK = "remove_playlist_track"
        const val TYPE_LIST_PLAYLIST_TRACKS = "list_playlist_tracks"
        const val TYPE_LOAD_NEXT_PLAYLIST_TRACKS_PAGE = "load_next_playlist_tracks_page"
        const val TYPE_REORDER_PLAYLIST_TRACKS = "reorder_playlist_tracks"
        const val TYPE_GET_ACCOUNT = "get_account"
        const val TYPE_DELETE_ACCOUNT = "delete_account"
        const val TYPE_LIST_DEVICE_SESSIONS = "list_device_sessions"
        const val TYPE_LOAD_NEXT_DEVICE_SESSIONS_PAGE = "load_next_device_sessions_page"
        const val TYPE_REVOKE_DEVICE_SESSION = "revoke_device_session"
        const val TYPE_LOAD_DISCOVERY_FEED = "load_discovery_feed"
        const val TYPE_LOAD_NEXT_DISCOVERY_PAGE = "load_next_discovery_page"

        @JvmField
        val CREATOR: Parcelable.Creator<EngineCommand> =
            object : Parcelable.Creator<EngineCommand> {
                override fun createFromParcel(parcel: Parcel): EngineCommand = EngineCommand(parcel)

                override fun newArray(size: Int): Array<EngineCommand?> = arrayOfNulls(size)
            }
    }
}
