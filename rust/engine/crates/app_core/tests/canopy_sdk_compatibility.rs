use std::fs;
use std::path::{Path, PathBuf};
use std::process::{Command, Output};

const CANONICAL_COMMIT: &str = "145678c1d73e45b7bbaebf7e16ee4d64";

struct Fixture {
    root: PathBuf,
}

impl Fixture {
    fn new(label: &str) -> Self {
        let root = std::env::temp_dir().join(format!(
            "pandawave-canopy-sdk-{label}-{}-{}",
            std::process::id(),
            fastrand::u64(..)
        ));
        let repository = repository_root();

        copy_file(
            &repository.join("rust/engine/Cargo.toml"),
            &root.join("rust/engine/Cargo.toml"),
        );
        copy_file(
            &repository.join("rust/engine/Cargo.lock"),
            &root.join("rust/engine/Cargo.lock"),
        );
        copy_file(
            &repository.join("rust/engine/.cargo/config.toml"),
            &root.join("rust/engine/.cargo/config.toml"),
        );
        copy_file(
            &repository.join("rust/engine/crates/app_core/Cargo.toml"),
            &root.join("rust/engine/crates/app_core/Cargo.toml"),
        );
        copy_file(
            &repository.join("rust/engine/crates/ffi/Cargo.toml"),
            &root.join("rust/engine/crates/ffi/Cargo.toml"),
        );
        copy_file(
            &repository.join("app/src/debug/assets/client-connection.json"),
            &root.join("app/src/debug/assets/client-connection.json"),
        );
        copy_file(
            &repository.join("core/rust-bridge/src/androidTest/assets/client-connection.json"),
            &root.join("core/rust-bridge/src/androidTest/assets/client-connection.json"),
        );
        write_file(&root.join("rust/engine/crates/app_core/src/lib.rs"), "");
        write_file(&root.join("rust/engine/crates/ffi/src/lib.rs"), "");

        Self { root }
    }

    fn verify(&self) -> Output {
        let script = repository_root().join("scripts/verify-canopy-sdk.ps1");
        let mut command = powershell_command();

        command
            .arg(script)
            .arg("-RepositoryRoot")
            .arg(&self.root)
            .output()
            .expect("PowerShell must be available to run the Canopy SDK verifier")
    }
}

impl Drop for Fixture {
    fn drop(&mut self) {
        let _ = fs::remove_dir_all(&self.root);
    }
}

#[test]
fn verifier_defaults_to_the_repository_containing_the_script() {
    let script = repository_root().join("scripts/verify-canopy-sdk.ps1");
    let mut command = powershell_command();
    let output = command
        .arg(script)
        .output()
        .expect("PowerShell must be available to run the Canopy SDK verifier");

    assert_success("no-argument CI invocation", output);
}

