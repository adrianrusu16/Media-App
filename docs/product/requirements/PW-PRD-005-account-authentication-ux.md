# PW-PRD-005: Account Authentication UX

**Status:** Implemented
**Owners:** Product, Design, Engineering
**Last updated:** 2026-08-03
**Related:** `PW-PRD-001-adaptive-app-shell.md`, Canopy `client-integration.md`, canopy-api `consumer-guide.md`

## Problem

Anonymous Profile actions and the initial Login/Create account forms are functional, but their interaction and feedback do not yet match normal Android expectations. Action cards appear tappable only at their trailing buttons, malformed input reaches Canopy before the user receives guidance, keyboard completion does not submit, backend policy drift produces an unhelpful generic message, and the mini-player competes visually with credential entry.

Authentication also needs an explicit reliability and abuse-control boundary. Client deadlines prevent indefinite waits but cannot defend the backend from denial-of-service traffic. Canopy and its production ingress remain responsible for authoritative validation, bounded password work, rate limits, and deployment-level protection.

## Outcomes

- Account actions behave as single, full-surface touch, rotary, DPAD, and accessibility targets.
- Login and account creation provide immediate, consistent field feedback before network submission.
- New and replacement password policy is easy to change in one policy object and remains aligned with Canopy.
- Credential entry remains focused and distraction-safe without interrupting active playback.
- Every Canopy request is bounded below the Kotlin/FFI boundary and ambiguous authentication mutations are never replayed.
- Backend and deployment owners have explicit, layered abuse-control responsibilities.

## Non-Goals

- Making Kotlin authoritative for Canopy policy or exposing protobuf/status/token details to Kotlin.
- Proving email deliverability through syntax validation; verification email remains authoritative.
- Applying the current password-creation range to login for established accounts.
- Stopping playback when Login or Create account is visible.
- Treating application rate limits or client deadlines as volumetric DDoS protection.
- Supporting internationalized email local parts in this policy version.

## Product Requirements

- `PR-AUTH-01`: Registration, password reset, and password change shall accept passwords containing 8 through 64 Unicode code points inclusive.
- `PR-AUTH-02`: Login shall require a non-empty password but shall not apply the current creation-length range.
- `PR-AUTH-03`: Email validation shall trim surrounding whitespace, require exactly one `@`, require a valid non-empty ASCII local and domain structure, reject whitespace/control characters and empty domain labels, and limit the complete UTF-8 encoding to 254 bytes.
- `PR-AUTH-04`: PandaWave shall define the mirrored client rules in one service-neutral `AuthInputPolicy`; form code shall not embed password thresholds or email patterns.
- `PR-AUTH-05`: Canopy and the canonical protobuf documentation remain authoritative. PandaWave shall branch on typed engine errors and never parse backend error messages.
- `PR-AUTH-06`: A locally valid request rejected with `INVALID_ARGUMENT` shall display a sanitized policy/contract mismatch message rather than attribute the failure to a specific field.
- `PR-AUTH-07`: Authentication mutations, verification, and refresh shall never be automatically replayed after an ambiguous result.
- `PR-AUTH-08`: PandaEngine shall apply a five-second deadline to authentication and other unary Canopy adapter requests below the FFI boundary. Kotlin shall not add a competing network deadline.
- `PR-AUTH-09`: Canopy shall retain per-subject abuse limits, bound concurrent Argon2 work, and return canonical `RESOURCE_EXHAUSTED` when password-processing capacity is saturated.
- `PR-AUTH-10`: Production ingress shall separately provide trusted-client-IP rate limiting, HTTP/2 connection/stream limits, request-size limits, and provider-level DDoS/WAF protection.

## UI/UX Requirements

