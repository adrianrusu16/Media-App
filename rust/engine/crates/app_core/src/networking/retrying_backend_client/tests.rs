use super::*;
use crate::networking::backend_client::MockBackendClient;
use tokio_stream::StreamExt;

#[test]
fn exponential_backoff_delay_caps_at_max_retry_delay() {
    let client = MockBackendClient::new();
    let retrying = RetryingBackendClient::new_with_exponential_backoff(
        Arc::new(client),
        5,
        Duration::from_millis(10),
        Duration::from_millis(25),
    );

    assert_eq!(
        retrying.retry_delay_for_attempt(1),
        Duration::from_millis(10)
    );
    assert_eq!(
        retrying.retry_delay_for_attempt(2),
        Duration::from_millis(20)
    );
    assert_eq!(
        retrying.retry_delay_for_attempt(3),
        Duration::from_millis(25)
    );
    assert_eq!(
        retrying.retry_delay_for_attempt(4),
        Duration::from_millis(25)
    );
}

#[test]
fn jittered_exponential_backoff_stays_bounded_and_capped() {
    let client = MockBackendClient::new();
    let retrying = RetryingBackendClient::new_with_exponential_backoff_and_jitter(
        Arc::new(client),
        5,
        Duration::from_millis(10),
        Duration::from_millis(25),
        20,
    );

    let delay1 = retrying.retry_delay_for_attempt(1).as_millis();
    assert!((8..=12).contains(&delay1));

    let delay2 = retrying.retry_delay_for_attempt(2).as_millis();
    assert!((16..=24).contains(&delay2));

    let delay3 = retrying.retry_delay_for_attempt(3).as_millis();
    assert!((20..=25).contains(&delay3));
}

#[test]
fn retry_delay_budget_caps_cumulative_sleep_delay() {
    let client = MockBackendClient::new();
    let retrying = RetryingBackendClient::new_with_exponential_backoff(
        Arc::new(client),
        5,
        Duration::from_millis(10),
        Duration::from_millis(80),
    )
    .with_retry_time_budget(Duration::from_millis(25));

    assert_eq!(
        retrying.retry_sleep_delay_for_attempt(1, Duration::from_millis(0)),
        Some(Duration::from_millis(10))
    );
    assert_eq!(
        retrying.retry_sleep_delay_for_attempt(2, Duration::from_millis(10)),
        Some(Duration::from_millis(15))
    );
    assert_eq!(
        retrying.retry_sleep_delay_for_attempt(3, Duration::from_millis(25)),
        None
    );
}

#[tokio::test]
async fn search_retries_until_success() {
    let mut client = MockBackendClient::new();
    let mut calls = 0;

    client.expect_search().times(2).returning(move |_| {
        calls += 1;
        if calls == 1 {
            Err(anyhow::anyhow!("transient failure"))
        } else {
            Ok(vec![MediaItem {
                id: "ok-1".to_string(),
                title: "Recovered".to_string(),
                ..Default::default()
            }])
        }
    });

    let retrying = RetryingBackendClient::new(Arc::new(client), 1, Duration::from_millis(1));
    let result = retrying.search("query").await.unwrap();

    assert_eq!(result.len(), 1);
    assert_eq!(result[0].id, "ok-1");
}

#[tokio::test]
async fn browse_returns_last_error_when_retries_exhausted() {
    let mut client = MockBackendClient::new();
    client
        .expect_fetch_children()
        .times(3)
        .returning(|_| Err(anyhow::anyhow!("still failing")));

    let retrying = RetryingBackendClient::new(Arc::new(client), 2, Duration::from_millis(1));
    let error = retrying.fetch_children("root").await.unwrap_err();

    assert!(
        error
            .to_string()
            .contains("fetch_children failed after 3 attempt(s)")
    );
}

#[tokio::test]
async fn fetch_children_retries_until_success() {
    let mut client = MockBackendClient::new();
    let mut calls = 0;

    client.expect_fetch_children().times(2).returning(move |_| {
        calls += 1;
        if calls == 1 {
            Err(anyhow::anyhow!("temporary network error"))
        } else {
            Ok(vec![MediaItem {
                id: "child-1".to_string(),
                title: "Recovered Child".to_string(),
                ..Default::default()
            }])
        }
    });

    let retrying = RetryingBackendClient::new(Arc::new(client), 1, Duration::from_millis(1));
    let result = retrying.fetch_children("root").await.unwrap();

    assert_eq!(result.len(), 1);
    assert_eq!(result[0].id, "child-1");
}

#[tokio::test]
async fn search_with_zero_retries_fails_immediately() {
    let mut client = MockBackendClient::new();
    client
        .expect_search()
        .times(1)
        .returning(|_| Err(anyhow::anyhow!("no retry path")));

    let retrying = RetryingBackendClient::new(Arc::new(client), 0, Duration::from_millis(1));
    let error = retrying.search("query").await.unwrap_err();

    assert!(
        error
            .to_string()
            .contains("search failed after 1 attempt(s)")
    );
}

#[tokio::test]
async fn search_non_retryable_error_fails_immediately_even_when_retries_configured() {
    let mut client = MockBackendClient::new();
    client
        .expect_search()
        .times(1)
        .returning(|_| Err(anyhow::anyhow!("fatal: invalid request")));

    fn retry_only_transient(error: &anyhow::Error) -> bool {
        error.to_string().contains("transient")
    }

    let retrying = RetryingBackendClient::new_with_policy(
        Arc::new(client),
        3,
        Duration::from_millis(1),
        retry_only_transient,
    );
    let error = retrying.search("query").await.unwrap_err();

    assert!(
        error
            .to_string()
            .contains("search failed after 1 attempt(s)")
    );
}

