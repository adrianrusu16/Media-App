use jni::objects::{GlobalRef, JByteArray, JObject, JObjectArray, JValue};
use jni::{JNIEnv, JavaVM};
use panda_engine_core::{SealedSession, SessionCryptor, SessionStoreError};

pub(crate) struct JniSessionCryptor {
    java_vm: JavaVM,
    cryptor: GlobalRef,
}

impl JniSessionCryptor {
    pub(crate) fn new(env: &mut JNIEnv, cryptor: JObject) -> Result<Self, SessionStoreError> {
        if cryptor.is_null() {
            return Err(unavailable());
        }
        Ok(Self {
            java_vm: env.get_java_vm().map_err(|_| unavailable())?,
            cryptor: env.new_global_ref(cryptor).map_err(|_| unavailable())?,
        })
    }
}

impl SessionCryptor for JniSessionCryptor {
    fn seal(
        &self,
        plaintext: &[u8],
        associated_data: &[u8],
    ) -> Result<SealedSession, SessionStoreError> {
        let mut env = self
            .java_vm
            .attach_current_thread()
            .map_err(|_| unavailable())?;
        let plaintext = env
            .byte_array_from_slice(plaintext)
            .map_err(|_| unavailable())?;
        let associated_data = env
            .byte_array_from_slice(associated_data)
            .map_err(|_| unavailable())?;
        let plaintext_object = JObject::from(plaintext);
        let associated_data_object = JObject::from(associated_data);
        let sealed = call_method(
            &mut env,
            self.cryptor.as_obj(),
            "seal",
            "([B[B)[[B",
            &[
                JValue::Object(&plaintext_object),
                JValue::Object(&associated_data_object),
            ],
            unavailable,
        )?
        .l()
        .map_err(|_| unavailable())?;
        if sealed.is_null() {
            return Err(unavailable());
        }
        let sealed = JObjectArray::from(sealed);
        if env.get_array_length(&sealed).map_err(|_| unavailable())? != 3 {
            return Err(unavailable());
        }

        Ok(SealedSession::new(
            object_array_bytes(&mut env, &sealed, 0, unavailable)?,
            object_array_bytes(&mut env, &sealed, 1, unavailable)?,
            object_array_bytes(&mut env, &sealed, 2, unavailable)?,
        ))
    }

    fn open(
        &self,
        sealed: &SealedSession,
        associated_data: &[u8],
    ) -> Result<Vec<u8>, SessionStoreError> {
        let mut env = self
            .java_vm
            .attach_current_thread()
            .map_err(|_| unavailable())?;
        let nonce = env
            .byte_array_from_slice(sealed.nonce())
            .map_err(|_| unavailable())?;
        let ciphertext = env
            .byte_array_from_slice(sealed.ciphertext())
            .map_err(|_| unavailable())?;
        let tag = env
            .byte_array_from_slice(sealed.tag())
            .map_err(|_| unavailable())?;
        let associated_data = env
            .byte_array_from_slice(associated_data)
            .map_err(|_| unavailable())?;
        let nonce_object = JObject::from(nonce);
        let ciphertext_object = JObject::from(ciphertext);
        let tag_object = JObject::from(tag);
        let associated_data_object = JObject::from(associated_data);
        let plaintext = call_method(
            &mut env,
            self.cryptor.as_obj(),
            "open",
            "([B[B[B[B)[B",
            &[
                JValue::Object(&nonce_object),
                JValue::Object(&ciphertext_object),
                JValue::Object(&tag_object),
                JValue::Object(&associated_data_object),
            ],
            corrupted,
        )?
        .l()
        .map_err(|_| corrupted())?;
        if plaintext.is_null() {
            return Err(corrupted());
        }
        let plaintext = JByteArray::from(plaintext);
        let mut result = env
            .convert_byte_array(&plaintext)
            .map_err(|_| corrupted())?;
        let zeros = vec![0_i8; result.len()];
        if env.set_byte_array_region(&plaintext, 0, &zeros).is_err() {
            result.fill(0);
            return Err(corrupted());
        }
        Ok(result)
    }
}

fn object_array_bytes(
    env: &mut JNIEnv,
    array: &JObjectArray,
    index: i32,
    error: fn() -> SessionStoreError,
) -> Result<Vec<u8>, SessionStoreError> {
    let value = env
        .get_object_array_element(array, index)
        .map_err(|_| error())?;
    if value.is_null() {
        return Err(error());
    }
    env.convert_byte_array(JByteArray::from(value))
        .map_err(|_| error())
}

fn call_method<'local>(
    env: &mut JNIEnv<'local>,
    object: &JObject<'local>,
    method: &str,
    signature: &str,
    args: &[JValue],
    error: fn() -> SessionStoreError,
) -> Result<jni::objects::JValueOwned<'local>, SessionStoreError> {
    match env.call_method(object, method, signature, args) {
        Ok(value) => Ok(value),
        Err(_) => {
            if env.exception_check().unwrap_or(false) {
                let _ = env.exception_clear();
            }
            Err(error())
        }
    }
}

fn unavailable() -> SessionStoreError {
    SessionStoreError::Unavailable("platform session cryptography unavailable".into())
}

fn corrupted() -> SessionStoreError {
    SessionStoreError::Corrupted("encrypted session could not be authenticated".into())
}
