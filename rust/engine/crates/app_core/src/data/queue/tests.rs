use super::{QueueManager, RepeatMode};
use crate::data::repository::MediaItem;

fn mock_items() -> Vec<MediaItem> {
    vec![
        MediaItem {
            id: "1".to_string(),
            title: "S1".to_string(),
            artist: "A1".to_string(),
            ..Default::default()
        },
        MediaItem {
            id: "2".to_string(),
            title: "S2".to_string(),
            artist: "A2".to_string(),
            ..Default::default()
        },
    ]
}

#[test]
fn test_next_item_wraps_in_repeat_all() {
    let mut qm = QueueManager::new(mock_items());
    qm.set_repeat_mode(RepeatMode::All);
    qm.set_current_index(1);

    let next = qm.next_item().unwrap();
    assert_eq!(next.id, "1");
}

#[test]
fn test_previous_item_wraps_in_repeat_all() {
    let mut qm = QueueManager::new(mock_items());
    qm.set_repeat_mode(RepeatMode::All);
    qm.set_current_index(0);

    let prev = qm.previous_item().unwrap();
    assert_eq!(prev.id, "2");
}

#[test]
fn test_next_item_repeat_one_stays_on_same() {
    let mut qm = QueueManager::new(mock_items());
    qm.set_repeat_mode(RepeatMode::One);
    qm.set_current_index(0);

    let next = qm.next_item().unwrap();
    assert_eq!(next.id, "1");
    let next2 = qm.next_item().unwrap();
    assert_eq!(next2.id, "1");
}

#[test]
fn test_has_next_with_repeat_modes() {
    let mut qm = QueueManager::new(mock_items());
    qm.set_current_index(1);

    assert!(!qm.has_next());

    qm.set_repeat_mode(RepeatMode::All);
    assert!(qm.has_next());

    qm.set_repeat_mode(RepeatMode::One);
    assert!(qm.has_next());
}

#[test]
fn test_empty_queue_handling() {
    let mut qm = QueueManager::new(vec![]);
    assert!(qm.current_item().is_none());
    assert!(qm.next_item().is_none());
    assert!(qm.previous_item().is_none());
    assert!(!qm.has_next());
    assert!(!qm.has_previous());
}

#[test]
fn test_next_item_repeat_none_is_unavailable_on_last_without_mutating_cursor() {
    let mut qm = QueueManager::new(mock_items());
    qm.set_current_index(1);

    assert!(qm.next_item().is_none());
    assert_eq!(qm.current_index(), Some(1));
}

#[test]
fn test_previous_item_repeat_none_is_unavailable_on_first_without_mutating_cursor() {
    let mut qm = QueueManager::new(mock_items());
    qm.set_current_index(0);

    assert!(qm.previous_item().is_none());
    assert_eq!(qm.current_index(), Some(0));
}

#[test]
fn test_has_previous_with_repeat_modes() {
    let mut qm = QueueManager::new(mock_items());
    qm.set_current_index(0);

    assert!(!qm.has_previous());

    qm.set_repeat_mode(RepeatMode::All);
    assert!(qm.has_previous());

    qm.set_repeat_mode(RepeatMode::One);
    assert!(qm.has_previous());
}

#[test]
fn test_set_items_resets_current_index() {
    let mut qm = QueueManager::new(vec![]);
    assert_eq!(qm.current_index(), None);
    assert_eq!(qm.len(), 0);
    assert_eq!(qm.generation(), 0);

    qm.set_items(mock_items());
    assert_eq!(qm.current_index(), Some(0));
    assert_eq!(qm.len(), 2);
    assert_eq!(qm.generation(), 1);

    qm.set_items(vec![]);
    assert_eq!(qm.current_index(), None);
    assert_eq!(qm.generation(), 2);
}

#[test]
fn test_set_current_index_ignores_out_of_bounds() {
    let mut qm = QueueManager::new(mock_items());
    qm.set_current_index(0);
    qm.set_current_index(99);
    assert_eq!(qm.current_index(), Some(0));
}
