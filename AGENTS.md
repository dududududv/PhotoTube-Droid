# Mandatory local-mock-only boundary

## Explicit local integration exception — 2026-10-10

The user now explicitly authorizes the local development API at `http://127.0.0.1:18474/api/v1` and the supplied local development account. Debug builds may opt in with `phototube.localIntegration=true`. On the authorized `emulator-5560`, use `adb reverse tcp:18474 tcp:18474`. This exception supersedes the mock-only requirement only for this exact local origin; NAS, LAN, production, remote AI and former real sessions remain prohibited. Never commit the supplied credentials or session data. Keep local integration sessions isolated from mock and former production storage. Run synthetic instrumentation with `-Pphototube.localIntegration=false`.

The user explicitly prohibits access to NAS and all private data.

- Only local synthetic/mock data and bundled sample media may be used in development, UI review, tests, screenshots and demos.
- Never connect to a NAS, LAN server, production service, remote AI provider, or stored real account/session. Do not run Real* instrumentation tests or real-service scripts.
- Do not open an older installed application that can restore a real session. Only install/start a verified mock-only build.
- Do not read, copy, screenshot, log or export private media, credentials, cookies, network responses or device backups. Do not inspect old private screenshots to classify them.
- Do not weaken the mock-only request guard. Any future exception requires an explicit new user instruction.
- Review evidence must come from synthetic fixtures, with its mock origin recorded.

# Delivery cadence

- The user requests timely commits and pushes during subsequent development.
- After completing a coherent change and its relevant checks, commit it and push to the configured remote branch; do not leave multiple finished changes accumulating locally.
- Exclude local screenshots, recordings, test logs, build outputs, credentials and private data from commits. Never weaken the mock-only boundary to verify a change.
- Report the commit and push result accurately. Do not force-push or overwrite remote work.
