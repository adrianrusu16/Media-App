use std::sync::Arc;

use prost_types_014::value::Kind;
use prost_types_014::{FieldMask, Struct, Value};
use tonic_014::Request;

use crate::{EngineError, EngineErrorType, EngineProfile, EngineProfileUpdate, ProfilePort};

use super::operation::CanopyOperation;
use super::request::execute as execute_request;
use super::sdk::clients::profile_service_client::ProfileServiceClient;
use super::sdk::resources::{
    DeleteProfileRequest, GetPreferencesRequest, GetProfileRequest, Preferences, Profile,
    UpdatePreferencesRequest, UpdateProfileRequest, UpsertProfileRequest,
};
use super::{CanopyChannel, SessionCoordinator};

/// Canonical authenticated Canopy profile adapter.
#[derive(Clone)]
pub struct CanopyProfileClient {
    client: ProfileServiceClient<super::sdk::runtime::transport::Channel>,
    session: Arc<SessionCoordinator>,
}

impl CanopyProfileClient {
    pub fn new(channel: &CanopyChannel, session: Arc<SessionCoordinator>) -> Self {
        Self {
            client: ProfileServiceClient::new(channel.clone_inner()),
            session,
        }
    }
}

#[async_trait::async_trait]
impl ProfilePort for CanopyProfileClient {
    async fn upsert(&self, display_name: Option<&str>) -> Result<EngineProfile, EngineError> {
        let request = UpsertProfileRequest {
            display_name: display_name.map(str::to_owned),
        };
        let client = self.client.clone();
        let response = execute_profile_request(
            self.session.as_ref(),
            CanopyOperation::UpsertProfile,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.upsert_profile(request).await }
            },
        )
        .await?
        .into_inner();
        map_profile(response)
    }

    async fn get(&self) -> Result<EngineProfile, EngineError> {
        let client = self.client.clone();
        let response = execute_profile_request(
            self.session.as_ref(),
            CanopyOperation::GetProfile,
            || Request::new(GetProfileRequest {}),
            move |request| {
                let mut client = client.clone();
                async move { client.get_profile(request).await }
            },
        )
        .await?
        .into_inner();
        map_profile(response)
    }

    async fn update(&self, update: EngineProfileUpdate) -> Result<EngineProfile, EngineError> {
        if update.field_mask_paths().is_empty() {
            return self.get().await;
        }
        let profile = update.apply_to(&self.get().await?);
        let request = UpdateProfileRequest {
            profile: Some(profile_to_wire(profile)),
            update_mask: Some(FieldMask {
                paths: update.field_mask_paths(),
            }),
        };
        let client = self.client.clone();
        let response = execute_profile_request(
            self.session.as_ref(),
            CanopyOperation::UpdateProfile,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.update_profile(request).await }
            },
        )
        .await?
        .into_inner();
        map_profile(response)
    }

    async fn delete(&self) -> Result<(), EngineError> {
        let client = self.client.clone();
        execute_profile_request(
            self.session.as_ref(),
            CanopyOperation::DeleteProfile,
            || Request::new(DeleteProfileRequest {}),
            move |request| {
                let mut client = client.clone();
                async move { client.delete_profile(request).await }
            },
        )
        .await?;
        Ok(())
    }

    async fn get_preferences(
        &self,
    ) -> Result<serde_json::Map<String, serde_json::Value>, EngineError> {
        let client = self.client.clone();
        let preferences = execute_profile_request(
            self.session.as_ref(),
            CanopyOperation::GetPreferences,
            || Request::new(GetPreferencesRequest {}),
            move |request| {
                let mut client = client.clone();
                async move { client.get_preferences(request).await }
            },
        )
        .await?
        .into_inner();
        map_preferences(preferences)
    }

    async fn update_preferences(
        &self,
        values: serde_json::Map<String, serde_json::Value>,
    ) -> Result<serde_json::Map<String, serde_json::Value>, EngineError> {
        let request = UpdatePreferencesRequest {
            preferences: Some(Preferences {
                values: Some(json_to_struct(values)),
            }),
        };
        let client = self.client.clone();
        let preferences = execute_profile_request(
            self.session.as_ref(),
            CanopyOperation::UpdatePreferences,
            || Request::new(request.clone()),
            move |request| {
                let mut client = client.clone();
                async move { client.update_preferences(request).await }
            },
        )
        .await?
        .into_inner();
        map_preferences(preferences)
    }
}

async fn execute_profile_request<TRequest, TResponse, MakeRequest, Execute, ExecuteFuture>(
    session: &SessionCoordinator,
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
    execute_request(Some(session), operation, make_request, execute).await
}

