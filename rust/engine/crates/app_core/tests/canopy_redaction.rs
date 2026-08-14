use panda_engine_core::networking::canopy::{CanopyConnectionConfig, DeploymentMode};
use panda_engine_core::{
    Account, AuthSession, AuthSessionEnvelope, EnginePlaybackSource, SealedSession,
};

const REDACTED: &str = "[REDACTED]";

fn auth_envelope() -> AuthSessionEnvelope {
    AuthSessionEnvelope::new(
        "access-opaque-credential".into(),
        2_000,
        "refresh-opaque-credential".into(),
        3_000,
        Account {
            id: "account-1".into(),
            primary_email: "driver@example.com".into(),
            status: "active".into(),
            created_at_epoch_millis: 500,
        },
        AuthSession {
            id: "session-1".into(),
            device_label: "car".into(),
            created_at_epoch_millis: 1_000,
            last_used_at_epoch_millis: 1_100,
            expires_at_epoch_millis: 4_000,
            current: true,
        },
    )
}

fn connection_config() -> CanopyConnectionConfig {
    let json = r#"{
        "schema_version": 1,
        "environment": "private-production-cell",
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
            "grpc_endpoint": "https://grpc.private.example",
            "stream_base_url": "https://stream.private.example",
            "openapi_url": "https://api.private.example/openapi.json",
            "tls_required_outside_loopback": true,
            "tls_server_name": "grpc.private.example",
            "private_ca_pem": "-----BEGIN CERTIFICATE-----\nAQIDBA==\n-----END CERTIFICATE-----"
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
    }"#;

    CanopyConnectionConfig::parse_and_validate(json, DeploymentMode::Production).unwrap()
}

#[test]
fn canopy_redaction_auth_session_debug_hides_credentials_and_opaque_identity() {
    let rendered = format!("{:?}", auth_envelope());

    assert!(rendered.contains(REDACTED));
    assert!(!rendered.contains("access-opaque-credential"));
    assert!(!rendered.contains("refresh-opaque-credential"));
    assert!(!rendered.contains("account-1"));
    assert!(!rendered.contains("driver@example.com"));
    assert!(!rendered.contains("session-1"));
    assert!(!rendered.contains("car"));
}

#[test]
fn canopy_redaction_playback_source_debug_hides_opaque_url() {
    let source = EnginePlaybackSource {
        track_id: "track-1".into(),
        url: "https://stream.private.example/capability?token=opaque".into(),
        content_type: "audio/flac".into(),
        codec: "flac".into(),
        duration_millis: 42_000,
        expires_at_epoch_millis: 2_000,
    };

    let rendered = format!("{source:?}");

    assert!(rendered.contains(REDACTED));
    assert!(!rendered.contains("track-1"));
    assert!(!rendered.contains("https://stream.private.example"));
    assert!(!rendered.contains("token=opaque"));
}

#[test]
fn canopy_redaction_connection_debug_hides_private_infrastructure() {
    let rendered = format!("{:?}", connection_config());

    assert!(rendered.contains(REDACTED));
    assert!(!rendered.contains("private-production-cell"));
    assert!(!rendered.contains("grpc.private.example"));
    assert!(!rendered.contains("stream.private.example"));
    assert!(!rendered.contains("api.private.example"));
    assert!(!rendered.contains("BEGIN CERTIFICATE"));
    assert!(!rendered.contains("AQIDBA"));
}

#[test]
fn canopy_redaction_sealed_session_debug_hides_all_encrypted_material() {
    let sealed = SealedSession::new(
        b"nonce-visible".to_vec(),
        b"ciphertext-visible".to_vec(),
        b"tag-visible".to_vec(),
    );

    let rendered = format!("{sealed:?}");

    assert!(rendered.contains(REDACTED));
    assert!(!rendered.contains("nonce-visible"));
    assert!(!rendered.contains("ciphertext-visible"));
    assert!(!rendered.contains("tag-visible"));
}
