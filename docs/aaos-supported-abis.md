# AAOS supported native ABIs

The production native package currently ships the four ABIs below. This is a
documentation contract for the Android/AAOS distribution; it does not change
the Gradle defaults or remove any target.

| Android ABI | Rust target | Current role |
| --- | --- | --- |
| `arm64-v8a` | `aarch64-linux-android` | 64-bit ARM production devices |
| `armeabi-v7a` | `armv7-linux-androideabi` | 32-bit ARM compatibility devices |
| `x86` | `i686-linux-android` | 32-bit x86 emulator/device compatibility |
| `x86_64` | `x86_64-linux-android` | 64-bit x86 AAOS/emulator compatibility |

Release builds use all four by default:

```text
arm64-v8a,armeabi-v7a,x86,x86_64
```

Debug builds intentionally default to `x86_64` for the common AAOS emulator
workflow. A developer can select another debug ABI with
`-PpandaEngine.debugAbis=<abi>`; this does not change the production set.

ABI reduction is a product and device-support decision, not a CI optimization.
Before removing an ABI, obtain an explicit platform-support review covering
the target AAOS images, supported OEM hardware, release artifact consumers,
and rollback/compatibility requirements. Until that review is complete, keep
all four production ABIs and keep the release workflow building them once.