fn map_profile(wire: Profile) -> Result<EngineProfile, EngineError> {
    if wire.id.is_empty() || wire.external_user_id.is_empty() {
        return Err(mapping_defect());
    }
    Ok(EngineProfile {
        id: wire.id,
        external_user_id: wire.external_user_id,
        display_name: wire.display_name,
        created_at_epoch_millis: wire.created_at.map(timestamp_to_epoch_millis).transpose()?,
        updated_at_epoch_millis: wire.updated_at.map(timestamp_to_epoch_millis).transpose()?,
    })
}

fn profile_to_wire(profile: EngineProfile) -> Profile {
    Profile {
        id: profile.id,
        external_user_id: profile.external_user_id,
        display_name: profile.display_name,
        created_at: None,
        updated_at: None,
    }
}

fn map_preferences(
    preferences: Preferences,
) -> Result<serde_json::Map<String, serde_json::Value>, EngineError> {
    preferences
        .values
        .map(struct_to_json)
        .transpose()
        .map(|value| value.unwrap_or_default())
}

fn struct_to_json(
    value: Struct,
) -> Result<serde_json::Map<String, serde_json::Value>, EngineError> {
    value
        .fields
        .into_iter()
        .map(|(key, value)| value_to_json(value).map(|value| (key, value)))
        .collect()
}

fn value_to_json(value: Value) -> Result<serde_json::Value, EngineError> {
    match value.kind {
        None | Some(Kind::NullValue(_)) => Ok(serde_json::Value::Null),
        Some(Kind::NumberValue(value)) if value.is_finite() => serde_json::Number::from_f64(value)
            .map(serde_json::Value::Number)
            .ok_or_else(mapping_defect),
        Some(Kind::StringValue(value)) => Ok(serde_json::Value::String(value)),
        Some(Kind::BoolValue(value)) => Ok(serde_json::Value::Bool(value)),
        Some(Kind::StructValue(value)) => struct_to_json(value).map(serde_json::Value::Object),
        Some(Kind::ListValue(value)) => value
            .values
            .into_iter()
            .map(value_to_json)
            .collect::<Result<Vec<_>, _>>()
            .map(serde_json::Value::Array),
        Some(Kind::NumberValue(_)) => Err(mapping_defect()),
    }
}

fn json_to_struct(values: serde_json::Map<String, serde_json::Value>) -> Struct {
    Struct {
        fields: values
            .into_iter()
            .map(|(key, value)| (key, json_to_value(value)))
            .collect(),
    }
}

fn json_to_value(value: serde_json::Value) -> Value {
    let kind = match value {
        serde_json::Value::Null => Kind::NullValue(0),
        serde_json::Value::Bool(value) => Kind::BoolValue(value),
        serde_json::Value::Number(value) => Kind::NumberValue(value.as_f64().unwrap_or(0.0)),
        serde_json::Value::String(value) => Kind::StringValue(value),
        serde_json::Value::Array(values) => Kind::ListValue(prost_types_014::ListValue {
            values: values.into_iter().map(json_to_value).collect(),
        }),
        serde_json::Value::Object(values) => Kind::StructValue(json_to_struct(values)),
    };
    Value { kind: Some(kind) }
}

fn timestamp_to_epoch_millis(timestamp: prost_types_014::Timestamp) -> Result<u64, EngineError> {
    if !(0..=253_402_300_799).contains(&timestamp.seconds)
        || !(0..1_000_000_000).contains(&timestamp.nanos)
    {
        return Err(mapping_defect());
    }
    u64::try_from(timestamp.seconds)
        .ok()
        .and_then(|seconds| seconds.checked_mul(1_000))
        .and_then(|millis| millis.checked_add(u64::from(timestamp.nanos as u32) / 1_000_000))
        .ok_or_else(mapping_defect)
}

fn mapping_defect() -> EngineError {
    EngineError::new(
        EngineErrorType::MappingDefect,
        "invalid canonical Canopy profile response",
        false,
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn mapper_preserves_unknown_preference_keys() {
        let preferences = Preferences {
            values: Some(json_to_struct(
                serde_json::json!({"theme":"dark","future_key":{"nested":7}})
                    .as_object()
                    .unwrap()
                    .clone(),
            )),
        };
        assert_eq!(
            map_preferences(preferences).unwrap()["future_key"]["nested"].as_f64(),
            Some(7.0)
        );
    }

    #[test]
    fn update_mask_uses_only_typed_lower_snake_case_fields() {
        assert_eq!(
            EngineProfileUpdate::display_name(Some("Driver".into())).field_mask_paths(),
            ["display_name"]
        );
    }
}
