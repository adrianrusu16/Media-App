# Live Authentication Vertical Slice Design

## Goal

Prove PandaWave's password-registration lifecycle against the real local Canopy stack on
`PandaEmulatorNoStore`, including email verification, authenticated publication only after
encrypted persistence, restoration after engine recreation, durable logout, anonymous fallback,
and the PW-PRD-005 interaction behavior.

## Scope

The verification is split across the two boundaries that own the behavior:

1. A repeatable Rust-bridge instrumentation test exercises PandaEngine, Android Keystore-backed
   session storage, Canopy gRPC, and Mailpit.
2. ADB-driven debug-app QA exercises Android deep-link delivery and the real Compose/navigation,
   IME, action-card, and mini-player behavior.

The work does not add a production test API, persist credentials or verification tokens in Kotlin,
change Canopy policy, or replace the focused JVM and Compose tests that are already green.

## Layer 1: Repeatable PandaEngine Lifecycle Test

Extend `PandaEngineCanopyLiveTest` with a separate opt-in auth lifecycle test. Keeping it separate
from the anonymous playback method makes failures attributable and preserves the existing baseline.

The test will:

1. Create a unique local email and an 8-64-code-point password without printing either value.
2. Create a test-owned absolute session file under the instrumentation target's
   `noBackupFilesDir`, deleting only that exact test directory before setup.
3. Construct PandaEngine with `AndroidKeystoreSecureSecretProtector`, configure the development
   backend, and assert that the initial snapshot is anonymous and no ciphertext exists.
4. Register through `PandaEngine.registerPassword` and require the typed verification-pending
   result while the engine remains anonymous.
5. Poll Mailpit's local HTTP API with a bounded deadline, select the message addressed to the unique
   email, and extract the opaque verification token in memory. Mail content and tokens must never be
   logged or written to disk.
6. Call `PandaEngine.verifyEmail` with the token and device label `PandaEmulatorNoStore`. Require an
   authenticated operation result, an authenticated snapshot, and a non-empty encrypted session
   file. The snapshot assertion occurs after `verifyEmail` returns, which is the publication point
   guarded by successful session-store replacement.
7. Close the engine, recreate it with the same session file and Keystore protector, configure the
   backend, and require the authenticated snapshot to be restored.
8. Log out, require an anonymous snapshot and removal of the test session file, then browse the root
   catalog successfully to prove anonymous access still works.

Mailpit polling and token parsing will remain test-only helpers. Polling will use condition-based
waiting with a short interval and a fixed overall deadline; it will fail with sanitized diagnostics
such as message count and HTTP status, never message bodies, addresses, or tokens.

## Layer 2: Debug App And ADB QA

Install and launch the current debug app on `PandaEmulatorNoStore`. Re-establish emulator Wi-Fi
IPv4 routing when necessary and configure `adb reverse tcp:8080 tcp:8080` for Canopy's opaque
loopback playback capability.

Drive taps from UI-automator bounds rather than screenshot coordinates. Use screenshots only as
visual evidence. The device pass will verify:

- anonymous catalog browsing and playback still work before authentication;
- the full Login and Create account tile surfaces invoke exactly one action;
- malformed email and 7/65-character passwords show inline validation and do not reach Canopy;
- focusing or editing an invalid field clears its visible error until revalidation;
- email IME Next moves focus to Password and password IME Done submits a valid form once;
- playback continues while Login/Create account hides the mini-player, and the mini-player returns
  on an eligible destination;
- a unique valid registration reaches the generic verification-pending state;
- the Mailpit token is transformed in memory into
  `pandawave-dev://verify-email?token=<opaque-token>` and delivered with an Android VIEW intent;
- the debug verification activity sanitizes the intent, verifies once, and returns to the main app
  with authenticated account state;
- force-stopping/relaunching the app recreates the service/engine and restores the authenticated
  session;
- Logout removes durable state and leaves anonymous browsing/playback usable.

If local validation accepts a request that Canopy rejects with canonical `INVALID_ARGUMENT`, the
pass will require the sanitized policy-mismatch copy. Other failures will be traced from the typed
PandaEngine result and Canopy logs without matching or exposing backend message text in the app.

## Evidence And Completion

Completion requires:

- the existing anonymous live instrumentation test passing;
- the new auth lifecycle instrumentation test passing against a freshly started Canopy stack;
- captured UI trees/screenshots and sanitized observations for the ADB-driven acceptance checks;
- focused automated tests remaining green for any code changed in response to a live defect;
- `graphify update .` after repository changes;
- PW-PRD-005 marked implemented only after every acceptance criterion has device or automated
  evidence.

The local Canopy stack is test infrastructure and should be stopped after the pass. Repository pushes
remain a separate final action after verification and review of the local commits.
