# Secure Storage Bridge

The secure storage bridge is the Android platform adapter that lets the Rust
engine protect local secret material without owning Android Keystore APIs.

## Current Shape

`:core:secure-storage-adapter` exposes:

- `SecureSecretProtector`
- `SecureSecretPurpose`
- `EncryptedSecret`
- `AndroidKeystoreSecureSecretProtector`

The API protects short-lived secret material by encrypting it with a
non-exportable Android Keystore AES key. The encrypted payload can be persisted
later by the Rust-owned database/session layer.

## Boundary Rule

Rust owns auth, session, database, and sync policy. Android owns only the
platform-backed cryptographic operation:

```text
Rust secret material
        |
Kotlin secure storage adapter
        |
Android Keystore AES-GCM key
        |
EncryptedSecret persisted by Rust-owned storage
```

## Security Notes

- Keystore key material is not exported to Kotlin or Rust.
- `EncryptedSecret` contains ciphertext and IV only.
- Callers must not log plaintext, ciphertext, IVs, aliases, or errors with
  secret payloads.
- This bridge does not store data yet; persistence belongs to a later Rust-owned
  local data milestone.

The implementation follows Android Keystore guidance for AES/GCM/NoPadding with
an Android Keystore-generated key.
