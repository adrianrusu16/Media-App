use std::sync::{Arc, Barrier};
use std::thread;

use panda_engine_core::{
    Account, AuthPort, AuthSession, AuthSessionEnvelope, AuthState, EngineError, EngineErrorType,
    InMemorySessionStore, SessionStore, SessionStoreError,
};

fn envelope(label: &str) -> AuthSessionEnvelope {
    AuthSessionEnvelope::new(
        format!("access-{label}"),
        2_000,
        format!("refresh-{label}"),
        3_000,
        Account {
            id: format!("account-{label}"),
            primary_email: format!("{label}@example.com"),
            status: "active".into(),
            created_at_epoch_millis: 500,
        },
        AuthSession {
            id: format!("session-{label}"),
            device_label: format!("device-{label}"),
            created_at_epoch_millis: 1_000,
            last_used_at_epoch_millis: 1_100,
            expires_at_epoch_millis: 4_000,
            current: true,
        },
    )
}

#[test]
fn replace_and_read_preserve_the_complete_envelope() {
    let store = InMemorySessionStore::new();
    let expected = envelope("first");

    store.replace(expected.clone()).unwrap();

    assert_eq!(store.read().unwrap(), Some(expected));
}

#[test]
fn clear_removes_the_complete_envelope() {
    let store = InMemorySessionStore::new();
    store.replace(envelope("first")).unwrap();

    store.clear().unwrap();

    assert_eq!(store.read().unwrap(), None);
}

#[test]
fn replacement_swaps_every_envelope_field() {
    let store = InMemorySessionStore::new();
    store.replace(envelope("old")).unwrap();
    let replacement = envelope("new");

    store.replace(replacement.clone()).unwrap();

    assert_eq!(store.read().unwrap(), Some(replacement));
}

#[test]
fn concurrent_reads_observe_only_complete_envelopes() {
    let store = Arc::new(InMemorySessionStore::new());
    let first = envelope("first");
    let second = envelope("second");
    store.replace(first.clone()).unwrap();
    let barrier = Arc::new(Barrier::new(3));

    let writer_store = Arc::clone(&store);
    let writer_barrier = Arc::clone(&barrier);
    let writer = thread::spawn(move || {
        writer_barrier.wait();
        for index in 0..2_000 {
            let next = if index % 2 == 0 {
                first.clone()
            } else {
                second.clone()
            };
            writer_store.replace(next).unwrap();
        }
    });

    let reader_store = Arc::clone(&store);
    let reader_barrier = Arc::clone(&barrier);
    let reader = thread::spawn(move || {
        reader_barrier.wait();
        for _ in 0..2_000 {
            let observed = reader_store.read().unwrap().unwrap();
            assert!(observed == envelope("first") || observed == envelope("second"));
        }
    });

    barrier.wait();
    writer.join().unwrap();
    reader.join().unwrap();
}

struct FailingBeforeCommitStore {
    inner: InMemorySessionStore,
}

impl SessionStore for FailingBeforeCommitStore {
    fn read(&self) -> Result<Option<AuthSessionEnvelope>, SessionStoreError> {
        self.inner.read()
    }

    fn replace(&self, _envelope: AuthSessionEnvelope) -> Result<(), SessionStoreError> {
        Err(SessionStoreError::Unavailable(
            "injected pre-commit failure".into(),
        ))
    }

    fn clear(&self) -> Result<(), SessionStoreError> {
        self.inner.clear()
    }
}

#[test]
fn failed_replacement_does_not_expose_a_half_updated_envelope() {
    let initial = envelope("initial");
    let store = FailingBeforeCommitStore {
        inner: InMemorySessionStore::with_session(initial.clone()),
    };

    assert!(store.replace(envelope("replacement")).is_err());
    assert_eq!(store.read().unwrap(), Some(initial));
}

#[test]
fn auth_state_contains_no_credentials() {
    let state = envelope("safe").state();

    assert_eq!(
        state,
        AuthState::Authenticated {
            account: Account {
                id: "account-safe".into(),
                primary_email: "safe@example.com".into(),
                status: "active".into(),
                created_at_epoch_millis: 500,
            },
            session: AuthSession {
                id: "session-safe".into(),
                device_label: "device-safe".into(),
                created_at_epoch_millis: 1_000,
                last_used_at_epoch_millis: 1_100,
                expires_at_epoch_millis: 4_000,
                current: true,
            },
        }
    );
    assert!(!format!("{state:?}").contains("access-safe"));
    assert!(!format!("{state:?}").contains("refresh-safe"));
}

#[test]
fn envelope_debug_output_redacts_credentials() {
    let rendered = format!("{:?}", envelope("secret"));

    assert!(!rendered.contains("access-secret"));
    assert!(!rendered.contains("refresh-secret"));
}

struct RecordingAuthPort;

#[async_trait::async_trait]
impl AuthPort for RecordingAuthPort {
    async fn login_password(
        &self,
        _email: &str,
        _password: &str,
        _device_label: &str,
    ) -> Result<AuthSessionEnvelope, EngineError> {
        Ok(envelope("login"))
    }

    async fn refresh_session(
        &self,
        _refresh_token: &str,
    ) -> Result<AuthSessionEnvelope, EngineError> {
        Ok(envelope("refresh"))
    }

    async fn logout(&self, _access_token: &str) -> Result<(), EngineError> {
        Ok(())
    }
}

#[tokio::test]
async fn auth_port_exposes_only_service_neutral_auth_operations() {
    let port = RecordingAuthPort;

    assert_eq!(
        port.login_password("driver@example.com", "secret", "car")
            .await
            .unwrap(),
        envelope("login")
    );
    assert_eq!(
        port.refresh_session("refresh-token").await.unwrap(),
        envelope("refresh")
    );
    port.logout("access-token").await.unwrap();
}

#[test]
fn storage_errors_map_to_a_typed_engine_error() {
    let engine_error = panda_engine_core::EngineError::from(SessionStoreError::Corrupted(
        "invalid stored envelope".into(),
    ));

    assert_eq!(engine_error.error_type, EngineErrorType::SessionStorage);
    assert!(!engine_error.is_fatal);
}

#[test]
fn in_memory_store_explicitly_rejects_production_use() {
    let production_ready = std::hint::black_box(InMemorySessionStore::PRODUCTION_READY);
    assert!(!production_ready);
}
