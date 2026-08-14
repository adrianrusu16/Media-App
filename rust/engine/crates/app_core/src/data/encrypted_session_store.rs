use std::{
    fs::{self, File, OpenOptions},
    io::{Read, Write},
    path::{Path, PathBuf},
    sync::{Arc, Mutex, MutexGuard},
};

use crate::{AuthSessionEnvelope, SessionStore, SessionStoreError, SessionStoreSecurity};

const MAGIC: &[u8; 8] = b"PWSESS01";
const FORMAT_VERSION: u8 = 1;
const PURPOSE: &[u8] = b"pandawave.auth-session-envelope";
const ASSOCIATED_DATA: &[u8] =
    b"PWSESS01\x00format=1\x00purpose=pandawave.auth-session-envelope\x00codec=1";
const HEADER_LEN: usize = 8 + 1 + 1 + 2 + 2 + 4;
const MAX_COMPONENT_LEN: usize = 1024 * 1024;

/// Opaque authenticated ciphertext returned by the platform cryptography boundary.
pub struct SealedSession {
    nonce: Vec<u8>,
    ciphertext: Vec<u8>,
    tag: Vec<u8>,
}

impl std::fmt::Debug for SealedSession {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        formatter
            .debug_struct("SealedSession")
            .field("nonce", &"[REDACTED]")
            .field("ciphertext", &"[REDACTED]")
            .field("tag", &"[REDACTED]")
            .finish()
    }
}

impl SealedSession {
    pub fn new(nonce: Vec<u8>, ciphertext: Vec<u8>, tag: Vec<u8>) -> Self {
        Self {
            nonce,
            ciphertext,
            tag,
        }
    }

    pub fn nonce(&self) -> &[u8] {
        &self.nonce
    }

    pub fn ciphertext(&self) -> &[u8] {
        &self.ciphertext
    }

    pub fn tag(&self) -> &[u8] {
        &self.tag
    }
}

/// Narrow platform cryptography port. Its byte inputs are opaque to platform code.
pub trait SessionCryptor: Send + Sync {
    fn seal(
        &self,
        plaintext: &[u8],
        associated_data: &[u8],
    ) -> Result<SealedSession, SessionStoreError>;

    fn open(
        &self,
        sealed: &SealedSession,
        associated_data: &[u8],
    ) -> Result<Vec<u8>, SessionStoreError>;
}

/// Durable session store whose file contains only authenticated ciphertext.
pub struct EncryptedFileSessionStore {
    path: PathBuf,
    cryptor: Arc<dyn SessionCryptor>,
    operation_lock: Mutex<()>,
}

impl EncryptedFileSessionStore {
    pub fn new(path: PathBuf, cryptor: Arc<dyn SessionCryptor>) -> Self {
        Self {
            path,
            cryptor,
            operation_lock: Mutex::new(()),
        }
    }

    fn lock(&self) -> Result<MutexGuard<'_, ()>, SessionStoreError> {
        self.operation_lock.lock().map_err(|_| {
            SessionStoreError::Unavailable("encrypted session store lock poisoned".into())
        })
    }

    fn temporary_path(&self) -> PathBuf {
        let extension = self
            .path
            .extension()
            .and_then(|value| value.to_str())
            .map_or_else(|| "tmp".to_owned(), |value| format!("{value}.tmp"));
        self.path.with_extension(extension)
    }

    fn remove_unusable_session(&self) -> Result<(), SessionStoreError> {
        match fs::remove_file(&self.path) {
            Ok(()) => {
                sync_parent_directory(&self.path)?;
                Ok(())
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
            Err(_) => Err(SessionStoreError::Unavailable(
                "unusable encrypted session could not be removed".into(),
            )),
        }
    }

    fn read_locked(&self) -> Result<Option<AuthSessionEnvelope>, SessionStoreError> {
        let file = match File::open(&self.path) {
            Ok(file) => file,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
            Err(_) => {
                return Err(SessionStoreError::Unavailable(
                    "encrypted session could not be opened".into(),
                ));
            }
        };
        let mut encoded = Vec::new();
        if file
            .take((MAX_COMPONENT_LEN * 3 + HEADER_LEN + PURPOSE.len() + 1) as u64)
            .read_to_end(&mut encoded)
            .is_err()
        {
            return Err(SessionStoreError::Unavailable(
                "encrypted session could not be read".into(),
            ));
        }

        let result = decode_sealed_session(&encoded)
            .and_then(|sealed| self.cryptor.open(&sealed, ASSOCIATED_DATA))
            .and_then(|mut plaintext| {
                let decoded = AuthSessionEnvelope::from_storage_bytes(&plaintext)
                    .map_err(SessionStoreError::Corrupted);
                plaintext.fill(0);
                decoded
            });

        match result {
            Ok(envelope) => Ok(Some(envelope)),
            Err(SessionStoreError::Corrupted(message)) => {
                self.remove_unusable_session()?;
                Err(SessionStoreError::Corrupted(message))
            }
            Err(error) => Err(error),
        }
    }

    fn write_locked(&self, encoded: &[u8]) -> Result<(), SessionStoreError> {
        commit_same_directory(&self.path, &self.temporary_path(), encoded, |_| Ok(()))
    }
}

