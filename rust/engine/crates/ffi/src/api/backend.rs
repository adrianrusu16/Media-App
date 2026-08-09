use std::ffi::{CStr, c_char};
use std::sync::Arc;

use panda_engine_core::networking::canopy::{
    CanopyChannel, CanopyConnectionConfig, CanopySystemClient, DeploymentMode,
};
use panda_engine_core::{
    CanopyAuthClient, CanopyCatalogClient, CanopyDiscoveryClient, CanopyHistoryClient,
    CanopyPlaybackClient, CanopyProfileClient, EngineError, EngineErrorType, RemoteRepository,
    SessionCoordinator, SessionStore,
};

use crate::engine_handle::{BackendConfigurationState, PandaEngine};

#[unsafe(no_mangle)]
/// Configures the production Canopy backend exactly once.
///
/// # Safety
/// `engine` must be a live pointer returned by `panda_engine_create`, and
/// `config_json` must point to a valid, NUL-terminated UTF-8 string.
pub unsafe extern "C" fn panda_engine_configure_backend(
    engine: *mut PandaEngine,
    config_json: *const c_char,
) -> bool {
    let Some(engine) = (unsafe { engine.as_ref() }) else {
        return false;
    };
    let Some(config_json) = c_string(config_json) else {
        return false;
    };
    configure_backend(engine, &config_json, DeploymentMode::Production).is_ok()
}

pub(crate) fn configure_backend(
    engine: &PandaEngine,
    config_json: &str,
    mode: DeploymentMode,
) -> Result<(), EngineError> {
    let config = begin_configuration(engine, config_json, mode)?;
    let Some(config) = config else {
        return Ok(());
    };
    let connected = engine.runtime.block_on(CanopyChannel::connect(&config));
    match connected {
        Ok(channel) => finish_configuration(engine, config, channel, mode),
        Err(error) => {
            fail_configuration(engine);
            Err(error)
        }
    }
}

#[cfg(test)]
pub(crate) fn configure_backend_with_channel(
    engine: &PandaEngine,
    config_json: &str,
    mode: DeploymentMode,
    channel: CanopyChannel,
) -> Result<(), EngineError> {
    let Some(config) = begin_configuration(engine, config_json, mode)? else {
        return Ok(());
    };
    finish_configuration(engine, config, channel, mode)
}

fn begin_configuration(
    engine: &PandaEngine,
    config_json: &str,
    mode: DeploymentMode,
) -> Result<Option<CanopyConnectionConfig>, EngineError> {
    let config = match CanopyConnectionConfig::parse_and_validate(config_json, mode) {
        Ok(config) => config,
        Err(error) => {
            fail_unstarted_configuration(engine);
            return Err(error);
        }
    };
    let mut state = engine.backend_configuration.lock().unwrap();
    match &*state {
        BackendConfigurationState::Unconfigured => {
            *state = BackendConfigurationState::Configuring;
            Ok(Some(config))
        }
        BackendConfigurationState::Ready(ready) if ready.as_ref() == &config => Ok(None),
        BackendConfigurationState::Configuring
        | BackendConfigurationState::Ready(_)
        | BackendConfigurationState::Failed => Err(configuration_error()),
    }
}

fn finish_configuration(
    engine: &PandaEngine,
    config: CanopyConnectionConfig,
    channel: CanopyChannel,
    mode: DeploymentMode,
) -> Result<(), EngineError> {
    let mut state = engine.backend_configuration.lock().unwrap();
    if !matches!(*state, BackendConfigurationState::Configuring) {
        return Err(configuration_error());
    }

    let session_store = engine.session_store.lock().unwrap().clone();
    let composition = compose_backend(&channel, session_store.clone());
    let session = composition.session.clone();

    engine.engine.with_engine(|inner| {
        inner.set_repository(Box::new(composition.repository));
        inner.set_playback_port(composition.playback);
        inner.set_discovery_port(composition.discovery);
        inner.set_profile_port(composition.profile);
        inner.set_history_port(composition.history);
        inner.set_system_port(composition.system);
        inner.set_auth_state_provider(session.clone());
    });
    *engine.auth_runtime.lock().unwrap() = Some(crate::engine_handle::EngineAuthRuntime {
        coordinator: session,
        store: session_store,
        production: mode == DeploymentMode::Production,
    });
    *state = BackendConfigurationState::Ready(Box::new(config));
    Ok(())
}

struct BackendComposition {
    session: Arc<SessionCoordinator>,
    repository: RemoteRepository<CanopyCatalogClient>,
    playback: Arc<CanopyPlaybackClient>,
    discovery: Arc<CanopyDiscoveryClient>,
    system: Arc<CanopySystemClient>,
    profile: Arc<CanopyProfileClient>,
    history: Arc<CanopyHistoryClient>,
}

fn compose_backend(channel: &CanopyChannel, store: Arc<dyn SessionStore>) -> BackendComposition {
    let auth = Arc::new(CanopyAuthClient::new(channel));
    let session = Arc::new(SessionCoordinator::new(store, auth));
    let catalog = Arc::new(CanopyCatalogClient::with_session_coordinator(
        channel,
        session.clone(),
    ));
    let repository = RemoteRepository::new(catalog);
    let playback = Arc::new(CanopyPlaybackClient::with_session_coordinator(
        channel,
        session.clone(),
    ));
    let discovery = Arc::new(CanopyDiscoveryClient::new(channel, session.clone()));
    let system = Arc::new(CanopySystemClient::new(channel));
    let profile = Arc::new(CanopyProfileClient::new(channel, session.clone()));
    let history = Arc::new(CanopyHistoryClient::new(channel, session.clone()));

    BackendComposition {
        session,
        repository,
        playback,
        discovery,
        system,
        profile,
        history,
    }
}

