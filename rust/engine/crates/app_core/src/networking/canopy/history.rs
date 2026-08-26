use std::sync::Arc;

use tonic_014::Request;
use url::Url;

use crate::{
    EngineError, EngineErrorType, EngineHistoryEntry, EngineHistoryIdentity, EngineHistorySettings,
    EngineHistorySettingsUpdate, EnginePageRequest, EnginePageToken, EnginePagedResult,
    EnginePlaybackRecord, HistoryPort, normalize_completion_ratio,
};

use super::catalog::{map_page_request, map_track_summary};
use super::operation::CanopyOperation;
use super::request::execute_with_bound_auth;
use super::sdk::clients::history_service_client::HistoryServiceClient;
use super::sdk::resources::{
    ClearHistoryRequest, DeleteHistoryEntryRequest, GetHistorySettingsRequest, HistoryEntry,
    HistorySettings, ListHistoryRequest, ListHistoryResponse, PageInfo, RecordPlaybackRequest,
    UpdateHistorySettingsRequest, UpdateHistorySettingsResponse,
};
use super::{CanopyChannel, SessionCoordinator};

#[derive(Clone)]
pub struct CanopyHistoryClient {
    client: HistoryServiceClient<super::sdk::runtime::transport::Channel>,
    session: Arc<SessionCoordinator>,
    media_origin: Url,
}

impl CanopyHistoryClient {
    pub fn new(
        channel: &CanopyChannel,
        session: Arc<SessionCoordinator>,
        media_origin: Url,
    ) -> Self {
        Self {
            client: HistoryServiceClient::new(channel.clone_inner()),
            session,
            media_origin,
        }
    }
}

#[async_trait::async_trait]
impl HistoryPort for CanopyHistoryClient {
    async fn get_settings(
        &self,
        identity: &EngineHistoryIdentity,
    ) -> Result<EngineHistorySettings, EngineError> {
        let client = self.client.clone();
        let response = execute_history_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::GetHistorySettings,
            || Request::new(GetHistorySettingsRequest {}),
            move |request| {
                let mut client = client.clone();
                async move { client.get_history_settings(request).await }
            },
        )
        .await?
        .into_inner();
        Ok(map_settings(response))
    }

    async fn update_settings(
        &self,
        identity: &EngineHistoryIdentity,
        enabled: bool,
    ) -> Result<EngineHistorySettingsUpdate, EngineError> {
        let request = UpdateHistorySettingsRequest { enabled };
        let client = self.client.clone();
        let response = execute_history_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::UpdateHistorySettings,
            || Request::new(request),
            move |request| {
                let mut client = client.clone();
                async move { client.update_history_settings(request).await }
            },
        )
        .await?
        .into_inner();
        map_settings_update(response)
    }

    async fn record(
        &self,
        identity: &EngineHistoryIdentity,
        event: EnginePlaybackRecord,
    ) -> Result<bool, EngineError> {
        let request = map_record_request(event);
        let client = self.client.clone();
        let response = execute_history_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::RecordPlayback,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.record_playback(request).await }
            },
        )
        .await?
        .into_inner();
        Ok(response.recorded)
    }

    async fn list(
        &self,
        identity: &EngineHistoryIdentity,
        page: EnginePageRequest,
    ) -> Result<EnginePagedResult<EngineHistoryEntry>, EngineError> {
        let request = ListHistoryRequest {
            page: Some(map_page_request(page)),
        };
        let client = self.client.clone();
        let response = execute_history_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::ListHistory,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.list_history(request).await }
            },
        )
        .await?
        .into_inner();
        map_history_response(response, Some(&self.media_origin))
    }

    async fn delete_entry(
        &self,
        identity: &EngineHistoryIdentity,
        id: &str,
    ) -> Result<(), EngineError> {
        if id.trim().is_empty() {
            return Err(EngineError::new(
                EngineErrorType::InvalidInput,
                "history id is required",
                false,
            ));
        }
        let request = DeleteHistoryEntryRequest {
            history_id: id.to_owned(),
        };
        let client = self.client.clone();
        execute_history_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::DeleteHistoryEntry,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.delete_history_entry(request).await }
            },
        )
        .await?;
        Ok(())
    }

    async fn clear(&self, identity: &EngineHistoryIdentity) -> Result<u64, EngineError> {
        let client = self.client.clone();
        let response = execute_history_request(
            self.session.as_ref(),
            identity,
            CanopyOperation::ClearHistory,
            || Request::new(ClearHistoryRequest {}),
            move |request| {
                let mut client = client.clone();
                async move { client.clear_history(request).await }
            },
        )
        .await?
        .into_inner();
        Ok(response.deleted_count)
    }
}

