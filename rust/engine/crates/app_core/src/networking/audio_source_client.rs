/// Transport-agnostic source descriptor used by playback-facing layers.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PlaybackSource {
    pub source_id: String,
    pub uri: String,
    pub mime_type: Option<String>,
    pub expected_duration_ms: Option<u64>,
}

/// Represents an incrementally downloaded or streamed audio chunk.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AudioChunk {
    pub source_id: String,
    pub index: u64,
    pub bytes: Vec<u8>,
    pub is_last: bool,
}

/// Abstract, transport-agnostic client for resolving and downloading playable
/// audio sources.
///
/// This seam intentionally does not return `MediaItem`: source acquisition and
/// playback transfer are separate from engine-domain projection/state updates.
#[cfg_attr(test, mockall::automock)]
#[async_trait::async_trait]
pub trait AudioSourceClient: Send + Sync {
    /// Resolves a track identifier into a playable source reference.
    async fn resolve_track(&self, track_id: &str) -> anyhow::Result<PlaybackSource>;

    /// Prefetches/downloads the full source and returns a local URI/path.
    async fn prefetch_full(&self, source_id: &str) -> anyhow::Result<String>;

    /// Fetches the next chunk for a source starting from `from_chunk_index`.
    async fn fetch_chunk(
        &self,
        source_id: &str,
        from_chunk_index: u64,
    ) -> anyhow::Result<AudioChunk>;
}
