use panda_engine_core::{EngineProfile, EngineProfileUpdate};

#[test]
fn profile_update_preserves_absent_display_name_distinct_from_empty_text() {
    let profile = EngineProfile {
        id: "profile-1".into(),
        external_user_id: "account-1".into(),
        display_name: Some("Driver".into()),
        created_at_epoch_millis: Some(1_000),
        updated_at_epoch_millis: Some(2_000),
    };
    let absent = EngineProfileUpdate::default();
    let empty = EngineProfileUpdate::display_name(Some(String::new()));

    assert_eq!(
        absent.apply_to(&profile).display_name.as_deref(),
        Some("Driver")
    );
    assert_eq!(empty.apply_to(&profile).display_name.as_deref(), Some(""));
}

#[test]
fn profile_preference_load_command_has_a_stable_wire_value() {
    assert_eq!(
        panda_engine_core::EngineCommand::load_profile_preferences()
            .command_type
            .as_wire(),
        "load_profile_preferences"
    );
}
