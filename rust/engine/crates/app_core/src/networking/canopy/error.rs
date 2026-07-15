use tonic_014::{Code, Status};

use crate::{EngineError, EngineErrorType};

/// Maps canonical gRPC status codes without depending on backend message text.
pub(crate) fn map_status(status: Status) -> EngineError {
    match status.code() {
        Code::InvalidArgument | Code::OutOfRange => typed(
            EngineErrorType::InvalidInput,
            "backend rejected invalid input",
        ),
        Code::Unauthenticated => typed(
            EngineErrorType::LoginRequired,
            "backend authentication is required",
        ),
        Code::PermissionDenied => typed(EngineErrorType::Forbidden, "backend access is forbidden"),
        Code::NotFound => typed(EngineErrorType::NotFound, "backend resource was not found"),
        Code::AlreadyExists => typed(
            EngineErrorType::AlreadyExists,
            "backend resource already exists",
        ),
        Code::FailedPrecondition => typed(
            EngineErrorType::FailedPrecondition,
            "backend precondition failed",
        ),
        Code::Aborted => typed(EngineErrorType::Conflict, "backend operation conflicted"),
        Code::ResourceExhausted => EngineError::rate_limited(None),
        Code::Unavailable => typed(
            EngineErrorType::ServiceUnavailable,
            "backend service is unavailable",
        ),
        Code::Cancelled | Code::DeadlineExceeded => typed(
            EngineErrorType::Transport,
            "backend request did not complete",
        ),
        Code::Unknown | Code::Unimplemented | Code::Internal | Code::DataLoss | Code::Ok => {
            typed(EngineErrorType::BackendFault, "backend request failed")
        }
    }
}

fn typed(error_type: EngineErrorType, message: &'static str) -> EngineError {
    EngineError::new(error_type, message, false)
}

#[cfg(test)]
mod tests {
    use super::map_status;
    use crate::EngineErrorType;
    use tonic_014::{Code, Status};

    #[test]
    fn canonical_codes_map_without_message_parsing() {
        let cases = [
            (Code::Unauthenticated, EngineErrorType::LoginRequired),
            (Code::PermissionDenied, EngineErrorType::Forbidden),
            (Code::NotFound, EngineErrorType::NotFound),
            (Code::AlreadyExists, EngineErrorType::AlreadyExists),
            (
                Code::FailedPrecondition,
                EngineErrorType::FailedPrecondition,
            ),
            (Code::Aborted, EngineErrorType::Conflict),
            (Code::ResourceExhausted, EngineErrorType::RateLimited),
            (Code::Unavailable, EngineErrorType::ServiceUnavailable),
            (Code::Internal, EngineErrorType::BackendFault),
        ];

        for (code, expected) in cases {
            let error = map_status(Status::new(code, "localized backend text"));
            assert_eq!(error.error_type, expected);
            assert!(!error.message.contains("localized"));
        }
    }

    #[test]
    fn client_and_transport_codes_remain_typed() {
        assert_eq!(
            map_status(Status::new(Code::InvalidArgument, "ignored")).error_type,
            EngineErrorType::InvalidInput
        );
        assert_eq!(
            map_status(Status::new(Code::DeadlineExceeded, "ignored")).error_type,
            EngineErrorType::Transport
        );
    }
}
