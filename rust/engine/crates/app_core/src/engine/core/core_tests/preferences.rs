use super::*;
use crate::model::preferences::{PreferenceSource, ThemePreference};

#[tokio::test]
async fn cached_theme_hydrates_engine_preferences() {
    let mut engine = Engine::new(10);

    engine
        .dispatch(
            EngineCommand::hydrate_theme_preference(ThemePreference::ForestTechDark),
            11,
        )
        .await;

    let state = &engine.snapshot().theme_preference;
    assert_eq!(ThemePreference::ForestTechDark, state.theme);
    assert_eq!(PreferenceSource::LocalCache, state.source);
    assert_eq!(1, state.revision);
}

#[tokio::test]
async fn identical_theme_is_a_reducer_no_op() {
    let mut engine = Engine::new(10);
    engine
        .dispatch(
            EngineCommand::hydrate_theme_preference(ThemePreference::ForestTechDark),
            11,
        )
        .await;
    let revision = engine.snapshot().theme_preference.revision;

    engine
        .dispatch(
            EngineCommand::hydrate_theme_preference(ThemePreference::ForestTechDark),
            12,
        )
        .await;

    assert_eq!(revision, engine.snapshot().theme_preference.revision);
}

#[tokio::test]
async fn active_user_remote_theme_overrides_cached_theme() {
    let mut engine = Engine::new(10);
    engine
        .dispatch(EngineCommand::start_session("driver-1".to_string()), 11)
        .await;
    engine
        .dispatch(
            EngineCommand::hydrate_theme_preference(ThemePreference::BambooGroveLight),
            12,
        )
        .await;
    let baseline_revision = engine.snapshot().theme_preference.revision;

    engine
        .dispatch(
            EngineCommand::apply_remote_theme_preference(
                ThemePreference::MoonlitBambooDark,
                "driver-1".to_string(),
                baseline_revision,
            ),
            13,
        )
        .await;

    let state = &engine.snapshot().theme_preference;
    assert_eq!(ThemePreference::MoonlitBambooDark, state.theme);
    assert_eq!(PreferenceSource::RemoteProfile, state.source);
    assert_eq!(Some("driver-1"), state.session_user_id.as_deref());
}

#[tokio::test]
async fn remote_theme_for_inactive_user_is_rejected() {
    let mut engine = Engine::new(10);
    engine
        .dispatch(EngineCommand::start_session("driver-1".to_string()), 11)
        .await;
    let baseline_revision = engine.snapshot().theme_preference.revision;

    engine
        .dispatch(
            EngineCommand::apply_remote_theme_preference(
                ThemePreference::ForestTechLight,
                "driver-2".to_string(),
                baseline_revision,
            ),
            12,
        )
        .await;

    assert_eq!(
        PreferenceSource::Uninitialized,
        engine.snapshot().theme_preference.source
    );
}

#[tokio::test]
async fn stale_remote_theme_does_not_replace_newer_local_selection() {
    let mut engine = Engine::new(10);
    engine
        .dispatch(EngineCommand::start_session("driver-1".to_string()), 11)
        .await;
    engine
        .dispatch(
            EngineCommand::hydrate_theme_preference(ThemePreference::ForestTechDark),
            12,
        )
        .await;
    let remote_baseline = engine.snapshot().theme_preference.revision;
    engine
        .dispatch(
            EngineCommand::set_theme_preference(ThemePreference::BambooGroveLight),
            13,
        )
        .await;

    engine
        .dispatch(
            EngineCommand::apply_remote_theme_preference(
                ThemePreference::MoonlitBambooDark,
                "driver-1".to_string(),
                remote_baseline,
            ),
            14,
        )
        .await;

    let state = &engine.snapshot().theme_preference;
    assert_eq!(ThemePreference::BambooGroveLight, state.theme);
    assert_eq!(PreferenceSource::LocalUser, state.source);
}
