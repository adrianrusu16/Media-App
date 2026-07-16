use std::{
    fs,
    path::{Path, PathBuf},
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
    },
    time::{SystemTime, UNIX_EPOCH},
};

use panda_engine_core::{
    Account, AuthSession, AuthSessionEnvelope, EncryptedFileSessionStore, SealedSession,
    SessionCryptor, SessionStore, SessionStoreError, SessionStoreSecurity,
};

struct TestDirectory(PathBuf);

impl TestDirectory {
    fn new(label: &str) -> Self {
        let unique = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .expect("system clock must be after the Unix epoch")
            .as_nanos();
        let path =
            std::env::temp_dir().join(format!("pandawave-{label}-{}-{unique}", std::process::id()));
        fs::create_dir_all(&path).expect("test directory must be created");
        Self(path)
    }

    fn session_path(&self) -> PathBuf {
        self.0.join("session.bin")
    }
}

impl Drop for TestDirectory {
    fn drop(&mut self) {
        let _ = fs::remove_dir_all(&self.0);
    }
}

#[derive(Default)]
struct TestCryptor;

impl SessionCryptor for TestCryptor {
    fn seal(
        &self,
        plaintext: &[u8],
        associated_data: &[u8],
    ) -> Result<SealedSession, SessionStoreError> {
        let mut authenticated = associated_data.to_vec();
        authenticated.extend_from_slice(plaintext);
        let checksum = authenticated
            .iter()
            .fold(0_u8, |value, byte| value.wrapping_add(*byte));
        let ciphertext = plaintext.iter().map(|byte| byte ^ 0xa5).collect();
        Ok(SealedSession::new(
            vec![0x11; 12],
            ciphertext,
            vec![checksum],
        ))
    }

    fn open(
        &self,
        sealed: &SealedSession,
        associated_data: &[u8],
    ) -> Result<Vec<u8>, SessionStoreError> {
        if sealed.nonce() != [0x11; 12] {
            return Err(SessionStoreError::Corrupted(
                "nonce authentication failed".into(),
            ));
        }
        let plaintext: Vec<u8> = sealed.ciphertext().iter().map(|byte| byte ^ 0xa5).collect();
        let mut authenticated = associated_data.to_vec();
        authenticated.extend_from_slice(&plaintext);
        let checksum = authenticated
            .iter()
            .fold(0_u8, |value, byte| value.wrapping_add(*byte));
        if sealed.tag() != [checksum] {
            return Err(SessionStoreError::Corrupted(
                "authentication tag failed".into(),
            ));
        }
        Ok(plaintext)
    }
}

struct FailableCryptor {
    fail_seal: AtomicBool,
}

impl FailableCryptor {
    fn new() -> Self {
        Self {
            fail_seal: AtomicBool::new(false),
        }
    }
}

impl SessionCryptor for FailableCryptor {
    fn seal(
        &self,
        plaintext: &[u8],
        associated_data: &[u8],
    ) -> Result<SealedSession, SessionStoreError> {
        if self.fail_seal.load(Ordering::SeqCst) {
            return Err(SessionStoreError::Unavailable(
                "platform encryption failed".into(),
            ));
        }
        TestCryptor.seal(plaintext, associated_data)
    }

    fn open(
        &self,
        sealed: &SealedSession,
        associated_data: &[u8],
    ) -> Result<Vec<u8>, SessionStoreError> {
        TestCryptor.open(sealed, associated_data)
    }
}

fn envelope(label: &str) -> AuthSessionEnvelope {
    AuthSessionEnvelope::new(
        format!("access-secret-{label}"),
        2_000,
        format!("refresh-secret-{label}"),
        3_000,
        Account {
            id: format!("account-{label}"),
            primary_email: format!("{label}@example.com"),
            status: "active".into(),
            created_at_epoch_millis: 500,
        },
        AuthSession {
            id: format!("session-{label}"),
            device_label: "PandaWave".into(),
            created_at_epoch_millis: 1_000,
            last_used_at_epoch_millis: 1_100,
            expires_at_epoch_millis: 4_000,
            current: true,
        },
    )
}

fn store(path: &Path) -> EncryptedFileSessionStore {
    EncryptedFileSessionStore::new(path.to_path_buf(), Arc::new(TestCryptor))
}

#[test]
fn missing_session_is_anonymous_and_store_is_durable_secure() {
    let directory = TestDirectory::new("missing");
    let store = store(&directory.session_path());

    assert_eq!(store.security_level(), SessionStoreSecurity::DurableSecure);
    assert_eq!(
        store.read().expect("missing session read must succeed"),
        None
    );
}

