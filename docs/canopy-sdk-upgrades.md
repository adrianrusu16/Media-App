# Canopy SDK Upgrades

PandaWave consumes the canonical `canopy.v1` API through immutable Prost and
Tonic packages published by the Buf Schema Registry (BSR). The two packages,
the resolved lockfile entries, and every shipped `client-connection.json`
contract are one compatibility unit. They must move together in a single
change.

The currently supported compatibility unit is the **locked BSR crate identity**
from `rust/engine/Cargo.lock`: commit `af019e2d7fa245a2a7d9fc21a4dd9afa`, Prost
`=0.5.0-00000000000000-af019e2d7fa2.2`, and Tonic
`=0.5.0-00000000000000-af019e2d7fa2.4`. The connection-contract label
`v0.2.0` in `client-connection.json` is that JSON field's stored label, not a
GitHub tag and not a separately published Buf BSR release.

## Upgrade procedure

1. Review the Canopy compatibility policy and changelog for every release
   between the current release and the target release. Identify wire changes,
   renamed or removed RPCs, changed status semantics, and mapping impacts.
2. Select one immutable BSR commit for the target Canopy release. Do not use a
   branch, tag, range, or different commit for each generator.
3. Select the Prost and Tonic artifacts generated from that same commit. Pin
   both dependencies with Cargo's exact `=` requirement and update them
   together in `rust/engine/crates/app_core/Cargo.toml`.
4. Regenerate `rust/engine/Cargo.lock` through Cargo. Do not add a local proto,
   `prost-build`, `tonic-build`, Buf generation, or an OpenAPI-generated client.
   The BSR artifacts remain the only production gRPC client source.
5. Update the release, full commit, package names, and exact package versions in
   both shipped connection artifacts:
   `app/src/debug/assets/client-connection.json` and
   `core/rust-bridge/src/androidTest/assets/client-connection.json`. Update the
   expected compatibility unit in `scripts/verify-canopy-sdk.ps1` in the same
   commit.
6. Run `./scripts/verify-canopy-sdk.ps1`. Treat a range, mixed commit fragment,
   unexpected lockfile resolution, stale documented release, or generation
   path as a failed upgrade. On Windows hosts whose execution policy blocks
   direct scripts, use
   `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-canopy-sdk.ps1`;
   this changes no persistent execution-policy setting.
7. Run all protobuf-to-domain and domain-to-request mapping tests. Exercise
   optional and unknown-field behavior explicitly so forward-compatible fields
   are preserved or safely ignored according to the Canopy policy.
8. Run the focused Canopy Rust tests and the full Rust workspace test suite.
9. Complete the real Canopy backend integration gate, including anonymous and
   authenticated operations affected by the release. Then run the Android
   emulator gate through the packaged debug asset and real gRPC transport.
10. Merge only after CI, the real backend gate, and the emulator gate all pass.
    Record the verified Canopy release and commit in the change description.

If a candidate release cannot satisfy these gates, keep the previous immutable
compatibility unit. Never merge only one generated package or relax an exact
requirement to make resolution succeed.
