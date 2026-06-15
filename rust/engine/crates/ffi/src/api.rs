// FFI API module root:
// - Groups C entrypoints by responsibility and re-exports stable symbols.
// - Keeps function names unchanged while reducing root-file complexity.
mod dispatch;
mod lifecycle;
mod persistence;
mod query;
mod queue;

pub use dispatch::{
    panda_engine_dispatch, panda_engine_dispatch_platform_event, panda_engine_process_audio_raw,
};
pub use lifecycle::{
    panda_engine_create, panda_engine_destroy, panda_engine_enable_vosk, panda_engine_init_logging,
    panda_engine_set_observer, panda_engine_tick,
};
pub use persistence::{panda_engine_restore, panda_engine_save};
pub use query::{
    panda_engine_free_string, panda_engine_get_browse_result_album,
    panda_engine_get_browse_result_artist, panda_engine_get_browse_result_id,
    panda_engine_get_browse_result_item_type, panda_engine_get_browse_result_thumbnail_url,
    panda_engine_get_browse_result_title, panda_engine_get_config, panda_engine_get_current_album,
    panda_engine_get_current_artist, panda_engine_get_current_media_id,
    panda_engine_get_current_thumbnail_url, panda_engine_get_current_title,
    panda_engine_get_current_user_id, panda_engine_get_effect_media_id,
    panda_engine_get_effect_notify_message, panda_engine_get_effect_type,
    panda_engine_get_effects_count, panda_engine_get_effects_types,
    panda_engine_get_last_error_message, panda_engine_get_last_event_message,
    panda_engine_get_search_result_album, panda_engine_get_search_result_artist,
    panda_engine_get_search_result_id, panda_engine_get_search_result_item_type,
    panda_engine_get_search_result_thumbnail_url, panda_engine_get_search_result_title,
    panda_engine_get_voice_hypothesis, panda_engine_snapshot,
};
pub use queue::{
    panda_engine_queue_set_items, panda_engine_queue_set_repeat_mode,
    panda_engine_queue_set_shuffle,
};
