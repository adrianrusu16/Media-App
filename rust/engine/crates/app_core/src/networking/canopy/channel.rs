use tonic_014::transport::{Channel, Endpoint};

use crate::{EngineError, EngineErrorType};

use super::CanopyConnectionConfig;

/// Cloneable shared HTTP/2 transport used by all canonical Canopy clients.
#[derive(Clone)]
pub struct CanopyChannel {
    channel: Channel,
}

impl CanopyChannel {
    pub async fn connect(config: &CanopyConnectionConfig) -> Result<Self, EngineError> {
        let endpoint = Endpoint::from_shared(config.grpc_endpoint().to_string()).map_err(|_| {
            EngineError::new(
                EngineErrorType::InvalidInput,
                "invalid gRPC endpoint",
                false,
            )
        })?;
        let channel = endpoint.connect().await.map_err(|error| {
            EngineError::new(EngineErrorType::Transport, error.to_string(), false)
        })?;
        Ok(Self { channel })
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
}
