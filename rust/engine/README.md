# Rust Engine

This directory is reserved for the Rust source-of-truth engine workspace.

The Rust engine will own auth/session state, Supabase and provider API calls,
local database management, playback decisions, user/profile state, catalog
normalization, sync policy, telemetry shaping, and security-sensitive domain
logic.

Android modules will communicate with the engine through the AIDL service
boundary in `:core:rust-bridge`.
