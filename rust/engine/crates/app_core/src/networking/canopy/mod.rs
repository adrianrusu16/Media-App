//! Canonical Canopy backend adapter.
//!
//! Generated protobuf and gRPC types must remain inside this module tree.

mod catalog;
mod channel;
mod config;
mod error;
mod playback;
pub(crate) mod sdk;
mod system;

pub use crate::networking::backend_client::CatalogPort;
pub use catalog::CanopyCatalogClient;
pub use channel::CanopyChannel;
pub use config::{CanopyConnectionConfig, DeploymentMode};
pub use playback::CanopyPlaybackClient;
pub use system::CanopySystemClient;
