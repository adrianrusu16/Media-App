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
mod operation;
mod playback;
mod playlist;
mod request;
mod sdk;
mod session;
mod system;

pub use crate::networking::backend_client::CatalogPort;
pub use auth::CanopyAuthClient;
pub use catalog::CanopyCatalogClient;
pub use channel::CanopyChannel;
pub use config::{CanopyConnectionConfig, CanopyTlsConfig, DeploymentMode};
pub use discovery::CanopyDiscoveryClient;
pub use history::CanopyHistoryClient;
pub use library::CanopyLibraryClient;
pub use operation::{AuthRequirement, CanopyOperation};
pub use playback::CanopyPlaybackClient;
pub use playlist::CanopyPlaylistClient;
pub use session::SessionCoordinator;
pub use system::CanopySystemClient;

mod profile;
pub use profile::CanopyProfileClient;
