use super::super::*;
use panda_engine_core::{MediaItem, MediaRepository};
use std::ffi::{CString, c_char};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::time::Duration;

static REENTRANT_DISPATCH_RESULT: AtomicUsize = AtomicUsize::new(usize::MAX);
static NESTED_ENGINE_PTR: AtomicUsize = AtomicUsize::new(0);

unsafe extern "C" fn nested_dispatch_on_state_changed(_snapshot: FfiEngineSnapshot) {
    let engine_ptr = NESTED_ENGINE_PTR.load(Ordering::SeqCst) as *mut PandaEngine;
    let outcome =
        unsafe { panda_engine_dispatch(engine_ptr, FFI_COMMAND_PLAY, std::ptr::null(), 610) };
    REENTRANT_DISPATCH_RESULT.store(outcome.event_type as usize, Ordering::SeqCst);
}

unsafe extern "C" fn noop_on_event_emitted(_event_type: i32) {}

struct SlowSearchRepository;

struct PanicSearchRepository;

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

#[async_trait::async_trait]
impl MediaRepository for PanicSearchRepository {
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
        panic!("panic from async repository search")
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

    let query =
        CString::new(r#"{"version":1,"query":"slow","page":{"page_size":25,"page_token":null}}"#)
            .unwrap();
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

#[test]
fn ffi_nested_dispatch_from_observer_is_rejected_without_deadlock() {
    let engine = panda_engine_create(1000);

    unsafe {
        NESTED_ENGINE_PTR.store(engine as usize, Ordering::SeqCst);
        REENTRANT_DISPATCH_RESULT.store(usize::MAX, Ordering::SeqCst);
        panda_engine_set_observer(
            engine,
            nested_dispatch_on_state_changed,
            noop_on_event_emitted,
        );

        let outcome = panda_engine_dispatch(engine, FFI_COMMAND_PLAY, std::ptr::null(), 600);
        assert_eq!(outcome.event_type, FFI_EVENT_COMMAND_APPLIED);

        let nested_event_type = REENTRANT_DISPATCH_RESULT.load(Ordering::SeqCst) as i32;
        assert_eq!(nested_event_type, FFI_COMMAND_UNKNOWN);

        panda_engine_destroy(engine);
        NESTED_ENGINE_PTR.store(0, Ordering::SeqCst);
    }
}

#[test]
fn ffi_dispatch_handles_async_future_panic_and_returns_invalid_outcome() {
    let engine = panda_engine_create(1000);
    unsafe {
        (*engine)
            .engine
            .with_engine(|e| e.set_repository(Box::new(PanicSearchRepository)));
    }

    let query =
        CString::new(r#"{"version":1,"query":"panic","page":{"page_size":25,"page_token":null}}"#)
            .unwrap();
    let outcome = unsafe { panda_engine_dispatch(engine, FFI_COMMAND_SEARCH, query.as_ptr(), 700) };

    assert_eq!(outcome, FfiEngineOutcome::invalid());

    unsafe {
        panda_engine_destroy(engine);
    }
}