#[test]
fn verifier_rejects_mixed_or_generated_sdk_contracts() {
    let baseline = Fixture::new("baseline");
    assert_success("canonical compatibility contract", baseline.verify());

    let mixed_commit = Fixture::new("mixed-commit");
    replace_in_file(
        &mixed_commit
            .root
            .join("app/src/debug/assets/client-connection.json"),
        CANONICAL_COMMIT,
        "245678c1d73e45b7bbaebf7e16ee4d64",
    );
    assert_rejected(
        "mixed artifact commit",
        mixed_commit.verify(),
        "immutable commit is",
    );

    let second_artifact_commit = Fixture::new("second-artifact-commit");
    replace_in_file(
        &second_artifact_commit
            .root
            .join("core/rust-bridge/src/androidTest/assets/client-connection.json"),
        CANONICAL_COMMIT,
        "345678c1d73e45b7bbaebf7e16ee4d64",
    );
    assert_rejected(
        "second shipped artifact commit",
        second_artifact_commit.verify(),
        "core/rust-bridge/src/androidTest/assets/client-connection.json immutable commit is",
    );

    let stale_release = Fixture::new("stale-release");
    replace_in_file(
        &stale_release
            .root
            .join("app/src/debug/assets/client-connection.json"),
        "\"release\": \"v0.2.0\"",
        "\"release\": \"v0.1.0\"",
    );
    assert_rejected(
        "stale documented release",
        stale_release.verify(),
        "documented release is",
    );

    let wrong_package = Fixture::new("wrong-package");
    replace_in_file(
        &wrong_package
            .root
            .join("app/src/debug/assets/client-connection.json"),
        "pandawave_canopy-api_community_neoeinstein-prost",
        "pandawave_canopy-api_other-prost",
    );
    assert_rejected(
        "artifact package name mismatch",
        wrong_package.verify(),
        "Prost package is",
    );

    let wrong_artifact_version = Fixture::new("wrong-artifact-version");
    replace_in_file(
        &wrong_artifact_version
            .root
            .join("app/src/debug/assets/client-connection.json"),
        "=0.5.0-00000000000000-145678c1d73e.4",
        "=0.5.0-00000000000000-145678c1d73e.5",
    );
    assert_rejected(
        "artifact package version mismatch",
        wrong_artifact_version.verify(),
        "Tonic version is",
    );

    let ranged_tonic = Fixture::new("ranged-tonic");
    replace_in_file(
        &ranged_tonic
            .root
            .join("rust/engine/crates/app_core/Cargo.toml"),
        "=0.5.0-00000000000000-145678c1d73e.4",
        ">=0.5.0-00000000000000-145678c1d73e.4, <0.6",
    );
    assert_rejected(
        "Tonic version range",
        ranged_tonic.verify(),
        "declared Tonic requirement is",
    );

    let stale_lock = Fixture::new("stale-lock");
    replace_in_file(
        &stale_lock.root.join("rust/engine/Cargo.lock"),
        "version = \"0.5.0-00000000000000-145678c1d73e.4\"",
        "version = \"0.5.0-00000000000000-145678c1d73e.5\"",
    );
    assert_rejected(
        "lockfile resolution mismatch",
        stale_lock.verify(),
        "cargo metadata could not parse the locked workspace",
    );

    let local_proto = Fixture::new("local-proto");
    write_file(
        &local_proto
            .root
            .join("rust/engine/crates/app_core/proto/canopy.proto"),
        "syntax = \"proto3\"; package canopy.v1;",
    );
    assert_rejected(
        "local protobuf generation input",
        local_proto.verify(),
        "local protobuf inputs are forbidden",
    );
}

#[test]
fn verifier_rejects_repository_root_openapi_generation_config() {
    let fixture = Fixture::new("root-openapi-generation");
    write_file(
        &fixture.root.join("openapi-generator-config.yaml"),
        "generatorName: rust\ninputSpec: openapi.json\n",
    );

    assert_rejected(
        "repository-root OpenAPI client generation config",
        fixture.verify(),
        "client generation configuration is forbidden",
    );
}

#[test]
fn verifier_rejects_generators_in_groovy_and_command_tooling() {
    let groovy = Fixture::new("groovy-generation");
    write_file(
        &groovy
            .root
            .join("build-logic/src/main/groovy/CanopySdk.gradle.groovy"),
        "plugins { id 'org.openapi.generator' }\nopenApiGenerate { generatorName = 'kotlin' }\n",
    );
    assert_rejected(
        "Groovy OpenAPI client generation",
        groovy.verify(),
        "local protobuf or OpenAPI client generation is forbidden",
    );

    let command = Fixture::new("command-generation");
    write_file(
        &command.root.join("tools/generate-canopy.cmd"),
        "@echo off\nopenapi-generator-cli generate -i openapi.json\n",
    );
    assert_rejected(
        "command-file OpenAPI client generation",
        command.verify(),
        "local protobuf or OpenAPI client generation is forbidden",
    );

    let extensionless = Fixture::new("extensionless-generation");
    write_file(
        &extensionless.root.join("tools/generate-canopy"),
        "#!/bin/sh\nopenapi-generator-cli generate -i openapi.json\n",
    );
    assert_rejected(
        "extensionless OpenAPI client generation",
        extensionless.verify(),
        "local protobuf or OpenAPI client generation is forbidden",
    );
}

#[test]
fn verifier_rejects_uppercase_local_proto_input() {
    let fixture = Fixture::new("uppercase-local-proto");
    write_file(
        &fixture
            .root
            .join("rust/engine/crates/app_core/proto/CANOPY.PROTO"),
        "syntax = \"proto3\"; package canopy.v1;",
    );

    assert_rejected(
        "uppercase local protobuf input",
        fixture.verify(),
        "local protobuf inputs are forbidden",
    );
}

