use super::{InMemoryRepository, MediaItem, MediaItemType, MediaRepository};

fn mock_items() -> Vec<MediaItem> {
    vec![
        MediaItem {
            id: "1".to_string(),
            title: "Song A".to_string(),
            artist: "Artist X".to_string(),
            item_type: MediaItemType::Track,
            parent_id: Some("album1".to_string()),
            ..Default::default()
        },
        MediaItem {
            id: "2".to_string(),
            title: "Song B".to_string(),
            artist: "Artist Y".to_string(),
            item_type: MediaItemType::Track,
            parent_id: Some("album1".to_string()),
            ..Default::default()
        },
        MediaItem {
            id: "3".to_string(),
            title: "Different".to_string(),
            artist: "Artist X".to_string(),
            item_type: MediaItemType::Track,
            parent_id: Some("album2".to_string()),
            ..Default::default()
        },
    ]
}

#[test]
fn test_get_by_id() {
    let repo = InMemoryRepository::new(mock_items());
    assert_eq!(repo.get_by_id("1").unwrap().title, "Song A");
    assert!(repo.get_by_id("99").is_none());
}

#[test]
fn test_get_next_prev_wrap() {
    let repo = InMemoryRepository::new(mock_items());
    assert_eq!(repo.get_next("3").unwrap().id, "1");
    assert_eq!(repo.get_previous("1").unwrap().id, "3");
}

#[test]
fn test_get_next_prev_middle() {
    let repo = InMemoryRepository::new(mock_items());
    assert_eq!(repo.get_next("1").unwrap().id, "2");
    assert_eq!(repo.get_previous("3").unwrap().id, "2");
}

#[test]
fn test_get_next_prev_unknown_id() {
    let repo = InMemoryRepository::new(mock_items());
    assert!(repo.get_next("nope").is_none());
    assert!(repo.get_previous("nope").is_none());
}

#[test]
fn test_get_next_prev_single_item_wraps_to_self() {
    let repo = InMemoryRepository::new(vec![MediaItem {
        id: "only".to_string(),
        title: "Solo".to_string(),
        artist: "Artist".to_string(),
        item_type: MediaItemType::Track,
        parent_id: None,
        ..Default::default()
    }]);
    assert_eq!(repo.get_next("only").unwrap().id, "only");
    assert_eq!(repo.get_previous("only").unwrap().id, "only");
}

#[tokio::test]
async fn test_browse_and_search_on_empty_repository() {
    let repo = InMemoryRepository::new(vec![]);
    assert!(repo.browse("album1").await.unwrap().is_empty());
    assert!(repo.search("anything").await.unwrap().is_empty());
    assert!(repo.get_by_id("1").is_none());
    assert!(repo.get_next("1").is_none());
}

#[tokio::test]
async fn test_search_empty_query_matches_all() {
    let repo = InMemoryRepository::new(mock_items());
    let results = repo.search("").await.unwrap();
    assert_eq!(results.len(), 3);
}

#[tokio::test]
async fn test_browse() {
    let repo = InMemoryRepository::new(mock_items());
    let album1_items = repo.browse("album1").await.unwrap();
    assert_eq!(album1_items.len(), 2);
    assert!(
        album1_items
            .iter()
            .all(|i| i.parent_id.as_deref() == Some("album1"))
    );

    assert!(repo.browse("nonexistent").await.unwrap().is_empty());
}

#[tokio::test]
async fn test_search() {
    let repo = InMemoryRepository::new(mock_items());

    let results = repo.search("Song").await.unwrap();
    assert_eq!(results.len(), 2);

    let results = repo.search("Artist X").await.unwrap();
    assert_eq!(results.len(), 2);
    assert!(results.iter().any(|i| i.id == "1"));
    assert!(results.iter().any(|i| i.id == "3"));

    let results = repo.search("song").await.unwrap();
    assert_eq!(results.len(), 2);

    assert!(repo.search("xyz").await.unwrap().is_empty());
}

#[test]
fn test_with_media_snapshot() {
    use crate::model::snapshot::EngineSnapshot;

    let snapshot = EngineSnapshot::default();
    let item = MediaItem {
        id: "1".to_string(),
        title: "T".to_string(),
        artist: "A".to_string(),
        album: Some("ALB".to_string()),
        duration_millis: Some(180_000),
        thumbnail_url: Some("https://example.com/art.jpg".to_string()),
        ..Default::default()
    };
    let updated = snapshot.with_media(item);
    assert_eq!(updated.metadata_revision, 1);
    assert_eq!(updated.media_id, Some("1".to_string()));
    assert_eq!(updated.title, Some("T".to_string()));
    assert_eq!(updated.artist, Some("A".to_string()));
    assert_eq!(updated.album, Some("ALB".to_string()));
    assert_eq!(updated.duration_millis, Some(180_000));
    assert_eq!(
        updated.thumbnail_url,
        Some("https://example.com/art.jpg".to_string())
    );
}
