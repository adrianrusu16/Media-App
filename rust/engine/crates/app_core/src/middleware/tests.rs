use super::*;
use crate::{
    Engine, EngineCommand, EngineErrorType, EngineEvent, EngineEventType, EngineObserver,
    EngineSnapshot, EventBus, data::repository::MediaItem,
};
use std::sync::{Arc, Mutex};

struct TestObserver {
    events: Arc<Mutex<Vec<EngineEvent>>>,
}
impl EngineObserver for TestObserver {
    fn on_state_changed(&self, _snapshot: &EngineSnapshot) {}
    fn on_event_emitted(&self, event: &EngineEvent) {
        self.events.lock().unwrap().push(event.clone());
    }
}

#[test]
fn test_logger_middleware_does_not_crash() {
    let middleware = LoggerMiddleware;
    let engine = Engine::new(100);
    let command = EngineCommand::play();
    assert!(middleware.before_dispatch(&engine, &command).is_ok());
}

#[tokio::test]
async fn test_telemetry_middleware_emits_event() {
    let bus = Arc::new(EventBus::default());
    let events = Arc::new(Mutex::new(Vec::new()));
    bus.subscribe(Box::new(TestObserver {
        events: events.clone(),
    }));

    let middleware = TelemetryMiddleware::new(bus.clone());

    let mut engine = Engine::new(100);
    let command = EngineCommand::play();

    assert!(middleware.before_dispatch(&engine, &command).is_ok());
    let mut outcome = Engine::dispatch(&mut engine, EngineCommand::play(), 200).await;
    middleware.after_dispatch(&mut engine, &mut outcome);

    let captured = events.lock().unwrap();
    assert!(
        captured
            .iter()
            .any(|e| matches!(e.event_type, EngineEventType::CommandApplied))
    );
}

#[test]
fn test_throttling_middleware() {
    let middleware = ThrottlingMiddleware::new(500);
    let cmd = EngineCommand::play();

    assert!(!middleware.should_throttle(&cmd, 1000));
    assert!(middleware.should_throttle(&cmd, 1200));
    let cmd2 = EngineCommand::pause();
    assert!(!middleware.should_throttle(&cmd2, 1205));
    assert!(!middleware.should_throttle(&cmd, 1600));
}

#[tokio::test]
async fn test_validation_middleware_detects_busy() {
    let middleware = ValidationMiddleware;
    let mut engine = Engine::new(100);
    let _ = engine
        .dispatch(EngineCommand::start_session("user".to_string()), 120)
        .await;
    engine.queue().set_items(vec![MediaItem {
        id: "id-1".to_string(),
        title: "t".to_string(),
        ..Default::default()
    }]);
    let _ = engine.dispatch(EngineCommand::play(), 150).await;
    let command = EngineCommand::play();
    let result = middleware.before_dispatch(&engine, &command);
    assert!(result.is_err());
}

#[tokio::test]
async fn test_search_clears_busy_on_repository_error() {
    use crate::data::repository::MockMediaRepository;

    let mut engine = Engine::new(100);

    let mut repo = MockMediaRepository::new();
    repo.expect_search()
        .times(1)
        .returning(|_| Err(anyhow::anyhow!("backend unavailable")));
    engine.set_repository(Box::new(repo));

    engine
        .dispatch(EngineCommand::search("query".to_string()), 150)
        .await;

    assert!(!engine.snapshot().is_busy);
    assert!(engine.snapshot().search_results.is_empty());
}

#[tokio::test]
async fn test_analytics_middleware() {
    let bus = Arc::new(EventBus::default());
    let events = Arc::new(Mutex::new(Vec::new()));
    bus.subscribe(Box::new(TestObserver {
        events: events.clone(),
    }));
    let middleware = AnalyticsMiddleware::new(bus);

    let mut engine = Engine::new(100);
    let mut outcome = Engine::dispatch(&mut engine, EngineCommand::play(), 200).await;

    middleware.after_dispatch(&mut engine, &mut outcome);

    let captured = events.lock().unwrap();
    assert!(
        captured
            .iter()
            .any(|e| matches!(e.event_type, EngineEventType::AnalyticsReported))
    );
}

#[test]
fn test_middleware_pipeline_execution() {
    struct MockMiddleware(Arc<std::sync::atomic::AtomicU32>);
    impl Middleware for MockMiddleware {
        fn before_dispatch(
            &self,
            _engine: &Engine,
            _command: &EngineCommand,
        ) -> Result<(), crate::EngineError> {
            self.0.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
            Ok(())
        }
    }

    let counter = Arc::new(std::sync::atomic::AtomicU32::new(0));
    let mut pipeline = MiddlewarePipeline::new();
    pipeline.add(Box::new(MockMiddleware(counter.clone())));

    let engine = Engine::new(100);
    let command = EngineCommand::play();
    assert!(pipeline.before_dispatch(&engine, &command).is_ok());

    assert_eq!(counter.load(std::sync::atomic::Ordering::SeqCst), 1);
}

#[tokio::test]
async fn test_engine_rejects_command_when_validation_fails() {
    let mut pipeline = MiddlewarePipeline::new();
    pipeline.add(Box::new(ValidationMiddleware));

    let mut engine = Engine::new(100);
    let _ = engine
        .dispatch(EngineCommand::start_session("user".to_string()), 120)
        .await;
    engine.queue().set_items(vec![MediaItem {
        id: "id-1".to_string(),
        title: "t".to_string(),
        ..Default::default()
    }]);
    let _ = engine.dispatch(EngineCommand::play(), 150).await;
    engine.set_middleware(pipeline);

    let outcome = engine.dispatch(EngineCommand::play(), 200).await;

    assert!(outcome.effects.is_empty());
    let err = outcome
        .snapshot
        .last_error
        .as_ref()
        .expect("expected middleware rejection error");
    assert_eq!(err.error_type, EngineErrorType::Unknown);
    assert!(err.message.contains("Rejecting command"));
}

#[tokio::test]
async fn test_engine_rejects_command_when_throttled() {
    let mut pipeline = MiddlewarePipeline::new();
    pipeline.add(Box::new(ThrottlingMiddleware::new(500)));

    let mut engine = Engine::new(100);
    engine.set_middleware(pipeline);

    let rejected_outcome = engine.dispatch(EngineCommand::play(), 700).await;
    let err = rejected_outcome
        .snapshot
        .last_error
        .as_ref()
        .expect("expected throttling rejection error");
    assert_eq!(err.error_type, EngineErrorType::Unknown);
    assert!(err.message.contains("Throttling"));
    assert!(rejected_outcome.effects.is_empty());
}
