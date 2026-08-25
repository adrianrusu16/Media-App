use super::*;

fn assert_clone_send_sync<T: Clone + Send + Sync>() {}

#[test]
fn ffi_handle_is_cloneable_send_and_sync_without_engine_access() {
    assert_clone_send_sync::<EngineActorHandle>();
}
