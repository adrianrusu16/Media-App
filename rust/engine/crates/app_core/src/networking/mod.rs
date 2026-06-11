//! Networking layer for the PandaEngine.
//!
//! This module owns the boundary between the engine and any remote backend.
//! It is intentionally **transport-agnostic**: the engine and the data layer
//! depend only on the [`BackendClient`] trait, never on the concrete transport
//! (gRPC/tonic, HTTP, etc.).
//!
//! The real, tonic-based client (`prost`-generated stubs, connection pooling,
//! tower interceptors, retry policies) will be implemented behind this trait in
//! a dedicated layer. Keeping the trait here means the rest of the engine stays
//! decoupled from `tonic`, and tests can inject a mock client.

pub mod audio_source_client;
pub mod backend_client;
pub mod jamendo_audio_source_client;
pub mod jamendo_proto;
pub mod jamendo_tonic_transport;
pub mod remote_repository;
pub mod retrying_audio_source_client;
pub mod retrying_backend_client;

pub use audio_source_client::{AudioChunk, AudioSourceClient, PlaybackSource};
pub use backend_client::BackendClient;
pub use jamendo_audio_source_client::JamendoAudioSourceClient;
pub use jamendo_proto::generated as jamendo_generated;
pub use jamendo_tonic_transport::JamendoTonicTransport;
pub use remote_repository::RemoteRepository;
pub use retrying_audio_source_client::RetryingAudioSourceClient;
pub use retrying_backend_client::RetryingBackendClient;
