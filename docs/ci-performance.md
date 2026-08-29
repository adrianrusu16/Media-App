# CI performance checkpoints

The CI workflow emits GitHub Actions timing notices for the expensive phases
that are useful when comparing runs:

| Job | Timing labels |
| --- | --- |
| `android-fast` | Android SDK install, Gradle quality/lint/test, total job |
| `android-debug` | Android SDK install, one-ABI native assemble, total job |
| `android-release` | Android SDK install, production release assemble, total job |
| `rust` | clippy, test compilation (`--no-run`), test execution, total job |
| `benchmark` | Android SDK install, benchmark artifact assembly, total job |

The notices are emitted even when a timed command fails, so a failed run still
contains useful duration evidence. The Rust test execution step has a five
minute step timeout; compilation is intentionally completed first so the host
Cargo cache can be saved before tests execute.

## Baseline

Do not infer a baseline from local or failed runs. Populate this table from the
first three successful CI runs after this workflow is merged. Record the run
URL, event/ref, cache result, and each timing notice from the job summary.

| Run | Event/ref | Cache result | Android fast | Rust | Android debug | Android release | Benchmark |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| 1 | pending | pending | pending | pending | pending | pending | pending |
| 2 | pending | pending | pending | pending | pending | pending | pending |
| 3 | pending | pending | pending | pending | pending | pending | pending |
| Median | after three successful runs | — | pending | pending | pending | pending | pending |

For each run, use the total-job notice for the job column and retain the
phase notices when diagnosing regressions. A warm-cache comparison is valid
only when the lockfile, toolchain, NDK, ABI set, and source hash dimensions
match the previous run.

## Cache and trigger validation

Each Cargo cache is restored with `actions/cache/restore@v4` and saved with
`actions/cache/save@v4` using the same key dimensions (runner OS, Rust
toolchain, NDK where applicable, lockfiles, and crate sources). The Rust job
saves its cache after `cargo test --no-run` and before test execution. Confirm
on the first post-merge runs that a miss creates a cache and the next matching
run reports a hit; this cannot be proven from a local checkout.

Push and pull-request triggers ignore Markdown and `docs/**`-only changes.
After merging, verify that a code change still starts the required checks and
that a docs-only change is handled correctly by the repository's branch
protection rules. A required check that remains pending for a skipped workflow
must be addressed in branch protection rather than by reintroducing duplicate
build work.

## Manual diagnostics

Gradle build scans/profiles and Cargo timing output are intentionally manual
diagnostics. Run them only when a CI timing notice identifies a regression;
they are not part of every pull request and do not replace the notices above.

The repository does not enable `sccache` yet. Revisit it only after the three
warm-cache runs provide evidence that compiler-cache misses dominate the
remaining Rust time and that a shared cache backend is available.