async fn execute_history_request<TRequest, TResponse, MakeRequest, Execute, ExecuteFuture>(
    session: &SessionCoordinator,
    identity: &EngineHistoryIdentity,
    operation: CanopyOperation,
    make_request: MakeRequest,
    execute: Execute,
) -> Result<tonic_014::Response<TResponse>, EngineError>
where
    MakeRequest: Fn() -> Request<TRequest>,
    Execute: Fn(Request<TRequest>) -> ExecuteFuture,
    ExecuteFuture:
        std::future::Future<Output = Result<tonic_014::Response<TResponse>, tonic_014::Status>>,
{
    execute_with_bound_auth(session, identity, operation, make_request, execute).await
}

fn map_settings(settings: HistorySettings) -> EngineHistorySettings {
    EngineHistorySettings {
        enabled: settings.enabled,
    }
}

fn map_settings_update(
    response: UpdateHistorySettingsResponse,
) -> Result<EngineHistorySettingsUpdate, EngineError> {
    let settings = response
        .settings
        .ok_or_else(|| mapping_defect("history settings update missing settings"))?;
    Ok(EngineHistorySettingsUpdate {
        settings: map_settings(settings),
        deleted_count: response.deleted_count,
    })
}

fn map_record_request(event: EnginePlaybackRecord) -> RecordPlaybackRequest {
    RecordPlaybackRequest {
        track_id: event.track_id,
        duration_ms: event.duration_millis,
        completion_ratio: event.completion_ratio,
    }
}

fn map_history_response(
    response: ListHistoryResponse,
    media_origin: Option<&Url>,
) -> Result<EnginePagedResult<EngineHistoryEntry>, EngineError> {
    map_history_page(response.entries, response.page_info, media_origin)
}

fn map_history_page(
    entries: Vec<HistoryEntry>,
    page_info: Option<PageInfo>,
    media_origin: Option<&Url>,
) -> Result<EnginePagedResult<EngineHistoryEntry>, EngineError> {
    Ok(EnginePagedResult {
        items: entries
            .into_iter()
            .map(|entry| map_history_entry(entry, media_origin))
            .collect::<Result<_, _>>()?,
        next_page_token: match page_info.map(|info| info.next_page_token) {
            Some(token) if !token.is_empty() => Some(
                EnginePageToken::new(token)
                    .map_err(|_| mapping_defect("history returned an invalid page token"))?,
            ),
            _ => None,
        },
    })
}

fn map_history_entry(
    entry: HistoryEntry,
    media_origin: Option<&Url>,
) -> Result<EngineHistoryEntry, EngineError> {
    if entry.id.trim().is_empty() {
        return Err(mapping_defect("history entry missing id"));
    }
    let completion_ratio = normalize_completion_ratio(entry.completion_ratio)
        .map_err(|_| mapping_defect("history entry has invalid completion ratio"))?;
    Ok(EngineHistoryEntry {
        id: entry.id,
        played_at_epoch_millis: entry.played_at.map(timestamp_to_epoch_millis).transpose()?,
        duration_millis: entry.duration_ms,
        completion_ratio,
        track: entry
            .track
            .map(|track| map_track_summary(track, Vec::new(), media_origin))
            .transpose()?,
    })
}

