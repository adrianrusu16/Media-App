package com.adrianrusu.mediaapp.core.secure.storage

enum class SecureSecretPurpose(
    internal val keystoreAlias: String,
) {
    DatabaseKey("media_app.database_key.v1"),
    SessionSecret("media_app.session_secret.v1"),
}
