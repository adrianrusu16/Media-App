# Canopy Backend Integration

PandaEngine's Canopy adapter is the only client component coupled to
`canopy.v1`, protobuf/gRPC, the pinned BSR Prost/Tonic packages, canonical gRPC
status codes, pagination cursors, and playback capability responses.
`app_core` stays backend-neutral. Kotlin owns Android lifecycle, deployment
configuration input, AIDL/JNI hosting, Keystore operations, and Media3 wiring;
it does not receive protobuf objects, gRPC status values, or tokens.

Canopy remains the authority for authorization policy. PandaEngine may know
which client auth state or metadata a call requires, but it must not duplicate
track, playlist, profile, or account access policy.

## Deployment Configuration

Every runnable variant supplies a `client-connection.json` asset matching the
versioned, secret-free Canopy handoff. The debug app currently supplies the
local emulator configuration at
`app/src/debug/assets/client-connection.json`:

- gRPC: `http://10.0.2.2:50051`
- streaming reference: `http://10.0.2.2:8080`
- OpenAPI: `http://10.0.2.2:8080/openapi.json`

`10.0.2.2` is the Android emulator's route to the Windows host. The WSL backend
itself continues to use its documented loopback endpoints. Release/deployment
variants must separately provide public TLS gRPC and HTTPS streaming/OpenAPI
URLs, certificate trust requirements, verification/reset action URLs or deep
links, and public Google OAuth client IDs when enabled.

Release builds also require the public email-verification App Link host:

```powershell
.\gradlew.bat :app:assembleRelease `
  '-Ppandawave.verificationAppLinkHost=accounts.example.com'
```

The value is a DNS hostname only, without a scheme or path. The resulting App
Link is `https://<host>/verify-email?token=<opaque-token>`. Deployment must host
a matching `assetlinks.json` for the release application ID and signing
certificate. Debug builds additionally accept
`pandawave-dev://verify-email?token=<opaque-token>`; that scheme is absent from
release manifests. The dedicated Android activity sanitizes the incoming URI,
does not persist it, and submits the token to PandaEngine at most once.

Never place database addresses, SMTP credentials, signing or sealing keys,
stream secrets, Mailpit, raw tokens, or Canopy's private stream-authorizer
address in this asset.

The Android service loads and validates the complete asset before exposing a
usable engine. PandaEngine creates one shared channel and installs catalog,
playback, and system adapters atomically. Invalid configuration or connection
failure produces a non-dispatchable network-error snapshot; it does not fall
back to synthetic media.

## Local WSL And Emulator Verification

Start the backend in Ubuntu WSL:

```bash
cd /home/catalina/projects/Canopy
./scripts/local-integration.sh up
```

Boot an Android emulator, then verify that adb lists it. The examples use
`emulator-5554`:

```powershell
adb devices
adb -s emulator-5554 reverse tcp:8080 tcp:8080
```

The reverse is intentional. Canopy returns an opaque playback URL rooted at
its configured `http://127.0.0.1:8080` public base. The client must preserve
that URL, so the local harness routes device loopback to the host instead of
rewriting the returned capability.

Run the live PandaEngine test:

```powershell
.\gradlew.bat --no-configuration-cache `
  :core:rust-bridge:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.canopyLive=true' `
  --console=plain
```

Run the real Android Media3 URI round-trip test:

```powershell
.\gradlew.bat --no-configuration-cache `
  :core:media-adapter:connectedDebugAndroidTest `
  --console=plain
```

The first flow proves:

1. development configuration is accepted only through the Android debug path;
2. `SystemService.GetStatus` reaches the WSL server;
3. catalog browse returns the seeded canonical resources;
4. `PlaybackService.ResolvePlayback` returns an opaque URL, MIME type, and
   expiry through PandaEngine and JNI;
5. Android performs a ranged read from that exact URL.

The Media3 test proves an escaped capability such as `a%2Fb%3Dc` survives the
real Android `Uri` and `MediaItem` path byte-for-byte.

Stop the backend when testing is complete:

```bash
cd /home/catalina/projects/Canopy
./scripts/local-integration.sh down
```

## Runtime Rules

- Send lowercase `authorization: Bearer <access-token>` metadata on protected
  calls when authenticated session support is active.
- Persist each complete `SessionEnvelope` atomically in the Rust-owned session
  store. Kotlin may supply Android Keystore cryptographic operations, but must
  not own refresh-token rotation semantics.
- Allow one refresh in flight and never replay an old or ambiguously used
  refresh token.
- Treat page tokens and playback URLs as opaque.
- Branch on canonical gRPC status codes, never error messages.
- Normal online playback uses Canopy's resolved URL. `content://` remains test
  or future explicitly designed offline-cache behavior only.