fn timestamp_to_epoch_millis(timestamp: prost_types_014::Timestamp) -> Result<u64, EngineError> {
    if !(0..=253_402_300_799).contains(&timestamp.seconds)
        || !(0..1_000_000_000).contains(&timestamp.nanos)
    {
        return Err(mapping_defect("history entry has invalid played_at"));
    }
    u64::try_from(timestamp.seconds)
        .ok()
        .and_then(|seconds| seconds.checked_mul(1_000))
        .and_then(|millis| millis.checked_add(u64::from(timestamp.nanos as u32) / 1_000_000))
        .ok_or_else(|| mapping_defect("history entry played_at overflowed"))
}

fn mapping_defect(message: &'static str) -> EngineError {
    EngineError::new(EngineErrorType::MappingDefect, message, false)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::networking::canopy::sdk::resources::{
        HistoryEntry, HistorySettings, PageInfo, RecordPlaybackRequest, TrackSummary,
        UpdateHistorySettingsResponse,
    };

    #[test]
    fn maps_settings_update_and_semantic_retry_classes() {
        assert_eq!(
            map_settings(HistorySettings { enabled: true }),
            EngineHistorySettings { enabled: true }
        );
        assert_eq!(
            map_settings_update(UpdateHistorySettingsResponse {
                settings: Some(HistorySettings { enabled: false }),
                deleted_count: 7
            })
            .unwrap()
            .deleted_count,
            7
        );
    }

    #[test]
    fn record_mapper_uses_validated_backend_neutral_values() {
        let request = map_record_request(EnginePlaybackRecord::new("track-1", 123, 1.4).unwrap());
        assert_eq!(
            request,
            RecordPlaybackRequest {
                track_id: "track-1".into(),
                duration_ms: 123,
                completion_ratio: 1.0
            }
        );
    }

    #[test]
    fn history_page_preserves_opaque_token_and_optional_track() {
        let page = map_history_page(
            vec![HistoryEntry {
                id: "history-1".into(),
                played_at: Some(prost_types_014::Timestamp {
                    seconds: 2,
                    nanos: 3_000_000,
                }),
                duration_ms: 10,
                completion_ratio: 0.5,
                track: None,
            }],
            Some(PageInfo {
                next_page_token: "opaque+/=".into(),
            }),
            None,
        )
        .unwrap();
        assert_eq!(page.items[0].played_at_epoch_millis, Some(2_003));
        assert!(page.items[0].track.is_none());
        assert_eq!(page.next_page_token.unwrap().as_str(), "opaque+/=");
        let request = map_page_request(EnginePageRequest {
            page_size: 25,
            page_token: Some(EnginePageToken::new("incoming+/=".into()).unwrap()),
        });
        assert_eq!(request.page_size, 25);
        assert_eq!(request.page_token, "incoming+/=");
    }

    #[test]
    fn mapping_rejects_invalid_timestamps_ratios_and_missing_required_fields() {
        let invalid_timestamp = HistoryEntry {
            id: "history-1".into(),
            played_at: Some(prost_types_014::Timestamp {
                seconds: -1,
                nanos: 0,
            }),
            duration_ms: 10,
            completion_ratio: 0.5,
            track: None,
        };
        assert_eq!(
            map_history_entry(invalid_timestamp, None)
                .unwrap_err()
                .error_type,
            EngineErrorType::MappingDefect
        );
        let invalid_ratio = HistoryEntry {
            id: "history-1".into(),
            played_at: None,
            duration_ms: 10,
            completion_ratio: f32::NAN,
            track: Some(TrackSummary::default()),
        };
        assert_eq!(
            map_history_entry(invalid_ratio, None)
                .unwrap_err()
                .error_type,
            EngineErrorType::MappingDefect
        );
        assert!(
            map_settings_update(UpdateHistorySettingsResponse {
                settings: None,
                deleted_count: 0
            })
            .is_err()
        );
    }
}
