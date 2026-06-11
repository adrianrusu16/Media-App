// FFI crate root:
// - Keeps exported C-ABI surface stable via curated re-exports.
// - Delegates constants/mappings/types/entrypoints to focused modules.
mod constants;
mod mappings;
mod engine_handle;
mod types;
mod api;

pub use constants::*;
pub use api::*;
pub use engine_handle::PandaEngine;
pub use types::{FfiControlState, FfiEngineConfig, FfiEngineOutcome, FfiEngineSnapshot, FfiPlayerControls};


#[cfg(test)]
mod tests;

