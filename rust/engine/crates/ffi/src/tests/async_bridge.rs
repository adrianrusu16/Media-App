use super::super::*;
use panda_engine_core::{MediaItem, MediaRepository};
use std::ffi::{CString, c_char};
use std::time::Duration;

struct SlowSearchRepository;

#[async_trait::async_trait]
impl MediaRepository for SlowSearchRepository {
    fn get_by_id(&self, _id: &str) -> Option<MediaItem> {
        None
    }

    fn get_next(&self, _current_id: &str) -> Option<MediaItem> {
        None
    }

    fn get_previous(&self, _current_id: &str) -> Option<MediaItem> {
        None
    }

    async fn browse(&self, _parent_id: &str) -> anyhow::Result<Vec<MediaItem>> {
        Ok(vec![])
    }

    async fn search(&self, _query: &str) -> anyhow::Result<Vec<MediaItem>> {
        tokio::time::sleep(Duration::from_millis(80)).await;
        Ok(vec![MediaItem {
            id: "slow-1".to_string(),
            title: "Slow Result".to_string(),
            ..Default::default()
        }])
    }
}

#[test]
fn ffi_block_on_bridge_handles_slow_async_dispatch_without_deadlock() {
    let engine = panda_engine_create(1000);
    unsafe {
        (*engine)
            .engine
            .with_engine(|e| e.set_repository(Box::new(SlowSearchRepository)));
    }

    let query = CString::new("slow").unwrap();
    let (tx, rx) = std::sync::mpsc::channel();
    let engine_addr = engine as usize;
    let query_addr = query.as_ptr() as usize;
    std::thread::spawn(move || {
        let outcome = unsafe {
            panda_engine_dispatch(
                engine_addr as *mut PandaEngine,
                FFI_COMMAND_SEARCH,
                query_addr as *const c_char,
                500,
            )
        };
        let _ = tx.send(outcome);
    });

    let outcome = rx
        .recv_timeout(Duration::from_millis(400))
        .expect("dispatch timed out, possible deadlock in FFI block_on bridge");
    assert_eq!(1, outcome.snapshot.search_results_count);

    unsafe {
        panda_engine_destroy(engine);
    }
}