fn commit_same_directory(
    destination_path: &Path,
    temporary_path: &Path,
    encoded: &[u8],
    after_flush_before_rename: impl FnOnce(&Path) -> Result<(), SessionStoreError>,
) -> Result<(), SessionStoreError> {
    let parent = destination_path.parent().ok_or_else(|| {
        SessionStoreError::Unavailable("encrypted session path has no parent".into())
    })?;
    fs::create_dir_all(parent).map_err(|_| {
        SessionStoreError::Unavailable("encrypted session directory unavailable".into())
    })?;
    match fs::remove_file(temporary_path) {
        Ok(()) => {}
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(_) => {
            return Err(SessionStoreError::Unavailable(
                "stale encrypted session temporary file could not be removed".into(),
            ));
        }
    }

    let write_result = (|| {
        let mut temporary = OpenOptions::new()
            .create_new(true)
            .write(true)
            .open(temporary_path)
            .map_err(|_| {
                SessionStoreError::Unavailable(
                    "encrypted session temporary file could not be created".into(),
                )
            })?;
        temporary.write_all(encoded).map_err(|_| {
            SessionStoreError::Unavailable(
                "encrypted session temporary file could not be written".into(),
            )
        })?;
        temporary.sync_all().map_err(|_| {
            SessionStoreError::Unavailable(
                "encrypted session temporary file could not be flushed".into(),
            )
        })?;
        after_flush_before_rename(temporary_path)?;
        fs::rename(temporary_path, destination_path).map_err(|_| {
            SessionStoreError::Unavailable("encrypted session commit failed".into())
        })?;
        sync_parent_directory(destination_path)
    })();

    if write_result.is_err() {
        let _ = fs::remove_file(temporary_path);
    }
    write_result
}

impl SessionStore for EncryptedFileSessionStore {
    fn security_level(&self) -> SessionStoreSecurity {
        SessionStoreSecurity::DurableSecure
    }

    fn read(&self) -> Result<Option<AuthSessionEnvelope>, SessionStoreError> {
        let _guard = self.lock()?;
        self.read_locked()
    }

    fn replace(&self, envelope: AuthSessionEnvelope) -> Result<(), SessionStoreError> {
        let _guard = self.lock()?;
        let mut plaintext = envelope.to_storage_bytes().map_err(|_| {
            SessionStoreError::Corrupted("authentication session could not be encoded".into())
        })?;
        let sealed_result = self.cryptor.seal(&plaintext, ASSOCIATED_DATA);
        plaintext.fill(0);
        let sealed = sealed_result?;
        let encoded = encode_sealed_session(&sealed)?;
        self.write_locked(&encoded)
    }

    fn clear(&self) -> Result<(), SessionStoreError> {
        let _guard = self.lock()?;
        self.remove_unusable_session()
    }
}

fn encode_sealed_session(sealed: &SealedSession) -> Result<Vec<u8>, SessionStoreError> {
    validate_component_lengths(sealed)?;
    let purpose_len = u8::try_from(PURPOSE.len()).map_err(|_| {
        SessionStoreError::Corrupted("encrypted session purpose is too large".into())
    })?;
    let nonce_len = u16::try_from(sealed.nonce.len())
        .map_err(|_| SessionStoreError::Corrupted("encrypted session nonce is too large".into()))?;
    let tag_len = u16::try_from(sealed.tag.len())
        .map_err(|_| SessionStoreError::Corrupted("encrypted session tag is too large".into()))?;
    let ciphertext_len = u32::try_from(sealed.ciphertext.len()).map_err(|_| {
        SessionStoreError::Corrupted("encrypted session ciphertext is too large".into())
    })?;

    let mut encoded = Vec::with_capacity(
        HEADER_LEN
            + PURPOSE.len()
            + sealed.nonce.len()
            + sealed.tag.len()
            + sealed.ciphertext.len(),
    );
    encoded.extend_from_slice(MAGIC);
    encoded.push(FORMAT_VERSION);
    encoded.push(purpose_len);
    encoded.extend_from_slice(&nonce_len.to_le_bytes());
    encoded.extend_from_slice(&tag_len.to_le_bytes());
    encoded.extend_from_slice(&ciphertext_len.to_le_bytes());
    encoded.extend_from_slice(PURPOSE);
    encoded.extend_from_slice(&sealed.nonce);
    encoded.extend_from_slice(&sealed.tag);
    encoded.extend_from_slice(&sealed.ciphertext);
    Ok(encoded)
}

