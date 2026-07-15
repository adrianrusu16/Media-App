//! Canonical Canopy backend adapter.
//!
//! Generated protobuf and gRPC types must remain inside this module tree.

mod auth;
mod catalog;
mod channel;
mod config;
mod error;
mod playback;
mod request;
pub(crate) mod sdk;
mod session;
mod system;

pub use crate::networking::backend_client::CatalogPort;
pub use auth::CanopyAuthClient;
pub use catalog::CanopyCatalogClient;
pub use channel::CanopyChannel;
pub use config::{CanopyConnectionConfig, DeploymentMode};
pub use playback::CanopyPlaybackClient;
pub use session::SessionCoordinator;
pub use system::CanopySystemClient;
