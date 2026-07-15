#[test]
fn canonical_generated_contract_is_available() {
    use canopy_api_prost::canopy::v1::{GetStatusRequest, ResolvePlaybackRequest, SearchRequest};

    let _ = GetStatusRequest {};
    let _ = ResolvePlaybackRequest {
        track_id: "track-1".into(),
    };
    let _ = SearchRequest {
        query: "panda".into(),
        page: None,
    };
}
