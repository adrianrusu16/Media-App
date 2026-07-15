use std::sync::Arc;

use crate::data::repository::{MediaItem, MediaRepository};
use crate::networking::backend_client::CatalogPort;
use crate::{EngineError, EnginePageRequest, EnginePagedResult, EngineTrack};

/// Projects backend-neutral catalog tracks into the legacy playback repository view.
pub struct RemoteRepository<C> {
    client: Arc<C>,
}

impl<C> RemoteRepository<C>
where
    C: CatalogPort,
{
    pub fn new(client: Arc<C>) -> Self {
        Self { client }
    }

    fn project_page(page: EnginePagedResult<EngineTrack>) -> EnginePagedResult<MediaItem> {
        EnginePagedResult {
            items: page.items.into_iter().map(project_track).collect(),
            next_page_token: page.next_page_token,
        }
    }
}

#[async_trait::async_trait]
impl<C> MediaRepository for RemoteRepository<C>
where
    C: CatalogPort,
{
    fn get_by_id(&self, _id: &str) -> Option<MediaItem> {
        None
    }

    fn get_next(&self, _current_id: &str) -> Option<MediaItem> {
        None
    }

    fn get_previous(&self, _current_id: &str) -> Option<MediaItem> {
        None
    }

    async fn browse(&self, parent_id: &str) -> anyhow::Result<Vec<MediaItem>> {
        Ok(self
            .browse_catalog(Some(parent_id), &[], EnginePageRequest::default())
            .await?
            .items)
    }

    async fn search(&self, query: &str) -> anyhow::Result<Vec<MediaItem>> {
        Ok(self
            .search_catalog(query, EnginePageRequest::default())
            .await?
            .items)
    }

    async fn browse_catalog<'a>(
        &'a self,
        parent_id: Option<&'a str>,
        genres: &[String],
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<MediaItem>, EngineError> {
        self.client
            .browse(parent_id, genres, page)
            .await
            .map(Self::project_page)
    }

    async fn search_catalog(
        &self,
        query: &str,
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<MediaItem>, EngineError> {
        self.client
            .search(query, page)
            .await
            .map(Self::project_page)
    }
}

fn project_track(track: EngineTrack) -> MediaItem {
    MediaItem {
        id: track.id,
        title: track.title,
        artist: track.artist.name,
        album: track.album.map(|album| album.title),
        duration_millis: Some(track.duration_millis),
        thumbnail_url: track.artwork_id,
        ..Default::default()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{EngineArtist, EnginePageToken};

    struct FakeCatalog;

    #[async_trait::async_trait]
    impl CatalogPort for FakeCatalog {
        async fn browse(
            &self,
            _parent_id: Option<&str>,
            _genres: &[String],
            _page: EnginePageRequest,
        ) -> Result<EnginePagedResult<EngineTrack>, EngineError> {
            Ok(page("browse-track"))
        }

        async fn search(
            &self,
            _query: &str,
            _page: EnginePageRequest,
        ) -> Result<EnginePagedResult<EngineTrack>, EngineError> {
            Ok(page("search-track"))
        }

        async fn get_media(&self, _track_id: &str) -> Result<EngineTrack, EngineError> {
            Ok(track("media-track"))
        }
    }

    fn track(id: &str) -> EngineTrack {
        EngineTrack {
            id: id.into(),
            title: "A Song".into(),
            artist: EngineArtist {
                id: "artist-1".into(),
                name: "An Artist".into(),
            },
            album: None,
            duration_millis: 42,
            explicit: false,
            artwork_id: Some("art-1".into()),
            genres: vec![],
        }
    }

    fn page(id: &str) -> EnginePagedResult<EngineTrack> {
        EnginePagedResult {
            items: vec![track(id)],
            next_page_token: Some(EnginePageToken::new("next+/=".into()).unwrap()),
        }
    }

    #[tokio::test]
    async fn search_projects_domain_tracks_and_preserves_page_token() {
        let repository = RemoteRepository::new(Arc::new(FakeCatalog));

        let result = repository
            .search_catalog("song", EnginePageRequest::default())
            .await
            .unwrap();

        assert_eq!(result.items[0].id, "search-track");
        assert_eq!(result.items[0].artist, "An Artist");
        assert_eq!(result.next_page_token.unwrap().as_str(), "next+/=");
    }
}
