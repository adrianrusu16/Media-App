use crate::model::event::EngineEvent;
use crate::model::snapshot::EngineSnapshot;
use std::sync::{Arc, Mutex};

/// Trait for observing engine state changes and events.
pub trait EngineObserver: Send + Sync {
    /// Called when the engine state changes.
    fn on_state_changed(&self, snapshot: &EngineSnapshot);
    /// Called when the engine emits an event.
    fn on_event_emitted(&self, event: &EngineEvent);
}

impl<T: EngineObserver + ?Sized> EngineObserver for Arc<T> {
    fn on_state_changed(&self, snapshot: &EngineSnapshot) {
        (**self).on_state_changed(snapshot);
    }

    fn on_event_emitted(&self, event: &EngineEvent) {
        (**self).on_event_emitted(event);
    }
}

/// A simple bus that allows multiple observers to subscribe to engine changes.
#[derive(Default)]
pub struct EventBus {
    observers: Arc<Mutex<Vec<Box<dyn EngineObserver>>>>,
}

impl EventBus {
    /// Adds a new observer to the bus.
    pub fn subscribe(&self, observer: Box<dyn EngineObserver>) {
        let mut observers = self.observers.lock().unwrap();
        observers.push(observer);
    }

    /// Notifies all observers of a state change.
    pub fn notify_state_changed(&self, snapshot: &EngineSnapshot) {
        let observers = self.observers.lock().unwrap();
        for observer in observers.iter() {
            observer.on_state_changed(snapshot);
        }
    }

    /// Notifies all observers of an emitted event.
    pub fn notify_event_emitted(&self, event: &EngineEvent) {
        let observers = self.observers.lock().unwrap();
        for observer in observers.iter() {
            observer.on_event_emitted(event);
        }
    }
}
