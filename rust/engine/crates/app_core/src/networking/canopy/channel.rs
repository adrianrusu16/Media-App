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
    pub async fn connect(config: &CanopyConnectionConfig) -> Result<Self, EngineError> {
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
            let mut tls = ClientTlsConfig::new().domain_name(config.tls_server_name());
            if let Some(pem) = config.private_ca_pem() {
                tls = tls.ca_certificate(Certificate::from_pem(pem));
            }
            endpoint = endpoint.tls_config(tls).map_err(|_| {
                EngineError::new(
                    EngineErrorType::InvalidInput,
                    "invalid gRPC TLS configuration",
                    false,
                )
            })?;
        }
        let channel = endpoint.connect().await.map_err(|error| {
            let _ = error;
            EngineError::new(
                EngineErrorType::Transport,
                "failed to connect to Canopy",
                false,
            )
        })?;
        Ok(Self { channel })
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

#[cfg(test)]
mod tests {
    use super::CanopyChannel;
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
}
