# Canopy local protected-service acceptance

The protected acceptance test is opt-in. It never stores credentials in source
control and must be supplied a dedicated test identity through instrumentation
arguments. The local Canopy backend and its data are operator-managed
prerequisites; PandaWave starts neither the backend nor its supporting services.

## Local backend startup

Use the Canopy workspace in WSL for the backend-owned reference environment:

```bash
cd /home/catalina/projects/Canopy
./scripts/local-integration.sh up
./scripts/local-integration.sh status
```

The script runs Canopy as a WSL process and uses the scoped
`canopy-local-integration` Compose project for PostgreSQL, Mailpit, and Nginx.
The public developer endpoints are:

- gRPC: `http://127.0.0.1:50051` inside WSL/Windows, exposed to the Android
  emulator as `http://10.0.2.2:50051` through `client-connection.json`.
- Streaming and OpenAPI: `http://127.0.0.1:8080`, exposed to the emulator as
  `http://10.0.2.2:8080` in the same asset.
- Mailpit: `http://127.0.0.1:8025`, operator-only for local email delivery
  checks.

Do not copy PostgreSQL credentials, SMTP credentials, generated stream secrets,
raw tokens, Mailpit contents, or private authorization listener addresses into
PandaWave assets, Gradle properties, source files, shell history, or captured
test output.

Run the backend-owned smoke before Android acceptance when diagnosing a local
environment:

```bash
cd /home/catalina/projects/Canopy
./scripts/local-integration.sh test
```

Stop the environment when finished:

```bash
cd /home/catalina/projects/Canopy
./scripts/local-integration.sh down
```

## Emulator route

Boot the emulator and confirm adb sees it:

```powershell
adb devices
```

The debug `client-connection.json` uses `10.0.2.2` for the emulator-to-host
route. Canopy may return opaque playback capabilities rooted at its public
loopback base. Preserve those URLs exactly; if a returned URL uses device
loopback, add an adb reverse for the streaming port instead of rewriting the
capability:

```powershell
adb -s emulator-5554 reverse tcp:8080 tcp:8080
```

## Protected acceptance

Compile the opt-in test without contacting a backend:

```powershell
.\gradlew.bat :core:rust-bridge:compileDebugAndroidTestKotlin
```

After the WSL backend, emulator, and route are available, invoke only the
protected test and supply a dedicated non-production identity at runtime:

```powershell
.\gradlew.bat :core:rust-bridge:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.adrianrusu.pandawave.core.rust.bridge.engine.native.CanopyProtectedServicesIntegrationTest `
  -Pandroid.testInstrumentationRunnerArguments.canopyProtected=true `
  -Pandroid.testInstrumentationRunnerArguments.canopyProtectedEmail=<dedicated-email> `
  -Pandroid.testInstrumentationRunnerArguments.canopyProtectedPassword=<runtime-secret>
```

The compiled sequence covers profile upsert/get/update/get; unknown
preference-key seeding followed by known theme update/read and direct key
readback; history consent, two playback-completed records, page-size-one
continuation, consent disable, and purge verification; saved/liked
relationships; playlist create/update/list, membership, reorder, stale-revision
reconciliation, and deletion; protected discovery paging; account read; and
device-session paging that must enumerate to completion within a 20-request
bound before revoking the non-current session.

Account deletion stays off unless the identity is disposable and
`-Pandroid.testInstrumentationRunnerArguments.canopyProtectedDelete=true` is
also supplied. Without `canopyProtected=true`, JUnit records the test as skipped
through an assumption; it does not attempt a network connection.

## Status diagnosis

Use these checks in order:

1. `./scripts/local-integration.sh status` confirms Canopy, PostgreSQL, SMTP
   delivery, managed media, Nginx, and OpenAPI readiness from the backend side.
2. `adb devices` confirms the emulator is attached.
3. `client-connection.json` confirms the app is using `10.0.2.2` for the debug
   emulator deployment and TLS-required production rules for non-loopback
   deployments.
4. `PandaEngineCanopyLiveTest` proves anonymous status, catalog browse,
   playback resolution, opaque URL projection, and ranged audio response.
5. `CanopyProtectedServicesIntegrationTest` proves authenticated profile,
   history, library, playlist, discovery, account, and session flows.

Canonical failures should be diagnosed by dependency status and gRPC status
codes, not by matching backend error-message text.
