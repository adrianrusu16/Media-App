use serde::Deserialize;
use tonic_014::transport::Uri;
use url::Url;

use crate::{EngineError, EngineErrorType};

const PROTOBUF_PACKAGE: &str = "canopy.v1";
const BSR_MODULE: &str = "buf.build/pandawave/canopy-api";
const RELEASE: &str = "v0.2.0";
const COMMIT: &str = "af019e2d7fa245a2a7d9fc21a4dd9afa";
const PROST_PACKAGE: &str = "pandawave_canopy-api_community_neoeinstein-prost";
const PROST_VERSION: &str = "=0.5.0-00000000000000-af019e2d7fa2.2";
const TONIC_PACKAGE: &str = "pandawave_canopy-api_community_neoeinstein-tonic";
const TONIC_VERSION: &str = "=0.5.0-00000000000000-af019e2d7fa2.4";
const MAX_PUBLIC_CA_PEM_BYTES: usize = 64 * 1024;

/// Security posture supplied by the Android build variant, not by JSON input.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DeploymentMode {
    Development,
    Production,
}

/// Public TLS trust inputs used when connecting to the Canopy gRPC endpoint.
#[derive(Clone, PartialEq)]
pub struct CanopyTlsConfig {
    server_name: String,
    private_ca_pem: Option<Vec<u8>>,
}

impl std::fmt::Debug for CanopyTlsConfig {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        formatter
            .debug_struct("CanopyTlsConfig")
            .field("server_name", &"[REDACTED]")
            .field("private_ca_pem", &"[REDACTED]")
            .finish()
    }
}

impl CanopyTlsConfig {
    pub fn server_name(&self) -> &str {
        &self.server_name
    }

    /// An optional deployment-supplied public CA certificate chain.
    pub fn private_ca_pem(&self) -> Option<&[u8]> {
        self.private_ca_pem.as_deref()
    }
}

/// Validated, secret-free public endpoints for the Canopy adapter.
#[derive(Clone, PartialEq)]
pub struct CanopyConnectionConfig {
    grpc_endpoint: Uri,
    stream_base_url: Url,
    openapi_url: Url,
    environment: String,
    tls: CanopyTlsConfig,
}

impl std::fmt::Debug for CanopyConnectionConfig {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        formatter
            .debug_struct("CanopyConnectionConfig")
            .field("grpc_endpoint", &"[REDACTED]")
            .field("stream_base_url", &"[REDACTED]")
            .field("openapi_url", &"[REDACTED]")
            .field("environment", &"[REDACTED]")
            .field("tls", &self.tls)
            .finish()
    }
}

impl CanopyConnectionConfig {
    pub fn parse_and_validate(json: &str, mode: DeploymentMode) -> Result<Self, EngineError> {
        let raw: RawConnectionConfig =
            serde_json::from_str(json).map_err(|_| invalid_input("invalid connection JSON"))?;

        if raw.schema_version != 1 {
            return Err(invalid_input("unsupported connection schema"));
        }
        if raw.environment.is_empty() {
            return Err(invalid_input("empty connection environment"));
        }
        validate_contract(&raw.contract)?;
        validate_authentication(&raw.authentication)?;
        if !raw.transport.tls_required_outside_loopback {
            return Err(invalid_input(
                "Canopy handoff must require TLS outside loopback",
            ));
        }

        let grpc_endpoint = raw
            .transport
            .grpc_endpoint
            .parse::<Uri>()
            .map_err(|_| invalid_input("invalid gRPC endpoint"))?;
        let stream_base_url = Url::parse(&raw.transport.stream_base_url)
            .map_err(|_| invalid_input("invalid stream URL"))?;
        let openapi_url = Url::parse(&raw.transport.openapi_url)
            .map_err(|_| invalid_input("invalid OpenAPI URL"))?;

        validate_endpoints(&grpc_endpoint, &stream_base_url, &openapi_url, mode)?;
        let tls = validate_tls_config(&grpc_endpoint, &raw.transport)?;

        Ok(Self {
            grpc_endpoint,
            stream_base_url,
            openapi_url,
            environment: raw.environment,
            tls,
        })
    }

    pub fn grpc_endpoint(&self) -> &Uri {
        &self.grpc_endpoint
    }

    pub fn stream_base_url(&self) -> &Url {
        &self.stream_base_url
    }

    pub fn openapi_url(&self) -> &Url {
        &self.openapi_url
    }

    pub fn environment(&self) -> &str {
        &self.environment
    }

    pub fn tls_server_name(&self) -> &str {
        self.tls.server_name()
    }

