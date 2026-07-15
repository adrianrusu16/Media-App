//! Canonical Canopy backend adapter.
//!
//! Generated protobuf and gRPC types must remain inside this module tree.

mod channel;
mod config;
mod error;
pub mod sdk;
mod system;

pub use channel::CanopyChannel;
pub use config::{CanopyConnectionConfig, DeploymentMode};
pub use system::CanopySystemClient;
