use super::*;
use crate::{
    Engine, EngineCommand, EngineEvent, EngineEventType, EngineObserver, EngineSnapshot, EventBus,
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
    middleware.before_dispatch(&engine, &command);
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

    middleware.before_dispatch(&engine, &command);
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
    use crate::data::repository::{MediaItem, MockMediaRepository};
    use tokio::time::{sleep, Duration};

    let middleware = ValidationMiddleware;
    let mut engine = Engine::new(100);

    let mut repo = MockMediaRepository::new();
    repo.expect_search()
        .withf(|query| query == "query")
        .times(1)
        .returning(|_| {
            std::thread::sleep(std::time::Duration::from_millis(25));
            Ok(vec![MediaItem {
                id: "result-1".to_string(),
                title: "Result One".to_string(),
                ..Default::default()
            }])
        });
    engine.set_repository(Box::new(repo));

    sleep(Duration::from_millis(1)).await;

    engine
        .dispatch(EngineCommand::search("query".to_string()), 150)
        .await;

    assert!(!engine.snapshot().is_busy);
    assert_eq!(engine.snapshot().search_results.len(), 1);
    assert_eq!(engine.snapshot().search_results[0].id, "result-1");

    let command = EngineCommand::play();
    middleware.before_dispatch(&engine, &command);
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
        fn before_dispatch(&self, _engine: &Engine, _command: &EngineCommand) {
            self.0.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
        }
    }

    let counter = Arc::new(std::sync::atomic::AtomicU32::new(0));
    let mut pipeline = MiddlewarePipeline::new();
    pipeline.add(Box::new(MockMiddleware(counter.clone())));

    let engine = Engine::new(100);
    let command = EngineCommand::play();
    pipeline.before_dispatch(&engine, &command);

    assert_eq!(counter.load(std::sync::atomic::Ordering::SeqCst), 1);
}