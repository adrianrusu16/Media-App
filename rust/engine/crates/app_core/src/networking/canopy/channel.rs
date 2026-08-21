use std::time::Duration;

use tonic_014::transport::{Certificate, Channel, ClientTlsConfig, Endpoint};

use crate::{EngineError, EngineErrorType};

use super::CanopyConnectionConfig;

const CONNECT_TIMEOUT: Duration = Duration::from_secs(5);

/// Cloneable shared HTTP/2 transport used by all canonical Canopy clients.
#[derive(Clone)]
pub struct CanopyChannel {
    channel: Channel,
}

impl CanopyChannel {
    /// Builds a reconnectable channel without requiring Canopy to be online.
    /// Endpoint validation and TLS configuration still happen eagerly; the
    /// actual connection is attempted by the first RPC and retried by Tonic.
    pub fn connect_lazy(config: &CanopyConnectionConfig) -> Result<Self, EngineError> {
        let mut endpoint = Endpoint::from_shared(config.grpc_endpoint().to_string())
            .map_err(|_| {
                EngineError::new(
                    EngineErrorType::InvalidInput,
                    "invalid gRPC endpoint",
                    false,
                )
            })?
            .connect_timeout(CONNECT_TIMEOUT);
        if config.grpc_endpoint().scheme_str() == Some("https") {
            endpoint = apply_tls_config(
                endpoint,
                tls_config(config.tls_server_name(), config.private_ca_pem()),
            )?;
        }
        Ok(Self {
            channel: endpoint.connect_lazy(),
        })
    }

    /// Creates a lazy transport for composition tests without opening a network connection.
    #[doc(hidden)]
    pub fn connect_lazy_for_test(endpoint: &'static str) -> Self {
        Self {
            channel: Endpoint::from_static(endpoint).connect_lazy(),
        }
    }

    pub(crate) fn clone_inner(&self) -> Channel {
        self.channel.clone()
    }
}

fn tls_config(server_name: &str, private_ca_pem: Option<&[u8]>) -> ClientTlsConfig {
    let mut tls = ClientTlsConfig::new()
        .with_enabled_roots()
        .domain_name(server_name);
    if let Some(pem) = private_ca_pem {
        tls = tls.ca_certificate(Certificate::from_pem(pem));
    }
    tls
}

fn apply_tls_config(endpoint: Endpoint, tls: ClientTlsConfig) -> Result<Endpoint, EngineError> {
    endpoint.tls_config(tls).map_err(|_| {
        EngineError::new(
            EngineErrorType::InvalidInput,
            "invalid gRPC TLS configuration",
            false,
        )
    })
}

#[cfg(test)]
mod tests {
    use super::{CanopyChannel, EngineErrorType};
    use tonic_014::transport::{Channel, Endpoint};

    #[tokio::test]
    async fn shared_channel_clones_the_same_transport_handle() {
        let inner = Endpoint::from_static("http://127.0.0.1:50051").connect_lazy();
        let shared = CanopyChannel { channel: inner };

        let _: Channel = shared.clone_inner();
        let _: Channel = shared.clone_inner();
    }

    #[test]
    fn production_connection_attempt_has_a_bounded_timeout() {
        assert!(super::CONNECT_TIMEOUT <= std::time::Duration::from_secs(5));
    }

    #[test]
    fn https_tls_configuration_enables_platform_roots() {
        let tls = super::tls_config("grpc.canopy.example", None);

        assert!(format!("{tls:?}").contains("with_native_roots: true"));
    }

    #[test]
    fn deployment_ca_augments_platform_trust() {
        let tls = super::tls_config(
            "grpc.canopy.example",
            Some(b"-----BEGIN CERTIFICATE-----\nAQIDBA==\n-----END CERTIFICATE-----"),
        );

        let rendered = format!("{tls:?}");
        assert!(rendered.contains("with_native_roots: true"));
        assert!(rendered.contains("certs: [Certificate"));
    }

    #[test]
    fn tls_application_failure_is_redacted() {
        let error = super::apply_tls_config(
            Endpoint::from_static("https://grpc.canopy.example"),
            super::tls_config(
                "grpc.canopy.example",
                Some(b"-----BEGIN CERTIFICATE-----\n#\n-----END CERTIFICATE-----"),
            ),
        )
        .unwrap_err();

        assert_eq!(error.error_type, EngineErrorType::InvalidInput);
        assert_eq!(error.message, "invalid gRPC TLS configuration");
    }
}
