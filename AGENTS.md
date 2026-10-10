# Mandatory local-mock-only boundary

## Application configuration and agent verification — 2026-10-10

The user explicitly requests removal of runtime mock code and support for logging into user-selected servers. The application must accept and persist user-entered HTTP/HTTPS server origins in both debug and release; do not encode this agent's verification restrictions as an application feature restriction. Never default to a hard-coded development endpoint or restore legacy development account storage. Cookies remain encrypted and scoped to the selected scheme/host/port; retain cross-origin and redirect protections.

Agent verification remains restricted: only synthetic fixtures and the user-authorized `http://127.0.0.1:18474/api/v1` service (generated test media) may be contacted. Never connect to the user's physical devices, NAS, any other LAN/production server or remote AI provider. Only emulator-5560 / PhotoTube_Mock_Audit is authorized. Ordinary UI tests use isolated injected fixtures; service tests require explicit opt-in. This separation supersedes earlier instructions to ship a mock-only or localhost-only application. No credentials, sessions, screenshots, logs or APKs in commits.

The user explicitly prohibits access to NAS and all private data.

- Only local synthetic/mock data and bundled sample media may be used in development, UI review, tests, screenshots and demos.
- Never connect to a NAS, LAN server, production service, remote AI provider, or stored real account/session. Do not run Real* instrumentation tests or real-service scripts.
- Do not open an older installed application that can restore a real session. Only install/start a verified build with a fresh address form, the authorized local origin, or an isolated test Activity.
- Do not read, copy, screenshot, log or export private media, credentials, cookies, network responses or device backups. Do not inspect old private screenshots to classify them.
- Do not weaken the exact-origin request guard. Any future exception requires an explicit new user instruction.
- Review evidence must come from synthetic fixtures, with its mock origin recorded.

# Delivery cadence

- The user requests timely commits and pushes during subsequent development.
- After completing a coherent change and its relevant checks, commit it and push to the configured remote branch; do not leave multiple finished changes accumulating locally.
- Exclude local screenshots, recordings, test logs, build outputs, credentials and private data from commits. Never weaken the data-access boundary to verify a change.
- Report the commit and push result accurately. Do not force-push or overwrite remote work.