    pub fn private_ca_pem(&self) -> Option<&[u8]> {
        self.tls.private_ca_pem()
    }
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct RawConnectionConfig {
    schema_version: u32,
    environment: String,
    contract: RawContract,
    transport: RawTransport,
    authentication: RawAuthentication,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct RawContract {
    protobuf_package: String,
    bsr_module: String,
    release: String,
    commit: String,
    prost_package: String,
    prost_version: String,
    tonic_package: String,
    tonic_version: String,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct RawTransport {
    grpc_endpoint: String,
    stream_base_url: String,
    openapi_url: String,
    tls_required_outside_loopback: bool,
    #[serde(default)]
    tls_server_name: Option<String>,
    #[serde(default)]
    private_ca_pem: Option<String>,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct RawAuthentication {
    metadata_key: String,
    metadata_scheme: String,
    verification_action_relative_path: String,
    verification_token_query_parameter: String,
    password_reset_action_relative_path: String,
    password_reset_token_query_parameter: String,
    expiry_query_parameter: String,
    auth_service_requires_postgresql: bool,
    password_bootstrap_requires_email_delivery: bool,
}

fn validate_contract(contract: &RawContract) -> Result<(), EngineError> {
    let valid = contract.protobuf_package == PROTOBUF_PACKAGE
        && contract.bsr_module == BSR_MODULE
        && contract.release == RELEASE
        && contract.commit == COMMIT
        && contract.prost_package == PROST_PACKAGE
        && contract.prost_version == PROST_VERSION
        && contract.tonic_package == TONIC_PACKAGE
        && contract.tonic_version == TONIC_VERSION;

    if valid {
        Ok(())
    } else {
        Err(invalid_input("unsupported Canopy contract"))
    }
}

fn validate_authentication(authentication: &RawAuthentication) -> Result<(), EngineError> {
    let valid = authentication.metadata_key == "authorization"
        && authentication.metadata_scheme == "Bearer"
        && authentication.verification_action_relative_path == "verify-email"
        && authentication.verification_token_query_parameter == "token"
        && authentication.password_reset_action_relative_path == "reset-password"
        && authentication.password_reset_token_query_parameter == "token"
        && authentication.expiry_query_parameter == "expires_at"
        && authentication.auth_service_requires_postgresql
        && authentication.password_bootstrap_requires_email_delivery;

    if valid {
        Ok(())
    } else {
        Err(invalid_input("unsupported authentication metadata"))
    }
}

fn validate_endpoints(
    grpc: &Uri,
    stream: &Url,
    openapi: &Url,
    mode: DeploymentMode,
) -> Result<(), EngineError> {
    let grpc_scheme = grpc
        .scheme_str()
        .ok_or_else(|| invalid_input("gRPC endpoint has no scheme"))?;
    let grpc_host = grpc
        .host()
        .ok_or_else(|| invalid_input("gRPC endpoint has no host"))?;
    let stream_host = validate_public_url(stream, "stream")?;
    let openapi_host = validate_public_url(openapi, "OpenAPI")?;
    let hosts = [grpc_host, stream_host, openapi_host];

    if hosts.iter().any(|host| is_listen_address(host)) {
        return Err(invalid_input("listen address is not a client endpoint"));
    }

    if !matches!(grpc_scheme, "http" | "https") {
        return Err(invalid_input("unsupported gRPC endpoint scheme"));
    }

    let cleartext =
        grpc_scheme == "http" || stream.scheme() == "http" || openapi.scheme() == "http";
    let all_approved_development_hosts = hosts.iter().all(|host| is_development_host(host));

    if cleartext && !(mode == DeploymentMode::Development && all_approved_development_hosts) {
        return Err(EngineError::unsafe_transport());
    }

    Ok(())
}

fn validate_tls_config(
    grpc_endpoint: &Uri,
    transport: &RawTransport,
) -> Result<CanopyTlsConfig, EngineError> {
    let grpc_host = grpc_endpoint
        .host()
        .ok_or_else(|| invalid_input("gRPC endpoint has no host"))?;
    let server_name = transport.tls_server_name.as_deref().unwrap_or(grpc_host);

    if server_name.is_empty() || !server_name.eq_ignore_ascii_case(grpc_host) {
        return Err(invalid_input(
            "TLS server name must match gRPC endpoint host",
        ));
    }

    let private_ca_pem = transport
        .private_ca_pem
        .as_deref()
        .map(validate_public_ca_pem)
        .transpose()?;

    if private_ca_pem.is_some() && grpc_endpoint.scheme_str() != Some("https") {
        return Err(EngineError::unsafe_transport());
    }

    Ok(CanopyTlsConfig {
        server_name: server_name.to_owned(),
        private_ca_pem,
    })
}

fn validate_public_ca_pem(pem: &str) -> Result<Vec<u8>, EngineError> {
    if pem.len() > MAX_PUBLIC_CA_PEM_BYTES || !is_public_certificate_chain(pem) {
        return Err(invalid_input("invalid TLS trust certificate"));
    }

    Ok(pem.as_bytes().to_vec())
}

fn is_public_certificate_chain(pem: &str) -> bool {
    const BEGIN: &str = "-----BEGIN CERTIFICATE-----";
    const END: &str = "-----END CERTIFICATE-----";

    let mut remaining = pem.trim();
    let mut certificate_count = 0;
    while !remaining.is_empty() {
        let Some(after_begin) = remaining.strip_prefix(BEGIN) else {
            return false;
        };
        let Some((body, after_end)) = after_begin.split_once(END) else {
            return false;
        };
        if body.is_empty()
            || !body.chars().all(|character| {
                character.is_ascii_alphanumeric()
                    || matches!(character, '+' | '/' | '=' | '\r' | '\n')
            })
        {
            return false;
        }
        certificate_count += 1;
        remaining = after_end.trim();
    }

    certificate_count > 0
}

fn validate_public_url<'a>(url: &'a Url, label: &str) -> Result<&'a str, EngineError> {
    if !matches!(url.scheme(), "http" | "https") {
        return Err(invalid_input(format!("unsupported {label} URL scheme")));
    }
    if !url.username().is_empty() || url.password().is_some() {
        return Err(invalid_input(format!("{label} URL contains credentials")));
    }
    url.host_str()
        .ok_or_else(|| invalid_input(format!("{label} URL has no host")))
}

fn is_development_host(host: &str) -> bool {
    matches!(host, "127.0.0.1" | "localhost" | "10.0.2.2" | "::1")
}

fn is_listen_address(host: &str) -> bool {
    matches!(host, "0.0.0.0" | "::")
}

fn invalid_input(message: impl Into<String>) -> EngineError {
    EngineError::new(EngineErrorType::InvalidInput, message, false)
}
