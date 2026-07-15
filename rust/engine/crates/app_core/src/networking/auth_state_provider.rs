use crate::AuthState;

/// Service-neutral source for the engine's current credential-free auth state.
///
/// Implementations remain responsible for all credential and transport details.
pub trait AuthStateProvider: Send + Sync {
    fn current_auth_state(&self) -> AuthState;
}