#[tokio::test]
async fn fetch_children_non_retryable_error_fails_immediately_even_when_retries_configured() {
    let mut client = MockBackendClient::new();
    client
        .expect_fetch_children()
        .times(1)
        .returning(|_| Err(anyhow::anyhow!("fatal: parent does not exist")));

    fn retry_only_transient(error: &anyhow::Error) -> bool {
        error.to_string().contains("transient")
    }

    let retrying = RetryingBackendClient::new_with_policy(
        Arc::new(client),
        3,
        Duration::from_millis(1),
        retry_only_transient,
    );
    let error = retrying.fetch_children("root").await.unwrap_err();

    assert!(
        error
            .to_string()
            .contains("fetch_children failed after 1 attempt(s)")
    );
}

#[tokio::test]
async fn search_retryable_error_retries_and_succeeds_with_policy() {
    let mut client = MockBackendClient::new();
    let mut calls = 0;

    client.expect_search().times(2).returning(move |_| {
        calls += 1;
        if calls == 1 {
            Err(anyhow::anyhow!("transient: backend unavailable"))
        } else {
            Ok(vec![MediaItem {
                id: "policy-ok-1".to_string(),
                title: "Recovered by policy retry".to_string(),
                ..Default::default()
            }])
        }
    });

    fn retry_only_transient(error: &anyhow::Error) -> bool {
        error.to_string().contains("transient")
    }

    let retrying = RetryingBackendClient::new_with_policy(
        Arc::new(client),
        3,
        Duration::from_millis(1),
        retry_only_transient,
    );
    let result = retrying.search("query").await.unwrap();

    assert_eq!(result.len(), 1);
    assert_eq!(result[0].id, "policy-ok-1");
}

#[tokio::test]
async fn jitter_strategy_can_be_combined_with_retry_policy() {
    let mut client = MockBackendClient::new();
    client
        .expect_search()
        .times(1)
        .returning(|_| Err(anyhow::anyhow!("fatal: invalid request")));

    fn retry_only_transient(error: &anyhow::Error) -> bool {
        error.to_string().contains("transient")
    }

    let retrying = RetryingBackendClient::new_with_exponential_backoff_and_jitter(
        Arc::new(client),
        3,
        Duration::from_millis(1),
        Duration::from_millis(4),
        20,
    )
    .with_retry_policy(retry_only_transient);

    let error = retrying.search("query").await.unwrap_err();
    assert!(
        error
            .to_string()
            .contains("search failed after 1 attempt(s)")
    );
}

#[tokio::test]
async fn fetch_children_retryable_error_retries_and_succeeds_with_policy() {
    let mut client = MockBackendClient::new();
    let mut calls = 0;

    client.expect_fetch_children().times(2).returning(move |_| {
        calls += 1;
        if calls == 1 {
            Err(anyhow::anyhow!("transient: backend unavailable"))
        } else {
            Ok(vec![MediaItem {
                id: "policy-child-1".to_string(),
                title: "Recovered child by policy retry".to_string(),
                ..Default::default()
            }])
        }
    });

    fn retry_only_transient(error: &anyhow::Error) -> bool {
        error.to_string().contains("transient")
    }

    let retrying = RetryingBackendClient::new_with_policy(
        Arc::new(client),
        3,
        Duration::from_millis(1),
        retry_only_transient,
    );
    let result = retrying.fetch_children("root").await.unwrap();

    assert_eq!(result.len(), 1);
    assert_eq!(result[0].id, "policy-child-1");
}

#[tokio::test]
async fn search_stream_retryable_error_retries_and_succeeds_with_policy() {
    let mut client = MockBackendClient::new();
    let mut calls = 0;

    client.expect_search_stream().times(2).returning(move |_| {
        calls += 1;
        if calls == 1 {
            Err(anyhow::anyhow!("transient: stream startup unavailable"))
        } else {
            Ok(Box::pin(tokio_stream::iter(vec![Ok(MediaItem {
                id: "stream-policy-ok-1".to_string(),
                title: "Recovered stream by policy retry".to_string(),
                ..Default::default()
            })])))
        }
    });

    fn retry_only_transient(error: &anyhow::Error) -> bool {
        error.to_string().contains("transient")
    }

    let retrying = RetryingBackendClient::new_with_policy(
        Arc::new(client),
        3,
        Duration::from_millis(1),
        retry_only_transient,
    );
    let mut stream = retrying.search_stream("query").await.unwrap();
    let first = stream.next().await.unwrap().unwrap();

    assert_eq!(first.id, "stream-policy-ok-1");
    assert!(stream.next().await.is_none());
}

#[tokio::test]
async fn search_stream_non_retryable_error_fails_immediately_even_when_retries_configured() {
    let mut client = MockBackendClient::new();
    client
        .expect_search_stream()
        .times(1)
        .returning(|_| Err(anyhow::anyhow!("fatal: invalid stream query")));

    fn retry_only_transient(error: &anyhow::Error) -> bool {
        error.to_string().contains("transient")
    }

    let retrying = RetryingBackendClient::new_with_policy(
        Arc::new(client),
        3,
        Duration::from_millis(1),
        retry_only_transient,
    );
    let error = match retrying.search_stream("query").await {
        Ok(_) => panic!("expected search_stream to fail for non-retryable error"),
        Err(error) => error,
    };

    assert!(
        error
            .to_string()
            .contains("search_stream failed after 1 attempt(s)")
    );
}