- `UX-AUTH-01`: The complete surface of every `BambooActionCard`, including Login and Create account, shall invoke its one action when enabled.
- `UX-AUTH-02`: An action card shall expose one semantic button target; its trailing action label shall not create a nested independent target.
- `UX-AUTH-03`: Invalid field feedback shall first appear after that field loses focus or after an explicit submit attempt.
- `UX-AUTH-04`: Invalid fields shall use the Material error outline and show a specific correction below the field.
- `UX-AUTH-05`: Focusing or editing an invalid field shall immediately clear its visible error. A later blur shall revalidate it.
- `UX-AUTH-06`: Email IME Next shall move focus to Password.
- `UX-AUTH-07`: Password IME Done shall use the same single-shot submission path as the visible action. A valid form submits once; an invalid form shows field errors and does not call PandaEngine.
- `UX-AUTH-08`: Login and Create account shall hide the mini-player while leaving playback and the Media3 session running. Returning to an eligible destination shall restore it.
- `UX-AUTH-09`: Registration success shall show only the generic verification-pending state. Backend text and account-existence information shall not appear.
- `UX-AUTH-10`: Password input shall remain an ephemeral Compose buffer, shall not enter ViewModel/SavedState/navigation/telemetry state, and shall be cleared before command submission or form closure.

## Accessibility And Automotive Safety

- Action-card focus, touch, rotary, DPAD, and accessibility activation shall resolve to the same enabled action.
- Error text shall be associated with its field through Material supporting-text semantics and shall not rely on color alone.
- When driving restrictions become active, PandaWave shall clear credentials, close the form, cancel the local wait where possible, and never replay the command.
- A late authenticated PandaEngine snapshot remains authoritative but shall not reopen credential UI.

## Architecture And Security Ownership

- `feature:auth` owns input policy, form feedback, IME behavior, sanitized messages, and ephemeral credential buffers.
- `feature:appshell` owns auth destinations and mini-player visibility.
- `core:ui` owns the full-surface action-card interaction contract.
- PandaEngine owns Canopy calls, deadlines, status mapping, token/session semantics, refresh coordination, and authoritative auth snapshots.
- Canopy owns canonical validation, account policy, per-subject rate limits, bounded password hashing, and server capacity limits.
- Production ingress/provider infrastructure owns trustworthy source-IP enforcement and volumetric DDoS protection.

## Verification Requirements

- Unit tests shall cover password boundaries 7/8/64/65, Unicode code-point counting, login compatibility, accepted/malformed/oversized emails, blur feedback, edit/focus clearing, and submit gating.
- Navigation tests shall prove Login, Create account, and Now Playing hide the mini-player while normal destinations show it.
- Compose tests shall prove the complete action card has one click action, invalid fields expose supporting errors, and IME Done submits only valid forms once.
- PandaEngine tests shall prove adapter requests carry bounded deadlines and non-idempotent operations are never replayed.
- Canopy tests shall cover the same input boundaries, legacy credential verification, saturation mapping, and existing authentication rate limits.
- Emulator QA shall cover touch, rotary/DPAD focus, blur/edit feedback, IME submission, hidden mini-player with uninterrupted playback, sanitized server rejection, and restricted-mode closure.

## Acceptance Criteria

- [x] Tapping anywhere on Login, Create account, Logout, Settings, or another enabled action card invokes exactly one action.
- [x] `foo` cannot be submitted as an email and receives an inline correction after blur or submit.
- [x] Account creation accepts exactly 8 and 64-character passwords and rejects 7 and 65-character passwords before transport.
- [x] Focusing or editing an invalid field removes its visible error until it is revalidated.
- [x] IME Done submits a valid form once and does not submit an invalid form.
- [x] Login and Create account hide the mini-player without stopping playback.
- [x] A Canopy `INVALID_ARGUMENT` after local validation produces the sanitized policy-mismatch message.
- [x] Authentication and adapter deadlines, no-replay behavior, backend hashing bounds, and operator DDoS responsibilities are covered by tests or documented deployment assertions.

## Dependencies

- PandaEngine Canopy adapter and typed engine auth boundary.
- Canopy AuthService, password policy, abuse-control repository, and production ingress.
- BambooUI action cards and Material 3 text fields.
- AAOS UX restriction observation and Media3 service-owned playback.
