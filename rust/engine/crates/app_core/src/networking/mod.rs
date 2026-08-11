//! Networking layer for the PandaEngine.
//!
//! This module owns the boundary between the engine and any remote backend.
//! It is intentionally **transport-agnostic**: the engine and the data layer
//! depend only on the [`CatalogPort`] trait, never on the concrete transport
//! (gRPC/tonic, HTTP, etc.).
//!
//! The real, tonic-based client (`prost`-generated stubs, connection pooling,
//! tower interceptors, retry policies) will be implemented behind this trait in
//! a dedicated layer. Keeping the trait here means the rest of the engine stays
//! decoupled from `tonic`, and tests can inject a mock client.

pub mod audio_source_client;
mod auth_port;
mod auth_state_provider;
pub mod backend_client;
pub mod canopy;
pub mod canopy_tonic_transport;
pub mod remote_repository;
pub mod retrying_audio_source_client;
pub mod system_port;

pub use audio_source_client::{AudioChunk, AudioSourceClient, PlaybackPort, PlaybackSource};
pub use auth_port::AuthPort;
pub use auth_state_provider::AuthStateProvider;
pub use backend_client::CatalogPort;
pub use canopy::CanopyPlaylistClient;
pub use canopy::{
    CanopyAuthClient, CanopyCatalogClient, CanopyDiscoveryClient, CanopyHistoryClient,
    CanopyLibraryClient, CanopyPlaybackClient, SessionCoordinator,
};
pub use canopy_tonic_transport::CanopyTonicTransport;
pub use remote_repository::RemoteRepository;
pub use retrying_audio_source_client::RetryingAudioSourceClient;
pub use system_port::SystemPort;

pub use canopy::CanopyProfileClient;
