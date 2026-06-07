# Observability

PandaWave uses `:core:telemetry-adapter` as the Android-side entry point for
logs, diagnostics, and future telemetry export.

## Principles

- Emit typed events instead of free-form strings.
- Redact sensitive attributes before events leave the caller.
- Keep telemetry sinks replaceable so debug logs, crash reporting, OpenTelemetry,
  and Rust-originated events can share the same contract later.
- Do not log access tokens, session IDs, user IDs, email addresses, raw request
  bodies, or native panic details.

## Current Shape

`TelemetryLogger` builds timestamped `TelemetryEvent` values and applies
`TelemetryAttributeRedactor`. Sinks receive already-redacted events.

Current sinks:

| Sink | Purpose |
| --- | --- |
| `AndroidLogTelemetrySink` | Local Logcat diagnostics |
| `CompositeTelemetrySink` | Fan-out to multiple sinks |

Future sinks can forward selected, redacted events to crash reporting,
OpenTelemetry, or a Rust-owned telemetry pipeline.

## Engine Boundary Events

`AidlEngineGateway` records `engine_gateway.command` for command lifecycle
diagnostics. Attributes are intentionally limited to `command_type`, `status`,
and `pending_count`; command payloads are never logged. Current statuses are
`applied`, `queued`, `replayed`, and `unavailable`.
