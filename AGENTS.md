# Mandatory local-mock-only boundary

## Explicit runtime-data change — 2026-10-10

The user explicitly requests removal of runtime mock code. Both debug and release now connect to the already authorized local development API at `http://127.0.0.1:18474/api/v1`; there is no mock build flag or fallback. Synthetic fixtures belong only in `androidTest`, injected into a test Activity. This supersedes earlier instructions to install mock-only application builds or pass `phototube.localIntegration=false`. On the authorized `emulator-5560 / PhotoTube_Mock_Audit`, local-service verification may use `adb reverse tcp:18474 tcp:18474`. Never connect to the user's physical devices. This exception remains limited to the exact local origin, whose media the user confirmed is generated test data; NAS, other LAN servers, production, remote AI and former real sessions remain prohibited. Never commit credentials or session data.

The user explicitly prohibits access to NAS and all private data.

- Only local synthetic/mock data and bundled sample media may be used in development, UI review, tests, screenshots and demos.
- Never connect to a NAS, LAN server, production service, remote AI provider, or stored real account/session. Do not run Real* instrumentation tests or real-service scripts.
- Do not open an older installed application that can restore a real session. Only install/start a verified build using the authorized local origin, or an isolated test Activity.
- Do not read, copy, screenshot, log or export private media, credentials, cookies, network responses or device backups. Do not inspect old private screenshots to classify them.
- Do not weaken the exact-origin request guard. Any future exception requires an explicit new user instruction.
- Review evidence must come from synthetic fixtures, with its mock origin recorded.

# Delivery cadence

- The user requests timely commits and pushes during subsequent development.
- After completing a coherent change and its relevant checks, commit it and push to the configured remote branch; do not leave multiple finished changes accumulating locally.
- Exclude local screenshots, recordings, test logs, build outputs, credentials and private data from commits. Never weaken the data-access boundary to verify a change.
- Report the commit and push result accurately. Do not force-push or overwrite remote work.
