package com.adrianrusu.pandawave.core.secure.storage

enum class SecureSecretPurpose(internal val keystoreAlias: String) {
    DatabaseKey("pandawave.database_key.v1"),
    SessionSecret("pandawave.session_secret.v1")
}
