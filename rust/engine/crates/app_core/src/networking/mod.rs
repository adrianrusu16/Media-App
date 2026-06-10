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

pub mod backend_client;
pub mod remote_repository;

pub use backend_client::BackendClient;
pub use remote_repository::RemoteRepository;
