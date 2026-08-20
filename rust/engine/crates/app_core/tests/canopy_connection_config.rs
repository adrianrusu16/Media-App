use panda_engine_core::EngineErrorType;
use panda_engine_core::networking::canopy::{CanopyConnectionConfig, DeploymentMode};
use serde_json::{Value, json};

fn valid_connection() -> Value {
    json!({
        "schema_version": 1,
        "environment": "local-emulator",
        "contract": {
            "protobuf_package": "canopy.v1",
            "bsr_module": "buf.build/pandawave/canopy-api",
            "release": "v0.2.0",
            "commit": "af019e2d7fa245a2a7d9fc21a4dd9afa",
            "prost_package": "pandawave_canopy-api_community_neoeinstein-prost",
            "prost_version": "=0.5.0-00000000000000-af019e2d7fa2.2",
            "tonic_package": "pandawave_canopy-api_community_neoeinstein-tonic",
            "tonic_version": "=0.5.0-00000000000000-af019e2d7fa2.4"
        },
        "transport": {
            "grpc_endpoint": "http://10.0.2.2:50051",
            "stream_base_url": "http://10.0.2.2:8080",
            "openapi_url": "http://10.0.2.2:8080/openapi.json",
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
    })
}

#[test]
fn development_accepts_emulator_host_without_normalizing_endpoints() {
    let config = CanopyConnectionConfig::parse_and_validate(
        &valid_connection().to_string(),
        DeploymentMode::Development,
    )
    .unwrap();

    assert_eq!(config.environment(), "local-emulator");
    assert_eq!(config.grpc_endpoint().to_string(), "http://10.0.2.2:50051/");
    assert_eq!(config.stream_base_url().as_str(), "http://10.0.2.2:8080/");
}

#[test]
fn production_rejects_cleartext() {
    let error = CanopyConnectionConfig::parse_and_validate(
        &valid_connection().to_string(),
        DeploymentMode::Production,
    )
    .unwrap_err();

    assert_eq!(error.error_type, EngineErrorType::UnsafeTransport);
}

#[test]
fn development_rejects_external_cleartext_host() {
    let mut value = valid_connection();
    value["transport"]["grpc_endpoint"] = json!("http://example.com:50051");

    let error =
        CanopyConnectionConfig::parse_and_validate(&value.to_string(), DeploymentMode::Development)
            .unwrap_err();

    assert_eq!(error.error_type, EngineErrorType::UnsafeTransport);
}

#[test]
fn listen_address_is_not_accepted_as_a_client_endpoint() {
    let mut value = valid_connection();
    value["transport"]["stream_base_url"] = json!("http://0.0.0.0:8080");

    let error =
        CanopyConnectionConfig::parse_and_validate(&value.to_string(), DeploymentMode::Development)
            .unwrap_err();

    assert_eq!(error.error_type, EngineErrorType::InvalidInput);
}

#[test]
fn immutable_contract_pin_mismatch_is_rejected() {
    let mut value = valid_connection();
    value["contract"]["commit"] = json!("moving-target");

    let error =
        CanopyConnectionConfig::parse_and_validate(&value.to_string(), DeploymentMode::Development)
            .unwrap_err();

    assert_eq!(error.error_type, EngineErrorType::InvalidInput);
}

#[test]
fn unknown_infrastructure_field_is_rejected() {
    let mut value = valid_connection();
    value["database_url"] = json!("postgresql://must-not-enter-client-config");

    let error =
        CanopyConnectionConfig::parse_and_validate(&value.to_string(), DeploymentMode::Development)
            .unwrap_err();

    assert_eq!(error.error_type, EngineErrorType::InvalidInput);
}

#[test]
fn production_accepts_public_tls_endpoints() {
    let mut value = valid_connection();
    value["environment"] = json!("production");
    value["transport"]["grpc_endpoint"] = json!("https://grpc.canopy.example");
    value["transport"]["stream_base_url"] = json!("https://stream.canopy.example");
    value["transport"]["openapi_url"] = json!("https://api.canopy.example/openapi.json");

    assert!(
        CanopyConnectionConfig::parse_and_validate(&value.to_string(), DeploymentMode::Production,)
            .is_ok()
    );
}

#[test]
fn production_accepts_a_tls_server_name_matching_its_grpc_endpoint() {
    let mut value = valid_connection();
    value["environment"] = json!("production");
    value["transport"]["grpc_endpoint"] = json!("https://grpc.canopy.example");
    value["transport"]["stream_base_url"] = json!("https://stream.canopy.example");
    value["transport"]["openapi_url"] = json!("https://api.canopy.example/openapi.json");
    value["transport"]["tls_server_name"] = json!("grpc.canopy.example");

    assert!(
        CanopyConnectionConfig::parse_and_validate(&value.to_string(), DeploymentMode::Production)
            .is_ok()
    );
}

#[test]
fn production_rejects_a_tls_server_name_that_differs_from_its_grpc_endpoint() {
    let mut value = valid_connection();
    value["environment"] = json!("production");
    value["transport"]["grpc_endpoint"] = json!("https://grpc.canopy.example");
    value["transport"]["stream_base_url"] = json!("https://stream.canopy.example");
    value["transport"]["openapi_url"] = json!("https://api.canopy.example/openapi.json");
    value["transport"]["tls_server_name"] = json!("other.example");

    let error =
        CanopyConnectionConfig::parse_and_validate(&value.to_string(), DeploymentMode::Production)
            .unwrap_err();

    assert_eq!(error.error_type, EngineErrorType::InvalidInput);
}

#[test]
fn production_accepts_a_public_ca_certificate_but_rejects_a_private_key() {
    let mut certificate = valid_connection();
    certificate["environment"] = json!("production");
    certificate["transport"]["grpc_endpoint"] = json!("https://grpc.canopy.example");
    certificate["transport"]["stream_base_url"] = json!("https://stream.canopy.example");
    certificate["transport"]["openapi_url"] = json!("https://api.canopy.example/openapi.json");
    certificate["transport"]["private_ca_pem"] =
        json!("-----BEGIN CERTIFICATE-----\nAQIDBA==\n-----END CERTIFICATE-----");

    assert!(
        CanopyConnectionConfig::parse_and_validate(
            &certificate.to_string(),
            DeploymentMode::Production,
        )
        .is_ok()
    );

    certificate["transport"]["private_ca_pem"] =
        json!("-----BEGIN PRIVATE KEY-----\nAQIDBA==\n-----END PRIVATE KEY-----");
    let error = CanopyConnectionConfig::parse_and_validate(
        &certificate.to_string(),
        DeploymentMode::Production,
    )
    .unwrap_err();

    assert_eq!(error.error_type, EngineErrorType::InvalidInput);
    assert!(!error.message.contains("PRIVATE KEY"));
}
