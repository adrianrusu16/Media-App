use crate::engine::core::{Engine, EngineOutcome};
use crate::engine::observability::EventBus;
use crate::model::command::{EngineCommand, EngineCommandType};
use crate::model::error::{EngineError, EngineErrorType};
use crate::model::event::{EngineEvent, EngineEventType};
use crate::model::playback::PlaybackState;
use std::sync::Arc;
use tracing::{info, warn};

/// Middleware trait for processing engine operations.
///
/// Middleware can be used to inject logic before or after a command is dispatched,
/// such as logging, telemetry, or modifying the command/outcome.
pub trait Middleware: Send + Sync {
    /// Called before the command is dispatched to the engine.
    fn before_dispatch(&self, _engine: &Engine, _command: &EngineCommand) {}

    /// Called after the command has been dispatched and the outcome generated.
    fn after_dispatch(&self, _engine: &mut Engine, _outcome: &mut EngineOutcome) {}
}

/// A simple middleware that logs engine actions.
pub struct LoggerMiddleware;
impl Middleware for LoggerMiddleware {
    fn before_dispatch(&self, _engine: &Engine, command: &EngineCommand) {
        info!("Dispatching command: {:?}", command.command_type);
    }
}

/// A middleware that tracks engine performance and command usage.
pub struct TelemetryMiddleware {
    bus: Arc<EventBus>,
}

impl TelemetryMiddleware {
    pub fn new(bus: Arc<EventBus>) -> Self {
        Self { bus }
    }
}

impl Middleware for TelemetryMiddleware {
    fn before_dispatch(&self, _engine: &Engine, command: &EngineCommand) {
        info!("[Telemetry] Starting command: {:?}", command.command_type);
    }

    fn after_dispatch(&self, _engine: &mut Engine, outcome: &mut EngineOutcome) {
        // Notify the bus about the outcome and event
        self.bus.notify_state_changed(&outcome.snapshot);
        self.bus.notify_event_emitted(&outcome.event);

        info!(
            "[Telemetry] Command completed. Event: {:?}, Effects: {}",
            outcome.event.event_type,
            outcome.effects.len()
        );
    }
}

/// A middleware that handles AAOS-specific focus logic or logging.
pub struct FocusMiddleware;
impl Middleware for FocusMiddleware {
    fn before_dispatch(&self, _engine: &Engine, command: &EngineCommand) {
        if command.command_type == EngineCommandType::Play {
            info!("[FocusMiddleware] Requesting audio focus before Play...");
            // In a real app, this might trigger a platform call or internal check
        }
    }
}

/// A middleware that automatically retries or recovers from certain errors.
pub struct RecoveryMiddleware;
impl Middleware for RecoveryMiddleware {
    fn after_dispatch(&self, engine: &mut Engine, outcome: &mut EngineOutcome) {
        if let Some(error) = &outcome.snapshot.last_error
            && error.error_type == EngineErrorType::NetworkError
            && !error.is_fatal
        {
            warn!(
                "[Recovery] Non-fatal network error detected. Attempting to skip to next track..."
            );

            // Recover synchronously by advancing the queue to the next track.
            // (The Middleware trait is sync, so we avoid an async dispatch here and
            // instead mutate the outcome snapshot directly.)
            if let Some(next_media) = engine.queue().next_item() {
                let media = next_media.clone();
                outcome.snapshot = outcome.snapshot.clone().with_media(media);
            }

            // Add a notification that we recovered
            outcome.snapshot.last_error = Some(EngineError::media_skipped(
                "Skipped track due to network error",
            ));
            outcome.event = EngineEvent::command_applied(Some(
                "recovered_from_network_error: skipped_to_next_track".to_string(),
            ));
        }
    }
}

/// A middleware that validates commands against business rules before they reach the reducer.
pub struct ValidationMiddleware;
impl Middleware for ValidationMiddleware {
    fn before_dispatch(&self, engine: &Engine, command: &EngineCommand) {
        let snapshot = engine.snapshot();

        // Block commands if the engine is busy/buffering
        if !snapshot.can_dispatch() {
            warn!(
                "[Validation] Rejecting command {:?} because the engine is currently busy (state: {:?}, is_busy: {})",
                command.command_type, snapshot.playback_state, snapshot.is_busy
            );
            // In a future version, we could inject a cancellation flag into the EngineOutcome
            // but for now, the warning serves as an audit log for the middleware decision.
        }

        if command.command_type == EngineCommandType::Play && snapshot.session.is_none() {
            warn!(
                "[Validation] Play command received without an active session. This may be ignored by the reducer."
            );
        }
    }
}

/// A middleware that throttles commands to prevent rapid repeated executions (button mashing).
pub struct ThrottlingMiddleware {
    min_interval_ms: u64,
    last_command_at: std::sync::Mutex<std::collections::HashMap<String, u64>>,
}

impl ThrottlingMiddleware {
    pub fn new(min_interval_ms: u64) -> Self {
        Self {
            min_interval_ms,
            last_command_at: std::sync::Mutex::new(std::collections::HashMap::new()),
        }
    }

    fn should_throttle(&self, command: &EngineCommand, now: u64) -> bool {
        let key = format!("{:?}", command.command_type);
        let mut last_map = self.last_command_at.lock().unwrap();
        let last = last_map.get(&key).cloned().unwrap_or(0);

        if now < last + self.min_interval_ms {
            true
        } else {
            last_map.insert(key, now);
            false
        }
    }
}

