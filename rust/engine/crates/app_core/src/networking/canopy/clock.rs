use std::time::{SystemTime, UNIX_EPOCH};

use crate::{EngineError, EngineErrorType};

pub(super) fn current_epoch_millis() -> Result<u64, EngineError> {
    epoch_millis(SystemTime::now())
}

fn epoch_millis(time: SystemTime) -> Result<u64, EngineError> {
    let millis = time
        .duration_since(UNIX_EPOCH)
        .map_err(|_| invalid_system_clock())?
        .as_millis();
    u64::try_from(millis).map_err(|_| invalid_system_clock())
}

fn invalid_system_clock() -> EngineError {
    EngineError::new(
        EngineErrorType::FailedPrecondition,
        "system clock cannot be used for authentication expiry checks",
        false,
    )
}

#[cfg(test)]
mod tests {
    use std::time::{Duration, UNIX_EPOCH};

    use super::epoch_millis;
    use crate::EngineErrorType;

    #[test]
    fn pre_epoch_clock_fails_closed_with_a_typed_error() {
        let before_epoch = UNIX_EPOCH.checked_sub(Duration::from_millis(1)).unwrap();

        let error = epoch_millis(before_epoch).unwrap_err();

        assert_eq!(error.error_type, EngineErrorType::FailedPrecondition);
    }
}