#[test]
fn verifier_discovers_and_rejects_a_third_shipped_artifact() {
    let fixture = Fixture::new("third-shipped-artifact");
    let source = fixture
        .root
        .join("app/src/debug/assets/client-connection.json");
    let third = fixture
        .root
        .join("feature/canopy/src/debug/assets/client-connection.json");
    copy_file(&source, &third);
    replace_in_file(&third, "\"release\": \"v0.2.0\"", "\"release\": \"v0.1.0\"");

    assert_rejected(
        "stale third shipped connection artifact",
        fixture.verify(),
        "feature/canopy/src/debug/assets/client-connection.json documented release is",
    );
}

#[test]
fn verifier_accepts_benign_build_tool_text() {
    let fixture = Fixture::new("benign-build-tool-text");
    write_file(
        &fixture
            .root
            .join("build-logic/src/main/groovy/CanopyMetadata.gradle.groovy"),
        "description = 'Validate immutable Canopy schema metadata'\n",
    );
    write_file(
        &fixture.root.join("tools/check-canopy.cmd"),
        "@echo off\necho Validating checked-in SDK pins\n",
    );
    write_file(
        &fixture.root.join("tools/check-canopy"),
        "#!/bin/sh\necho Validating checked-in SDK pins\n",
    );
    write_file(
        &fixture.root.join("gradle/CanopyEndpoints.kt"),
        "val openApiEndpoint = \"/openapi.json\"\nval protocolName = \"canopy.v1\"\n",
    );
    write_file(
        &fixture.root.join("generated/CanopyClient.java"),
        "// test fixture: openapi-generator-cli generate\n",
    );
    write_file(
        &fixture.root.join("core/testing/CanopyGenerator.kt"),
        "// test support fixture: openApiGenerate\n",
    );

    assert_success("benign production build-tool text", fixture.verify());
}

#[test]
fn verifier_reports_production_violations_deterministically() {
    let fixture = Fixture::new("deterministic-violation");
    write_file(
        &fixture.root.join("zeta/CANOPY.PROTO"),
        "syntax = \"proto3\"; package canopy.v1;",
    );
    write_file(
        &fixture.root.join("alpha-first/CANOPY.PROTO"),
        "syntax = \"proto3\"; package canopy.v1;",
    );

    assert_rejected(
        "deterministic production violation",
        fixture.verify(),
        "alpha-first",
    );
}

fn powershell_command() -> Command {
    if cfg!(windows) {
        let mut command = Command::new("powershell.exe");
        command.args(["-NoProfile", "-ExecutionPolicy", "Bypass", "-File"]);
        command
    } else {
        let mut command = Command::new("pwsh");
        command.args(["-NoProfile", "-File"]);
        command
    }
}

fn repository_root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .ancestors()
        .nth(4)
        .expect("app_core must live under rust/engine/crates")
        .to_path_buf()
}

fn copy_file(source: &Path, destination: &Path) {
    fs::create_dir_all(
        destination
            .parent()
            .expect("fixture file must have a parent"),
    )
    .unwrap();
    fs::copy(source, destination).unwrap_or_else(|error| {
        panic!(
            "failed to copy {} to {}: {error}",
            source.display(),
            destination.display()
        )
    });
}

fn write_file(path: &Path, contents: &str) {
    fs::create_dir_all(path.parent().expect("fixture file must have a parent")).unwrap();
    fs::write(path, contents).unwrap();
}

fn replace_in_file(path: &Path, old: &str, new: &str) {
    let contents = fs::read_to_string(path).unwrap();
    assert!(contents.contains(old), "fixture did not contain {old}");
    fs::write(path, contents.replacen(old, new, 1)).unwrap();
}

fn assert_success(label: &str, output: Output) {
    assert!(
        output.status.success(),
        "{label} should pass\nstdout:\n{}\nstderr:\n{}",
        String::from_utf8_lossy(&output.stdout),
        String::from_utf8_lossy(&output.stderr)
    );
}

fn assert_rejected(label: &str, output: Output, expected_error: &str) {
    let stdout = String::from_utf8_lossy(&output.stdout);
    let stderr = String::from_utf8_lossy(&output.stderr);
    let diagnostic = format!("{stdout} {stderr}")
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ");
    assert!(
        !output.status.success(),
        "{label} should be rejected\nstdout:\n{}\nstderr:\n{}",
        stdout,
        stderr
    );
    assert!(
        diagnostic.contains(expected_error),
        "{label} should explain '{expected_error}'\nstdout:\n{stdout}\nstderr:\n{stderr}"
    );
}