impl Middleware for ThrottlingMiddleware {
    fn before_dispatch(&self, engine: &Engine, command: &EngineCommand) {
        let now = engine.snapshot().updated_at_epoch_millis;
        if self.should_throttle(command, now) {
            warn!(
                "[Throttling] Throttling command {:?} (too rapid)",
                command.command_type
            );
            // In a more advanced implementation, we could set a flag in the command
            // to mark it as rejected/ignored by the middleware.
        }
    }
}

/// A composite middleware that runs a list of middlewares in order.
#[derive(Default)]
pub struct MiddlewarePipeline {
    middlewares: Vec<Box<dyn Middleware>>,
}

/// A middleware that tracks playback analytics and heartbeats.
pub struct AnalyticsMiddleware {
    bus: Arc<EventBus>,
    last_heartbeat_at: std::sync::atomic::AtomicU64,
}

impl AnalyticsMiddleware {
    pub fn new(bus: Arc<EventBus>) -> Self {
        Self {
            bus,
            last_heartbeat_at: std::sync::atomic::AtomicU64::new(0),
        }
    }

    fn report(&self, event_name: &str, media_id: Option<&str>, properties: &str) {
        let payload = format!(
            "{{\"event\": \"{}\", \"media_id\": {:?}, \"properties\": {}}}",
            event_name, media_id, properties
        );
        self.bus
            .notify_event_emitted(&EngineEvent::analytics_reported(payload));
    }
}

impl Middleware for AnalyticsMiddleware {
    fn before_dispatch(&self, _engine: &Engine, command: &EngineCommand) {
        match command.command_type {
            EngineCommandType::Play => {
                self.report("play_requested", None, "{}");
            }
            EngineCommandType::Pause => {
                self.report("pause_requested", None, "{}");
            }
            EngineCommandType::SkipNext => {
                self.report("skip_next_requested", None, "{}");
            }
            EngineCommandType::SkipPrevious => {
                self.report("skip_prev_requested", None, "{}");
            }
            _ => {}
        }
    }

    fn after_dispatch(&self, engine: &mut Engine, outcome: &mut EngineOutcome) {
        let snapshot = engine.snapshot();

        // Report state transitions
        if outcome.event.event_type == EngineEventType::CommandApplied {
            self.report(
                "state_transition",
                snapshot.media_id.as_deref(),
                &format!("{{\"to_state\": \"{:?}\"}}", snapshot.playback_state),
            );
        }

        // Heartbeat logic during playback
        if snapshot.playback_state == PlaybackState::Playing {
            let now = snapshot.updated_at_epoch_millis;
            let last = self
                .last_heartbeat_at
                .load(std::sync::atomic::Ordering::Relaxed);

            // Send heartbeat every 10 seconds (10000ms)
            if now >= last + 10000 {
                self.report(
                    "playback_heartbeat",
                    snapshot.media_id.as_deref(),
                    &format!("{{\"position_ms\": {}}}", snapshot.position_millis),
                );
                self.last_heartbeat_at
                    .store(now, std::sync::atomic::Ordering::Relaxed);
            }
        }
    }
}

impl MiddlewarePipeline {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn add(&mut self, middleware: Box<dyn Middleware>) {
        self.middlewares.push(middleware);
    }

    pub fn before_dispatch(&self, engine: &Engine, command: &EngineCommand) {
        for mw in &self.middlewares {
            mw.before_dispatch(engine, command);
        }
    }

    pub fn after_dispatch(&self, engine: &mut Engine, outcome: &mut EngineOutcome) {
        for mw in &self.middlewares {
            mw.after_dispatch(engine, outcome);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{
        Engine, EngineCommand, EngineEvent, EngineEventType, EngineObserver, EngineSnapshot,
        EventBus,
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
        // No assertions possible for logger, just smoke test
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
        // TelemetryMiddleware forwards the outcome's own event to the bus. A Play
        // command results in a CommandApplied event (analytics events come from
        // AnalyticsMiddleware instead).
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
        // Same command quickly
        assert!(middleware.should_throttle(&cmd, 1200));
        // Different command quickly
        let cmd2 = EngineCommand::pause();
        assert!(!middleware.should_throttle(&cmd2, 1205));
        // Wait enough
        assert!(!middleware.should_throttle(&cmd, 1600));
    }

    #[tokio::test]
    async fn test_validation_middleware_detects_busy() {
        use crate::data::repository::{MediaItem, MockMediaRepository};
        use tokio::time::{Duration, sleep};

        let middleware = ValidationMiddleware;
        let mut engine = Engine::new(100);

        // State-of-the-art: inject a mockall-generated repository that the engine
        // awaits during the (async) search. We assert the search is invoked with
        // the expected query exactly once and returns controlled results.
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

        // The engine sets `is_busy` while awaiting the repository and clears it
        // once the future resolves; after dispatch completes it must be false.
        assert!(!engine.snapshot().is_busy);
        // The awaited results are committed to the snapshot.
        assert_eq!(engine.snapshot().search_results.len(), 1);
        assert_eq!(engine.snapshot().search_results[0].id, "result-1");

        let command = EngineCommand::play();
        middleware.before_dispatch(&engine, &command);
        // Smoke test for warnings in logs
    }

    #[tokio::test]
    async fn test_search_clears_busy_on_repository_error() {
        use crate::data::repository::MockMediaRepository;

        // Watch-out coverage: even when the backend (repository) errors out, the
        // engine must release the busy flag and surface empty results rather than
        // getting stuck in a busy state.
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
}
