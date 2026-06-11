mod in_memory;
mod snapshot_ext;
mod trait_def;
mod types;

pub use in_memory::InMemoryRepository;
pub use trait_def::MediaRepository;
pub use types::{MediaItem, MediaItemType};

#[cfg(test)]
pub use trait_def::MockMediaRepository;

#[cfg(test)]
mod tests;
