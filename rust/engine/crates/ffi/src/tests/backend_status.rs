use std::ffi::{CStr, c_char};
use std::ptr;
use std::sync::Arc;

use panda_engine_core::{
    EngineBackendStatus, EngineDependencyStatus, EngineError, EngineStatusValue, SystemPort,
};

use crate::engine_handle::build_engine;
use crate::{
    FFI_COMMAND_REFRESH_BACKEND_STATUS, panda_engine_destroy, panda_engine_dispatch,
    panda_engine_free_string, panda_engine_get_backend_dependency_message,
    panda_engine_get_backend_dependency_name, panda_engine_get_backend_dependency_status,
    panda_engine_get_backend_status, panda_engine_get_backend_version,
};

struct StubSystemPort;

#[async_trait::async_trait]
impl SystemPort for StubSystemPort {
    async fn get_status(&self) -> Result<EngineBackendStatus, EngineError> {
        Ok(EngineBackendStatus {
            healthy: true,
            version: "0.2.0".into(),
            status: EngineStatusValue::from_wire("ready"),
            dependencies: vec![EngineDependencyStatus {
                name: "database".into(),
                status: EngineStatusValue::from_wire("healthy"),
                message: "connected".into(),
            }],
            checked_at_epoch_millis: Some(1_750_000_000_250),
        })
    }
}

#[test]
fn backend_status_crosses_ffi_as_domain_projection() {
    let engine = build_engine(1_000);
    engine
        .engine
        .with_engine(|core| core.set_system_port(Arc::new(StubSystemPort)));
    let engine = Box::into_raw(Box::new(engine));

    let outcome = unsafe {
        panda_engine_dispatch(
            engine,
            FFI_COMMAND_REFRESH_BACKEND_STATUS,
            ptr::null(),
            1_100,
        )
    };

    assert!(outcome.snapshot.has_backend_status);
    assert!(outcome.snapshot.backend_healthy);
    assert_eq!(
        outcome.snapshot.backend_checked_at_epoch_millis,
        1_750_000_000_250
    );
    assert_eq!(outcome.snapshot.backend_dependencies_count, 1);
    assert_eq!(
        unsafe { take_string(panda_engine_get_backend_version(engine)) },
        Some("0.2.0".into())
    );
    assert_eq!(
        unsafe { take_string(panda_engine_get_backend_status(engine)) },
        Some("ready".into())
    );
    assert_eq!(
        unsafe { take_string(panda_engine_get_backend_dependency_name(engine, 0)) },
        Some("database".into())
    );
    assert_eq!(
        unsafe { take_string(panda_engine_get_backend_dependency_status(engine, 0)) },
        Some("healthy".into())
    );
    assert_eq!(
        unsafe { take_string(panda_engine_get_backend_dependency_message(engine, 0)) },
        Some("connected".into())
    );
    assert!(unsafe { panda_engine_get_backend_dependency_name(engine, 1) }.is_null());

    unsafe { panda_engine_destroy(engine) };
}

unsafe fn take_string(value: *const c_char) -> Option<String> {
    if value.is_null() {
        return None;
    }

    let result = unsafe { CStr::from_ptr(value) }
        .to_string_lossy()
        .into_owned();
    unsafe { panda_engine_free_string(value as *mut c_char) };
    Some(result)
}
