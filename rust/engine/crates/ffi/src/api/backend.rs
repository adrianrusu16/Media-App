use std::ffi::{CStr, c_char};
use std::sync::Arc;

use panda_engine_core::networking::canopy::{
    CanopyChannel, CanopyConnectionConfig, CanopySystemClient, DeploymentMode,
};
use panda_engine_core::{
    CanopyCatalogClient, CanopyPlaybackClient, EngineError, EngineErrorType, RemoteRepository,
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
    let Some(engine) = (unsafe { engine.as_mut() }) else {
        return false;
    };
    let Some(config_json) = c_string(config_json) else {
        return false;
    };
    configure_backend(engine, config_json, DeploymentMode::Production).is_ok()
}

pub(crate) fn configure_backend(
    engine: &mut PandaEngine,
    config_json: &str,
    mode: DeploymentMode,
) -> Result<(), EngineError> {
    let config = begin_configuration(engine, config_json, mode)?;
    let Some(config) = config else {
        return Ok(());
    };
    let connected = engine.runtime.block_on(CanopyChannel::connect(&config));
    match connected {
        Ok(channel) => finish_configuration(engine, config, channel),
        Err(error) => {
            fail_configuration(engine);
            Err(error)
        }
    }
}

#[cfg(test)]
pub(crate) fn configure_backend_with_channel(
    engine: &mut PandaEngine,
    config_json: &str,
    mode: DeploymentMode,
    channel: CanopyChannel,
) -> Result<(), EngineError> {
    let Some(config) = begin_configuration(engine, config_json, mode)? else {
        return Ok(());
    };
    finish_configuration(engine, config, channel)
}

fn begin_configuration(
    engine: &PandaEngine,
    config_json: &str,
    mode: DeploymentMode,
) -> Result<Option<CanopyConnectionConfig>, EngineError> {
    let config = match CanopyConnectionConfig::parse_and_validate(config_json, mode) {
        Ok(config) => config,
        Err(error) => {
            fail_configuration(engine);
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
) -> Result<(), EngineError> {
    let catalog = Arc::new(CanopyCatalogClient::new(&channel));
    let repository = RemoteRepository::new(catalog);
    let playback = Arc::new(CanopyPlaybackClient::new(&channel));
    let system = Arc::new(CanopySystemClient::new(&channel));

    engine.engine.with_engine(|inner| {
        inner.set_repository(Box::new(repository));
        inner.set_playback_port(playback);
        inner.set_system_port(system);
    });
    *engine.backend_configuration.lock().unwrap() =
        BackendConfigurationState::Ready(Box::new(config));
    Ok(())
}

fn fail_configuration(engine: &PandaEngine) {
    let mut state = engine.backend_configuration.lock().unwrap();
    if !matches!(*state, BackendConfigurationState::Ready(_)) {
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

fn c_string<'a>(value: *const c_char) -> Option<&'a str> {
    if value.is_null() {
        return None;
    }
    unsafe { CStr::from_ptr(value) }.to_str().ok()
}
