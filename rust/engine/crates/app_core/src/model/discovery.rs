use crate::{EngineError, EnginePageRequest, EnginePagedResult, EngineTrack};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DiscoveryFeed {
    Discovery,
    ForYou,
    Recommendations,
}

impl DiscoveryFeed {
    pub const fn as_wire(self) -> &'static str {
        match self {
            Self::Discovery => "discovery",
            Self::ForYou => "for_you",
            Self::Recommendations => "recommendations",
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EngineDiscoveryIdentity {
    pub account_id: String,
    pub session_id: String,
}

/// Backend-neutral boundary for the authenticated Canopy discovery feed.
#[cfg_attr(test, mockall::automock)]
#[async_trait::async_trait]
pub trait DiscoveryPort: Send + Sync {
    async fn get_feed(
        &self,
        feed: DiscoveryFeed,
        expected_identity: &EngineDiscoveryIdentity,
        excluded_track_ids: &[String],
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<EngineTrack>, EngineError>;
}
