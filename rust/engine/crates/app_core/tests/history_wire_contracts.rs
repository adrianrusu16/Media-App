use panda_engine_core::{EngineCommand, EngineCommandType, EnginePageRequest};

#[test]
fn list_history_wire_payload_preserves_requested_page_size() {
    let command = EngineCommand::from_wire(
        EngineCommandType::LIST_HISTORY_WIRE,
        Some(r#"{"version":1,"page":{"page_size":37}}"#.into()),
    );

    assert_eq!(
        command.command_type,
        EngineCommandType::ListHistory {
            page: EnginePageRequest {
                page_size: 37,
                page_token: None,
            },
        },
    );
}
