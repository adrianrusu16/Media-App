# PandaWave

State-of-the-art Android Automotive OS media app built with Kotlin, Compose,
Media3, AIDL, and a Rust source-of-truth engine.

The app is being built incrementally from the Android Studio Car No Activity
template. Each milestone should be committed separately so architecture, platform
integration, and product features stay easy to review.

## Vision

PandaWave is an AAOS-first music application designed around platform media
integration, safety-aware automotive UX, and a Rust runtime that owns the
important product state.

Kotlin and Compose provide the Android UI and platform adapter layer. Rust owns
API access, local data, playback state, user/session state, telemetry policy,
and security-sensitive business logic. AIDL is the primary boundary between the
Android surface and the engine.

## Architecture Roadmap

See [docs/architecture-roadmap.md](docs/architecture-roadmap.md) for the current
architecture direction, milestone plan, and security posture.

See [docs/native-engine-host.md](docs/native-engine-host.md) for the Android
service, AIDL, JNI, Rust FFI, and PandaEngine hosting model.

See [docs/testing.md](docs/testing.md) for the Kotlin/JUnit testing conventions
and the split between local JVM and Android instrumentation test lanes.

See [docs/assets-and-branding.md](docs/assets-and-branding.md) for third-party
asset source recommendations and intake rules.
