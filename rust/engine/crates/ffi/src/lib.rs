// FFI crate root:
// - Keeps exported C-ABI surface stable via curated re-exports.
// - Delegates constants/mappings/types/entrypoints to focused modules.
mod api;
mod constants;
mod engine_handle;
mod jni_audio_source_client;
mod jni_bridge;
mod mappings;
mod types;

pub use api::*;
pub use constants::*;
pub use engine_handle::PandaEngine;
pub use types::{
    FfiControlState, FfiEngineConfig, FfiEngineOutcome, FfiEngineSnapshot, FfiPlayerControls,
};

#[cfg(test)]
mod tests;
