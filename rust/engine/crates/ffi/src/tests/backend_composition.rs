use panda_engine_core::networking::canopy::{CanopyChannel, DeploymentMode};

use crate::api::backend::configure_backend_with_channel;
use crate::engine_handle::build_engine;

#[test]
fn production_backend_configuration_replaces_default_adapters() {
    let mut engine = build_engine(0);
    let channel = {
        let _runtime = engine.runtime.enter();
        CanopyChannel::connect_lazy_for_test("https://canopy.example.com")
    };

    let result = configure_backend_with_channel(
        &mut engine,
        valid_config_json(),
        DeploymentMode::Production,
        channel,
    );

    assert!(result.is_ok());
    assert!(engine.backend_is_configured());
}

#[test]
fn identical_ready_configuration_is_idempotent() {
    let mut engine = build_engine(0);
    configure_with_lazy_channel(&mut engine, valid_config_json()).unwrap();

    let result = configure_with_lazy_channel(&mut engine, valid_config_json());

    assert!(result.is_ok());
    assert!(engine.backend_is_configured());
}

#[test]
fn conflicting_ready_configuration_is_rejected() {
    let mut engine = build_engine(0);
    configure_with_lazy_channel(&mut engine, valid_config_json()).unwrap();
    let conflicting = valid_config_json().replace("production", "staging");

    assert!(configure_with_lazy_channel(&mut engine, &conflicting).is_err());
    assert!(engine.backend_is_configured());
}

#[test]
fn failed_configuration_cannot_be_retried() {
    let mut engine = build_engine(0);
    assert!(configure_with_lazy_channel(&mut engine, "{}").is_err());

    assert!(configure_with_lazy_channel(&mut engine, valid_config_json()).is_err());
    assert!(!engine.backend_is_configured());
}

fn configure_with_lazy_channel(
    engine: &mut crate::PandaEngine,
    json: &str,
) -> Result<(), panda_engine_core::EngineError> {
    let channel = {
        let _runtime = engine.runtime.enter();
        CanopyChannel::connect_lazy_for_test("https://canopy.example.com")
    };
    configure_backend_with_channel(engine, json, DeploymentMode::Production, channel)
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
