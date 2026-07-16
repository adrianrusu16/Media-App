// Data layer module root:
// - Declares storage/session boundaries consumed by engine internals.
// - Submodules own queueing, repository access, persistence, and media-session state.
pub mod encrypted_session_store;
pub mod persistence;
pub mod queue;
pub mod repository;
pub mod session;
pub mod session_store;
