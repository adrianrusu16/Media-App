use panda_engine_core::networking::canopy::{CanopyConnectionConfig, DeploymentMode};
use panda_engine_core::{
    Account, AuthSession, AuthSessionEnvelope, EngineAccountIdentity, EnginePlaybackSource,
    SealedSession,
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

fn connection_config(with_private_ca: bool) -> CanopyConnectionConfig {
    let json = r#"{
        "schema_version": 1,
        "environment": "private-production-cell",
        "contract": {
            "protobuf_package": "canopy.v1",
            "bsr_module": "buf.build/pandawave/canopy-api",
            "release": "v0.3.0",
            "commit": "ff8940d1a15b4034bb430fd47dd45cdc",
            "prost_package": "pandawave_canopy-api_community_neoeinstein-prost",
            "prost_version": "=0.5.0-00000000000000-ff8940d1a15b.2",
            "tonic_package": "pandawave_canopy-api_community_neoeinstein-tonic",
            "tonic_version": "=0.5.0-00000000000000-ff8940d1a15b.4"
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
    let mut json: serde_json::Value = serde_json::from_str(json).unwrap();
    if !with_private_ca {
        json["transport"]
            .as_object_mut()
            .unwrap()
            .remove("private_ca_pem");
    }

    CanopyConnectionConfig::parse_and_validate(&json.to_string(), DeploymentMode::Production)
        .unwrap()
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
fn canopy_redaction_auth_state_debug_is_independent_of_identity_values() {
    let first = format!("{:?}", auth_envelope().state());
    let second = format!(
        "{:?}",
        AuthSessionEnvelope::new(
            "different-access".into(),
            7_000,
            "different-refresh".into(),
            8_000,
            Account {
                id: "account-with-a-different-length".into(),
                primary_email: "another-driver@example.net".into(),
                status: "suspended".into(),
                created_at_epoch_millis: 9_000,
            },
            AuthSession {
                id: "other-session".into(),
                device_label: "other-device".into(),
                created_at_epoch_millis: 10_000,
                last_used_at_epoch_millis: 11_000,
                expires_at_epoch_millis: 12_000,
                current: false,
            },
        )
        .state()
    );

    assert_eq!(first, second);
    assert!(first.contains(REDACTED));
    for sensitive in [
        "account-1",
        "driver@example.com",
        "active",
        "session-1",
        "car",
    ] {
        assert!(!first.contains(sensitive));
    }
}

#[test]
fn canopy_redaction_public_identity_struct_debug_is_constant() {
    let first_account = format!(
        "{:?}",
        Account {
            id: "account-1".into(),
            primary_email: "driver@example.com".into(),
            status: "active".into(),
            created_at_epoch_millis: 500,
        }
    );
    let second_account = format!(
        "{:?}",
        Account {
            id: "another-account-with-a-different-length".into(),
            primary_email: "other@example.net".into(),
            status: "suspended".into(),
            created_at_epoch_millis: 9_999,
        }
    );
    let first_session = format!(
        "{:?}",
        AuthSession {
            id: "session-1".into(),
            device_label: "car".into(),
            created_at_epoch_millis: 1_000,
            last_used_at_epoch_millis: 1_100,
            expires_at_epoch_millis: 4_000,
            current: true,
        }
    );
    let second_session = format!(
        "{:?}",
        AuthSession {
            id: "another-session".into(),
            device_label: "longer-device-label".into(),
            created_at_epoch_millis: 8_000,
            last_used_at_epoch_millis: 8_100,
            expires_at_epoch_millis: 9_000,
            current: false,
        }
    );
    let first_identity = format!(
        "{:?}",
        EngineAccountIdentity {
            account_id: "account-1".into(),
            session_id: "session-1".into(),
        }
    );
    let second_identity = format!(
        "{:?}",
        EngineAccountIdentity {
            account_id: "different-account".into(),
            session_id: "different-session-with-longer-text".into(),
        }
    );

    assert_eq!(first_account, second_account);
    assert_eq!(first_session, second_session);
    assert_eq!(first_identity, second_identity);
    for rendered in [first_account, first_session, first_identity] {
        assert!(rendered.contains(REDACTED));
    }
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
    let rendered = format!("{:?}", connection_config(true));

    assert!(rendered.contains(REDACTED));
    assert!(!rendered.contains("private-production-cell"));
    assert!(!rendered.contains("grpc.private.example"));
    assert!(!rendered.contains("stream.private.example"));
    assert!(!rendered.contains("api.private.example"));
    assert!(!rendered.contains("BEGIN CERTIFICATE"));
    assert!(!rendered.contains("AQIDBA"));
}

#[test]
fn canopy_redaction_tls_debug_does_not_reveal_private_ca_presence() {
    let configured = format!("{:?}", connection_config(true));
    let absent = format!("{:?}", connection_config(false));

    assert_eq!(configured, absent);
    assert!(configured.contains(REDACTED));
    assert!(!configured.contains("Some"));
    assert!(!absent.contains("None"));
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
