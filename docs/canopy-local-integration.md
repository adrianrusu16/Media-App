# Canopy local protected-service acceptance

The protected acceptance test is opt-in. It never stores credentials in source control and must be supplied a dedicated test identity through instrumentation arguments. The local Canopy backend and its data are operator-managed prerequisites; PandaWave does not start or attach to them.

Required setup:

1. Start the approved local Canopy environment independently.
2. Configure the emulator network route expected by `client-connection.json`.
3. Create a dedicated non-production account and pass secrets only as instrumentation arguments. Do not put them in Gradle properties, source files, shell history, or captured test output.
4. Run the protected test with `canopyProtected=true`. Account deletion additionally requires an explicitly designated throwaway account and `canopyProtectedDelete=true`.

The compiled sequence covers profile upsert/get/update/get; unknown preference-key seeding followed by a known theme update/read and direct key readback; history consent, two distinct playback-completed records, a required page-size-one continuation with accumulated results, consent disable, and purge verification; saved/liked relationships; playlist create/update/list, membership, reorder, stale-revision reconciliation, and deletion; protected discovery first/next paging; account read; and device-session paging that must execute at least one continuation and enumerate to completion within a 20-request bound before revoking the non-current session. Destructive account deletion must never be enabled for a reusable identity.

This repository task was implemented under a boundary that forbids access to the external WSL Canopy project, so no backend-backed acceptance result is recorded here. Unit, compile, lint, and assembly gates remain valid without that backend.

Compile the opt-in test without contacting a backend:

```text
gradlew.bat :core:rust-bridge:compileDebugAndroidTestKotlin
```

After the approved backend and emulator routing are independently available, invoke only the protected test and supply a dedicated identity at runtime:

```text
gradlew.bat :core:rust-bridge:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.adrianrusu.pandawave.core.rust.bridge.engine.native.CanopyProtectedServicesIntegrationTest -Pandroid.testInstrumentationRunnerArguments.canopyProtected=true -Pandroid.testInstrumentationRunnerArguments.canopyProtectedEmail=<dedicated-email> -Pandroid.testInstrumentationRunnerArguments.canopyProtectedPassword=<runtime-secret>
```

The test creates two sessions and exercises the protected profile/preferences, history, discovery, library, playlist, account, and device-session paths. It checks playlist conflict reconciliation, forces history and device-session continuation requests, exhausts session pagination within a fixed bound, and revokes the non-current session. Account deletion stays off unless the identity is disposable and `-Pandroid.testInstrumentationRunnerArguments.canopyProtectedDelete=true` is also supplied. Without `canopyProtected=true`, JUnit records the test as skipped through an assumption; it does not attempt a network connection.
