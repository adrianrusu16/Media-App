use anyhow::{Context, bail};
use async_trait::async_trait;
use jni::objects::{GlobalRef, JObject, JString, JValue, JValueOwned};
use jni::{JNIEnv, JavaVM};
use panda_engine_core::{AudioChunk, AudioSourceClient, PlaybackSource};

pub(crate) struct JniAudioSourceClient {
    java_vm: JavaVM,
    resolver: GlobalRef,
}

impl JniAudioSourceClient {
    pub(crate) fn new(env: &mut JNIEnv, resolver: JObject) -> anyhow::Result<Self> {
        if resolver.is_null() {
            bail!("audio source resolver must not be null");
        }

        Ok(Self {
            java_vm: env.get_java_vm().context("failed to get JavaVM")?,
            resolver: env
                .new_global_ref(resolver)
                .context("failed to retain audio source resolver")?,
        })
    }
}

#[async_trait]
impl AudioSourceClient for JniAudioSourceClient {
    async fn resolve_track(&self, track_id: &str) -> anyhow::Result<PlaybackSource> {
        let mut env = self
            .java_vm
            .attach_current_thread()
            .context("failed to attach source resolver thread to JVM")?;
        let track_id = env
            .new_string(track_id)
            .context("failed to allocate source resolver track id")?;
        let track_id = JObject::from(track_id);
        let resolved = call_method_checked(
            &mut env,
            self.resolver.as_obj(),
            JniAudioSourceResolverMethod::RESOLVE,
            JniAudioSourceResolverMethod::RESOLVE_SIGNATURE,
            &[JValue::Object(&track_id)],
        )?
        .l()
        .context("audio source resolver returned a non-object value")?;

        if resolved.is_null() {
            bail!("audio source resolver returned null for track_id={track_id:?}");
        }

        Ok(PlaybackSource {
            source_id: required_string_getter(
                &mut env,
                &resolved,
                JniAudioSourceResolverMethod::GET_SOURCE_ID,
            )?,
            uri: required_string_getter(
                &mut env,
                &resolved,
                JniAudioSourceResolverMethod::GET_URI,
            )?,
            mime_type: optional_string_getter(
                &mut env,
                &resolved,
                JniAudioSourceResolverMethod::GET_MIME_TYPE,
            )?,
            expected_duration_ms: optional_u64_getter(
                &mut env,
                &resolved,
                JniAudioSourceResolverMethod::GET_EXPECTED_DURATION_MILLIS,
            )?,
        })
    }

    async fn prefetch_full(&self, _source_id: &str) -> anyhow::Result<String> {
        bail!("jni audio source prefetch is not implemented")
    }

    async fn fetch_chunk(
        &self,
        _source_id: &str,
        _from_chunk_index: u64,
    ) -> anyhow::Result<AudioChunk> {
        bail!("jni audio source chunk fetching is not implemented")
    }
}

fn required_string_getter<'local>(
    env: &mut JNIEnv<'local>,
    object: &JObject<'local>,
    method: &str,
) -> anyhow::Result<String> {
    let value = optional_string_getter(env, object, method)?;
    value.with_context(|| format!("audio source resolver returned null from {method}"))
}

fn optional_string_getter<'local>(
    env: &mut JNIEnv<'local>,
    object: &JObject<'local>,
    method: &str,
) -> anyhow::Result<Option<String>> {
    let value = call_method_checked(
        env,
        object,
        method,
        JniAudioSourceResolverMethod::STRING_GETTER_SIGNATURE,
        &[],
    )?
    .l()
    .with_context(|| format!("audio source resolver {method} returned a non-object value"))?;

    if value.is_null() {
        return Ok(None);
    }

    let value = JString::from(value);
    Ok(Some(
        env.get_string(&value)
            .with_context(|| format!("failed to read audio source resolver {method} value"))?
            .to_string_lossy()
            .into_owned(),
    ))
}

fn optional_u64_getter<'local>(
    env: &mut JNIEnv<'local>,
    object: &JObject<'local>,
    method: &str,
) -> anyhow::Result<Option<u64>> {
    let value = call_method_checked(
        env,
        object,
        method,
        JniAudioSourceResolverMethod::LONG_GETTER_SIGNATURE,
        &[],
    )?
    .l()
    .with_context(|| format!("audio source resolver {method} returned a non-object value"))?;

    if value.is_null() {
        return Ok(None);
    }

    let duration = call_method_checked(
        env,
        &value,
        JniAudioSourceResolverMethod::LONG_VALUE,
        JniAudioSourceResolverMethod::LONG_VALUE_SIGNATURE,
        &[],
    )?
    .j()
    .context("audio source resolver duration returned a non-long value")?;

    if duration < 0 {
        bail!("audio source resolver returned negative duration {duration}");
    }

    Ok(Some(duration as u64))
}

fn call_method_checked<'local>(
    env: &mut JNIEnv<'local>,
    object: &JObject<'local>,
    method: &str,
    signature: &str,
    args: &[JValue],
) -> anyhow::Result<JValueOwned<'local>> {
    match env.call_method(object, method, signature, args) {
        Ok(value) => Ok(value),
        Err(error) => {
            clear_pending_exception(env)?;
            Err(anyhow::Error::new(error)
                .context(format!("audio source resolver JNI call failed: {method}")))
        }
    }
}

fn clear_pending_exception(env: &mut JNIEnv) -> anyhow::Result<()> {
    if env
        .exception_check()
        .context("failed to inspect pending JNI exception")?
    {
        env.exception_clear()
            .context("failed to clear pending JNI exception")?;
    }

    Ok(())
}

struct JniAudioSourceResolverMethod;

impl JniAudioSourceResolverMethod {
    const RESOLVE: &'static str = "resolve";
    const RESOLVE_SIGNATURE: &'static str = "(Ljava/lang/String;)Lcom/adrianrusu/pandawave/core/rust/bridge/engine/EnginePlaybackSource;";
    const GET_SOURCE_ID: &'static str = "getSourceId";
    const GET_URI: &'static str = "getUri";
    const GET_MIME_TYPE: &'static str = "getMimeType";
    const GET_EXPECTED_DURATION_MILLIS: &'static str = "getExpectedDurationMillis";
    const STRING_GETTER_SIGNATURE: &'static str = "()Ljava/lang/String;";
    const LONG_GETTER_SIGNATURE: &'static str = "()Ljava/lang/Long;";
    const LONG_VALUE: &'static str = "longValue";
    const LONG_VALUE_SIGNATURE: &'static str = "()J";
}