fn fail_configuration(engine: &PandaEngine) {
    let mut state = engine.backend_configuration.lock().unwrap();
    if matches!(*state, BackendConfigurationState::Configuring) {
        *state = BackendConfigurationState::Failed;
    }
}

fn fail_unstarted_configuration(engine: &PandaEngine) {
    let mut state = engine.backend_configuration.lock().unwrap();
    if matches!(*state, BackendConfigurationState::Unconfigured) {
        *state = BackendConfigurationState::Failed;
    }
}

fn configuration_error() -> EngineError {
    EngineError::new(
        EngineErrorType::InvalidInput,
        "backend configuration has already been attempted",
        false,
    )
}

fn c_string(value: *const c_char) -> Option<String> {
    if value.is_null() {
        return None;
    }
    unsafe { CStr::from_ptr(value) }
        .to_str()
        .ok()
        .map(str::to_owned)
}

#[cfg(test)]
mod concurrency_tests {
    use panda_engine_core::networking::canopy::{CanopyChannel, DeploymentMode};
    use panda_engine_core::{
        Account, AuthSession, AuthSessionEnvelope, AuthState, EngineError, InMemorySessionStore,
        SessionStore,
    };
    use std::sync::Arc;

    use crate::engine_handle::{BackendConfigurationState, PandaEngine, build_engine};

    use super::{begin_configuration, compose_backend, configure_backend, finish_configuration};

    #[test]
    fn configuration_api_requires_only_shared_engine_access() {
        let _: fn(&PandaEngine, &str, DeploymentMode) -> Result<(), EngineError> =
            configure_backend;
    }

    #[test]
    fn backend_composition_shares_one_anonymous_session_coordinator() {
        let engine = build_engine(0);
        let channel = {
            let _entered = engine.runtime.enter();
            CanopyChannel::connect_lazy_for_test("https://canopy.example.com")
        };

        let store: Arc<dyn SessionStore> = Arc::new(InMemorySessionStore::new());
        let composition = compose_backend(&channel, store);

        assert_eq!(
            composition.session.auth_state().unwrap(),
            AuthState::Anonymous
        );
        assert_eq!(std::sync::Arc::strong_count(&composition.session), 6);
    }

    #[test]
    fn backend_composition_restores_the_injected_session_store() {
        let engine = build_engine(0);
        let channel = {
            let _entered = engine.runtime.enter();
            CanopyChannel::connect_lazy_for_test("https://canopy.example.com")
        };
        let envelope = AuthSessionEnvelope::new(
            "access-secret".into(),
            2_000,
            "refresh-secret".into(),
            3_000,
            Account {
                id: "account-1".into(),
                primary_email: "driver@example.com".into(),
                status: "active".into(),
                created_at_epoch_millis: 500,
            },
            AuthSession {
                id: "session-1".into(),
                device_label: "PandaWave".into(),
                created_at_epoch_millis: 1_000,
                last_used_at_epoch_millis: 1_100,
                expires_at_epoch_millis: 4_000,
                current: true,
            },
        );
        let expected = envelope.state();
        let store: Arc<dyn SessionStore> = Arc::new(InMemorySessionStore::with_session(envelope));

        let composition = compose_backend(&channel, store);

        assert_eq!(composition.session.auth_state().unwrap(), expected);
    }

    #[test]
    fn rejected_concurrent_attempt_cannot_fail_or_replace_the_in_flight_owner() {
        let engine = build_engine(0);
        let config = begin_configuration(&engine, valid_config_json(), DeploymentMode::Production)
            .unwrap()
            .unwrap();
        assert!(matches!(
            *engine.backend_configuration.lock().unwrap(),
            BackendConfigurationState::Configuring
        ));

        assert!(begin_configuration(&engine, "{}", DeploymentMode::Production).is_err());
        assert!(matches!(
            *engine.backend_configuration.lock().unwrap(),
            BackendConfigurationState::Configuring
        ));

        let channel = {
            let _runtime = engine.runtime.enter();
            CanopyChannel::connect_lazy_for_test("https://canopy.example.com")
        };
        finish_configuration(&engine, config, channel, DeploymentMode::Production).unwrap();
        assert!(matches!(
            *engine.backend_configuration.lock().unwrap(),
            BackendConfigurationState::Ready(_)
        ));
    }

    fn valid_config_json() -> &'static str {
        r#"{
          "schema_version": 1,
          "environment": "production",
          "contract": {
            "protobuf_package": "canopy.v1",
            "bsr_module": "buf.build/pandawave/canopy-api",
            "release": "v0.2.0",
            "commit": "145678c1d73e45b7bbaebf7e16ee4d64",
            "prost_package": "pandawave_canopy-api_community_neoeinstein-prost",
            "prost_version": "=0.5.0-00000000000000-145678c1d73e.2",
            "tonic_package": "pandawave_canopy-api_community_neoeinstein-tonic",
            "tonic_version": "=0.5.0-00000000000000-145678c1d73e.4"
          },
          "transport": {
            "grpc_endpoint": "https://canopy.example.com",
            "stream_base_url": "https://stream.example.com",
            "openapi_url": "https://api.example.com/openapi.json",
            "tls_required_outside_loopback": true
          },
          "authentication": {
            "metadata_key": "authorization",
            "metadata_scheme": "Bearer",
            "verification_action_relative_path": "verify-email",
            "verification_token_query_parameter": "token",
            "password_reset_action_relative_path": "reset-password",
            "password_reset_token_query_parameter": "token",
            "expiry_query_parameter": "expires_at",
            "auth_service_requires_postgresql": true,
            "password_bootstrap_requires_email_delivery": true
          }
        }"#
    }
}
