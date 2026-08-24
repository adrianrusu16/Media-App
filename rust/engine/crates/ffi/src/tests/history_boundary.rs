use panda_engine_core::{EngineHistoryEntry, EngineHistorySettings, EngineSnapshot};

use crate::mappings::{command_from_ffi, platform_event_from_ffi};
use crate::{
    FFI_COMMAND_CLEAR_HISTORY, FFI_COMMAND_LOAD_HISTORY_SETTINGS,
    FFI_COMMAND_UPDATE_HISTORY_SETTINGS, FFI_PLATFORM_EVENT_PLAYBACK_COMPLETED,
    FFI_PLATFORM_EVENT_PLAYBACK_POSITION_CHECKPOINT, FfiEngineSnapshot,
};

#[test]
fn ffi_snapshot_projects_only_history_consent_and_counts() {
    let mut snapshot = EngineSnapshot::idle(1);
    snapshot.history_settings = Some(EngineHistorySettings { enabled: true });
    snapshot.history_state.generation = 3;
    snapshot.history_deleted_count = 7;
    snapshot.history_entries = vec![EngineHistoryEntry {
        id: "history-1".into(),
        played_at_epoch_millis: None,
        duration_millis: 1,
        completion_ratio: 1.0,
        track: None,
    }];

    let ffi = FfiEngineSnapshot::from(&snapshot);

    assert!(ffi.has_history_settings);
    assert!(ffi.history_enabled);
    assert_eq!(ffi.history_deleted_count, 7);
    assert_eq!(ffi.history_entries_count, 1);
    assert_eq!(ffi.history_generation, 3);
}

#[test]
fn ffi_discriminants_map_history_commands_and_completion_event() {
    assert_eq!(
        command_from_ffi(FFI_COMMAND_LOAD_HISTORY_SETTINGS),
        panda_engine_core::EngineCommandType::LoadHistorySettings
    );
    assert!(matches!(
        command_from_ffi(FFI_COMMAND_UPDATE_HISTORY_SETTINGS),
        panda_engine_core::EngineCommandType::UpdateHistorySettings { .. }
    ));
    assert_eq!(
        command_from_ffi(FFI_COMMAND_CLEAR_HISTORY),
        panda_engine_core::EngineCommandType::ClearHistory
    );
    assert_eq!(
        platform_event_from_ffi(FFI_PLATFORM_EVENT_PLAYBACK_COMPLETED),
        panda_engine_core::EnginePlatformEventType::PlaybackCompleted
    );
    assert_eq!(
        platform_event_from_ffi(FFI_PLATFORM_EVENT_PLAYBACK_POSITION_CHECKPOINT),
        panda_engine_core::EnginePlatformEventType::PlaybackPositionCheckpoint
    );
}
