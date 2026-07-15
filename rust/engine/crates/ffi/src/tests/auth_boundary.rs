use panda_engine_core::{Account, AuthSession, AuthState, EngineSnapshot};

use crate::{
    FFI_AUTH_ANONYMOUS, FFI_AUTH_AUTHENTICATED, FFI_AUTH_LOGIN_REQUIRED, FfiEngineSnapshot,
};

#[test]
fn ffi_snapshot_projects_only_the_auth_state_discriminant() {
    let mut snapshot = EngineSnapshot::idle(1);
    assert_eq!(
        FfiEngineSnapshot::from(&snapshot).auth_state,
        FFI_AUTH_ANONYMOUS
    );

    snapshot.auth_state = AuthState::Authenticated {
        account: Account {
            id: "account-1".into(),
            primary_email: "driver@example.com".into(),
            status: "active".into(),
            created_at_epoch_millis: 10,
        },
        session: AuthSession {
            id: "session-1".into(),
            device_label: "emulator".into(),
            created_at_epoch_millis: 20,
            last_used_at_epoch_millis: 30,
            expires_at_epoch_millis: 40,
            current: true,
        },
    };
    assert_eq!(
        FfiEngineSnapshot::from(&snapshot).auth_state,
        FFI_AUTH_AUTHENTICATED
    );

    snapshot.auth_state = AuthState::LoginRequired;
    assert_eq!(
        FfiEngineSnapshot::from(&snapshot).auth_state,
        FFI_AUTH_LOGIN_REQUIRED
    );
}

#[test]
fn public_ffi_snapshot_type_contains_no_credentials_or_wire_types() {
    let source = include_str!("../types.rs");
    for forbidden in [
        "access_token",
        "refresh_token",
        "SessionEnvelope",
        "canopy.v1",
        "tonic::Status",
    ] {
        assert!(!source.contains(forbidden), "public FFI leaked {forbidden}");
    }
}
