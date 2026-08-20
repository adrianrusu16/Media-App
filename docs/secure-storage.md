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
non-exportable Android Keystore AES key. Rust uses that narrow primitive to
protect persisted session envelopes without giving Kotlin ownership of token
rotation, account identity, or storage policy.

## Boundary Rule

Rust owns client auth state, session rotation, and session persistence. Canopy
owns accounts, PostgreSQL data, media, and server authorization policy. Android
owns only the platform-backed cryptographic operation:

```text
Rust secret material
        |
Kotlin secure storage adapter
        |
Android Keystore AES-GCM key
        |
Encrypted session envelope persisted by the Rust-owned file store
```

## Security Notes

- Keystore key material is not exported to Kotlin or Rust.
- `EncryptedSecret` contains ciphertext and IV only. Both are treated as
  sensitive diagnostics and are redacted from telemetry.
- Keystore aliases are stable identifiers, not secrets. They may later move to a
  generated namespace or Rust-owned constant map to reduce obvious strings, but
  hiding aliases is not a security boundary.
- Callers must not log plaintext, ciphertext, IVs, aliases, session envelope
  fields, account identifiers, or errors with secret payloads.
- Session persistence policy belongs to Rust. Kotlin performs only platform
  cryptographic operations and returns encrypted bytes. This bridge is not a
  general client database or media-storage layer.

The implementation follows Android Keystore guidance for AES/GCM/NoPadding with
an Android Keystore-generated key.