#[test]
fn complete_envelope_round_trips_without_plaintext_credentials_on_disk() {
    let directory = TestDirectory::new("roundtrip");
    let path = directory.session_path();
    let store = store(&path);
    let expected = envelope("new");

    store
        .replace(expected.clone())
        .expect("replace must commit");

    assert_eq!(
        store.read().expect("committed session must read"),
        Some(expected)
    );
    let ciphertext = fs::read(path).expect("ciphertext file must exist");
    assert!(
        !ciphertext
            .windows(b"access-secret-new".len())
            .any(|window| window == b"access-secret-new")
    );
    assert!(
        !ciphertext
            .windows(b"refresh-secret-new".len())
            .any(|window| window == b"refresh-secret-new")
    );
}

#[test]
fn leftover_temporary_file_is_never_accepted_as_a_session() {
    let directory = TestDirectory::new("leftover-temp");
    let path = directory.session_path();
    fs::write(
        path.with_extension("bin.tmp"),
        b"incomplete secret material",
    )
    .expect("leftover temporary file must be created");

    assert_eq!(
        store(&path).read().expect("temporary file must be ignored"),
        None
    );
}

#[test]
fn clear_removes_the_complete_encrypted_session() {
    let directory = TestDirectory::new("clear");
    let path = directory.session_path();
    let store = store(&path);
    store.replace(envelope("old")).expect("replace must commit");

    store.clear().expect("clear must commit");

    assert_eq!(store.read().expect("cleared session must read"), None);
    assert!(!path.exists());
}

#[test]
fn replacing_a_session_commits_only_the_new_complete_envelope() {
    let directory = TestDirectory::new("replace");
    let path = directory.session_path();
    let store = store(&path);
    store
        .replace(envelope("old"))
        .expect("initial replace must commit");

    let replacement = envelope("new");
    store
        .replace(replacement.clone())
        .expect("rotated envelope must replace the old envelope");

    assert_eq!(
        store.read().expect("replacement must read"),
        Some(replacement)
    );
}

#[test]
fn encryption_failure_preserves_the_previous_complete_envelope() {
    let directory = TestDirectory::new("failed-replace");
    let cryptor = Arc::new(FailableCryptor::new());
    let store = EncryptedFileSessionStore::new(directory.session_path(), cryptor.clone());
    let initial = envelope("old");
    store
        .replace(initial.clone())
        .expect("initial replace must commit");
    cryptor.fail_seal.store(true, Ordering::SeqCst);

    assert!(store.replace(envelope("new")).is_err());
    cryptor.fail_seal.store(false, Ordering::SeqCst);
    assert_eq!(
        store.read().expect("old envelope must remain readable"),
        Some(initial)
    );
}

#[test]
fn ciphertext_nonce_tag_and_bound_metadata_tampering_fail_closed() {
    for (label, mutation) in [
        ("metadata", Tamper::Metadata),
        ("nonce", Tamper::Nonce),
        ("tag", Tamper::Tag),
        ("ciphertext", Tamper::Ciphertext),
    ] {
        let directory = TestDirectory::new(label);
        let path = directory.session_path();
        let store = store(&path);
        store.replace(envelope(label)).expect("replace must commit");
        let mut encoded = fs::read(&path).expect("ciphertext must exist");
        mutate(&mut encoded, mutation);
        fs::write(&path, encoded).expect("tampered ciphertext must be written");

        assert!(
            matches!(store.read(), Err(SessionStoreError::Corrupted(_))),
            "{label} tampering must be rejected"
        );
        assert!(
            !path.exists(),
            "{label} tampering must remove unusable state"
        );
    }
}

#[derive(Clone, Copy)]
enum Tamper {
    Metadata,
    Nonce,
    Tag,
    Ciphertext,
}

fn mutate(encoded: &mut [u8], tamper: Tamper) {
    const HEADER_LEN: usize = 18;
    let purpose_len = encoded[9] as usize;
    let nonce_len = u16::from_le_bytes([encoded[10], encoded[11]]) as usize;
    let tag_len = u16::from_le_bytes([encoded[12], encoded[13]]) as usize;
    let nonce_start = HEADER_LEN + purpose_len;
    let tag_start = nonce_start + nonce_len;
    let ciphertext_start = tag_start + tag_len;
    let index = match tamper {
        Tamper::Metadata => 8,
        Tamper::Nonce => nonce_start,
        Tamper::Tag => tag_start,
        Tamper::Ciphertext => ciphertext_start,
    };
    encoded[index] ^= 0x01;
}
