//! Canonical Canopy backend adapter.
//!
//! Generated protobuf and gRPC types must remain inside this module tree.

mod auth;
mod catalog;
mod channel;
mod clock;
mod config;
mod discovery;
mod error;
mod history;
mod library;
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
pub use discovery::CanopyDiscoveryClient;
pub use history::CanopyHistoryClient;
pub use library::CanopyLibraryClient;
pub use playback::CanopyPlaybackClient;
pub use session::SessionCoordinator;
pub use system::CanopySystemClient;

mod profile;
pub use profile::CanopyProfileClient;