fn decode_sealed_session(encoded: &[u8]) -> Result<SealedSession, SessionStoreError> {
    if encoded.len() < HEADER_LEN {
        return Err(corrupted_format());
    }
    if &encoded[..8] != MAGIC || encoded[8] != FORMAT_VERSION {
        return Err(corrupted_format());
    }
    let purpose_len = encoded[9] as usize;
    let nonce_len = u16::from_le_bytes([encoded[10], encoded[11]]) as usize;
    let tag_len = u16::from_le_bytes([encoded[12], encoded[13]]) as usize;
    let ciphertext_len =
        u32::from_le_bytes([encoded[14], encoded[15], encoded[16], encoded[17]]) as usize;
    if nonce_len > MAX_COMPONENT_LEN
        || tag_len > MAX_COMPONENT_LEN
        || ciphertext_len > MAX_COMPONENT_LEN
    {
        return Err(corrupted_format());
    }
    let expected_len = HEADER_LEN
        .checked_add(purpose_len)
        .and_then(|value| value.checked_add(nonce_len))
        .and_then(|value| value.checked_add(tag_len))
        .and_then(|value| value.checked_add(ciphertext_len))
        .ok_or_else(corrupted_format)?;
    if encoded.len() != expected_len {
        return Err(corrupted_format());
    }
    let mut offset = HEADER_LEN;
    if &encoded[offset..offset + purpose_len] != PURPOSE {
        return Err(corrupted_format());
    }
    offset += purpose_len;
    let nonce = encoded[offset..offset + nonce_len].to_vec();
    offset += nonce_len;
    let tag = encoded[offset..offset + tag_len].to_vec();
    offset += tag_len;
    let ciphertext = encoded[offset..].to_vec();
    let sealed = SealedSession::new(nonce, ciphertext, tag);
    validate_component_lengths(&sealed)?;
    Ok(sealed)
}

fn validate_component_lengths(sealed: &SealedSession) -> Result<(), SessionStoreError> {
    if sealed.nonce.is_empty()
        || sealed.tag.is_empty()
        || sealed.ciphertext.is_empty()
        || sealed.nonce.len() > MAX_COMPONENT_LEN
        || sealed.tag.len() > MAX_COMPONENT_LEN
        || sealed.ciphertext.len() > MAX_COMPONENT_LEN
    {
        return Err(corrupted_format());
    }
    Ok(())
}

fn corrupted_format() -> SessionStoreError {
    SessionStoreError::Corrupted("encrypted session format is invalid".into())
}

#[cfg(unix)]
fn sync_parent_directory(path: &Path) -> Result<(), SessionStoreError> {
    let parent = path.parent().ok_or_else(|| {
        SessionStoreError::Unavailable("encrypted session path has no parent".into())
    })?;
    File::open(parent)
        .and_then(|directory| directory.sync_all())
        .map_err(|_| {
            SessionStoreError::Unavailable(
                "encrypted session directory could not be flushed".into(),
            )
        })
}

#[cfg(not(unix))]
fn sync_parent_directory(_path: &Path) -> Result<(), SessionStoreError> {
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{SystemTime, UNIX_EPOCH};

    struct TestDirectory(PathBuf);

    impl TestDirectory {
        fn new() -> Self {
            let unique = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .expect("system clock must be after Unix epoch")
                .as_nanos();
            let path = std::env::temp_dir().join(format!(
                "pandawave-atomic-store-{}-{unique}",
                std::process::id()
            ));
            fs::create_dir_all(&path).expect("test directory must be created");
            Self(path)
        }
    }

    impl Drop for TestDirectory {
        fn drop(&mut self) {
            let _ = fs::remove_dir_all(&self.0);
        }
    }

    #[test]
    fn failure_after_flush_before_rename_preserves_previous_file() {
        let directory = TestDirectory::new();
        let destination = directory.0.join("session.bin");
        let temporary = directory.0.join("session.bin.tmp");
        fs::write(&destination, b"previous-complete-envelope")
            .expect("previous file must be written");

        let result = commit_same_directory(
            &destination,
            &temporary,
            b"new-complete-envelope",
            |flushed_path| {
                assert_eq!(
                    fs::read(flushed_path).expect("flushed temporary file must be readable"),
                    b"new-complete-envelope"
                );
                Err(SessionStoreError::Unavailable(
                    "injected failure before rename".into(),
                ))
            },
        );

        assert!(result.is_err());
        assert_eq!(
            fs::read(&destination).expect("previous file must remain"),
            b"previous-complete-envelope"
        );
        assert!(!temporary.exists());
    }
}
