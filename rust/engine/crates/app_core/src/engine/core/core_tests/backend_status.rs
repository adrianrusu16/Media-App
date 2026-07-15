use std::sync::Arc;

use async_trait::async_trait;

use crate::{
    Engine, EngineBackendStatus, EngineCommand, EngineError, EngineErrorType, EngineStatusValue,
    SystemPort,
};

struct StubSystemPort {
    result: Result<EngineBackendStatus, EngineError>,
}

#[async_trait]
impl SystemPort for StubSystemPort {
    async fn get_status(&self) -> Result<EngineBackendStatus, EngineError> {
        self.result.clone()
    }
}

fn status_fixture() -> EngineBackendStatus {
    EngineBackendStatus {
        healthy: true,
        version: "0.2.0".into(),
        status: EngineStatusValue::from_wire("ready"),
        dependencies: vec![],
        checked_at_epoch_millis: Some(1_750_000_000_250),
    }
}

#[tokio::test]
async fn refresh_backend_status_stores_domain_projection() {
    let mut engine = Engine::new(10);
    engine.set_system_port(Arc::new(StubSystemPort {
        result: Ok(status_fixture()),
    }));

    let outcome = engine
        .dispatch(EngineCommand::refresh_backend_status(), 20)
        .await;

    let status = outcome.snapshot.backend_status.unwrap();
    assert!(status.healthy);
    assert_eq!(status.status.as_wire(), "ready");
    assert!(outcome.snapshot.last_error.is_none());
}

#[tokio::test]
async fn refresh_without_configured_backend_is_typed_unavailable() {
    let mut engine = Engine::new(10);

    let outcome = engine
        .dispatch(EngineCommand::refresh_backend_status(), 20)
        .await;

    assert!(outcome.snapshot.backend_status.is_none());
    assert_eq!(
        outcome.snapshot.last_error.unwrap().error_type,
        EngineErrorType::ServiceUnavailable
    );
}
